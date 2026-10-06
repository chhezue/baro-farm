package com.barofarm.ai.recommend.application;

import com.barofarm.ai.search.domain.ProductDocument;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 벡터 유사도 검색 결과에서 탐욕적 최원거리(Greedy Max-Min / Farthest-first) 방식으로 대표 상품들을 선별하여
 * 추천 목록의 다양성(Diversity)을 극대화하는 서비스입니다.
 *
 * [동작 원리 및 특징]
 * 1. 1순위 고정: 검색된 후보 중 기준 쿼리 벡터와 가장 유사한 1위 상품을 가장 먼저 선택 목록에 고정합니다.
 * 2. 거리 갱신: 아직 선택되지 않은 각 후보 상품이 "이미 선택된 상품들"과 이루는 최소 거리(min distance)를 유지·갱신합니다.
 * 3. 최원거리 선택: 이 최소 거리들 중 가장 큰 값(Max-Min)을 갖는 후보(즉, 지금까지 고른 상품들과 가장 멀리 떨어진 상품)를 다음 추천 상품으로 추가합니다.
 * 4. k-Medoids 대비 이점: 중심점을 반복 재계산(클러스터링)하지 않고 단일 패스로 빠르게 수렴하므로 지연 시간(Latency)이 매우 낮으며 추천 다양성을 확실히 보장합니다.
 */
@Slf4j
@Service
public class MaxMinDiversityService {

    /**
     * 벡터가 누락되었거나 차원이 맞지 않아, 혹은 길이가 0이어서 거리를 측정할 수 없을 때 반환하는 표식값(NaN).
     */
    private static final double UNMEASURABLE = Double.NaN;

