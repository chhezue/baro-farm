package com.barofarm.ai.search.application;

import com.barofarm.ai.embedding.application.ProductEmbeddingService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.domain.ProductAutocompleteDocument;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 상품 색인 서비스.
 *
 * Elasticsearch 메인 상품 인덱스(`product`)와 자동완성 인덱스(`product-autocomplete`)에
 * 상품 문서를 쓰고 지우며, 색인 시 상품 임베딩 생성 및 LLM 기반 재료명 정규화를 수행합니다.
 * 조회(자동완성 검색)는 {@link ProductSearchService}가 담당합니다.
 *
 * 상품은 묶음으로 색인한다(2026-09-06, 감사 keyword-search/002 A-1). 임베딩은 묶음당 API 1회, ES 저장은 인덱스당
 * `saveAll` 1회(bulk)다. 재료명 LLM 호출만 상품마다 1회로 남아 있다 — 프롬프트를 여러 상품용으로 바꾸면 골든셋 측정
 * (cart-recipe/004, 17/17)이 무효가 되어 별도 라운드로 미뤘다. 즉 묶음 색인의 시간 이득은 임베딩·ES 몫이고,
 * 색인 시간의 대부분을 차지하는 재료명 호출은 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    /** 메인 상품 검색 Elasticsearch 레포지토리 */
    private final ProductSearchRepository repository;

    /** 상품명 접두어 자동완성 Elasticsearch 레포지토리 */
    private final ProductAutocompleteRepository autocompleteRepository;

    /** 상품명 텍스트 기반 768차원 dense_vector 임베딩 생성 서비스 */
    private final ProductEmbeddingService productEmbeddingService;

    /** 상품명 및 카테고리로부터 요리 재료 표준명을 추출하는 LLM 정규화 컴포넌트 */
    private final ProductIngredientNormalizer ingredientNormalizer;

    /**
     * 상품 여러 개를 Elasticsearch 메인 인덱스 및 자동완성 인덱스에 묶음으로 색인합니다.
     * 같은 상품 ID가 묶음 안에 두 번 있으면 뒤의 것이 이긴다(ES가 같은 ID를 덮어쓴다) — 이벤트 순서가 곧 최종 상태다.
     *
     * @param requests 색인할 상품 정보 목록 (빈 목록이면 아무것도 하지 않는다)
     * @return 저장된 ProductDocument 목록 (입력 순서)
     */
    public List<ProductDocument> indexProducts(List<ProductIndexRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        // [1단계: 상품명 임베딩 벡터(768차원) 묶음 생성]
        // 외부 임베딩 모델 장애가 발생하더라도 상품의 기본 키워드 검색은 계속 가능해야 하므로,
        // 실패한 상품은 벡터 null로 두고 색인 절차를 중단 없이 진행합니다(폴백은 ProductEmbeddingService 안에 있다).
        List<float[]> vectors = productEmbeddingService.embedProducts(
            requests.stream().map(ProductIndexRequest::productName).toList());

        List<ProductDocument> docs = new ArrayList<>(requests.size());
        List<ProductAutocompleteDocument> autocompleteDocs = new ArrayList<>(requests.size());

        for (int i = 0; i < requests.size(); i++) {
            ProductIndexRequest request = requests.get(i);
            float[] vector = vectors.get(i);
            if (vector == null) {
                log.error("❌ Product embedding failed. productId={} — 벡터 없이 색인합니다", request.productId());
            }

            // [2단계: LLM을 통한 요리 재료 표준명 사전 정규화 (상품별 1회)]
            // 레시피 추천 시점마다 LLM을 호출하는 오버헤드를 없애기 위해, 색인 시점에 상품명과 카테고리로부터
            // 표준 재료명을 1회 추출하여 문서에 함께 저장합니다. (추출 실패 시 빈 문자열 반환)
            String ingredientName =
                ingredientNormalizer.normalize(request.productName(), request.productCategoryName());

            // [3단계: 메인 상품 검색 문서(ProductDocument) 생성]
            // 정렬 2순위인 updatedAt에는 이벤트가 실어 보낸 값을 그대로 쓴다. 색인 시각을 넣으면
            // 위의 임베딩·재료명 LLM 호출에 걸린 시간만큼 상품마다 제각각 밀려 순서가 흔들린다.
            // 다만 이 값은 baro-shopping이 이벤트를 내보낸 시각이지 상품이 바뀐 시각은 아니다
            // (ProductEventPublisher가 Instant.now()를 찍는다). 값이 없는 이벤트만 현재 시각으로 대신한다.
            Instant updatedAt = request.updatedAt() != null ? request.updatedAt() : Instant.now();

            docs.add(new ProductDocument(
                request.productId(),
                request.productName(),
                request.productCategoryId(),
                request.productCategoryCode(),
                request.productCategoryName(),
                request.price(),
                request.status(),
                updatedAt,
                ingredientName,
                vector));

            // 자동완성 전용 인덱스에도 상품명과 상태를 함께 저장한다(접두어 검색·상태 필터용).
            autocompleteDocs.add(
                new ProductAutocompleteDocument(request.productId(), request.productName(), request.status()));
        }

        // [4단계: 두 인덱스에 bulk 저장 — 상품 수와 무관하게 인덱스당 왕복 1회]
        autocompleteRepository.saveAll(autocompleteDocs);
        List<ProductDocument> saved = new ArrayList<>(requests.size());
        repository.saveAll(docs).forEach(saved::add);
        return saved;
    }

    /**
     * 상품 삭제 요청을 처리합니다 (Kafka Consumer에서 호출).
     * 메인 상품 검색 인덱스와 자동완성 인덱스에서 해당 상품 문서를 모두 제거합니다.
     *
     * @param productId 삭제할 상품 ID
     */
    public void deleteProduct(UUID productId) {
        // [1단계: 메인 상품 검색 인덱스(product)에서 문서 삭제]
        repository.deleteById(productId);

        // [2단계: 실시간 자동완성 인덱스(product-autocomplete)에서 문서 삭제]
        autocompleteRepository.deleteById(productId);
    }
}
