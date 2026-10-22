package com.barofarm.ai.embedding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.barofarm.ai.embedding.application.UserProfileEmbeddingService.WeightedProduct;
import com.barofarm.ai.embedding.domain.TasteProfile;
import com.barofarm.ai.log.domain.UserBehaviorLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.UserBehaviorLogRepository;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.log.history.model.HistoryEventType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 취향 벡터의 비중 계산과 가중 평균, 그리고 행동 로그에서 취향 벡터를 만드는 경로를 고정한다.
 * 앞의 둘은 인스턴스 상태 없는 순수 계산이고, 마지막(`computeProfile`)만 두 저장소를 mock으로 대신한다.
 * 비중은 행동 종류 하나로만 정해지므로 범위가 0 또는 1~3이다 — 2026-09-07에 수량 축을 빼면서
 * 15배였던 범위가 3배로 줄었다(docs/audits/taste-vector/005-signal-and-weight-simplification.md).
 * "같은 배수는 정규화에서 소거된다"는 사실도 이름으로 남긴다 — 그게 시간 감쇠 축을 뺀 근거다(감사 002 F-1).
 * 삭제가 같은 상품의 담기 비중을 지우는지, 최소 조건을 상품 수로 세는지는 감사 006 A-1·B-1이 근거다.
 */
class UserProfileEmbeddingServiceTest {

    private final UUID user = UUID.randomUUID();

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

    // ---- 행동 로그 → 취향 벡터 (computeProfile) ----

    @Test
    @DisplayName("담았다가 뺀 상품은 취향에도, 경험 상품에도 남지 않는다")
    void computeProfile_removedAfterAdded_dropsProduct() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        UUID z = UUID.randomUUID();
        UUID w = UUID.randomUUID();

        // 최신순: X 삭제, X 담기, Y 담기, Z 담기, W 주문
        TasteProfile profile = computeProfile(
            List.of(
                log(x, HistoryEventType.CART_ITEM_REMOVED),
                log(x, HistoryEventType.CART_ITEM_ADDED),
                log(y, HistoryEventType.CART_ITEM_ADDED),
                log(z, HistoryEventType.CART_ITEM_ADDED),
                log(w, HistoryEventType.ORDER_CONFIRMED)),
            Map.of(x, new float[] {1f, 0f, 0f, 0f}, y, new float[] {0f, 1f, 0f, 0f},
                z, new float[] {0f, 0f, 1f, 0f}, w, new float[] {0f, 0f, 0f, 1f}))
            .orElseThrow();

