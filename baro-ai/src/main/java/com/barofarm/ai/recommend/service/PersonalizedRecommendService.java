package com.barofarm.ai.recommend.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.barofarm.ai.embedding.model.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.repository.UserProfileEmbeddingRepository;
import com.barofarm.ai.recommend.model.PersonalRecommendResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 사용자 프로필 벡터를 기반으로 개인화된 상품을 추천합니다.
     *
     * @param userId 사용자 ID
     * @param topK 추천할 상품 개수
     * @return 추천 상품 목록
     */
    public List<PersonalRecommendResponse> recommendProducts(UUID userId, int topK) {
        // 1. 사용자 프로필 벡터 조회
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null || profile.getUserProfileVector() == null) {
            log.warn("사용자 ID {}의 프로필 벡터가 없습니다. 임베딩을 먼저 생성해야 합니다.", userId);
            return List.of();
        }

        // 2. List<Double>을 float[]로 변환
        float[] userVector = convertToFloatArray(profile.getUserProfileVector());

        // 3. Elasticsearch 벡터 유사도 검색
        List<ProductDocument> similarProducts =
            findSimilarProductsByVector(userVector, topK);

        // 4. PersonalRecommendResponse로 변환
        return similarProducts.stream()
            .map(product -> new PersonalRecommendResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductCategory(),
                product.getPrice()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Elasticsearch에서 벡터 유사도 검색을 수행합니다.
     * script_score 쿼리를 사용하여 코사인 유사도를 계산합니다.
     */
    private List<ProductDocument> findSimilarProductsByVector(float[] userVector, int topK) {
        try {
            // Elasticsearch script_score 쿼리 생성
            NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                    .scriptScore(ss -> ss
                        .query(q2 -> q2
                            .bool(b -> b
                                .filter(f -> f
                                    .terms(t -> t
                                        .field("status")
                                        .terms(v -> v.value(
                                            List.of(
                                                FieldValue.of("ON_SALE"),
                                                FieldValue.of("DISCOUNTED")
                                            )
                                        ))
                                    )
                                )
                            )
                        )
                        .script(s -> s
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params(java.util.Map.of(
                                "query_vector", co.elastic.clients.json.JsonData.of(convertToDoubleList(userVector))
                            ))
                        )
                    )
                )
                .withPageable(PageRequest.of(0, topK))
                .build();

            SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(query, ProductDocument.class);

            List<ProductDocument> results = hits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());

            log.debug("사용자 벡터 기반 추천 결과: {}개 상품 발견", results.size());
            return results;

        } catch (Exception e) {
            log.error("벡터 유사도 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * List<Double>을 float[]로 변환합니다.
     */
    private float[] convertToFloatArray(List<Double> doubleList) {
        if (doubleList == null || doubleList.isEmpty()) {
            throw new IllegalArgumentException("벡터 리스트가 비어있습니다.");
        }

        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            floatArray[i] = doubleList.get(i).floatValue();
        }
        return floatArray;
    }

    /**
     * float[]을 List<Double>로 변환합니다.
     * Elasticsearch script params에 전달하기 위해 필요합니다.
     */
    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> doubleList = new java.util.ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
}
