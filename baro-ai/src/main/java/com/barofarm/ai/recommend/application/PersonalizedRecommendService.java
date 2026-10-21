package com.barofarm.ai.recommend.application;

import com.barofarm.ai.embedding.application.UserProfileEmbeddingService;
import com.barofarm.ai.embedding.domain.TasteProfile;
import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 이력(장바구니·주문)으로 만든 취향 벡터를 기반으로 개인화 맞춤 상품을 추천합니다.
 *
 * [주요 특징]
 * 1. 취향 벡터를 이 요청 안에서 계산합니다 — 미리 만들어 저장해 두지 않습니다.
 *    저장을 그만둔 이유는 {@link TasteProfile} 참고.
 * 2. 이미 구매하거나 담았던 상품은 추천에서 제외합니다.
 * 3. 공통 추천 파이프라인({@link VectorRecommendPipeline})으로 전달하여 벡터 유사도 검색과 카테고리 상한 적용을 거칩니다.
 *
 * 취향 신호는 장바구니·주문 두 가지입니다 — 검색은 사용자가 고른 상품이 아니라 검색 엔진이 상위에 올린
 * 상품을 취향으로 삼는 것이어서 2026-09-07에 뺐습니다.
 * 근거: docs/audits/taste-vector/005-signal-and-weight-simplification.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    /** 행동 로그로 취향 벡터를 만드는 서비스 */
    private final UserProfileEmbeddingService userProfileEmbeddingService;

    /** 기준 벡터를 받아 후보군 벡터 유사도 검색과 카테고리 상한 적용을 수행하는 공통 추천 파이프라인 */
    private final VectorRecommendPipeline vectorRecommendPipeline;

    /** 추천 결과가 실제로 채워졌는지 지표로 남기는 컴포넌트 */
    private final RecommendationMetrics recommendationMetrics;

    /**
     * 특정 사용자에게 맞춤형 개인화 상품 목록을 추천합니다.
     *
     * 행동 로그가 아직 3건이 안 되면 빈 목록이 나갑니다 — 취향을 판단할 근거가 없다는 뜻이지 오류가 아니라
     * 예외를 던지지 않습니다. 메인 화면 한 칸이 비는 것과 화면 전체가 오류로 덮이는 것은 다릅니다.
     *
     * @param userId 추천 대상 사용자의 고유 UUID
     * @param topK   최종 추천받을 상품 개수
     */
    public List<ProductSummaryResponse> recommendProducts(UUID userId, int topK) {
        // [0단계: 요청 파라미터 검증]
        // 조회를 시작하기 전에 확인해, 로그가 없어 아래에서 일찍 반환되는 경우에도 같은 400이 나가게 한다.
        VectorRecommendPipeline.validateTopK(topK);

        // [1단계: 취향 벡터 계산]
        Optional<TasteProfile> profile = userProfileEmbeddingService.computeProfile(userId);
        if (profile.isEmpty()) {
            log.debug("사용자 ID {}의 취향 벡터를 만들지 못했습니다. 행동 로그가 더 쌓여야 합니다.", userId);
            recommendationMetrics.record(RecommendationMetrics.PERSONALIZED, 0);
            return List.of();
        }

        // [2단계: 공통 추천 파이프라인 호출]
        // 이미 담아 봤거나 산 상품은 제외 목록으로 넘긴다.
        List<ProductSummaryResponse> results = vectorRecommendPipeline.recommend(
            profile.get().vector(), profile.get().experiencedProductIds(), topK);

        recommendationMetrics.record(RecommendationMetrics.PERSONALIZED, results.size());
        return results;
    }
}
