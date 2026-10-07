package com.barofarm.ai.search.application;

import com.barofarm.ai.embedding.application.ProductEmbeddingService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.domain.ProductAutocompleteDocument;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import java.time.Instant;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    /** 메인 상품 검색 Elasticsearch 레포지토리 */
    private final ProductSearchRepository repository;

    /** 상품명 접두어 자동완성 Elasticsearch 레포지토리 */
    private final ProductAutocompleteRepository autocompleteRepository;

    /** 상품명 텍스트 기반 1536차원 dense_vector 임베딩 생성 서비스 */
    private final ProductEmbeddingService productEmbeddingService;

    /** 상품명 및 카테고리로부터 요리 재료 표준명을 추출하는 LLM 정규화 컴포넌트 */
    private final ProductIngredientNormalizer ingredientNormalizer;

    /**
     * 상품 정보를 Elasticsearch 메인 인덱스 및 자동완성 인덱스에 동기화 저장(색인)합니다.
     *
     * @param request 색인할 상품 정보 DTO
     * @return 저장된 ProductDocument 엔티티
     */
    public ProductDocument indexProduct(ProductIndexRequest request) {
        float[] vector = null;

        // [1단계: 상품명 임베딩 벡터(1536차원) 생성]
        // 상품명을 기반으로 Google GenAI 1536차원 임베딩 벡터를 생성합니다.
        // 외부 임베딩 모델 장애가 발생하더라도 상품의 기본 키워드 검색은 계속 가능해야 하므로,
        // 예외를 catch하여 에러 로그만 남기고 벡터는 null로 유지한 채 색인 절차를 중단 없이 진행합니다.
        try {
            vector = productEmbeddingService.embedProduct(request.productName());
        } catch (Exception e) {
            log.error("❌ Product embedding failed. productId=" + request.productId() + ", error=" + e.getMessage(), e);
        }

        // [2단계: LLM을 통한 요리 재료 표준명 사전 정규화]
        // 레시피 추천 시점마다 LLM을 호출하는 오버헤드를 없애기 위해, 색인 시점에 상품명과 카테고리로부터
        // 표준 재료명을 1회 추출하여 문서에 함께 저장합니다. (추출 실패 시 빈 문자열 반환)
        String ingredientName = ingredientNormalizer.normalize(request.productName(), request.productCategoryName());

        // [3단계: 메인 상품 검색 문서(ProductDocument) 생성 및 저장]
        // 정렬 2순위인 updatedAt에는 이벤트가 실어 보낸 값을 그대로 쓴다. 색인 시각을 넣으면
        // 바로 위의 임베딩·재료명 LLM 호출에 걸린 시간만큼 상품마다 제각각 밀려 순서가 흔들린다.
        // 다만 이 값은 baro-shopping이 이벤트를 내보낸 시각이지 상품이 바뀐 시각은 아니다
        // (ProductEventPublisher가 Instant.now()를 찍는다). 값이 없는 이벤트만 현재 시각으로 대신한다.
        Instant updatedAt = request.updatedAt() != null ? request.updatedAt() : Instant.now();

        ProductDocument doc =
            new ProductDocument(
                request.productId(),
                request.productName(),
                request.productCategoryId(),
                request.productCategoryCode(),
                request.productCategoryName(),
                request.price(),
                request.status(),
                updatedAt,
                ingredientName,
                vector);

        // [4단계: 자동완성 전용 인덱스(ProductAutocompleteDocument) 동기화 저장]
        // 접두어 자동완성 검색 및 상태 필터링에 활용하기 위해 'product-autocomplete' 인덱스에도 상품명과 상태를 저장합니다.
        ProductAutocompleteDocument autocompleteDoc =
            new ProductAutocompleteDocument(request.productId(), request.productName(), request.status());
        autocompleteRepository.save(autocompleteDoc);

        return repository.save(doc);
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
