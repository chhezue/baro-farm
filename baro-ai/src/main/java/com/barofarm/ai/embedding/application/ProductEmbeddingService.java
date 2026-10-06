package com.barofarm.ai.embedding.application;

import com.barofarm.ai.embedding.exception.EmbeddingErrorCode;
import com.barofarm.exception.CustomException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 상품명을 기반으로 Google GenAI 텍스트 임베딩 모델(gemini-embedding-001)을 호출하여
 * 1536차원 dense_vector를 생성하는 임베딩 애플리케이션 서비스입니다.
 *
 * [주요 동작]
 * 1. 상품 색인 시점(`ProductIndexService.indexProduct`)에 호출되어 상품명 텍스트를 고차원 벡터 공간으로 투영합니다.
 * 2. `application.yml`의 `spring.ai.google.genai.embedding.text.options.dimensions: 1536` 설정에 따라
 *    Elasticsearch 매핑(`dims=1536`) 규격과 정확히 일치하는 1536차원 float 배열을 생성합니다.
 */
@Slf4j
@Service
public class ProductEmbeddingService {

    /** Spring AI Google GenAI 텍스트 임베딩 모델 Bean */
    private final EmbeddingModel embeddingModel;

    /**
     * Google GenAI 텍스트 임베딩 모델을 명시적 한정자(@Qualifier)로 주입받는 생성자.
     */
    public ProductEmbeddingService(@Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 단일 상품명을 전달받아 1536차원 임베딩 벡터(float[])를 생성합니다.
     *
     * @param productName 임베딩 대상 상품명 (예: "유기농 무농약 달콤한 사과 1kg")
     * @return 1536차원 실수 벡터 (실패 시 CustomException 발생)
     */
    public float[] embedProduct(String productName) {
        try {
            log.debug("🔄 [PRODUCT_EMBEDDING] Generating embedding for product: {}", productName);

            // [1단계: Spring AI EmbeddingModel을 통한 원격 임베딩 API 호출]
            // 단일 상품명을 List에 담아 호출하며, 결과가 비어있는 경우 get(0)에서 IndexOutOfBoundsException이 발생하여 catch 블록으로 이동합니다.
            var embeddings = embeddingModel.embed(List.of(productName));

            // [2단계: 1536차원 실수 벡터 추출]
            // "벡터 없음" 상태는 null 하나로만 일관되게 표현하므로, 빈 배열 대신 정상 생성된 float[] 벡터를 그대로 반환합니다.
            float[] floatVector = embeddings.get(0);

            log.debug("✅ [PRODUCT_EMBEDDING] Successfully generated embedding with {} dimensions", floatVector.length);
            return floatVector;

        } catch (Exception e) {
            // [3단계: 실패 로깅 및 비즈니스 예외 변환]
            // 외부 AI API 호출 실패 시 상세 로그를 기록하고 비즈니스 표준 예외(EMBEDDING_GENERATION_FAILED)를 발생시킵니다.
            log.warn("⚠️ [PRODUCT_EMBEDDING] Failed to generate embedding for product: {}, Error: {}",
                    productName, e.getMessage());
            throw new CustomException(EmbeddingErrorCode.EMBEDDING_GENERATION_FAILED);
        }
    }
}
