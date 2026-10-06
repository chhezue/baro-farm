package com.barofarm.ai.recommend.domain;

import com.barofarm.ai.recommend.infrastructure.llm.RecipeCandidateDraft;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gemini LLM이 생성한 원본 레시피 초안에 서버 측의 재료 매칭 연산 결과가 결합된 최종 레시피 계획 도메인 모델입니다.
 *
 * [핵심 검증 정책]
 * - LLM이 자체적으로 응답하는 "사용된 재료" 및 "부족한 재료"는 환각(Hallucination) 오류가 발생하기 쉬우므로 사용하지 않습니다.
 * - 대신 {@link #from(RecipeCandidateDraft, List)} 정적 팩토리 메서드에서 사용자의 실제 보유 재료와 레시피 필요 재료를
 *   정규화 비교하여 '실제 사용된 재료'와 '부족한 핵심 재료'를 서버가 100% 보증하여 계산합니다.
 *
 * @param cookingMethod          조리 방식 (예: ONE_POT, STIR_FRY 등)
 * @param difficulty             난이도 (EASY, NORMAL, CHALLENGE)
 * @param mealType               식사 유형 (MAIN, SIDE, SOUP)
 * @param recipeName             레시피 명칭 (예: "애호박 된장찌개")
 * @param recipeIngredientsCore  해당 요리의 필수 핵심 재료 목록 (정규화 완료)
 * @param recipeIngredientsExtra 해당 요리의 부가 선택 재료 목록 (정규화 완료)
 * @param instructions           단계별 조리법 안내
 * @param staplesAssumed         기본 양념 보유 가정 여부
 * @param usedOwnedIngredients   사용자의 장바구니 보유 재료 중 이 요리에 실제로 사용되는 재료 (서버 계산)
 * @param missingIngredientsCore 레시피 완성을 위해 추가로 구매해야 하는 핵심 재료 (서버 계산)
 */
public record CandidateRecipePlan(
    String cookingMethod,
    String difficulty,
    String mealType,
    String recipeName,
    List<String> recipeIngredientsCore,
    List<String> recipeIngredientsExtra,
    String instructions,
    boolean staplesAssumed,
    List<String> usedOwnedIngredients,
    List<String> missingIngredientsCore
) {

    /**
     * LLM 원본 초안(RecipeCandidateDraft)과 사용자의 보유 재료 목록을 대조하여
     * 재료 정규화 및 서버 측 사용/부족 재료를 계산한 후 CandidateRecipePlan 객체를 생성합니다.
     *
     * @param draft            LLM이 생성한 레시피 원본 초안
     * @param ownedIngredients 사용자의 장바구니 보유 재료명 목록
     * @return 재료 정합성이 검증된 최종 레시피 계획 객체
     */
    public static CandidateRecipePlan from(RecipeCandidateDraft draft, List<String> ownedIngredients) {
        // [1단계: LLM 응답 재료명 목록 정규화]
        // 핵심 재료(core)와 보조 재료(extra)의 문자열 공백을 다듬고 빈 항목을 제거합니다.
        List<String> core = IngredientProcessingUtil.normalizeList(draft.recipeIngredientsCore());
        List<String> extra = IngredientProcessingUtil.normalizeList(draft.recipeIngredientsExtra());

        // [2단계: 보유 재료 비교용 Set 구성]
        // 대소문자 무시 및 공백 제거된 정규화 키 집합을 생성하여 O(1) 포함 여부 조회를 준비합니다.
        Set<String> ownedNormalized = IngredientProcessingUtil.normalizeList(ownedIngredients).stream()
            .map(IngredientProcessingUtil::normalizeForCompare)
            .collect(Collectors.toSet());

        // [3단계: 사용된 보유 재료(usedOwnedIngredients) 계산]
        // 레시피의 핵심 및 추가 재료 중 사용자의 장바구니에 실제로 존재하는 재료들의 교집합을 구합니다.
        List<String> usedOwned = Stream.concat(core.stream(), extra.stream())
            .filter(ing -> ownedNormalized.contains(IngredientProcessingUtil.normalizeForCompare(ing)))
            .distinct()
            .toList();

        // [4단계: 부족한 핵심 재료(missingIngredientsCore) 계산]
        // 핵심 재료(core) 중 사용자가 아직 장바구니에 담지 않은 차집합 재료들을 추출합니다.
        List<String> missingCore =
            IngredientProcessingUtil.subtractNormalized(core, ownedIngredients);

        // [5단계: 서버 검증이 완료된 CandidateRecipePlan 도메인 객체 생성]
        return new CandidateRecipePlan(
            draft.cookingMethod(),
            draft.difficulty(),
            draft.mealType(),
            draft.recipeName(),
            core,
            extra,
            draft.instructions(),
            draft.staplesAssumed(),
            usedOwned,
            missingCore
        );
    }
}
