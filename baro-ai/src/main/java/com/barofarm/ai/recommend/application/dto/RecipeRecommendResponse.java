package com.barofarm.ai.recommend.application.dto;

import java.util.List;

/**
 * 사용자의 장바구니 품목을 분석하여 생성된 맞춤 요리 레시피 및 부족한 재료 추천 응답 DTO 레코드입니다.
 *
 * @param reason                 응답이 이 모양인 이유. {@link RecipeRecommendReason#OK}가 아니면 나머지 필드는
 *                               비어 있거나 일부만 채워진다 — 어떤 필드가 채워지는지는 각 상수의 설명 참고
 * @param recipeName             추천된 요리 명칭 (예: "애호박 된장찌개")
 * @param ownedIngredients       이 레시피가 실제로 사용하는 보유 식재료 목록 (핵심 재료 ∩ 장바구니).
 *                               장바구니 전체가 아니다 — missingCoreIngredients와 합치면 핵심 재료가 된다
 * @param missingCoreIngredients 해당 요리 완성에 필수적이지만 장바구니에 없는 부족한 핵심 식재료 목록
 * @param missingRecommendations 부족한 핵심 식재료별 실제 구매 가능한 추천 상품 목록 (IngredientRecommendResponse)
 * @param instructions           3~6단계로 요약된 요리 조리법 안내 문구
 */
public record RecipeRecommendResponse(
    RecipeRecommendReason reason,
    String recipeName,
    List<String> ownedIngredients,
    List<String> missingCoreIngredients,
    List<IngredientRecommendResponse> missingRecommendations,
    String instructions
) {

    /**
     * 레시피를 만들지 못했을 때의 응답. 이유만 담고 나머지는 전부 빈 값이다.
     *
     * @param reason 만들지 못한 이유 ({@link RecipeRecommendReason#OK} 제외)
     * @return 빈 필드로 채워진 응답
     */
    public static RecipeRecommendResponse empty(RecipeRecommendReason reason) {
        return new RecipeRecommendResponse(reason, "", List.of(), List.of(), List.of(), "");
    }
}
