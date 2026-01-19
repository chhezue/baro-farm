package com.barofarm.ai.recommend.application.dto.response;

import java.util.UUID;

// 개인화 추천 결과 (유사도 점수 포함)
public record PersonalRecommendResponse(
    UUID productId,
    String productName,
    String productCategory,
    Long price,
    Double similarityScore // 코사인 유사도 + 1.0 (0~2 범위, 높을수록 유사)
) { }
