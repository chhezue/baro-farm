package com.barofarm.ai.embedding.application;

import com.barofarm.ai.embedding.exception.EmbeddingErrorCode;
import com.barofarm.exception.CustomException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public ProductEmbeddingService(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // 상품명을 기반으로 벡터 생성 (ES에서 호출)
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
            throw new CustomException(EmbeddingErrorCode.EMBEDDING_GENERATION_FAILED);
        }
    }
}
