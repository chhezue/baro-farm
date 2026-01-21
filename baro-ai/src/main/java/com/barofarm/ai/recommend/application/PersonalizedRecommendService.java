package com.barofarm.ai.recommend.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.barofarm.ai.common.exception.CustomException;
import com.barofarm.ai.embedding.domain.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.infrastructure.elasticsearch.UserProfileEmbeddingRepository;
import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.recommend.exception.RecommendErrorCode;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // 사용자 프로필 벡터를 기반으로 개인화된 상품을 추천
    public List<ProductRecommendResponse> recommendProducts(UUID userId, int topK) {
        // 1. 사용자 프로필 벡터 조회
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null || profile.getUserProfileVector() == null) {
            log.warn("사용자 ID {}의 프로필 벡터가 없습니다. 임베딩을 먼저 생성해야 합니다.", userId);
            return List.of();
        }

        // 이미 구매했거나 장바구니에 담은 상품은 추천하지 않기 위해 sourceProductIds 사용
        List<String> experiencedProductIds = profile.getSourceProductIds() != null
            ? profile.getSourceProductIds()
            : List.of();  // null이면 빈 리스트 사용

        log.debug("사용자 {}의 추천에서 임베딩의 sourceProductIds({}개)로 상품 제외",
                 userId, experiencedProductIds.size());

        // 3. List<Double>을 float[]로 변환
        float[] userVector = convertToFloatArray(profile.getUserProfileVector());

        // 4. Elasticsearch 벡터 유사도 검색
        List<ProductRecommendResponse> results =
            findSimilarProductsByVector(userVector, topK, experiencedProductIds);

        return results;
    }

    // Elasticsearch에서 벡터 유사도 검색을 수행
    @SuppressWarnings("checkstyle:MethodLength")
    private List<ProductRecommendResponse> findSimilarProductsByVector(
        float[] userVector,
        int topK,
        List<String> experiencedProductIds
    ) {
        try {
            // 중복 제거를 위해 더 많은 상품을 가져온 후 필터링 (topK * 2로 충분히 가져옴)
            NativeQuery queryWithBuffer = NativeQuery.builder()
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
                            .params(Map.of(
                                "query_vector", co.elastic.clients.json.JsonData.of(convertToDoubleList(userVector))
                            ))
                        )
                    )
                )
                .withPageable(PageRequest.of(0, topK * 2)) // 중복 제거를 위해 더 많이 가져옴
                .build();

            SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(queryWithBuffer, ProductDocument.class);

            // 이미 주문하거나 장바구니에 담은 상품 ID 수집
            Set<UUID> excludedProductIds = experiencedProductIds.stream()
                .map(UUID::fromString)  // String을 UUID로 변환
                .collect(Collectors.toSet());

            List<ProductRecommendResponse> results = new ArrayList<>();
            Set<UUID> seenProductIds = new HashSet<>(); // 중복 제거용 Set

            for (SearchHit<ProductDocument> hit : hits.getSearchHits()) {
                ProductDocument product = hit.getContent();
                UUID productId = product.getProductId();

                // 중복 제거: 이미 본 상품은 건너뛰기
                if (seenProductIds.contains(productId)) {
                    log.debug("중복 상품 제거: productId={}, productName={}", productId, product.getProductName());
                    continue;
                }

                // 이미 주문하거나 장바구니에 담은 상품 제외
                if (excludedProductIds.contains(productId)) {
                    log.debug("이미 주문/장바구니에 담은 상품 제외: productId={}, productName={}",
                        productId, product.getProductName());
                    continue;
                }

                // topK 개수만큼만 반환
                if (results.size() >= topK) {
                    break;
                }

                seenProductIds.add(productId);

                results.add(new ProductRecommendResponse(
                    productId,
                    product.getProductName(),
                    product.getProductCategory(),
                    product.getPrice()
                ));
            }

            log.debug("사용자 벡터 기반 추천 결과: {}개 상품 발견 (중복 제거 후)", results.size());
            return results;

        } catch (Exception e) {
            log.error("벡터 유사도 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // List<Double>을 float[]로 변환합니다.
     private float[] convertToFloatArray(List<Double> doubleList) {
        if (doubleList == null || doubleList.isEmpty()) {
            throw new CustomException(RecommendErrorCode.INVALID_VECTOR_DATA);
        }

        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            floatArray[i] = doubleList.get(i).floatValue();
        }
        return floatArray;
    }

    // float[]을 List<Double>로 변환합니다.
    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> doubleList = new java.util.ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
}
