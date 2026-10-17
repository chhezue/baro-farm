package com.barofarm.ai.search.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.json.JsonData;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Elasticsearch의 `dense_vector` 필드와 Painless 스크립트를 활용하여
 * 기준 벡터와의 코사인 유사도(Cosine Similarity) 기반 상품 검색을 수행하는 서비스입니다.
 *
 * [핵심 역할 및 특징]
 * 1. 유사 상품 추천 및 개인화 추천에서 요구하는 1차 후보군을 벡터 유사도 순으로 신속하게 조회합니다.
 * 2. 후속 단계인 Max-Min 다양성 선별(MaxMinDiversityService)에서 상호 거리 계산이 가능하도록
 *    상품 문서의 `vector` 필드를 온전히 유지한 채 `ProductDocument` 목록을 반환합니다.
 *    이것이 검색 경로와 달리 `_source`에서 벡터를 빼지 않는 이유다. 비용은 실측으로 확인했다 — k=30 응답이
 *    벡터 포함 약 1,030KB, 제외 시 약 2KB(2026-09-06). 그 대가로 얻는 것(같은 카테고리 쌍 45%→11.5%)도 같은 날
 *    잰 뒤 유지하기로 했다. 근거: docs/audits/vector-similarity/002 A-1·M-2
 * 4. 후보 조회는 `script_score` 선형 스캔이다. native `knn`(HNSW)으로 바꾸지 않는 이유는 상품 1만 건까지 p95 8ms 미만으로
 *    두 방식의 지연이 같기 때문이다 — 상품이 수만 건을 넘으면 다시 연다. 근거: docs/decisions/004-script-score-over-knn.md
 * 3. 벡터가 미생성된 불완전 상품 문서가 존재하더라도 검색 쿼리가 중단되지 않도록 `exists(vector)` 필터를 사전 적용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorProductSearchService {

    /**
     * Painless 스크립트의 `cosineSimilarity()` 반환값(-1.0 ~ 1.0)을 Elasticsearch 점수 규격(0.0 이상)으로 변환하기 위한 오프셋.
     * ES는 음수 점수(_score < 0)를 허용하지 않으므로 +1.0을 더해 0.0 ~ 2.0 범위의 양수 점수로 변환합니다.
     */
    private static final double SCORE_OFFSET = 1.0;

    /** Spring Data Elasticsearch 쿼리 실행 템플릿 */
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 기준 쿼리 벡터와 코사인 유사도가 높은 판매 중 상품 문서들을 후보군 크기(candidateSize)만큼 조회합니다.
     *
     * @param queryVector       비교 기준이 되는 1536차원 임베딩 벡터
     * @param candidateSize     조회할 최대 후보 상품 수 (예: topK * 3)
     * @param excludeProductIds 검색 결과에서 제외할 상품 UUID 목록 (자기 자신, 이미 구매한 상품 등)
     * @return 1536차원 벡터가 포함된 상품 문서 목록 (조회 실패 시 안전하게 빈 목록 반환)
     */
    public List<ProductDocument> findSimilarProductDocumentsByVector(
        float[] queryVector,
        int candidateSize,
        List<UUID> excludeProductIds
    ) {
        // [1단계: 요청 후보 수 유효성 검증]
        // 0 이하의 크기가 전달된 경우 즉시 빈 목록을 반환하여 불필요한 ES 네트워크 I/O를 방지합니다.
        if (candidateSize <= 0) {
            return List.of();
        }

        try {
            // [2단계: Elasticsearch script_score 네이티브 쿼리 생성]
            // 제외 상품 필터, 판매 상태 필터, vector 존재 필터와 함께 코사인 유사도 Painless 스크립트를 조립합니다.
            NativeQuery query = buildVectorSearchQuery(queryVector, excludeProductIds, candidateSize);

            // [3단계: Elasticsearch 쿼리 실행 및 SearchHits 수신]
            SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(query, ProductDocument.class);

            // [4단계: 검색된 ProductDocument 엔티티 리스트 추출]
            List<ProductDocument> results = new ArrayList<>();
            for (SearchHit<ProductDocument> hit : hits.getSearchHits()) {
                ProductDocument product = hit.getContent();
                if (product != null) {
                    results.add(product);
                }
            }

            int excludedCount = excludeProductIds != null ? excludeProductIds.size() : 0;
            log.debug("벡터 유사도 후보 검색 결과: {}개 상품 발견 (요청 후보 수: {}, 제외 상품: {})",
                results.size(), candidateSize, excludedCount);

            return results;

        } catch (Exception e) {
            log.error("벡터 유사도 후보 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Painless script_score를 사용한 Elasticsearch 네이티브 벡터 유사도 쿼리를 빌드합니다.
     */
    private NativeQuery buildVectorSearchQuery(
        float[] queryVector,
        List<UUID> excludeProductIds,
        int fetchSize
    ) {
        // float[] 배열을 Elasticsearch Painless 스크립트 파라미터 규격(List<Double>)으로 변환
        Map<String, JsonData> scriptParams = Map.of(
            "query_vector", JsonData.of(convertToDoubleList(queryVector))
        );

        return NativeQuery.builder()
            .withQuery(q -> q
                .scriptScore(ss -> ss
                    // 1차 필터링: 제외 상품 및 판매 상태, 벡터 존재 여부 확인
                    .query(q2 -> q2
                        .bool(b -> buildBoolQuery(b, excludeProductIds))
                    )
                    // 2차 점수 계산: 코사인 유사도 계산식 (최종 점수 = cosineSimilarity + 1.0)
                    .script(s -> s
                        .inline(i -> i
                            .source("cosineSimilarity(params.query_vector, 'vector') + " + SCORE_OFFSET)
                            .params(scriptParams)
                        )
                    )
                )
            )
            // 후보군 크기(fetchSize)만큼 상위 문서를 페이징 조회
            .withPageable(PageRequest.of(0, fetchSize))
            .build();
    }

    /**
     * 벡터 검색 시 제외할 상품 및 유효한 판매 상태, 벡터 필드 존재 여부를 확인하는 BoolQuery를 구성합니다.
     */
    private BoolQuery.Builder buildBoolQuery(BoolQuery.Builder b, List<UUID> excludeProductIds) {
        // [제외 대상 상품 필터 (mustNot)]
        // 기준 상품 자신이나 이미 구매/담은 상품 ID를 쿼리 레벨에서 제외합니다.
        if (excludeProductIds != null && !excludeProductIds.isEmpty()) {
            b.mustNot(mn -> mn.ids(i -> i.values(
                excludeProductIds.stream()
                    .map(UUID::toString)
                    .toList()
            )));
        }

        // [판매 상태 필터 (filter)]
        // 현재 실제로 구매 가능한 상품만 추천 대상에 포함합니다. 목록은 ProductDocument가 갖고 있습니다 —
        // 검색 경로와 같은 값을 써야 "검색에는 뜨는데 추천에는 안 뜨는" 상태가 생기지 않습니다.
        b.filter(f -> f
            .terms(t -> t
                .field("status")
                .terms(v -> v.value(
                    ProductDocument.PURCHASABLE_STATUSES.stream().map(FieldValue::of).toList()
                ))
            )
        );

        // [임베딩 벡터 필드 존재 필터 (filter)]
        // ES의 cosineSimilarity 함수는 vector 필드가 null인 문서를 만나면 즉시 ScriptException을 발생시키며
        // 검색 쿼리 전체를 실패로 만듭니다. 따라서 vector 필드가 정상 색인된 문서만 사전에 필터링합니다.
        b.filter(f -> f.exists(e -> e.field("vector")));
        return b;
    }

    /**
     * float[] 원시 배열을 Elasticsearch JsonData에 전달 가능한 List<Double> 객체로 변환합니다.
     */
    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> doubleList = new ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
}
