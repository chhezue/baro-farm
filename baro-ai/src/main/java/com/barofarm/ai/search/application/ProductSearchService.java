package com.barofarm.ai.search.application;

import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductQueryFactory;
import com.barofarm.dto.CustomPage;
import com.barofarm.exception.CustomException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

/**
 * 상품 인덱스를 읽는 서비스입니다. 키워드 검색과, 서버 내부에서 상품을 찾아야 하는 조회를 맡습니다.
 *
 * <p>조회만 하고 아무것도 기록하지 않습니다. 2026-09-07까지는 회원 검색마다 검색 로그를 남기고 취향 벡터
 * 갱신을 요청했으나, 검색을 취향 신호에서 빼면서 그 경로를 통째로 지웠습니다 — 검색 결과 상위에 뜬 상품을
 * 그 사람의 취향으로 삼는 방식이었기 때문입니다.
 * 근거: docs/audits/taste-vector/005-signal-and-weight-simplification.md
 *
 * <p><b>2026-09-09에 셋으로 나눴습니다.</b> 한 클래스가 426줄이었고 성격이 다른 셋이 섞여 있었습니다 —
 * 질의 조립({@link ProductQueryFactory}), 검색(이 클래스), 자동완성({@link ProductAutocompleteService}).
 * 자동완성은 캐시와 입력 길이 규칙을 따로 갖고 있어 특히 섞일 이유가 없었습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    /** 한 페이지에 담을 수 있는 최대 상품 수 */
    private static final int MAX_PAGE_SIZE = 100;

    /** Elasticsearch가 한 번에 훑을 수 있는 최대 결과 위치 (index.max_result_window 기본값) */
    private static final int MAX_RESULT_WINDOW = 10_000;

    /** Spring Data Elasticsearch 네이티브 쿼리 실행 템플릿 */
    private final ElasticsearchOperations operations;

    /**
     * 키워드 및 다중 필터 조건에 부합하는 상품 목록을 페이징 조회합니다.
     *
     * @param request 검색 요청 파라미터 (키워드, 카테고리 목록, 가격 범위)
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 상품 수 (1~100)
     */
    public CustomPage<ProductSummaryResponse> search(ProductSearchRequest request, int page, int size) {
        Pageable pageable = toPageable(page, size);
        SearchHits<ProductDocument> hits =
            operations.search(ProductQueryFactory.buildSearchQuery(request, pageable), ProductDocument.class);

        return CustomPage.of(hits.getTotalHits(), toResponses(hits), pageable);
    }

    /**
     * 부족 재료 상품 조회처럼 키워드 여러 개를 한 번에 찾을 때 씁니다. 키워드마다 `search`를 따로 부르면
     * ES 왕복이 키워드 수만큼 순차로 늘어나므로(2026-09-06 감사 cart-recipe/005 A-2), 같은 쿼리를
     * 키워드 수만큼 만들어 `_msearch` 한 번으로 보냅니다.
     *
     * 배치 안의 쿼리 하나가 실패하면 Spring Data ES는 그 항목을 결과에서 빼고 자리를 당겨 버립니다(어느 것이
     * 실패했는지는 알 수 없습니다). 그러면 호출자가 순서로 맞추는 대응이 어긋나므로, 결과 수가 키워드 수와
     * 다르면 전부 빈 목록으로 돌려줍니다 — 상품 카드 하나를 엉뚱한 재료에 붙이는 것보다 카드가 빠지는 쪽이 낫습니다.
     *
     * @param keywords       찾을 키워드 목록 (빈 목록이면 빈 결과)
     * @param sizePerKeyword 키워드당 최대 상품 수 (1~100)
     * @return 요청한 키워드 순서와 1:1로 대응하는 상품 목록의 목록
     */
    public List<List<ProductSummaryResponse>> searchByKeywords(List<String> keywords, int sizePerKeyword) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Pageable pageable = toPageable(0, sizePerKeyword);

        List<NativeQuery> queries = keywords.stream()
            .map(keyword -> ProductQueryFactory.buildIngredientQuery(keyword, pageable))
            .toList();

        List<SearchHits<ProductDocument>> hitsPerKeyword = operations.multiSearch(queries, ProductDocument.class);
        if (hitsPerKeyword.size() != keywords.size()) {
            log.warn("multi-search 결과 수({})가 키워드 수({})와 달라 순서를 믿을 수 없어 전부 빈 결과로 돌립니다. keywords={}",
                hitsPerKeyword.size(), keywords.size(), keywords);
            return keywords.stream().map(k -> List.<ProductSummaryResponse>of()).toList();
        }

        return hitsPerKeyword.stream().map(this::toResponses).toList();
    }

    /**
     * 상품 ID 목록으로 상품 문서를 한 번에 읽습니다. 레시피 추천이 장바구니 상품의 재료 표준명을 읽을 때 씁니다.
     *
     * 벡터는 받지 않습니다 — 부르는 쪽이 쓰지 않는데 문서당 약 10KB가 붙기 때문입니다. 상품 인덱스에 질의하는
     * 코드를 이 클래스 하나로 모으려고 여기 둡니다. 예전에는 레시피 추천이 ElasticsearchOperations를 직접
     * 주입받아 같은 질의를 따로 조립했습니다.
     * 근거: docs/audits/cross-cutting/003-stale-comments-and-write-only-fields.md B-2
     *
     * @return 찾은 상품 문서 목록. 색인되지 않은 ID는 결과에서 빠지므로 요청보다 적을 수 있습니다
     */
    public List<ProductDocument> findByIds(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        NativeQuery query = ProductQueryFactory.byIds(
            productIds.stream().map(UUID::toString).toList(),
            PageRequest.of(0, productIds.size()));

        return operations.search(query, ProductDocument.class).getSearchHits().stream()
            .map(hit -> hit.getContent())
            .toList();
    }

    /**
     * 검색 히트를 응답 항목 목록으로 변환합니다.
     */
    private List<ProductSummaryResponse> toResponses(SearchHits<ProductDocument> hits) {
        return hits.getSearchHits().stream()
            .map(hit -> ProductSummaryResponse.from(hit.getContent()))
            .toList();
    }

    /**
     * 페이징 파라미터를 검증하고 Pageable로 변환합니다.
     * 범위를 벗어나면 ES가 400을 돌려주거나(from + size 초과) 응답이 무제한으로 커지므로 진입부에서 끊습니다.
     */
    private Pageable toPageable(int page, int size) {
        if (page < 0) {
            throw new CustomException(SearchErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(SearchErrorCode.INVALID_PAGE_SIZE);
        }
        if ((long) (page + 1) * size > MAX_RESULT_WINDOW) {
            throw new CustomException(SearchErrorCode.PAGE_RANGE_EXCEEDED);
        }
        return PageRequest.of(page, size);
    }
}
