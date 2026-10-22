package com.barofarm.ai.search.application;

import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.client.ProductCatalogClient;
import com.barofarm.ai.search.infrastructure.client.dto.CatalogProduct;
import com.barofarm.dto.CustomPage;
import com.barofarm.dto.ResponseDto;
import com.barofarm.exception.CustomException;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 상품을 원본(`baro-shopping`)에서 다시 읽어 색인합니다. 매핑을 바꾼 뒤 인덱스를 채우는 용도입니다.
 *
 * <p><b>왜 필요한가.</b> 평소 색인은 Kafka 상품 이벤트로 이뤄지는데, 이미 지나간 이벤트는 다시 받을 수
 * 없습니다. 그래서 매핑이 바뀌어 인덱스를 새로 만들면 그 안이 비어 있고, 채우려면 발행 모듈 담당자에게
 * "전 상품을 다시 발행해 달라"고 부탁하는 수밖에 없었습니다. 실제로 그 부탁이 남긴 채로 인덱스 재생성
 * 항목이 계속 쌓여 있었습니다(docs/audits/open-items.md A-3). 이제 이쪽에서 직접 읽어 옵니다.
 *
 * <p><b>한 번에 한 페이지만 합니다.</b> 전 상품을 한 요청에서 처리하지 않는 이유는 둘입니다. 상품마다
 * 재료명 LLM을 부르므로 수천 건이면 요청이 몇 시간짜리가 되고, 무료 티어 한도에 걸립니다(1건씩 보내면
 * 13회 뒤 429가 났습니다 — docs/audits/open-items.md C-12). 페이지를 부르는 쪽이 속도를 조절하면
 * 한도에 맞춰 끊어 갈 수 있고, 중간에 실패해도 그 페이지부터 다시 하면 됩니다.
 *
 * <p><b>인덱스를 지우지는 않습니다.</b> 매핑을 바꿔야 하면 사람이 먼저 인덱스를 지우고 다시 만든 뒤 이
 * 경로를 부릅니다 — 되돌릴 수 없는 삭제를 API 뒤에 숨기지 않기 위해서입니다. 색인은 상품 ID로 덮어쓰므로
 * 인덱스가 그대로인 상태에서 불러도 안전합니다(같은 값으로 다시 쓸 뿐입니다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReindexService {

    /** 한 번에 읽을 수 있는 최대 상품 수. 상품마다 LLM을 부르므로 너무 크면 요청이 끝나지 않는다 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 동시에 두 번 돌지 않게 막는 표시. 겹치면 같은 상품에 외부 AI 호출이 두 배로 나간다.
     *
     * <p><b>이 가드는 서버 한 대 안에서만 유효하다.</b> 지금은 파드가 하나라(`k8s/apps/baro-ai/deployment.yml`
     * `replicas: 1`) 충분하지만, 나중에 대수를 늘리면 두 대가 각자 "나는 안 돌고 있다"고 판단해 동시에 돈다.
     * 그때는 여기가 아니라 공유 잠금(예: Elasticsearch 문서 하나를 잠금으로 쓰는 방식)이 필요하다.
     * 지금 그것을 미리 넣지 않는 이유는 파드가 하나라 쓸 일이 없기 때문이다.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ProductCatalogClient productCatalogClient;
    private final ProductIndexService productIndexService;

    /**
     * 상품 한 페이지를 원본에서 읽어 색인합니다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param size 이 페이지에서 읽을 상품 수 (1~100)
     * @return 이 페이지의 결과와 다음 페이지가 있는지
     */
    public ReindexPageResult reindexPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(SearchErrorCode.INVALID_PAGE_SIZE);
        }
        if (!running.compareAndSet(false, true)) {
            throw new CustomException(SearchErrorCode.REINDEX_ALREADY_RUNNING);
        }

        try {
            ResponseDto<CustomPage<CatalogProduct>> response = productCatalogClient.getProducts(page, size);
            CustomPage<CatalogProduct> catalogPage = response != null ? response.data() : null;
            if (catalogPage == null || catalogPage.content() == null) {
                log.warn("상품 목록 응답이 비어 재색인을 건너뜁니다. page={}", page);
                return new ReindexPageResult(page, 0, 0, false);
            }

            List<ProductIndexRequest> requests = catalogPage.content().stream()
                .filter(product -> product.id() != null)
                .map(ProductReindexService::toIndexRequest)
                .toList();

            productIndexService.indexProducts(requests);

            log.info("재색인 완료 - page={}, 색인 {}건, 전체 {}건, 다음 페이지 {}",
                page, requests.size(), catalogPage.totalElements(), catalogPage.hasNext());

            return new ReindexPageResult(
                page, requests.size(), catalogPage.totalElements(), catalogPage.hasNext());
        } finally {
            running.set(false);
        }
    }

    /**
     * 상류 상품을 색인 요청으로 바꿉니다.
     *
     * 상류는 시각을 지역 시간대 없이 `LocalDateTime`으로 주므로 서버 시간대를 붙여 절대 시각으로 바꿉니다.
     * 이벤트 경로와 달리 이 값은 <b>상품이 실제로 바뀐 시각</b>이라, 재색인한 상품의 최신순 정렬이
     * 이벤트로 색인했을 때보다 정확합니다(이벤트는 발행 시각이 들어갑니다).
     */
    private static ProductIndexRequest toIndexRequest(CatalogProduct product) {
        return new ProductIndexRequest(
            product.id(),
            product.productName(),
            product.categoryId(),
            product.categoryCode(),
            product.categoryName(),
            product.price(),
            product.productStatus(),
            product.updatedAt() == null ? null : product.updatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 한 페이지를 재색인한 결과입니다.
     *
     * @param page          방금 처리한 페이지 번호
     * @param indexed       이 페이지에서 색인한 상품 수
     * @param totalProducts 상류가 알려 준 전체 상품 수
     * @param hasNext       다음 페이지가 남아 있는지. 부르는 쪽은 이 값이 false가 될 때까지 페이지를 올린다
     */
    public record ReindexPageResult(int page, int indexed, long totalProducts, boolean hasNext) { }
}
