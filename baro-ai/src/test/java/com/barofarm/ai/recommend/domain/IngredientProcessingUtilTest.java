package com.barofarm.ai.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 재료명 정규화·차집합은 "이걸 더 사세요" 카드의 정확성을 결정한다.
 * 표기가 조금 달라도 같은 재료로 보고, 없는 것만 부족 재료로 남기는지 고정한다.
 */
class IngredientProcessingUtilTest {

    @Test
    @DisplayName("normalizeList — null·빈 문자열·앞뒤 공백·중복을 정리하고 순서는 유지한다")
    void normalizeList_cleansAndKeepsOrder() {
        List<String> input = Arrays.asList("  돼지고기", null, "돼지고기 ", "  ", "소금", "양파");

        assertThat(IngredientProcessingUtil.normalizeList(input))
            .containsExactly("돼지고기", "소금", "양파");
    }

    @Test
    @DisplayName("normalizeList — null 목록은 빈 목록이다")
    void normalizeList_nullIsEmpty() {
        assertThat(IngredientProcessingUtil.normalizeList(null)).isEmpty();
    }

    @Test
    @DisplayName("normalizeForCompare — 대소문자·내부 공백을 무시한 비교 키를 만든다")
    void normalizeForCompare_ignoresCaseAndWhitespace() {
        assertThat(IngredientProcessingUtil.normalizeForCompare("돼지 고기")).isEqualTo("돼지고기");
        assertThat(IngredientProcessingUtil.normalizeForCompare(" Kimchi ")).isEqualTo("kimchi");
        assertThat(IngredientProcessingUtil.normalizeForCompare("다진\t마늘")).isEqualTo("다진마늘");
        assertThat(IngredientProcessingUtil.normalizeForCompare(null)).isEmpty();
    }

    @Test
    @DisplayName("subtractNormalized — 표기가 달라도 같은 재료는 빼고, 없는 것만 남긴다")
    void subtractNormalized_removesMatchesRegardlessOfSpacing() {
        List<String> needed = List.of("돼지고기", "양파", "마늘");
        List<String> owned = List.of("돼지 고기", "소금");

        assertThat(IngredientProcessingUtil.subtractNormalized(needed, owned))
            .containsExactly("양파", "마늘");
    }

    @Test
    @DisplayName("subtractNormalized — 보유 목록이 null이면 필요 재료가 그대로 부족 재료다")
    void subtractNormalized_nullOwnedMeansAllMissing() {
        assertThat(IngredientProcessingUtil.subtractNormalized(List.of("양파", "마늘"), null))
            .containsExactly("양파", "마늘");
    }

    @Test
    @DisplayName("subtractNormalized — 결과에도 정리(trim·중복 제거)가 적용된다")
    void subtractNormalized_resultIsNormalized() {
        List<String> needed = Arrays.asList(" 양파", "양파 ", null, "마늘");

        assertThat(IngredientProcessingUtil.subtractNormalized(needed, List.of()))
            .containsExactly("양파", "마늘");
    }

    // ---- 상품 검색어 변환 (감사 cart-recipe/006 A-2) ----

    @Test
    @DisplayName("검색어 변환 — 수식어가 붙으면 마지막 어절만 쓴다")
    void toSearchKeyword_takesHeadNoun() {
        assertThat(IngredientProcessingUtil.toSearchKeyword("국거리용 소고기")).isEqualTo("소고기");
        assertThat(IngredientProcessingUtil.toSearchKeyword("다진 마늘")).isEqualTo("마늘");
        assertThat(IngredientProcessingUtil.toSearchKeyword("  볶음용 대파  ")).isEqualTo("대파");
    }

    @Test
    @DisplayName("검색어 변환 — 공백이 없으면 그대로 두고, null은 빈 문자열")
    void toSearchKeyword_passesThroughSingleWord() {
        assertThat(IngredientProcessingUtil.toSearchKeyword("소고기")).isEqualTo("소고기");
        assertThat(IngredientProcessingUtil.toSearchKeyword(null)).isEmpty();
    }
}
