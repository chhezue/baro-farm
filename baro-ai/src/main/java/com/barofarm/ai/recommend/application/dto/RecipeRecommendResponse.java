package com.barofarm.ai.recommend.application.dto;

import java.util.List;

/**
 * 사용자의 장바구니 품목을 분석하여 생성된 맞춤 요리 레시피 및 부족한 재료 추천 응답 DTO 레코드입니다.
 *
 * @param recipeName             추천된 요리 명칭 (예: "애호박 된장찌개")
 * @param ownedIngredients       이 레시피가 실제로 사용하는 보유 식재료 목록 (핵심 재료 ∩ 장바구니).
 *                               장바구니 전체가 아니다 — missingCoreIngredients와 합치면 핵심 재료가 된다
 * @param missingCoreIngredients 해당 요리 완성에 필수적이지만 장바구니에 없는 부족한 핵심 식재료 목록
 * @param missingRecommendations 부족한 핵심 식재료별 실제 구매 가능한 추천 상품 목록 (IngredientRecommendResponse)
 * @param instructions           3~6단계로 요약된 요리 조리법 안내 문구
 */
public record RecipeRecommendResponse(
    String recipeName,
    List<String> ownedIngredients,
    List<String> missingCoreIngredients,
    List<IngredientRecommendResponse> missingRecommendations,
    String instructions
) { }
