package com.barofarm.ai.recommend.infrastructure.llm;

import java.util.List;

/**
 * Gemini LLM이 반환하는 개별 레시피 후보의 원본(Raw Draft) 구조를 담는 DTO 레코드입니다.
 *
 * [설계 특징]
 * - BeanOutputConverter를 통해 Gemini API의 Structured Output JSON 스키마로 직접 등록됩니다.
 * - '사용한 보유 재료(usedOwnedIngredients)'나 '부족한 핵심 재료(missingIngredientsCore)'는
 *   LLM의 환각(Hallucination)을 방지하기 위해 이 DTO에 포함하지 않고, 서버(CandidateRecipePlan)에서 직접 계산합니다.
 *
 * @param cookingMethod          조리 방식 (ONE_POT: 냄비/원팬, STIR_FRY: 볶음, NO_COOK: 비조리/샐러드, OVEN_BAKE: 오븐구이)
 * @param difficulty             조리 난이도 (EASY: 쉬움, NORMAL: 보통, CHALLENGE: 어려움)
 * @param mealType               식사 유형 (MAIN: 메인요리, SIDE: 반찬류, SOUP: 국/찌개)
 * @param recipeName             추천 요리 명칭 (예: "돼지고기 김치찌개")
 * @param recipeIngredientsCore  요리의 정체성을 결정하는 핵심 식재료 목록 (3~5개 권장)
 * @param recipeIngredientsExtra 요리의 풍미를 돋우는 보조/선택 식재료 목록 (1~3개 권장)
 * @param instructions           3~6단계로 요약된 단계별 간이 조리법 안내 문구
 * @param staplesAssumed         소금, 간장, 물 등 기본 양념류를 가정하고 작성되었는지 여부 (항상 true)
 */
public record RecipeCandidateDraft(
    String cookingMethod,
    String difficulty,
    String mealType,
    String recipeName,
    List<String> recipeIngredientsCore,
    List<String> recipeIngredientsExtra,
    String instructions,
    boolean staplesAssumed
) { }
