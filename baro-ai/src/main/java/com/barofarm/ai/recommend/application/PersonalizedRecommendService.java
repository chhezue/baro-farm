package com.barofarm.ai.recommend.application;

import com.barofarm.ai.embedding.domain.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.infrastructure.elasticsearch.UserProfileEmbeddingRepository;
import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 이력(장바구니, 주문, 검색 등)으로 사전 계산된 취향 벡터를 기반으로
 * 개인화 맞춤 상품을 추천하는 애플리케이션 서비스입니다.
 *
 * [주요 특징]
 * 1. Elasticsearch에 저장된 1536차원 사용자 취향 프로필 벡터를 조회합니다.
 * 2. 이미 구매하거나 담았던 경험 상품(sourceProductIds)은 추천에서 제외합니다.
 * 3. 공통 추천 파이프라인(VectorRecommendPipeline)으로 전달하여 벡터 유사도 검색 및 Max-Min 다양화를 거친 상품을 추천합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    /** Elasticsearch에 저장된 사용자 취향 프로필 벡터 문서를 조회하는 레포지토리 */
    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;

    /** 기준 벡터를 받아 후보군 벡터 유사도 검색 및 Max-Min 다양성 선별을 수행하는 공통 추천 파이프라인 */
    private final VectorRecommendPipeline vectorRecommendPipeline;

    /**
     * 특정 사용자에게 맞춤형 개인화 상품 목록을 추천합니다.
     *
     * @param userId 추천 대상 사용자의 고유 UUID
     * @param topK   최종 추천받을 상품 개수
     * @return 개인화 추천 상품 응답 DTO 목록 (행동 로그 부재로 프로필 미생성 시 빈 목록 반환)
     */
    public List<ProductRecommendResponse> recommendProducts(UUID userId, int topK) {
        // [0단계: 요청 파라미터 검증]
        // 조회를 시작하기 전에 확인해, 프로필이 없어 아래에서 일찍 반환되는 경우에도 같은 400이 나가게 한다.
        VectorRecommendPipeline.validateTopK(topK);

        // [1단계: 사용자 프로필 벡터 조회 및 유효성 검증]
        // Elasticsearch에서 사용자 ID로 취향 프로필 문서를 조회합니다.
        // 프로필이 아직 생성되지 않았거나 벡터가 없는 경우(행동 로그 부족),
        // 메인 화면 전체 장애를 방지하기 위해 예외를 던지지 않고 빈 목록(List.of())을 즉시 반환합니다.
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null
            || profile.getUserProfileVector() == null
            || profile.getUserProfileVector().length == 0) {
            log.warn("사용자 ID {}의 프로필 벡터가 없습니다. 행동 로그가 더 쌓여야 만들어집니다.", userId);
            return List.of();
        }

        // [2단계: 기경험(장바구니/주문) 상품 제외 목록 추출]
        // 사용자가 이미 구매했거나 장바구니에 담아본 상품(sourceProductIds)은 추천 후보에서 제외합니다.
        List<UUID> experiencedProductIds = profile.getSourceProductIds() != null
            ? profile.getSourceProductIds()
            : List.of();

        log.debug("사용자 {}의 추천에서 임베딩의 sourceProductIds({}개)로 상품 제외",
                 userId, experiencedProductIds.size());

        // [3단계: 공통 추천 파이프라인 호출 (벡터 유사도 검색 및 Greedy Max-Min 다양성 선별)]
        // 사용자 취향 벡터(1536차원)와 기경험 제외 상품 ID 목록, 추천 개수를 파이프라인에 전달하여
        // 코사인 유사도가 높으면서도 서로 비슷하지 않은 상품 목록을 최종 완성합니다.
        // 다양화 기준은 벡터 간 거리이지 카테고리가 아니다 — 카테고리 가산점은 2026-09-05에 삭제했다.
        return vectorRecommendPipeline.recommend(
            profile.getUserProfileVector(), experiencedProductIds, topK);
    }
}
