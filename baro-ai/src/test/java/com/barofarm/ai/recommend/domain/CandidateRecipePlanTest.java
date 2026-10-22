package com.barofarm.ai.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LLM이 말한 "사용한 재료·부족한 재료"를 믿지 않고 서버가 보유 재료와 대조해 계산한다.
 * 핵심 재료 = 사용한 보유 재료 ∪ 부족 재료가 항상 성립해야 화면에서 앞뒤가 맞는다.
 */
class CandidateRecipePlanTest {

    private static final String INSTRUCTIONS = "1. 썬다 2. 끓인다";

    /** 도메인은 LLM 응답 타입을 모른다 — 원시값으로 만든다 (audits/cart-recipe/010 B-4) */
    private static CandidateRecipePlan plan(String name, List<String> core, List<String> owned) {
        return CandidateRecipePlan.from(name, core, INSTRUCTIONS, owned);
    }

    @Test
    @DisplayName("보유 재료와 겹치는 핵심 재료는 usedOwned로, 나머지는 missingCore로 나뉜다")
    void from_splitsCoreIntoOwnedAndMissing() {
        CandidateRecipePlan plan = plan("김치찌개", List.of("김치", "돼지고기", "두부", "대파"), List.of("김치", "두부", "우유"));

        assertThat(plan.recipeName()).isEqualTo("김치찌개");
        assertThat(plan.usedOwnedIngredients()).containsExactly("김치", "두부");
        assertThat(plan.missingIngredientsCore()).containsExactly("돼지고기", "대파");
    }

    @Test
    @DisplayName("사용한 보유 재료와 부족 재료를 합치면 핵심 재료 전체가 된다")
    void from_usedPlusMissingEqualsCore() {
        CandidateRecipePlan plan = plan("계란찜", List.of("계란", "대파", "새우젓"), List.of("계란"));

        List<String> union = new java.util.ArrayList<>(plan.usedOwnedIngredients());
        union.addAll(plan.missingIngredientsCore());

        assertThat(union).containsExactlyInAnyOrderElementsOf(plan.recipeIngredientsCore());
    }

    @Test
    @DisplayName("표기가 달라도(띄어쓰기·대소문자) 같은 재료로 본다")
    void from_matchesIgnoringSpacingAndCase() {
        CandidateRecipePlan plan = plan("제육볶음", List.of("돼지 고기", "양파", "Kimchi"), List.of("돼지고기", "kimchi"));

        assertThat(plan.usedOwnedIngredients()).containsExactly("돼지 고기", "Kimchi");
        assertThat(plan.missingIngredientsCore()).containsExactly("양파");
    }

    @Test
    @DisplayName("LLM 핵심 재료의 null·공백·중복은 정리되어 들어온다")
    void from_normalizesCoreList() {
        CandidateRecipePlan plan = plan("된장찌개", Arrays.asList(" 된장", null, "된장 ", "애호박", "  "), List.of());

        assertThat(plan.recipeIngredientsCore()).containsExactly("된장", "애호박");
        assertThat(plan.usedOwnedIngredients()).isEmpty();
        assertThat(plan.missingIngredientsCore()).containsExactly("된장", "애호박");
    }

    @Test
    @DisplayName("보유 재료만으로 완성되는 요리면 부족 재료가 비어 있다")
    void from_noMissingWhenEverythingOwned() {
        CandidateRecipePlan plan = plan("계란말이", List.of("계란", "대파"), List.of("계란", "대파", "당근"));

        assertThat(plan.missingIngredientsCore()).isEmpty();
        assertThat(plan.usedOwnedIngredients()).containsExactly("계란", "대파");
    }
}
