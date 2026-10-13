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
 * 비중 범위(0 또는 1~15)와 "같은 배수는 정규화에서 소거된다"는 사실을 테스트 이름으로 남긴다 —
 * 후자가 시간 감쇠 축을 뺀 근거다(감사 taste-vector/002 F-1).
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

    // ---- 수량 가중치 ----

    @Test
    @DisplayName("수량 가중치 — null·0은 1로, 5를 넘으면 5로 자른다")
    void quantityWeight_clampsBetweenOneAndFive() {
        assertThat(UserProfileEmbeddingService.quantityWeight(null)).isEqualTo(1);
        assertThat(UserProfileEmbeddingService.quantityWeight(0)).isEqualTo(1);
        assertThat(UserProfileEmbeddingService.quantityWeight(-3)).isEqualTo(1);
        assertThat(UserProfileEmbeddingService.quantityWeight(3)).isEqualTo(3);
        assertThat(UserProfileEmbeddingService.quantityWeight(5)).isEqualTo(5);
        assertThat(UserProfileEmbeddingService.quantityWeight(100)).isEqualTo(UserProfileEmbeddingService.MAX_QUANTITY_WEIGHT);
    }

    // ---- 행동 비중 (행동 × 수량) ----

    @Test
    @DisplayName("행동 비중 — 범위는 0(삭제·취소) 또는 1~15(수량변경 1개 ~ 주문 5개 이상)")
    void behaviorWeight_rangeIsZeroOrOneToFifteen() {
        assertThat(UserProfileEmbeddingService.behaviorWeight(HistoryEventType.CART_QUANTITY_UPDATED, 1)).isEqualTo(1.0);
        assertThat(UserProfileEmbeddingService.behaviorWeight(HistoryEventType.ORDER_CONFIRMED, 100)).isEqualTo(15.0);
        assertThat(UserProfileEmbeddingService.behaviorWeight(HistoryEventType.CART_ITEM_ADDED, 3)).isEqualTo(6.0);
    }

    @Test
    @DisplayName("행동 비중 — 삭제·취소는 수량이 아무리 커도 0이다")
    void behaviorWeight_removalIsZeroRegardlessOfQuantity() {
        assertThat(UserProfileEmbeddingService.behaviorWeight(HistoryEventType.CART_ITEM_REMOVED, 100)).isZero();
        assertThat(UserProfileEmbeddingService.behaviorWeight(HistoryEventType.ORDER_CANCELLED, 100)).isZero();
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
            List.of(new WeightedProduct(a, 15.0), new WeightedProduct(b, 1.0)), vectors);
        float[] favorsB = UserProfileEmbeddingService.blendIntoProfileVector(
            List.of(new WeightedProduct(a, 1.0), new WeightedProduct(b, 15.0)), vectors);

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
