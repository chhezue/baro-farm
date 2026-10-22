package com.barofarm.ai.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barofarm.ai.recommend.exception.RecommendErrorCode;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.exception.CustomException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 추천 개수 검증 경계와 **후보에서 topK개를 고르는 규칙**을 고정한다.
 *
 * 고르는 규칙이 2026-09-09에 바뀌었다. 그전에는 후보 중 서로 가장 먼 것을 반복해 뽑는 방식이라
 * 첫 번째 이후로는 기준 벡터를 보지 않았다 — 유사 상품을 찾아 달라는 요청에 가장 덜 유사한 조합을
 * 돌려주고 있었다. 지금은 유사도 순서를 그대로 두고 같은 카테고리만 최대 2개로 막는다.
 * 그 두 성질(순서 보존 · 상한 · 그래도 개수는 채운다)을 여기서 고정한다.
 */
class VectorRecommendPipelineTest {

    @Test
    @DisplayName("validateTopK — 1과 50은 통과한다")
    void validateTopK_boundariesAllowed() {
        assertThatCode(() -> VectorRecommendPipeline.validateTopK(1)).doesNotThrowAnyException();
        assertThatCode(() -> VectorRecommendPipeline.validateTopK(50)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTopK — 0·음수·51은 INVALID_TOP_K")
    void validateTopK_outOfRangeRejected() {
        for (int topK : new int[] {0, -1, 51, 100, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> VectorRecommendPipeline.validateTopK(topK))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(RecommendErrorCode.INVALID_TOP_K);
        }
    }

    @Test
    @DisplayName("고르기 — 카테고리가 다양하면 유사도 순서를 그대로 지킨다")
    void select_keepsSimilarityOrderWhenCategoriesDiffer() {
        List<ProductDocument> candidates = List.of(
            product("A", "사과"), product("B", "배"), product("C", "감귤"));

        List<ProductDocument> selected = VectorRecommendPipeline.selectWithCategorySpread(candidates, 3);

        assertThat(selected).extracting(ProductDocument::getProductName)
            .containsExactly("사과", "배", "감귤");
    }

    @Test
    @DisplayName("고르기 — 같은 카테고리는 2개까지만 담고 그다음 카테고리로 넘어간다")
    void select_capsSameCategoryAtTwo() {
        List<ProductDocument> candidates = List.of(
            product("A", "사과1"), product("A", "사과2"), product("A", "사과3"),
            product("B", "배1"), product("B", "배2"));

        List<ProductDocument> selected = VectorRecommendPipeline.selectWithCategorySpread(candidates, 3);

        // 사과3은 상한에 걸려 건너뛰고, 그 자리에 다음 카테고리의 1위가 들어간다
        assertThat(selected).extracting(ProductDocument::getProductName)
            .containsExactly("사과1", "사과2", "배1");
    }

    @Test
    @DisplayName("고르기 — 카테고리가 하나뿐이면 상한을 풀어서라도 요청한 개수를 채운다")
    void select_fillsUpEvenWhenOneCategoryOnly() {
        List<ProductDocument> candidates = List.of(
            product("A", "사과1"), product("A", "사과2"), product("A", "사과3"), product("A", "사과4"));

        List<ProductDocument> selected = VectorRecommendPipeline.selectWithCategorySpread(candidates, 4);

        // 상한은 쏠림을 막자는 것이지 결과를 줄이자는 것이 아니다 —
        // 좁은 카탈로그에서 "덜 다양한 4개"가 "다양한 2개"보다 낫다
        assertThat(selected).hasSize(4);
        assertThat(selected.subList(0, 2)).extracting(ProductDocument::getProductName)
            .containsExactly("사과1", "사과2");
    }

    @Test
    @DisplayName("고르기 — 후보가 요청 개수보다 적으면 있는 만큼만 돌려준다")
    void select_returnsWhatExists() {
        assertThat(VectorRecommendPipeline.selectWithCategorySpread(List.of(product("A", "사과")), 5))
            .hasSize(1);
    }

    @Test
    @DisplayName("고르기 — 카테고리 코드가 비어 있어도 한 묶음으로 세고 순서는 유지한다")
    void select_treatsMissingCategoryAsOneBucket() {
        List<ProductDocument> candidates = new ArrayList<>(List.of(
            product(null, "무분류1"), product(null, "무분류2"), product(null, "무분류3"),
            product("A", "사과")));

        List<ProductDocument> selected = VectorRecommendPipeline.selectWithCategorySpread(candidates, 3);

        assertThat(selected).extracting(ProductDocument::getProductName)
            .containsExactly("무분류1", "무분류2", "사과");
    }

    private static ProductDocument product(String categoryCode, String name) {
        return new ProductDocument(
            UUID.randomUUID(), name, null, categoryCode, "카테고리", 1000L,
            "ON_SALE", Instant.now(), "재료", null);
    }
}
