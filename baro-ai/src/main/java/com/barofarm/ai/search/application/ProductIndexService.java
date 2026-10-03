package com.barofarm.ai.search.application;

import com.barofarm.ai.embedding.application.ProductEmbeddingService;
import com.barofarm.ai.embedding.application.UserProfileEmbeddingService;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.domain.ProductAutocompleteDocument;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 상품 인덱싱 및 관련 서비스
 * Elasticsearch 상품 인덱스 관리, 자동완성, 사용자 프로필 업데이트를 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private final ProductSearchRepository repository;
    private final ProductAutocompleteRepository autocompleteRepository;
    private final ProductEmbeddingService productEmbeddingService;
    private final UserProfileEmbeddingService userProfileEmbeddingService;
    private final ProductIngredientNormalizer ingredientNormalizer;

    /**
     * 상품 문서를 ES에 저장 (인덱싱), updatedAt은 현재 시각으로 자동 설정
     * Kafka Consumer에서 호출됨
     */
    public ProductDocument indexProduct(ProductIndexRequest request) {
        float[] vector = null;

        // 임베딩이 실패하더라도 인덱싱은 계속되어야 함.
        try {
            // 상품 이름을 기반으로 임베딩 생성
            vector = productEmbeddingService.embedProduct(request.productName());
        } catch (Exception e) {
            log.error("❌ Product embedding failed. productId=" + request.productId() + ", error=" + e.getMessage(), e);
        }

        // 요리 재료명은 색인 시점에 1회만 계산한다. 추천 요청마다 LLM으로 뽑던 것을 여기로 옮긴 것.
        // normalize()는 예외를 던지지 않고 실패하면 빈 문자열을 돌려주므로 여기서 잡을 예외가 없다.
        // 빈 값으로 색인된 상품은 레시피 추천이 보유 재료에서 건너뛰고 재색인 대상으로 센다.
        // 근거: docs/decisions/002-ingredient-name-at-index-time.md
        String ingredientName = ingredientNormalizer.normalize(request.productName(), request.productCategoryName());

        ProductDocument doc =
            new ProductDocument(
                request.productId(),
                request.productName(),
                request.productCategoryId(),
                request.productCategoryCode(),
                request.productCategoryName(),
                request.price(),
                request.status(),
                Instant.now(),
                ingredientName,
                vector);

        // 자동완성 인덱스에도 저장 (status 포함하여 필터링 가능하도록)
        ProductAutocompleteDocument autocompleteDoc =
            new ProductAutocompleteDocument(request.productId(), request.productName(), request.status());
        autocompleteRepository.save(autocompleteDoc);

        return repository.save(doc);
    }

    /**
     * 상품 삭제 (Kafka Consumer에서 호출됨)
     */
    public void deleteProduct(UUID productId) {
        repository.deleteById(productId); // Document 삭제
        autocompleteRepository.deleteById(productId); // 자동완성 삭제
    }

    /**
     * 상품 자동완성 검색
     * 로컬 캐시(Caffeine, CacheConfig) 적용 — 입력할 때마다 호출되고 흔한 접두어는 반복 요청이 많아
     * 반복 호출 절약 효과가 큰 지점. 근거: docs/decisions/001-autocomplete-caching.md
     */
    @Cacheable(value = "autocomplete", key = "#query + '-' + #size")
    public List<ProductAutoCompleteResponse> autocomplete(String query, int size) {
        if (query == null || query.length() < 2) {
            return List.of(); // 최소 2글자 이상으로 제한
        }
        Pageable pageable = PageRequest.of(0, size);
        return autocompleteRepository.findByPrefix(query, pageable).stream()
            .map(document -> new ProductAutoCompleteResponse(document.getProductId(), document.getProductName()))
            .toList();
    }

    /**
     * 사용자 프로필 벡터를 비동기로 업데이트합니다.
     * 검색 로그 저장 후 호출되며, 검색 성능에 영향을 주지 않도록 별도 스레드에서 실행됩니다.
     */
    @Async("profileUpdateExecutor")
    public void updateUserProfileAsync(UUID userId) {
        try {
            log.debug("🔄 [INDEX_SERVICE] Updating user profile embedding for user: {}", userId);
            userProfileEmbeddingService.updateUserProfileEmbedding(userId);
            log.debug("✅ [INDEX_SERVICE] Successfully updated user profile embedding for user: {}", userId);
        } catch (Exception e) {
            log.warn("⚠️ [INDEX_SERVICE] Failed to update user profile embedding for user: {}, error: {}",
                    userId, e.getMessage());
            // 프로필 업데이트 실패는 검색 결과에 영향을 주지 않음
        }
    }
}
