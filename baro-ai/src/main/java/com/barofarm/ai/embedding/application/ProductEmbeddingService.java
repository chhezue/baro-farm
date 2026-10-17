package com.barofarm.ai.embedding.application;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 상품명을 기반으로 Google GenAI 텍스트 임베딩 모델(gemini-embedding-001)을 호출하여
 * 768차원 dense_vector를 생성하는 임베딩 애플리케이션 서비스입니다.
 *
 * 실패는 예외로 올리지 않고 {@code null} 벡터로 표현합니다. 임베딩은 색인 경로에서만 부르고 그 경로는
 * 벡터 없이도 색인을 마치도록 돼 있어, 여기서 던진 예외가 HTTP 응답으로 나갈 경로가 없기 때문입니다.
 * 근거: docs/audits/cross-cutting/003-stale-comments-and-write-only-fields.md
 *
 * [주요 동작]
 * 1. 상품 색인 시점(`ProductIndexService.indexProducts`)에 호출되어 상품명 텍스트를 고차원 벡터 공간으로 투영합니다.
 * 2. `application.yml`의 `spring.ai.google.genai.embedding.text.options.dimensions: 768` 설정에 따라
 *    Elasticsearch 매핑(`dims=768`) 규격과 정확히 일치하는 768차원 float 배열을 생성합니다.
 * 3. 상품 여러 개는 {@link #embedProducts(List)}로 한 번에 보낸다. Spring AI의 Gemini 구현은 목록 전체를
 *    `batchEmbedContents` 한 번으로 보내므로 왕복이 N번에서 1번이 된다. 실측(2026-09-06, 무료 티어): 1건씩 보내면
 *    13회 성공 뒤 14번째 호출부터 429가 났고, 10건 묶음은 5번 연속 성공했다 — 배치는 속도보다 한도 문제다.
 *    근거: docs/audits/keyword-search/002-index-throughput-and-search-log.md M-3
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
     * 상품명 하나를 임베딩합니다. 묶음 호출이 실패했을 때의 폴백 경로에서만 씁니다.
     *
     * @param productName 임베딩 대상 상품명 (예: "유기농 무농약 달콤한 사과 1kg")
     * @return 768차원 실수 벡터, 만들지 못하면 {@code null}
     */
    private float[] embedProduct(String productName) {
        try {
            log.debug("🔄 [PRODUCT_EMBEDDING] Generating embedding for product: {}", productName);

            // [1단계: Spring AI EmbeddingModel을 통한 원격 임베딩 API 호출]
            // 단일 상품명을 List에 담아 호출하며, 결과가 비어있는 경우 get(0)에서 IndexOutOfBoundsException이 발생하여 catch 블록으로 이동합니다.
            var embeddings = embeddingModel.embed(List.of(productName));

            // [2단계: 768차원 실수 벡터 추출]
            // "벡터 없음" 상태는 null 하나로만 일관되게 표현하므로, 빈 배열 대신 정상 생성된 float[] 벡터를 그대로 반환합니다.
            float[] floatVector = embeddings.get(0);

            log.debug("✅ [PRODUCT_EMBEDDING] Successfully generated embedding with {} dimensions", floatVector.length);
            return floatVector;

        } catch (Exception e) {
            // [3단계: 실패 로깅 및 '벡터 없음' 반환]
            // 외부 AI API 호출이 실패해도 색인은 계속돼야 하므로 예외를 올리지 않고 null을 돌려줍니다.
            log.warn("⚠️ [PRODUCT_EMBEDDING] Failed to generate embedding for product: {}, Error: {}",
                    productName, e.getMessage());
            return null;
        }
    }

    /**
     * 상품명 여러 개를 한 번의 API 호출로 임베딩합니다. 결과는 입력 순서와 1:1이고, 만들지 못한 자리는 {@code null}입니다.
     *
     * 배치 호출은 전부 성공하거나 전부 실패한다. 배치가 실패하면 상품별로 한 번씩 다시 시도해 "실패한 상품만 벡터 없음"이라는
     * 1건 색인 때의 동작을 그대로 지킨다 — 배치 하나가 실패했다고 상품 N개가 전부 벡터를 잃지 않게 하기 위해서다.
     *
     * @param productNames 임베딩할 상품명 목록 (빈 목록이면 빈 결과)
     * @return 입력과 같은 길이의 벡터 목록. 실패한 항목은 {@code null}
     */
    public List<float[]> embedProducts(List<String> productNames) {
        if (productNames == null || productNames.isEmpty()) {
            return List.of();
        }

        // [1단계: 배치 호출 — 목록 전체가 batchEmbedContents 한 번으로 나간다]
        try {
            List<float[]> vectors = embeddingModel.embed(productNames);
            if (vectors.size() == productNames.size()) {
                log.debug("✅ [PRODUCT_EMBEDDING] Batch embedded {} products in one call", productNames.size());
                return vectors;
            }
            log.warn("⚠️ [PRODUCT_EMBEDDING] Batch returned {} vectors for {} products. Falling back per product",
                vectors.size(), productNames.size());
        } catch (Exception e) {
            log.warn("⚠️ [PRODUCT_EMBEDDING] Batch embedding failed for {} products ({}). Falling back per product",
                productNames.size(), e.getMessage());
        }

        // [2단계: 배치 실패 시 상품별 폴백 — 실패한 상품만 null]
        List<float[]> fallback = new ArrayList<>(productNames.size());
        for (String name : productNames) {
            fallback.add(embedProduct(name));
        }
        return fallback;
    }
}
