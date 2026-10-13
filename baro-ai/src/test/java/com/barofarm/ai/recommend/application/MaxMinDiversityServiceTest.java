package com.barofarm.ai.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.barofarm.ai.search.domain.ProductDocument;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Max-Min 다양화의 선택 규칙을 손으로 답을 낼 수 있는 3차원 예제로 고정한다.
 * 같은 예제를 측정 스크립트(`scripts/vectorbench/diversity.py --selfcheck`)도 쓴다 — 두 구현이 같은 답을 내야 그 측정을 믿을 수 있다.
 */
class MaxMinDiversityServiceTest {

    private final MaxMinDiversityService service = new MaxMinDiversityService();

    private static ProductDocument product(String name, float... vector) {
        return new ProductDocument(UUID.randomUUID(), name, null, "C", "카테고리", 1000L, "ON_SALE", null, "", vector);
    }

    @Test
    @DisplayName("가장 비슷한 첫 후보를 고정한 뒤, 이미 고른 것들과 가장 먼 후보를 차례로 고른다 (a, c, d)")
    void selectDiverse_picksFarthestFirst() {
        float[] query = {1f, 0f, 0f};
        List<ProductDocument> candidates = List.of(
            product("a", 1f, 0f, 0f),     // 기준과 동일 → 첫 고정
            product("b", 0.9f, 0.1f, 0f), // a와 매우 비슷 → 밀린다
            product("c", 0f, 1f, 0f),     // a와 직교
            product("d", 0f, 0f, 1f),     // a·c 모두와 직교
            product("e", 0.8f, 0.2f, 0f));

        List<ProductDocument> picked = service.selectDiverse(query, candidates, 3);

        assertThat(picked).extracting(ProductDocument::getProductName).containsExactly("a", "c", "d");
    }

    @Test
    @DisplayName("최종 순서는 다양화 순서가 아니라 기준 벡터와의 유사도순이다")
    void selectDiverse_resortsBySimilarityToQuery() {
        float[] query = {0f, 1f, 0f};
        List<ProductDocument> candidates = List.of(
            product("a", 0f, 1f, 0f),
            product("b", 1f, 0f, 0f),
            product("c", 0.6f, 0.8f, 0f), // a와 거리 0.2, b와 0.4 → 최소 0.2
            product("d", 0.7f, 0.7f, 0f)); // a와 거리 0.3, b와 0.3 → 최소 0.3 → c보다 멀어 세 번째로 뽑힌다

        List<ProductDocument> picked = service.selectDiverse(query, candidates, 3);

        // 뽑힌 순서는 a, b, d 였지만 유사도순 재정렬로 d(0.7)가 b(0.0)보다 앞선다
        assertThat(picked).extracting(ProductDocument::getProductName).containsExactly("a", "d", "b");
    }

    @Test
    @DisplayName("유효 후보가 k 이하면 다양화 없이 ES 순서 그대로 돌려준다")
    void selectDiverse_fewCandidatesPassThrough() {
        List<ProductDocument> candidates = List.of(product("a", 1f, 0f), product("b", 0f, 1f));

        List<ProductDocument> picked = service.selectDiverse(new float[] {1f, 0f}, candidates, 5);

        assertThat(picked).extracting(ProductDocument::getProductName).containsExactly("a", "b");
    }

    @Test
    @DisplayName("벡터가 없거나 빈 상품은 후보에서 빠지고, 차원이 다른 후보는 거리를 잴 수 없어 뽑히지 않는다")
    void selectDiverse_skipsUnmeasurable() {
        ProductDocument noVector = new ProductDocument(UUID.randomUUID(), "none", null, "C", "카테고리", 1000L, "ON_SALE", null, "", null);
        List<ProductDocument> candidates = List.of(
            product("a", 1f, 0f, 0f),
            noVector,
            product("wrongDims", 0f, 1f),
            product("c", 0f, 1f, 0f),
            product("d", 0f, 0f, 1f)); // 유효 후보 4 > k 3 이어야 다양화가 돈다

        List<ProductDocument> picked = service.selectDiverse(new float[] {1f, 0f, 0f}, candidates, 3);

        assertThat(picked).extracting(ProductDocument::getProductName).containsExactly("a", "c", "d");
    }

    @Test
    @DisplayName("topK가 0 이하거나 후보가 없으면 빈 목록이다")
    void selectDiverse_emptyInputs() {
        assertThat(service.selectDiverse(new float[] {1f}, List.of(), 3)).isEmpty();
        assertThat(service.selectDiverse(new float[] {1f}, List.of(product("a", 1f)), 0)).isEmpty();
        assertThat(service.selectDiverse(new float[] {1f}, null, 3)).isEmpty();
    }

    @Test
    @DisplayName("같은 상품 인스턴스가 후보에 두 번 있어도 한 번만 뽑힌다 (참조 비교가 아니라 인덱스로 표시)")
    void selectDiverse_duplicateInstanceNotPickedTwice() {
        ProductDocument a = product("a", 1f, 0f, 0f);
        ProductDocument c = product("c", 0f, 1f, 0f);
        List<ProductDocument> candidates = List.of(a, a, c);

        List<ProductDocument> picked = service.selectDiverse(new float[] {1f, 0f, 0f}, candidates, 3);

        // a·a는 거리 0이라 c가 먼저 뽑히고, 남은 a(두 번째 인스턴스)가 마지막에 뽑힌다 — 무한 루프·누락 없이 3개
        assertThat(picked).hasSize(3);
        assertThat(picked).extracting(ProductDocument::getProductName).containsExactly("a", "a", "c");
    }
}
