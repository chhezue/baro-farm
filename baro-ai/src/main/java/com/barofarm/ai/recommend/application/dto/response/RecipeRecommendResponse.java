package com.barofarm.ai.recommend.application.dto.response;

import java.util.List;

// 장바구니 기반 레시피 추천 응답 DTO (부족한 재료 포함)
public record RecipeRecommendResponse(
    String recipeName, // 레시피 이름
    List<String> ownedIngredients, // 보유 중인 재료
    List<String> missingCoreIngredients, // 부족한 재료
    List<IngredientRecommendResponse> missingRecommendations, // 부족한 재료별 상품 추천 목록 (ES 검색)
    String instructions // 레시피 조리법
) { }
