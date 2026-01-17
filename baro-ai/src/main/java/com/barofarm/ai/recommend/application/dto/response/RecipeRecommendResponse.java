package com.barofarm.ai.recommend.application.dto.response;

import java.util.List;

// 레시피 추천 결과
public record RecipeRecommendResponse(
    String recipeName,
    List<String> ingredients,
    String instructions
) {
}