    /**
     * 후보 상품 목록 중에서 기준 벡터와 유사하면서도 서로 겹치지 않는 대표 상품 k개를 다양성 알고리즘으로 선별합니다.
     *
     * @param queryVector 추천의 기준이 되는 쿼리 벡터 (최종 유사도 정렬에 사용되며, null일 경우 다양화 선택 순서 유지)
     * @param candidates  1차 유사도 검색으로 조회된 후보 상품 목록 (유사도 내림차순 정렬 상태 권장)
     * @param topK        최종 선택할 최대 상품 개수
     * @return 다양성이 확보된 대표 상품 목록
     */
    public List<ProductDocument> selectDiverse(
        float[] queryVector,
        List<ProductDocument> candidates,
        int topK
    ) {
        // [1단계: 입력 파라미터 유효성 검증]
        // 요청된 개수가 0 이하이거나, 후보 리스트가 null 또는 비어 있는 경우 즉시 빈 목록을 반환합니다.
        if (topK <= 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // [2단계: 유효한 임베딩 벡터를 보유한 상품만 1차 필터링]
        // 상품 문서가 null이거나, 벡터 필드가 null 또는 빈 배열인 상품은 거리 계산이 불가능하므로 제외합니다.
        List<ProductDocument> validCandidates = new ArrayList<>();
        for (ProductDocument candidate : candidates) {
            if (candidate != null && candidate.getVector() != null && candidate.getVector().length > 0) {
                validCandidates.add(candidate);
            }
        }

        // 유효한 벡터를 가진 상품이 전혀 없다면 추천을 중단하고 빈 목록을 반환합니다.
        if (validCandidates.isEmpty()) {
            log.debug("다양화할 유효한 후보가 없습니다. candidates size={}", candidates.size());
            return List.of();
        }

        // 최종 선별 목표 개수 k 결정 (요청 개수와 유효 후보 수 중 작은 값)
        int k = Math.min(topK, validCandidates.size());

        // [3단계: 후보군 수 부족 시 조기 반환]
        // 유효 후보 수가 목표 k 이하인 경우, 추가 다양화 연산 없이 ES가 정렬한 유사도 순서 그대로 상위 k건을 반환합니다.
        if (validCandidates.size() <= k) {
            return new ArrayList<>(validCandidates.subList(0, k));
        }

        List<ProductDocument> selected = new ArrayList<>();
        // [4단계: 최상위 유사도 1위 상품 시작점 고정]
        // ES 유사도 검색 결과에서 가장 점수가 높은 0번째 상품을 첫 번째 추천 아이템으로 확정합니다.
        selected.add(validCandidates.get(0));

        // [5단계: 각 후보 상품별 '선택된 그룹과의 최소 거리' 배열 초기화]
        // minDistances[i]는 i번째 후보가 이미 selected에 들어간 상품들과 이루는 거리 중 최솟값을 의미합니다.
        // 초기값은 무한대(POSITIVE_INFINITY)로 설정합니다.
        double[] minDistances = new double[validCandidates.size()];
        for (int i = 0; i < minDistances.length; i++) {
            minDistances[i] = Double.POSITIVE_INFINITY;
        }

        // [6단계: Greedy Max-Min 반복 루프 (목표 k개에 도달할 때까지 수행)]
        while (selected.size() < k) {
            int nextIndex = -1;
            double maxMinDistance = -1.0;

            for (int i = 0; i < validCandidates.size(); i++) {
                ProductDocument candidate = validCandidates.get(i);
                // 이미 선별된 상품은 대상에서 제외
                if (selected.contains(candidate)) {
                    continue;
                }

                // 직전에 새롭게 추가된 상품과의 코사인 거리를 계산하여 최소 거리를 점진적으로 갱신합니다.
                // (이전 루프에서 계산된 기존 최소 거리와 방금 추가된 상품과의 거리 중 최솟값만 취하면 되므로 O(1)에 갱신 가능)
                ProductDocument lastSelected = selected.get(selected.size() - 1);
                double distanceToLast = cosineDistance(candidate.getVector(), lastSelected.getVector());
                if (Double.isNaN(distanceToLast)) {
                    continue;
                }

                double newMinDistance = Math.min(minDistances[i], distanceToLast);
                minDistances[i] = newMinDistance;

                // 후보들 중 '선택된 아이템들과의 최소 거리'가 가장 큰(즉, 가장 고립되어 있고 새로운 특징을 가진) 상품을 탐색
                if (newMinDistance > maxMinDistance) {
                    maxMinDistance = newMinDistance;
                    nextIndex = i;
                }
            }

            // 더 이상 유효하게 거리를 측정할 수 있는 후보가 없으면 루프를 조기 종료합니다.
            if (nextIndex < 0) {
                break;
            }

            // 가장 멀리 떨어진 후보 상품을 선택 목록에 추가
            selected.add(validCandidates.get(nextIndex));
        }

        // [7단계: 기준 쿼리 벡터 기반 최종 재정렬]
        // 다양성을 고려하여 선별된 상품들이더라도, 사용자 화면에는 기준 취향/상품과 더 유사한 순서대로
        // 자연스럽게 노출되도록 쿼리 벡터와의 코사인 유사도 기준 내림차순으로 최종 정렬합니다.
        if (queryVector != null) {
            selected.sort((a, b) -> Double.compare(
                sortableSimilarity(b.getVector(), queryVector),
                sortableSimilarity(a.getVector(), queryVector)
            ));
        }

        log.debug("다양화 선택 완료: 후보 {}개 중 {}개 선택 (topK={})",
            validCandidates.size(), selected.size(), topK);

        return selected;
    }

    /**
     * 두 벡터 간의 코사인 거리(Cosine Distance = 1.0 - Cosine Similarity)를 계산합니다.
     * 값의 범위는 0.0(완전 동일 방향) ~ 2.0(완전 반대 방향)이며, 값이 클수록 서로 상이한 상품을 의미합니다.
     *
     * @param v1 첫 번째 상품 벡터
     * @param v2 두 번째 상품 벡터
     * @return 코사인 거리 값 (계산 불가 시 Double.NaN)
     */
    private double cosineDistance(float[] v1, float[] v2) {
        double similarity = cosineSimilarity(v1, v2);
        if (Double.isNaN(similarity)) {
            return UNMEASURABLE;
        }
        return 1.0 - similarity;
    }

    /**
     * 정렬 비교를 위한 유사도 값을 반환합니다.
     * 계산이 불가능한 경우(NaN) 가장 유사하지 않은 최하위(-1.0)로 간주하여 목록 뒤편에 배치합니다.
     */
    private double sortableSimilarity(float[] v1, float[] v2) {
        double similarity = cosineSimilarity(v1, v2);
        return Double.isNaN(similarity) ? -1.0 : similarity;
    }

    /**
     * 두 float 벡터 간의 코사인 유사도(Cosine Similarity)를 계산합니다.
     * 공식: (v1 · v2) / (||v1|| * ||v2||)
     *
     * @param v1 첫 번째 벡터
     * @param v2 두 번째 벡터
     * @return -1.0 ~ 1.0 사이의 코사인 유사도 (벡터가 null이거나 길이가 0, 크기 불일치, 0 벡터인 경우 Double.NaN)
     */
    private double cosineSimilarity(float[] v1, float[] v2) {
        // 벡터 유효성 검사 (null, 빈 배열, 차원 불일치 방어)
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0 || v1.length != v2.length) {
            return UNMEASURABLE;
        }

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        // 벡터 내적(Dot Product) 및 각 벡터의 L2 노름 제곱합 계산
        for (int i = 0; i < v1.length; i++) {
            double a = v1[i];
            double b = v2[i];
            dot += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }

        // 분모가 0인 경우(영벡터) 나누기 오류 방지
        if (norm1 == 0.0 || norm2 == 0.0) {
            return UNMEASURABLE;
        }

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
