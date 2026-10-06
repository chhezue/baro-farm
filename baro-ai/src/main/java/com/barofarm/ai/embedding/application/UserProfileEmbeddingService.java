package com.barofarm.ai.embedding.application;

import com.barofarm.ai.embedding.domain.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.infrastructure.elasticsearch.UserProfileEmbeddingRepository;
import com.barofarm.ai.log.domain.CartLogDocument;
import com.barofarm.ai.log.domain.OrderLogDocument;
import com.barofarm.ai.log.domain.SearchLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.CartLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.OrderLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.SearchLogRepository;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.log.history.model.HistoryEventType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 로그 기반 취향 프로필 벡터 생성 및 갱신 서비스.
 *
 * 최근 30일간의 장바구니, 주문, 검색 로그를 분석하여 사용자가 관심을 보인 상품들의 임베딩 벡터를
 * 행동 유형·수량·시간 경과에 따른 가중치로 가중 평균(Weighted Average)하고 L2 정규화하여
 * 1536차원 사용자 취향 벡터를 생성 및 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileEmbeddingService {

    /** 프로필 벡터를 생성하기 위한 최소 필요 로그 건수 (3건 미만 시 갱신 스킵) */
    private static final int MIN_TOTAL_LOGS_FOR_EMBEDDING = 3;

    /** 로그 수집 대상 기간 (최근 30일) */
    private static final int LOOKBACK_DAYS = 30;

    /** 로그 유형(검색/장바구니/주문)별로 최신순으로 가져올 최대 건수 */
    private static final int RECENT_LOG_LIMIT = 5;

    /** 검색 이벤트의 기본 가중치 */
    private static final double SEARCH_EVENT_WEIGHT = 1.0;

    /** 수량 가중치 최대 상한선 (단일 대량 구매로 인한 벡터 왜곡 방지) */
    private static final int MAX_QUANTITY_WEIGHT = 5;

    /** 시간 경과 가중치 최댓값 (최근 발생 행동) */
    private static final double MAX_TIME_WEIGHT = 3.0;

    /** 시간 경과 가중치 최솟값 (30일 이내 가장 오래된 행동) */
    private static final double MIN_TIME_WEIGHT = 0.3;

    /** 시간 가중치 반감기 (168시간 = 7일, 7일 경과마다 가중치가 절반으로 지수 감쇠) */
    private static final double TIME_WEIGHT_HALF_LIFE_HOURS = 168.0;

    /** 벡터 유효성 판정을 위한 최소 노름(norm) 임계값 (0 나누기 및 부동소수점 오차 방지) */
    private static final double MIN_VECTOR_LENGTH = 1e-6;

    /** 장바구니 행동 로그 레포지토리 */
    private final CartLogRepository cartLogRepository;

    /** 주문 행동 로그 레포지토리 */
    private final OrderLogRepository orderLogRepository;

    /** 검색 행동 로그 레포지토리 */
    private final SearchLogRepository searchLogRepository;

    /** 상품 검색 및 임베딩 벡터 조회 레포지토리 */
    private final ProductSearchRepository productSearchRepository;

    /** 사용자 프로필 벡터 저장소 레포지토리 */
    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;

    /**
     * 사용자의 최근 행동 로그를 종합하여 취향 벡터를 재계산하고 Elasticsearch에 영속화합니다.
     *
     * @param userId 대상 사용자 식별자
     */
    public void updateUserProfileEmbedding(UUID userId) {
        // [1단계: 최근 30일간의 행동 로그 조회]
        // 최근 30일(LOOKBACK_DAYS) 이내에 발생한 검색, 장바구니, 주문 로그를 각 도메인별로 최신순 최대 5건(RECENT_LOG_LIMIT)씩 조회합니다.
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        Pageable recent = PageRequest.of(0, RECENT_LOG_LIMIT);

        List<SearchLogDocument> searchLogs = searchLogRepository
            .findAllByUserIdAndSearchedAtAfterOrderBySearchedAtDesc(userId, since, recent);
        List<CartLogDocument> cartLogs = cartLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, since, recent);
        List<OrderLogDocument> orderLogs = orderLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, since, recent);

        int totalLogCount = searchLogs.size() + cartLogs.size() + orderLogs.size();

        // [2단계: 최소 로그 조건 검증]
        // 조회된 총 로그 수가 최소 기준치(MIN_TOTAL_LOGS_FOR_EMBEDDING = 3건) 미만이면
        // 취향을 판별할 데이터가 부족하므로 프로필 갱신을 건너뜁니다.
        if (totalLogCount < MIN_TOTAL_LOGS_FOR_EMBEDDING) {
            log.info("사용자 ID {}의 총 로그 개수({})가 {}개 미만이므로 프로필 갱신을 건너뜁니다. "
                    + "(검색:{}, 장바구니:{}, 주문:{})",
                userId, totalLogCount, MIN_TOTAL_LOGS_FOR_EMBEDDING,
                searchLogs.size(), cartLogs.size(), orderLogs.size());
            return;
        }

        // [3단계: 로그별 가중치를 반영한 관심 상품(WeightedProduct) 산출]
        // - 장바구니/주문: 행동 종류(주문 3점, 담기 2점, 수량변경 1점, 취소/삭제 0점) × 수량 가중치(최대 5) × 시간 감쇠(7일 반감기) 적용
        // - 검색: 검색어 자체가 아닌 상위 노출 상품 목록(topProductIds)에 시간 감쇠 가중치 적용
        // - 가중치가 0(삭제/취소)이거나 상품 정보가 누락된 무효 항목은 filter(WeightedProduct::isUsable)로 제외합니다.
        List<WeightedProduct> cartAndOrderInterests = Stream.concat(
            cartLogs.stream().map(this::toWeightedProduct),
            orderLogs.stream().map(this::toWeightedProduct)
        ).filter(WeightedProduct::isUsable).toList();

        List<WeightedProduct> searchInterests = searchLogs.stream()
            .flatMap(this::toWeightedProducts)
            .filter(WeightedProduct::isUsable)
            .toList();

        List<WeightedProduct> interests =
            Stream.concat(cartAndOrderInterests.stream(), searchInterests.stream()).toList();

        if (interests.isEmpty()) {
            log.info("사용자 ID {}의 로그에 취향으로 볼 만한 상품이 없어 프로필을 저장하지 않습니다.", userId);
            return;
        }

        // [4단계: 관심 상품들의 사전 생성된 임베딩 벡터 일괄 조회]
        // 도출된 관심 상품들의 1536차원 사전 계산 벡터를 Elasticsearch에서 일괄 로드합니다.
        Map<UUID, float[]> productVectors = loadProductVectors(
            interests.stream().map(WeightedProduct::productId).distinct().toList());

        // [5단계: 벡터 가중 평균 합산 및 L2 정규화 (단위 벡터 변환)]
        // 각 상품 벡터에 관심도 가중치를 곱해 누적 합산한 뒤, 전체 유클리드 길이(L2 Norm)로 나누어 길이가 1인 단위 벡터를 생성합니다.
        float[] profileVector = blendIntoProfileVector(interests, productVectors);
        if (profileVector == null) {
            log.info("사용자 ID {}의 로그에 등장한 상품 중 벡터를 가진 것이 없어 프로필을 저장하지 않습니다.", userId);
            return;
        }

        // [6단계: 기경험 상품 목록 추출 및 Elasticsearch 프로필 문서 영속화]
        // 이미 담거나 구매한 상품 ID를 추천 제외 대상('sourceProductIds')으로 분리하고,
        // 최종 계산된 프로필 벡터와 함께 Elasticsearch 'user_profile_embeddings' 인덱스에 저장합니다.
        List<UUID> sourceProductIds = cartAndOrderInterests.stream()
            .map(WeightedProduct::productId)
            .distinct()
            .toList();

        userProfileEmbeddingRepository.save(UserProfileEmbeddingDocument.builder()
            .userId(userId)
            .userProfileVector(profileVector)
            .lastUpdatedAt(Instant.now())
            .sourceProductIds(sourceProductIds)
            .build());

        log.info("사용자 ID {}의 프로필 벡터를 성공적으로 업데이트했습니다. (로그 {}개, 상품 {}개 반영)",
            userId, totalLogCount, productVectors.size());
    }

    /**
     * 상품 식별자와 해당 상품에 대한 종합 계산 가중치를 보관하는 내부 레코드.
     */
    private record WeightedProduct(UUID productId, double weight) {
        /** 상품 ID가 존재하고 가중치가 양수인 경우에만 유효한 관심도로 판정 */
        private boolean isUsable() {
            return productId != null && weight > 0;
        }
    }

    /** 장바구니 로그를 가중치 적용 상품 객체로 변환 */
    private WeightedProduct toWeightedProduct(CartLogDocument cartLog) {
        return new WeightedProduct(cartLog.getProductId(),
            behaviorWeight(cartLog.getEventType(), cartLog.getQuantity(), cartLog.getOccurredAt()));
    }

    /** 주문 로그를 가중치 적용 상품 객체로 변환 */
    private WeightedProduct toWeightedProduct(OrderLogDocument orderLog) {
        return new WeightedProduct(orderLog.getProductId(),
            behaviorWeight(orderLog.getEventType(), orderLog.getQuantity(), orderLog.getOccurredAt()));
    }

    /**
     * 검색 로그의 상위 노출 상품들을 가중치 적용 상품 스트림으로 변환.
     * 검색어 텍스트 대신 상위 노출 상품들의 벡터에 검색 이벤트 가중치와 시간 감쇠 가중치를 적용합니다.
     */
    private Stream<WeightedProduct> toWeightedProducts(SearchLogDocument searchLog) {
        List<UUID> topProductIds = searchLog.getTopProductIds();
        if (topProductIds == null || topProductIds.isEmpty()) {
            return Stream.empty();
        }

        double weight = SEARCH_EVENT_WEIGHT * calculateTimeWeight(searchLog.getSearchedAt());
        return topProductIds.stream().map(productId -> new WeightedProduct(productId, weight));
    }

    /**
     * 단일 행동 로그의 종합 가중치 계산 (행동 가중치 × 수량 가중치 × 시간 감쇠 가중치).
     */
    private double behaviorWeight(HistoryEventType eventType, Integer quantity, Instant occurredAt) {
        int eventWeight = calculateEventWeight(eventType);
        if (eventWeight == 0) {
            return 0.0;
        }
        return eventWeight * quantityWeight(quantity) * calculateTimeWeight(occurredAt);
    }

    /**
     * 이벤트 유형별 기본 중요도 가중치 정의.
     * - ORDER_CONFIRMED: 3 (실제 구매로 가장 강력한 선호 표현)
     * - CART_ITEM_ADDED: 2 (구매 고려 단계의 높은 관심)
     * - CART_QUANTITY_UPDATED: 1 (관심 유지)
     * - CART_ITEM_REMOVED, ORDER_CANCELLED: 0 (관심 철회로 간주하여 가중 평균에서 제외)
     */
    private int calculateEventWeight(HistoryEventType eventType) {
        return switch (eventType) {
            case ORDER_CONFIRMED -> 3;
            case CART_ITEM_ADDED -> 2;
            case CART_QUANTITY_UPDATED -> 1;
            case CART_ITEM_REMOVED, ORDER_CANCELLED -> 0;
        };
    }

    /**
     * 수량에 따른 가중치 산출 (최소 1, 최대 5로 클램핑).
     */
    private int quantityWeight(Integer quantity) {
        if (quantity == null) {
            return 1;
        }
        return Math.min(Math.max(1, quantity), MAX_QUANTITY_WEIGHT);
    }

    /**
     * 시간 경과에 따른 지수 감쇠 가중치 계산 (반감기 7일 = 168시간).
     * 공식: MAX_TIME_WEIGHT * exp(-시간차 * ln(2) / 168)
     * 결과는 [MIN_TIME_WEIGHT(0.3), MAX_TIME_WEIGHT(3.0)] 범위로 보정됩니다.
     */
    private double calculateTimeWeight(Instant eventTime) {
        long hoursAgo = ChronoUnit.HOURS.between(eventTime, Instant.now());
        double decayFactor = Math.exp(-hoursAgo * Math.log(2) / TIME_WEIGHT_HALF_LIFE_HOURS);

        return Math.max(MIN_TIME_WEIGHT, Math.min(MAX_TIME_WEIGHT * decayFactor, MAX_TIME_WEIGHT));
    }

    /**
     * Elasticsearch에서 대상 상품들의 1536차원 임베딩 벡터를 일괄 조회하여 Map으로 반환합니다.
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
    private float[] blendIntoProfileVector(
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

        // 벡터 길이가 임계값(MIN_VECTOR_LENGTH) 미만인 경우(상쇄되어 방향이 소실된 영벡터) null 반환
        if (length < MIN_VECTOR_LENGTH) {
            return null;
        }

        // [3단계: 단위 벡터(길이 1.0)로 L2 정규화]
        // 각 차원을 전체 길이로 나누어 크기를 1로 통일함으로써 후속 코사인 유사도 검색에서 공정한 비교가 가능하게 합니다.
        float[] normalized = new float[blended.length];
        for (int i = 0; i < blended.length; i++) {
            normalized[i] = (float) (blended[i] / length);
        }
        return normalized;
    }
}