        assertThat(profile.experiencedProductIds()).containsExactlyInAnyOrder(y, z, w);
        assertThat(profile.vector()[0]).isZero();
    }

    @Test
    @DisplayName("장바구니 삭제는 같은 상품의 주문 확정 비중을 지우지 않는다")
    void computeProfile_removeKeepsConfirmedOrder() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        UUID z = UUID.randomUUID();

        // 최신순: X 삭제(구매 뒤 장바구니 정리), X 주문, X 담기, Y 담기, Z 담기
        TasteProfile profile = computeProfile(
            List.of(
                log(x, HistoryEventType.CART_ITEM_REMOVED),
                log(x, HistoryEventType.ORDER_CONFIRMED),
                log(x, HistoryEventType.CART_ITEM_ADDED),
                log(y, HistoryEventType.CART_ITEM_ADDED),
                log(z, HistoryEventType.CART_ITEM_ADDED)),
            Map.of(x, new float[] {1f, 0f, 0f}, y, new float[] {0f, 1f, 0f}, z, new float[] {0f, 0f, 1f}))
            .orElseThrow();

        // X는 주문 비중 3만 남고 담기 비중 2는 지워진다 — (3, 2, 2) 방향
        assertThat(profile.experiencedProductIds()).contains(x);
        assertThat((double) profile.vector()[0] / profile.vector()[1]).isCloseTo(1.5, within(1e-5));
    }

    @Test
    @DisplayName("뺐다가 다시 담은 상품은 다시 취향에 들어간다")
    void computeProfile_readdedAfterRemove_counts() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        UUID z = UUID.randomUUID();

        // 최신순: X 담기, X 삭제, X 담기, Y 담기, Z 담기
        TasteProfile profile = computeProfile(
            List.of(
                log(x, HistoryEventType.CART_ITEM_ADDED),
                log(x, HistoryEventType.CART_ITEM_REMOVED),
                log(x, HistoryEventType.CART_ITEM_ADDED),
                log(y, HistoryEventType.CART_ITEM_ADDED),
                log(z, HistoryEventType.CART_ITEM_ADDED)),
            Map.of(x, new float[] {1f, 0f, 0f}, y, new float[] {0f, 1f, 0f}, z, new float[] {0f, 0f, 1f}))
            .orElseThrow();

        // 삭제 이전의 담기는 지워지고 삭제 이후의 담기 하나만 남는다 — (2, 2, 2) 방향
        assertThat(profile.experiencedProductIds()).contains(x);
        assertThat(profile.vector()[0]).isCloseTo(profile.vector()[1], within(1e-6f));
    }

    @Test
    @DisplayName("로그가 3건이어도 상품이 3개 미만이면 취향 벡터를 만들지 않는다")
    void computeProfile_fewerThanThreeProducts_returnsEmpty() {
        UUID x = UUID.randomUUID();

        Optional<TasteProfile> profile = computeProfile(
            List.of(
                log(x, HistoryEventType.CART_QUANTITY_UPDATED),
                log(x, HistoryEventType.CART_QUANTITY_UPDATED),
                log(x, HistoryEventType.CART_ITEM_ADDED)),
            Map.of(x, new float[] {1f, 0f}));

        assertThat(profile).isEmpty();
    }

    @Test
    @DisplayName("같은 상품의 로그 비중은 지금처럼 합산된다 (담기 2 + 주문 3 = 5)")
    void computeProfile_sameProductLogsAccumulate() {
        UUID p = UUID.randomUUID();
        UUID q = UUID.randomUUID();
        UUID r = UUID.randomUUID();

        // 최신순: P 주문, P 담기, Q 담기, R 담기
        TasteProfile profile = computeProfile(
            List.of(
                log(p, HistoryEventType.ORDER_CONFIRMED),
                log(p, HistoryEventType.CART_ITEM_ADDED),
                log(q, HistoryEventType.CART_ITEM_ADDED),
                log(r, HistoryEventType.CART_ITEM_ADDED)),
            Map.of(p, new float[] {1f, 0f, 0f}, q, new float[] {0f, 1f, 0f}, r, new float[] {0f, 0f, 1f}))
            .orElseThrow();

        assertThat((double) profile.vector()[0] / profile.vector()[1]).isCloseTo(2.5, within(1e-5));
    }

    // 로그를 최신순 그대로 돌려주는 로그 저장소와, 주어진 벡터만 가진 상품 저장소로 계산한다.
    // 실제 저장소도 최신순으로 돌려주므로, 로그 시각은 목록 순서대로 한 시간씩 과거로 매긴다.
    private Optional<TasteProfile> computeProfile(List<UserBehaviorLogDocument> logsNewestFirst,
                                                  Map<UUID, float[]> vectors) {
        List<UserBehaviorLogDocument> timed = new ArrayList<>();
        for (int i = 0; i < logsNewestFirst.size(); i++) {
            UserBehaviorLogDocument log = logsNewestFirst.get(i);
            timed.add(UserBehaviorLogDocument.builder()
                .id("log-" + i).userId(user).productId(log.getProductId()).eventType(log.getEventType())
                .occurredAt(Instant.now().minus(i + 1L, ChronoUnit.HOURS)).build());
        }
        List<ProductDocument> products = vectors.entrySet().stream()
            .map(e -> new ProductDocument(e.getKey(), "상품", null, "C", "카테고리", 1000L, "ON_SALE",
                Instant.now(), "", e.getValue()))
            .toList();

        UserBehaviorLogRepository logRepository = mock(UserBehaviorLogRepository.class);
        ProductSearchRepository productRepository = mock(ProductSearchRepository.class);
        when(logRepository.findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(eq(user), any(), any()))
            .thenReturn(timed);
        when(productRepository.findAllById(anyCollection())).thenReturn(products);
        return new UserProfileEmbeddingService(logRepository, productRepository).computeProfile(user);
    }

    private UserBehaviorLogDocument log(UUID productId, HistoryEventType eventType) {
        return UserBehaviorLogDocument.builder().productId(productId).eventType(eventType).build();
    }

    private static double length(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }
}
