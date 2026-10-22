package com.barofarm.ai.embedding.application;

import com.barofarm.ai.embedding.domain.TasteProfile;
import com.barofarm.ai.log.domain.UserBehaviorLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.UserBehaviorLogRepository;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.log.history.model.HistoryEventType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 로그로 취향 벡터를 만드는 서비스.
 *
 * 최근 30일간의 행동 로그(장바구니·주문)를 최신순으로 읽어, 그 상품들의 임베딩 벡터를 행동 종류별
 * 가중치로 가중 평균하고 L2 정규화하여 768차원 취향 벡터를 만듭니다.
 *
 * <p><b>미리 만들어 두지 않고 추천을 요청받은 자리에서 계산합니다</b>(2026-09-08 변경).
 * 그전에는 장바구니·주문 이벤트마다 이 계산을 비동기로 돌려 결과를 `user_profile_embeddings`
 * 인덱스에 저장해 뒀는데, 그 구조가 안고 있던 문제가 셋이었습니다.
 * 첫째, 이벤트 1건마다 Elasticsearch 왕복이 4회 생겼습니다 — 장바구니에 상품 다섯 개를 담으면
 * 스무 번을 왕복하면서 같은 결과를 다섯 번 다시 만들었습니다.
 * 둘째, 같은 사용자의 이벤트 둘이 동시에 처리되면 두 스레드가 각자 계산해 각자 저장했고,
 * 버전 확인이 없어 나중 쓰기가 먼저 쓰기를 덮어썼습니다(한쪽 행동이 조용히 사라졌다는 뜻입니다).
 * 셋째, 갱신이 비동기라 방금 담은 상품이 추천에 바로 반영되지 않았고, 부하가 걸리면 대기열이 넘쳐
 * 갱신 자체가 버려졌습니다.
 * 계산 비용은 벡터 열 개를 더하는 정도라, 요청 때마다 해도 사람이 느낄 만한 차이가 없습니다.
 * 대신 위 세 문제가 전부 사라지고 추천은 항상 최신 행동을 반영합니다.
 *
 * <p>가중치는 행동 종류 하나로만 정해집니다 — 주문 3, 담기 2, 수량 변경 1, 취소·삭제 0입니다.
 * 장바구니 삭제는 0점으로 끝나지 않고, 같은 상품의 그 이전 장바구니 비중까지 지웁니다(2026-09-15,
 * {@link #aggregateByProduct} 참고). 한때 여기에 시간 감쇠와 수량을 더 곱했으나 둘 다 뺐습니다.
 *
 * 시간 감쇠(2026-09-06 삭제): 최근 몇 건만 읽는 구조에서는 한 번의 쇼핑으로 생긴 로그가
 * 전부 같은 시간대라 감쇠값이 같고, 결과를 L2 정규화하므로 같은 배수는 소거된다 — 계산만 하고 결과에 영향이 없었다.
 * "오래된 행동은 덜 본다"는 의도는 30일 조회 창이 담당한다. 근거: docs/audits/taste-vector/002-weight-axes-and-tests.md F-1·F-2
 *
 * 수량(2026-09-07 삭제): 수량은 선호의 세기가 아니라 소비량이다. 계란 30구를 사는 것은 계란을 다섯 배
 * 좋아해서가 아니라 계란이 그렇게 팔리기 때문이다.
 *
 * 검색 로그(2026-09-07 삭제): 검색은 사용자가 고른 상품이 아니라 검색 엔진이 상위에 올린 상품을 취향으로
 * 삼는 것이어서 신호로 쓰지 않는다. 근거: docs/audits/taste-vector/005-signal-and-weight-simplification.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileEmbeddingService {

    /**
     * 취향 벡터를 만들기 위한 최소 상품 수 (비중이 남은 서로 다른 상품이 3개 미만이면 만들지 않는다).
     *
     * 2026-09-15 이전에는 로그 건수를 셌다. 그러면 한 상품을 담고 수량을 두 번 바꾼 3건만으로도 통과해,
     * 취향이 그 상품 하나의 벡터가 됐다 — "1~2개 상품만으로 취향을 단정하지 않는다"는 이 조건의 의도를
     * 코드가 막지 못했다. 값 3 자체의 근거는 아직 없다.
     * 근거: docs/audits/taste-vector/006-behavior-weight-semantics.md B-1
     */
    private static final int MIN_PRODUCTS_FOR_EMBEDDING = 3;

    /** 로그 수집 대상 기간 (최근 30일) */
    private static final int LOOKBACK_DAYS = 30;

    /**
     * 최신순으로 가져올 행동 로그 건수.
     *
     * 2026-09-09 이전에는 "유형별 5건"이라 최대 10건이었고, 그 방식은 한쪽에 행동이 몰렸을 때 최근순을
     * 깨뜨렸습니다 — 오늘 담은 15개보다 25일 전 주문 1건이 우선했습니다. 인덱스를 합치면서 유형 구분 없이
     * 최근 10건을 읽습니다. 총량은 같고 "최근"의 뜻만 정확해졌습니다.
     */
    private static final int RECENT_LOG_LIMIT = 10;

    /** 벡터 유효성 판정을 위한 최소 노름(norm) 임계값 (0 나누기 및 부동소수점 오차 방지) */
    private static final double MIN_VECTOR_LENGTH = 1e-6;

    /** 장바구니·주문 행동 로그 레포지토리 */
    private final UserBehaviorLogRepository userBehaviorLogRepository;

    /** 상품 검색 및 임베딩 벡터 조회 레포지토리 */
    private final ProductSearchRepository productSearchRepository;

    /**
     * 사용자의 최근 행동 로그로 취향 벡터를 계산합니다. 저장하지 않고 만들어 돌려줍니다.
     *
     * 비중이 남은 상품이 3개 미만이거나, 그 상품 중 벡터를 가진 것이 하나도 없거나, 벡터들이 서로 상쇄돼
     * 방향이 사라지면 빈 결과를 돌려줍니다. 그 경우 개인화 추천은 빈 목록으로 나갑니다 — 아직 취향을
     * 판단할 근거가 없다는 뜻이지 오류가 아닙니다.
     *
     * @param userId 대상 사용자 식별자
     */
    public Optional<TasteProfile> computeProfile(UUID userId) {
        // [1단계: 최근 30일간의 행동 로그 조회]
        // 장바구니·주문 구분 없이 최신순 10건을 한 번에 읽습니다.
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<UserBehaviorLogDocument> logs = userBehaviorLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(
                userId, since, PageRequest.of(0, RECENT_LOG_LIMIT));

        // [2단계: 로그를 상품별 비중으로 묶기]
        // 행동 종류로 비중을 매기되, 장바구니 삭제는 같은 상품의 그 이전 장바구니 비중을 지웁니다.
        List<WeightedProduct> interests = aggregateByProduct(logs);

        // [3단계: 최소 상품 수 검증]
        // 로그 건수가 아니라 비중이 남은 서로 다른 상품 수로 셉니다 — 한 상품의 로그만 쌓인 경우를 걸러냅니다.
        if (interests.size() < MIN_PRODUCTS_FOR_EMBEDDING) {
            log.debug("사용자 ID {}의 취향에 반영할 상품이 {}개라 {}개에 못 미쳐 취향 벡터를 만들지 않습니다.",
                userId, interests.size(), MIN_PRODUCTS_FOR_EMBEDDING);
            return Optional.empty();
        }

        // [4단계: 관심 상품들의 사전 생성된 임베딩 벡터 일괄 조회]
        List<UUID> productIds = interests.stream().map(WeightedProduct::productId).toList();
        Map<UUID, float[]> productVectors = loadProductVectors(productIds);

        // [5단계: 벡터 가중 평균 합산 및 L2 정규화]
        float[] profileVector = blendIntoProfileVector(interests, productVectors);
        if (profileVector == null) {
            log.debug("사용자 ID {}의 로그에 등장한 상품 중 벡터를 가진 것이 없습니다.", userId);
            return Optional.empty();
        }

        log.debug("사용자 ID {}의 취향 벡터를 만들었습니다. (로그 {}개, 상품 {}개 반영)",
            userId, logs.size(), productVectors.size());

        // 이미 담아 봤거나 산 상품은 추천에서 뺄 수 있도록 함께 돌려줍니다.
        return Optional.of(new TasteProfile(profileVector, productIds));
    }

    /**
     * 상품 하나와 그 상품의 비중(같은 상품의 로그 비중을 합한 값)을 보관하는 내부 레코드.
     */
    record WeightedProduct(UUID productId, double weight) {
        /** 상품 ID가 존재하고 비중이 양수인 경우에만 유효한 관심도로 판정 */
        private boolean isUsable() {
            return productId != null && weight > 0;
        }
    }

    /**
     * 최신순 행동 로그를 상품별 비중으로 묶습니다. 비중이 0보다 큰 상품만 돌려줍니다.
     *
     * 오래된 로그부터 순서대로 훑으며 상품마다 장바구니 비중과 주문 비중을 따로 쌓습니다.
     * 장바구니 삭제를 만나면 그 상품의 장바구니 비중을 0으로 되돌립니다. 담았다가 뺀 상품은 관심을 거둔
     * 것이라, 취향에서도 경험 상품에서도 빠져야 다시 추천될 수 있기 때문입니다. 삭제 뒤에 다시 담으면
     * 그때부터 다시 쌓입니다.
     *
     * 주문 확정 비중은 삭제로 지우지 않습니다. 주문 흐름이 장바구니를 비우지 않아서, 산 뒤에 사용자가
     * 장바구니를 정리하면 삭제 이벤트가 들어오는데 그때 구매 신호까지 사라지면 안 되기 때문입니다.
     *
     * 같은 상품의 로그 비중은 합산됩니다(담기 2와 주문 3이면 5). 상품당 한 번만 셀지는 추천 품질을 잴
     * 수단이 생긴 뒤 정합니다. 2026-09-15 이전에는 로그 한 줄마다 비중을 매겨 비중 0인 삭제 로그만 버렸고,
     * 같은 상품의 담기 로그는 그대로 남아 뺀 상품이 계속 취향에 섞이고 추천에서도 제외됐습니다.
     * 근거: docs/audits/taste-vector/006-behavior-weight-semantics.md A-1·C-1
     *
     * 테스트에서 직접 부를 수 있게 패키지 접근입니다 — 인스턴스 상태를 쓰지 않습니다.
     */
    static List<WeightedProduct> aggregateByProduct(List<UserBehaviorLogDocument> logsNewestFirst) {
        Map<UUID, ProductSignal> signals = new LinkedHashMap<>();
        for (int i = logsNewestFirst.size() - 1; i >= 0; i--) {
            UserBehaviorLogDocument behaviorLog = logsNewestFirst.get(i);
            if (behaviorLog.getProductId() == null) {
                continue;
            }
            signals.computeIfAbsent(behaviorLog.getProductId(), productId -> new ProductSignal())
                .apply(behaviorLog.getEventType());
        }
        return signals.entrySet().stream()
            .map(entry -> new WeightedProduct(entry.getKey(), entry.getValue().weight()))
            .filter(WeightedProduct::isUsable)
            .toList();
    }

    /**
     * 상품 하나에 쌓인 장바구니 비중과 주문 비중. 삭제가 장바구니 쪽만 지우도록 둘을 나눠 둡니다.
     */
    private static final class ProductSignal {

        /** 담기·수량 변경으로 쌓인 비중. 장바구니 삭제를 만나면 0으로 되돌아간다 */
        private int cartWeight;

        /** 주문 이벤트로 쌓인 비중. 장바구니 삭제로는 지워지지 않는다 */
        private int orderWeight;

        /** 로그 하나를 반영합니다. 로그는 오래된 것부터 들어와야 삭제가 그 이전 비중만 지웁니다. */
        private void apply(HistoryEventType eventType) {
            if (eventType == HistoryEventType.CART_ITEM_REMOVED) {
                cartWeight = 0;
                return;
            }
            int weight = calculateEventWeight(eventType);
            // 값을 돌려주는 switch 식으로 써야 도메인이 늘어날 때 컴파일 에러로 드러난다(switch 문은 빠진 경우를 그냥 넘긴다).
            boolean cartSide = switch (eventType.getDomain()) {
                case CART -> true;
                case ORDER -> false;
            };
            if (cartSide) {
                cartWeight += weight;
            } else {
                orderWeight += weight;
            }
        }

        private int weight() {
            return cartWeight + orderWeight;
        }
    }

    /**
     * 이벤트 유형별 중요도 가중치. 이 값이 곧 로그 1건의 비중이다 — 곱하는 축이 더 없다.
     * 테스트에서 직접 부를 수 있게 패키지 접근이다 — 인스턴스 상태를 쓰지 않는다.
     * - ORDER_CONFIRMED: 3 (실제 구매로 가장 강력한 선호 표현)
     * - CART_ITEM_ADDED: 2 (구매 고려 단계의 높은 관심)
     * - CART_QUANTITY_UPDATED: 1 (관심 유지)
     * - CART_ITEM_REMOVED: 0. 비중만 0인 것이 아니라 같은 상품의 그 이전 장바구니 비중을 지운다({@link #aggregateByProduct})
     * - ORDER_CANCELLED: 0. 현재 이 이벤트를 발행하는 모듈이 없다 — baro-order의 발행 코드가 2026-01-24 병합
     *   커밋(23face20)에서 빠졌다. 들어오더라도 로그에 주문 ID가 없어 어느 확정을 취소하는지 짝지을 수 없으므로,
     *   같은 상품의 주문 확정 비중을 지우지 못한다. 근거: docs/audits/taste-vector/006-behavior-weight-semantics.md B-2
     * 3/2/1이라는 값 자체는 기록된 근거가 없다(같은 문서 D-1) — 비율만 결과에 작동한다.
     */
    static int calculateEventWeight(HistoryEventType eventType) {
        return switch (eventType) {
            case ORDER_CONFIRMED -> 3;
            case CART_ITEM_ADDED -> 2;
            case CART_QUANTITY_UPDATED -> 1;
            case CART_ITEM_REMOVED, ORDER_CANCELLED -> 0;
        };
    }

    /**
     * Elasticsearch에서 대상 상품들의 768차원 임베딩 벡터를 일괄 조회하여 Map으로 반환합니다.
     */
    private Map<UUID, float[]> loadProductVectors(Collection<UUID> productIds) {
        Map<UUID, float[]> vectors = new HashMap<>();
        productSearchRepository.findAllById(productIds).forEach(product -> {
            if (product.getVector() != null) {
                vectors.put(product.getProductId(), product.getVector());
            }
        });
        return vectors;
    }

    /**
     * 여러 상품의 임베딩 벡터를 각 가중치에 비례하여 누적 합산하고 단위 벡터(길이 1)로 L2 정규화합니다.
     * 누적 과정의 부동소수점 오차를 최소화하기 위해 double 정밀도로 합산한 후 최종 float[]으로 반환합니다.
     */
    static float[] blendIntoProfileVector(
        List<WeightedProduct> interests,
        Map<UUID, float[]> productVectors
    ) {
        double[] blended = null;

        // [1단계: 각 관심 상품 벡터의 가중치 비례 차원별 누적 합산]
        for (WeightedProduct interest : interests) {
            float[] vector = productVectors.get(interest.productId());
            if (vector == null) {
                continue;
            }
            if (blended == null) {
                blended = new double[vector.length];
            }
            if (vector.length != blended.length) {
                log.warn("차원이 다른 상품 벡터를 건너뜁니다. productId: {}, 차원: {}",
                    interest.productId(), vector.length);
                continue;
            }
            for (int i = 0; i < vector.length; i++) {
                blended[i] += interest.weight() * vector[i];
            }
        }

        // 유효한 상품 벡터가 하나도 없는 경우 프로필 벡터를 생성할 수 없으므로 null 반환
        if (blended == null) {
            return null;
        }

        // [2단계: 누적 벡터의 유클리드 노름(L2 Norm = sqrt(sum(x_i^2))) 계산 및 유효성 검증]
        double length = 0.0;
        for (double value : blended) {
            length += value * value;
        }
        length = Math.sqrt(length);

        // 벡터 길이가 임계값 미만인 경우(상쇄되어 방향이 소실된 영벡터) null 반환
        if (length < MIN_VECTOR_LENGTH) {
            return null;
        }

        // [3단계: 단위 벡터(길이 1.0)로 L2 정규화]
        float[] normalized = new float[blended.length];
        for (int i = 0; i < blended.length; i++) {
            normalized[i] = (float) (blended[i] / length);
        }
        return normalized;
    }
}
