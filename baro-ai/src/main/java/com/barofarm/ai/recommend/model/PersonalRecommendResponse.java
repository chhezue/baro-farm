package com.barofarm.ai.recommend.model;

import java.util.UUID;

// 개인화 추천 결과
public record PersonalRecommendResponse(
    UUID productId,
    String productName, // 상품명
    String productCategory, // 카테고리
    Long price // 가격
) { }
