package com.barofarm.ai.recommend.infrastructure.llm;

import java.util.List;

/**
 * Gemini LLM이 반환하는 개별 레시피 후보의 원본(Raw Draft) 구조를 담는 DTO 레코드입니다.
 *
 * [설계 특징]
 * - BeanOutputConverter를 통해 Gemini API의 Structured Output JSON 스키마로 직접 등록됩니다.
 *   따라서 이 레코드의 필드가 곧 모델이 만들어야 할 출력이며, 필드를 늘리면 토큰과 지연이 함께 늘어난다.
 * - '사용한 보유 재료(usedOwnedIngredients)'나 '부족한 핵심 재료(missingIngredientsCore)'는
 *   LLM의 환각(Hallucination)을 방지하기 위해 이 DTO에 포함하지 않고, 서버(CandidateRecipePlan)에서 직접 계산합니다.
 * - 조리 방식·난이도·식사 유형·보조 재료·기본 양념 가정 플래그는 2026-09-06에 제거했습니다.
 *   후보를 1개만 생성하게 되면서 후보 간 비교에만 쓰이던 값들이 소비처를 잃었고,
 *   응답에도 들어가지 않아 토큰만 쓰고 버려지고 있었습니다(docs/specs/cart-recipe/recipe-quality-redesign.md).
 *
 * @param recipeName            추천 요리 명칭 (예: "돼지고기 김치찌개")
 * @param recipeIngredientsCore 요리의 정체성을 결정하는 핵심 식재료 목록 (3~5개 권장)
 * @param instructions          3~6단계로 요약된 단계별 간이 조리법 안내 문구
 */
public record RecipeCandidateDraft(
    String recipeName,
    List<String> recipeIngredientsCore,
    String instructions
) { }
