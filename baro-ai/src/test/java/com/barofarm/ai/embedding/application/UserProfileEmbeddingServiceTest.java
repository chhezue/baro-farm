package com.barofarm.ai.embedding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.barofarm.ai.embedding.application.UserProfileEmbeddingService.WeightedProduct;
import com.barofarm.log.history.model.HistoryEventType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 취향 벡터의 비중 계산과 가중 평균을 고정한다. 전부 인스턴스 상태 없는 순수 계산이라 mock이 없다.
 * 비중은 행동 종류 하나로만 정해지므로 범위가 0 또는 1~3이다 — 2026-09-07에 수량 축을 빼면서
 * 15배였던 범위가 3배로 줄었다(docs/audits/taste-vector/005-signal-and-weight-simplification.md).
 * "같은 배수는 정규화에서 소거된다"는 사실도 이름으로 남긴다 — 그게 시간 감쇠 축을 뺀 근거다(감사 002 F-1).
 */
class UserProfileEmbeddingServiceTest {

    // ---- 행동 가중치 ----

    @Test
    @DisplayName("행동 가중치 — 주문 3 · 담기 2 · 수량변경 1 · 삭제·취소 0")
    void eventWeight_perEventType() {
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.ORDER_CONFIRMED)).isEqualTo(3);
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.CART_ITEM_ADDED)).isEqualTo(2);
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.CART_QUANTITY_UPDATED)).isEqualTo(1);
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.CART_ITEM_REMOVED)).isZero();
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.ORDER_CANCELLED)).isZero();
    }

    // ---- 비중 범위 ----

    @Test
    @DisplayName("비중 범위 — 0(삭제·취소) 또는 1~3이다. 곱하는 축이 더 없어 수량이 커도 달라지지 않는다")
    void eventWeight_rangeIsZeroOrOneToThree() {
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.CART_QUANTITY_UPDATED)).isEqualTo(1);
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.ORDER_CONFIRMED)).isEqualTo(3);

        // 같은 이벤트면 장바구니에 1개를 담든 100개를 담든 비중이 같다 (2026-09-07 수량 축 삭제)
        assertThat(UserProfileEmbeddingService.calculateEventWeight(HistoryEventType.CART_ITEM_ADDED)).isEqualTo(2);
    }

    // ---- 가중 평균 · L2 정규화 ----

    @Test
    @DisplayName("가중 평균 — 결과는 길이 1인 단위 벡터다")
    void blend_resultIsUnitVector() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        float[] result = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 2.0), new WeightedProduct(b, 1.0)),
            Map.of(a, new float[] {1f, 0f, 0f}, b, new float[] {0f, 1f, 0f}));

        // 2·(1,0,0) + 1·(0,1,0) = (2,1,0) → 정규화하면 (2,1,0)/√5
        double norm = Math.sqrt(5);
        assertThat(result[0]).isCloseTo((float) (2 / norm), within(1e-6f));
        assertThat(result[1]).isCloseTo((float) (1 / norm), within(1e-6f));
        assertThat(result[2]).isZero();
        assertThat(length(result)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("가중 평균 — 모든 비중에 같은 배수를 곱해도 결과가 같다 (시간 감쇠 축을 뺀 근거)")
    void blend_uniformScalingCancelsOut() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, float[]> vectors = Map.of(a, new float[] {1f, 2f, 0f}, b, new float[] {0f, 1f, 3f});

        float[] base = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 2.0), new WeightedProduct(b, 6.0)), vectors);
        float[] scaled = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 2.0 * 3.0), new WeightedProduct(b, 6.0 * 3.0)), vectors);

        for (int i = 0; i < base.length; i++) {
            assertThat(scaled[i]).isCloseTo(base[i], within(1e-6f));
        }
    }

    @Test
    @DisplayName("가중 평균 — 비중이 다르면 방향이 바뀐다 (비중이 실제로 작동한다)")
    void blend_differentWeightsChangeDirection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, float[]> vectors = Map.of(a, new float[] {1f, 0f}, b, new float[] {0f, 1f});

        float[] favorsA = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 3.0), new WeightedProduct(b, 1.0)), vectors);
        float[] favorsB = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 1.0), new WeightedProduct(b, 3.0)), vectors);

        assertThat(favorsA[0]).isGreaterThan(favorsA[1]);
        assertThat(favorsB[1]).isGreaterThan(favorsB[0]);
    }

    @Test
    @DisplayName("가중 평균 — 벡터가 하나도 없거나 서로 상쇄돼 영벡터가 되면 null")
    void blend_returnsNullWhenNothingUsable() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 1.0)), Map.of())).isNull();

        assertThat(UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 1.0), new WeightedProduct(b, 1.0)),
            Map.of(a, new float[] {1f, 0f}, b, new float[] {-1f, 0f}))).isNull();
    }

    @Test
    @DisplayName("가중 평균 — 차원이 다른 벡터는 건너뛰고 나머지로 계산한다")
    void blend_skipsMismatchedDimension() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        float[] result = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 1.0), new WeightedProduct(b, 1.0)),
            Map.of(a, new float[] {0f, 1f}, b, new float[] {1f, 1f, 1f}));

        assertThat(result).hasSize(2);
        assertThat(result[0]).isZero();
        assertThat(result[1]).isCloseTo(1f, within(1e-6f));
    }

    private static double length(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }
}
