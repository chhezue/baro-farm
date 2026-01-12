package com.barofarm.ai.embedding.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 상품 임베딩을 생성하는 서비스
 * 상품명을 기반으로 벡터를 생성하여 Elasticsearch에 저장합니다.
 */
@Slf4j
@Service
public class ProductEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public ProductEmbeddingService(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 상품명을 기반으로 벡터 생성
     * @param productName 상품명
     * @return float 배열 형태의 벡터
     */
    public float[] embedProduct(String productName) {
        try {
            log.debug("🔄 [PRODUCT_EMBEDDING] Generating embedding for product: {}", productName);

            var embeddings = embeddingModel.embed(List.of(productName));

            if (embeddings.isEmpty()) {
                log.warn("⚠️ [PRODUCT_EMBEDDING] No embedding generated for product: {}", productName);
                return new float[0];
            }

            // float 배열 반환
            float[] floatVector = embeddings.get(0);

            log.debug("✅ [PRODUCT_EMBEDDING] Successfully generated embedding with {} dimensions", floatVector.length);
            return floatVector;

        } catch (Exception e) {
            log.error("❌ [PRODUCT_EMBEDDING] Failed to generate embedding for product: {}, Error: {}",
                    productName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate product embedding", e);
        }
    }

    /**
     * 벡터화 예시를 보여주는 테스트 메소드
     */
    public void showEmbeddingExample() {
        String sampleText = "강아지 사료 프리미엄";
        log.info("🔍 [EXAMPLE] 샘플 상품명: '{}'", sampleText);

        float[] vector = embedProduct(sampleText);

        log.info("📊 [EXAMPLE] 생성된 벡터 차원: {}", vector.length);
        log.info("📊 [EXAMPLE] 벡터 첫 10개 값: [{}]",
                java.util.Arrays.toString(java.util.Arrays.copyOf(vector, 10)));
        log.info("📊 [EXAMPLE] 벡터 마지막 10개 값: [{}]",
                java.util.Arrays.toString(java.util.Arrays.copyOfRange(vector, vector.length - 10, vector.length)));
    }
}
