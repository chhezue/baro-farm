package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.search.application.VectorProductSearchService;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기준 벡터를 기반으로 유사 상품 또는 개인화 추천 목록을 생성하는 공통 추천 파이프라인 서비스입니다.
 *
 * [핵심 역할]
 * - "유사 상품 추천"(기준 상품 벡터 사용)과 "개인화 추천"(사용자 취향 프로필 벡터 사용)은
 *   기준 벡터를 획득하는 출처만 다를 뿐, 이후 처리(후보군 kNN 조회 → Max-Min 다양성 선별 → DTO 변환)는
 *   완전히 동일하므로 중복 로직을 이 공통 파이프라인 클래스로 통합 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRecommendPipeline {

    /**
     * 다양성 확보를 위해 최종 추천 개수(topK) 대비 조회할 1차 후보군 배수.
     * 너무 적게 가져오면 서로 다른 특징을 가진 상품을 고를 선택지가 부족해지므로 topK의 3배수를 1차 후보군으로 확보합니다.
     */
    private static final int CANDIDATE_MULTIPLIER = 3;

    /**
     * 1차 벡터 유사도 검색으로 가져올 후보 상품 수의 최대 상한선.
     * Elasticsearch의 kNN 쿼리 비용 및 다양성 거리 계산 연산 비용(O(k * N))을 고려하여 최대 100건으로 제한합니다.
     */
    private static final int MAX_CANDIDATE_SIZE = 100;

    /** Elasticsearch dense_vector 기반 kNN 유사도 검색을 수행하는 서비스 */
    private final VectorProductSearchService vectorProductSearchService;

    /** Greedy Max-Min(Farthest-first) 방식으로 대표 상품들을 골라 추천 다양성을 확보하는 서비스 */
    private final MaxMinDiversityService maxMinDiversityService;

    /**
     * 기준 벡터와 코사인 유사도가 높으면서도 서로 중복/유사하지 않은 대표 상품 topK개를 선별하여 반환합니다.
     *
     * @param queryVector       추천의 기준이 되는 1536차원 벡터 (상품 벡터 또는 사용자 취향 벡터)
     * @param excludeProductIds 추천 결과에서 제외할 상품 UUID 목록 (예: 현재 조회 중인 상품 본인)
     * @param topK              최종 사용자에게 노출할 추천 상품 개수
     * @return 추천 상품 응답 DTO 목록 (후보가 없거나 유효하지 않은 경우 빈 리스트)
     */
    public List<ProductRecommendResponse> recommend(
        float[] queryVector,
        List<UUID> excludeProductIds,
        int topK
    ) {
        // [1단계: 1차 후보군 크기(candidateSize) 계산]
        // 다양화 알고리즘이 서로 다른 군집의 상품을 선택할 수 있도록 topK의 3배수를 확보하되,
        // ES 검색 및 거리 계산 부하를 방지하기 위해 최대 100개 상한을 적용합니다.
        int candidateSize = Math.min(topK * CANDIDATE_MULTIPLIER, MAX_CANDIDATE_SIZE);

        // [2단계: Elasticsearch dense_vector kNN 검색을 통한 1차 후보 상품 조회]
        // 제외 대상 상품(자기 자신 등)을 쿼리 필터로 제외하고, 기준 벡터와 코사인 유사도가 가장 높은 상위 후보군을 가져옵니다.
        List<ProductDocument> candidates = vectorProductSearchService.findSimilarProductDocumentsByVector(
            queryVector,
            candidateSize,
            excludeProductIds
        );

        // [3단계: 1차 후보군 유효성 검증]
        // ES 검색 결과 일치하는 상품이 없거나 비어있는 경우 즉시 빈 리스트를 반환하여 후속 연산을 조기 종료합니다.
        if (candidates.isEmpty()) {
            return List.of();
        }

        // [4단계: Greedy Max-Min 알고리즘을 통한 다양성 선별 및 최종 DTO 변환]
        // 1차 후보군 중 가장 유사한 1위 상품을 고정한 후, 이미 선택된 상품들과 코사인 거리가 가장 먼 상품들을
        // 반복적으로 선택하여 상품 목록의 다양성을 확보하고, API 응답 규격(ProductRecommendResponse)으로 변환합니다.
        return maxMinDiversityService.selectDiverse(queryVector, candidates, topK).stream()
            .map(ProductRecommendResponse::from)
            .toList();
    }
}
