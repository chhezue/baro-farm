package com.barofarm.ai.recommend.application.dto;

import java.util.List;

// 부족한 재료에 대한 상품 추천 응답 DTO
public record IngredientRecommendResponse(
    String ingredientName, // 부족한 재료 이름
    List<ProductRecommendResponse> products // ES에 검색한 부족한 재료 (최대 2개)
) { }
