package com.barofarm.ai.embedding.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * OpenAI 임베딩을 사용하여 텍스트를 벡터로 변환하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextEmbeddingService {

    private final @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel;

    /**
     * 텍스트를 벡터로 변환
     * @param text 변환할 텍스트
     * @return float 배열 형태의 벡터
     */
    public float[] embedText(String text) {
        try {
            log.debug("🔄 [EMBEDDING] Generating embedding for text: {}", text);

            var embeddings = embeddingModel.embed(List.of(text));

            if (embeddings.isEmpty()) {
                log.warn("⚠️ [EMBEDDING] No embedding generated for text: {}", text);
                return new float[0];
            }

            // float 배열 반환
            float[] floatVector = embeddings.get(0);

            log.debug("✅ [EMBEDDING] Successfully generated embedding with {} dimensions", floatVector.length);
            return floatVector;

        } catch (Exception e) {
            log.error("❌ [EMBEDDING] Failed to generate embedding for text: {}, Error: {}", text, e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * 상품명을 기반으로 벡터 생성
     */
    public float[] embedProduct(String productName) {
        return embedText(productName);
    }

    /**
     * 벡터화 예시를 보여주는 테스트 메소드
     */
    public void showEmbeddingExample() {
        String sampleText = "강아지 사료 프리미엄";
        log.info("🔍 [EXAMPLE] 샘플 텍스트: '{}'", sampleText);

        float[] vector = embedText(sampleText);

        log.info("📊 [EXAMPLE] 생성된 벡터 차원: {}", vector.length);
        log.info("📊 [EXAMPLE] 벡터 첫 10개 값: [{}]", java.util.Arrays.toString(java.util.Arrays.copyOf(vector, 10)));
        log.info("📊 [EXAMPLE] 벡터 마지막 10개 값: [{}]",
            java.util.Arrays.toString(java.util.Arrays.copyOfRange(vector, vector.length - 10, vector.length)));
    }
}
