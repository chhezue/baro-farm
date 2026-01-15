package com.barofarm.ai.recommend.application.dto.response;

import java.util.UUID;

/**
 * 개인화 추천 결과 (유사도 점수 포함)
 * 검증 및 디버깅을 위해 유사도 점수를 포함합니다.
 */
public record PersonalRecommendWithScoreResponse(
    UUID productId,
    String productName,
    String productCategory,
    Long price,
    Double similarityScore, // 코사인 유사도 + 1.0 (0~2 범위, 높을수록 유사)
    String matchReason // 로그와의 매칭 이유
) { }
