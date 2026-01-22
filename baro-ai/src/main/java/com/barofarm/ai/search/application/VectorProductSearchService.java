package com.barofarm.ai.search.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

/**
 * 벡터 기반 상품 검색 서비스
 * 코사인 유사도를 이용한 상품 유사도 검색을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 벡터 유사도를 기반으로 유사 상품을 검색합니다.
     * PersonalizedRecommendService와 SimilarProductService의 공통 로직을 추출했습니다.
     *
     * @param queryVector 검색할 벡터
     * @param topK 반환할 최대 상품 수
     * @param excludeProductIds 제외할 상품 ID 목록 (이미 주문/장바구니에 담은 상품들)
     * @param excludeSelfProductId 자기 자신을 제외할 상품 ID (유사 상품 검색 시 사용)
     * @param removeDuplicates 중복 제거 여부 (true일 경우 topK * 2개를 가져와 필터링)
     * @return 유사 상품 목록
     */
    public List<ProductRecommendResponse> findSimilarProductsByVector(
        float[] queryVector,
        int topK,
        List<UUID> excludeProductIds,
        UUID excludeSelfProductId,
        boolean removeDuplicates
    ) {
        try {
            // 중복 제거가 필요한 경우 더 많은 상품을 가져옴
            int fetchSize = removeDuplicates ? topK * 2 : topK;

            NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                    .scriptScore(ss -> ss
                        .query(q2 -> q2
                            .bool(b -> {
                                // 자기 자신 제외 (excludeSelfProductId가 있는 경우만)
                                if (excludeSelfProductId != null) {
                                    b.mustNot(mn -> mn.ids(i -> i.values(excludeSelfProductId.toString())));
                                }
                                // 제외할 상품 ID들도 쿼리 레벨에서 제외
                                if (excludeProductIds != null && !excludeProductIds.isEmpty()) {
                                    b.mustNot(mn -> mn.ids(i -> i.values(
                                        excludeProductIds.stream()
                                            .map(UUID::toString)
                                            .toList()
                                    )));
                                }
                                // 판매 중인 상품만 추천
                                b.filter(f -> f
                                    .terms(t -> t
                                        .field("status")
                                        .terms(v -> v.value(
                                            List.of(
                                                FieldValue.of("ON_SALE"),
                                                FieldValue.of("DISCOUNTED")
                                            )
                                        ))
                                    )
                                );
                                return b;
                            })
                        )
                        .script(s -> s
                            .inline(i -> i
                                .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                                .params(Map.of(
                                    "query_vector",
                                    co.elastic.clients.json.JsonData.of(convertToDoubleList(queryVector))
                                ))
                            )
                        )
                    )
                )
                .withPageable(PageRequest.of(0, fetchSize))
                .build();

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

            List<ProductRecommendResponse> results = new ArrayList<>();
            Set<UUID> seenProductIds = removeDuplicates ? new HashSet<>() : null;

            for (SearchHit<ProductDocument> hit : hits.getSearchHits()) {
                ProductDocument product = hit.getContent();
                UUID productId = product.getProductId();

                // 중복 제거 (필요한 경우)
                if (removeDuplicates && seenProductIds.contains(productId)) {
                    log.debug("중복 상품 제거: productId={}, productName={}", productId, product.getProductName());
                    continue;
                }

                // 최대 개수 제한
                if (results.size() >= topK) {
                    break;
                }

                if (removeDuplicates) {
                    seenProductIds.add(productId);
                }

                results.add(new ProductRecommendResponse(
                    productId,
                    product.getProductName(),
                    product.getProductCategoryName(),
                    product.getPrice()
                ));
            }

            int excludedCount = excludeProductIds != null ? excludeProductIds.size() : 0;
            log.debug("벡터 유사도 검색 결과: {}개 상품 발견 (제외 상품: {}, 중복 제거: {})",
                results.size(), excludedCount, removeDuplicates);
            return results;

        } catch (Exception e) {
            log.error("벡터 유사도 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * float[]을 List<Double>로 변환합니다.
     * Elasticsearch JSON 파라미터에 사용하기 위함입니다.
     */
    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> doubleList = new java.util.ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
}
