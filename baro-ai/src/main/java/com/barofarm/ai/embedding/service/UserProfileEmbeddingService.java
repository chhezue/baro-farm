package com.barofarm.ai.embedding.service;

import com.barofarm.ai.embedding.model.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.repository.UserProfileEmbeddingRepository;
import com.barofarm.ai.log.domain.CartLogDocument;
import com.barofarm.ai.log.domain.OrderLogDocument;
import com.barofarm.ai.log.domain.SearchLogDocument;
import com.barofarm.ai.log.repository.CartLogRepository;
import com.barofarm.ai.log.repository.OrderLogRepository;
import com.barofarm.ai.log.repository.SearchLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class UserProfileEmbeddingService {

    private static final int MIN_TOTAL_LOGS_FOR_EMBEDDING = 3;

    private final EmbeddingModel embeddingModel;
    private final CartLogRepository cartLogRepository;
    private final OrderLogRepository orderLogRepository;
    private final SearchLogRepository searchLogRepository;
    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;

    public UserProfileEmbeddingService(
        @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
        CartLogRepository cartLogRepository,
        OrderLogRepository orderLogRepository,
        SearchLogRepository searchLogRepository,
        UserProfileEmbeddingRepository userProfileEmbeddingRepository
    ) {
        this.embeddingModel = embeddingModel;
        this.cartLogRepository = cartLogRepository;
        this.orderLogRepository = orderLogRepository;
        this.searchLogRepository = searchLogRepository;
        this.userProfileEmbeddingRepository = userProfileEmbeddingRepository;
    }

    /**
     * 특정 사용자의 프로필 벡터를 생성하고 저장/업데이트합니다.
     * 각 로그 타입별로 최대 5개씩 (총 최대 15개) 최근 로그를 사용합니다.
     * @param userId 대상 사용자 ID
     */
    public void updateUserProfileEmbedding(UUID userId) {
        // 1. 최근 30일간의 사용자 활동 로그를 각 타입별로 최대 5개씩 가져옵니다.
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Pageable top5 = PageRequest.of(0, 5);

        List<SearchLogDocument> searchLogs = searchLogRepository
            .findAllByUserIdAndSearchedAtAfterOrderBySearchedAtDesc(userId, thirtyDaysAgo, top5);
        List<CartLogDocument> cartLogs = cartLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);
        List<OrderLogDocument> orderLogs = orderLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);

        int totalLogCount = searchLogs.size() + cartLogs.size() + orderLogs.size();
        int minTotalLogs = MIN_TOTAL_LOGS_FOR_EMBEDDING;

        if (totalLogCount < minTotalLogs) {
            log.info("사용자 ID {}의 총 로그 개수({})가 {}개 미만이므로 임베딩을 건너뜁니다. " +
                    "(검색:{}, 장바구니:{}, 주문:{})",
                    userId, totalLogCount, minTotalLogs,
                    searchLogs.size(), cartLogs.size(), orderLogs.size());
            return;
        }

        log.debug("사용자 ID {}의 로그 데이터: 검색={}, 장바구니={}, 주문={}",
                 userId, searchLogs.size(), cartLogs.size(), orderLogs.size());

        // 3. 로그에서 텍스트를 추출하고 가중치를 적용하여 '대표 텍스트'를 생성합니다.
        String representativeText = buildRepresentativeText(searchLogs, cartLogs, orderLogs);

        if (!StringUtils.hasText(representativeText)) {
            log.info("사용자 ID {}에 대한 임베딩을 생성할 충분한 텍스트 데이터가 없습니다.", userId);
            return;
        }

        // 4. 대표 텍스트를 임베딩하여 벡터를 생성합니다.
        List<Double> vector;
        try {
            vector = embedText(representativeText);
        } catch (Exception e) {
            log.error("사용자 ID {}의 임베딩 생성 실패: {}", userId, e.getMessage(), e);
            return;
        }

        // 5. 생성된 벡터로 UserProfileEmbeddingDocument를 만들어 저장합니다.
        UserProfileEmbeddingDocument embeddingDocument = UserProfileEmbeddingDocument.builder()
            .userId(userId)
            .userProfileVector(vector)
            .lastUpdatedAt(Instant.now())
            .build();

        userProfileEmbeddingRepository.save(embeddingDocument);
        log.info("사용자 ID {}의 프로필 벡터를 성공적으로 업데이트했습니다. (총 {}개 로그 사용)",
                userId, totalLogCount);
    }

    private String buildRepresentativeText(
        List<SearchLogDocument> searchLogs,
        List<CartLogDocument> cartLogs,
        List<OrderLogDocument> orderLogs
    ) {
        // 1. 검색어 텍스트 (시간 가중치 적용)
        Stream<String> searchKeywords = searchLogs.stream()
            .filter(log -> StringUtils.hasText(log.getSearchQuery()))
            .flatMap(this::expandSearchQueryWithTimeWeight);

        // 2. 장바구니 상품 텍스트 (수량 + 이벤트 + 시간 가중치 적용)
        Stream<String> cartProductTexts = cartLogs.stream()
            .filter(log -> StringUtils.hasText(log.getProductName()))
            .flatMap(this::expandCartProductWithFullWeight);

        // 3. 주문 상품 텍스트 (수량 + 이벤트 + 시간 가중치 적용)
        Stream<String> orderProductTexts = orderLogs.stream()
            .filter(log -> StringUtils.hasText(log.getProductName()))
            .flatMap(this::expandOrderProductWithFullWeight);

        // 모든 텍스트를 결합
        return Stream.of(searchKeywords, cartProductTexts, orderProductTexts)
            .flatMap(s -> s)
            .collect(Collectors.joining(", "));
    }

    /**
     * 검색어에 시간 가중치를 적용합니다.
     */
    private Stream<String> expandSearchQueryWithTimeWeight(SearchLogDocument log) {
        double timeWeight = calculateTimeWeight(log.getSearchedAt());
        long finalWeight = Math.max(1L, Math.round(timeWeight * 1.0)); // 검색어 기본 가중치 1.0

        return Stream.generate(() -> log.getSearchQuery())
            .limit(finalWeight);
    }

    /**
     * 장바구니 상품에 수량, 이벤트 타입, 시간 가중치를 모두 적용합니다.
     */
    private Stream<String> expandCartProductWithFullWeight(CartLogDocument log) {
        int eventWeight = calculateCartEventWeight(log.getEventType());
        int quantityWeight = Math.max(1, log.getQuantity());
        double timeWeight = calculateTimeWeight(log.getOccurredAt());

        long totalWeight = Math.max(1L, Math.round((double) eventWeight * quantityWeight * timeWeight));

        return Stream.generate(() -> log.getProductName())
            .limit(totalWeight);
    }

    /**
     * 주문 상품에 수량, 이벤트 타입, 시간 가중치를 모두 적용합니다.
     */
    private Stream<String> expandOrderProductWithFullWeight(OrderLogDocument log) {
        int eventWeight = calculateOrderEventWeight(log.getEventType());
        int quantityWeight = Math.max(1, log.getQuantity());
        double timeWeight = calculateTimeWeight(log.getOccurredAt());

        long totalWeight = Math.max(1L, Math.round((double) eventWeight * quantityWeight * timeWeight));

        return Stream.generate(() -> log.getProductName())
            .limit(totalWeight);
    }

    /**
     * 장바구니 이벤트 타입별 가중치 계산
     */
    private int calculateCartEventWeight(String eventType) {
        return switch (eventType) {
            case "ADD" -> 2;      // 상품 추가: 관심 표현
            case "UPDATE" -> 1;   // 수량 변경: 관심 유지
            case "REMOVE" -> 0;   // 상품 제거: 관심 감소 (가중치 제거)
            default -> 1;
        };
    }

    /**
     * 주문 이벤트 타입별 가중치 계산
     */
    private int calculateOrderEventWeight(String eventType) {
        return switch (eventType) {
            case "ORDER_CREATED" -> 3;   // 주문 완료: 가장 높은 관심
            case "ORDER_CANCELLED" -> 0; // 주문 취소: 관심 제거
            default -> 1;
        };
    }

    /**
     * 이벤트 발생 시간에 따른 가중치를 계산합니다.
     * 최근 이벤트일수록 높은 가중치를 부여합니다.
     */
    private double calculateTimeWeight(Instant eventTime) {
        long hoursAgo = ChronoUnit.HOURS.between(eventTime, Instant.now());

        // 지수 감쇠: 시간이 지날수록 가중치 감소
        // 7일(168시간) 기준으로 0.5배까지 감쇠
        double decayFactor = Math.exp(-hoursAgo / 168.0);

        // 최소 가중치 0.3, 최대 가중치 3.0
        double timeWeight = Math.max(0.3, 3.0 * decayFactor);

        return Math.min(timeWeight, 3.0); // 최대 3배 제한
    }

    // 예시: 매일 새벽 4시에 모든 사용자의 프로필 벡터를 업데이트하는 스케줄러
    // 실제 운영시에는 모든 사용자를 가져오는 로직이 필요합니다. (e.g., 별도의 User DB 조회)
    // @Scheduled(cron = "0 0 4 * * *")
    public void updateAllUserProfiles() {
        log.info("전체 사용자 프로필 벡터 업데이트 작업을 시작합니다...");
        // List<UUID> allUserIds = userRepository.findAllIds();
        // allUserIds.forEach(this::updateUserProfileEmbedding);
        log.info("전체 사용자 프로필 벡터 업데이트 작업을 완료했습니다.");
    }

    /**
     * 테스트용 메소드: 가중치 계산 로직을 검증합니다.
     */
    public String testWeightCalculation(UUID userId) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<SearchLogDocument> searchLogs =
            searchLogRepository.findAllByUserIdAndSearchedAtAfter(userId, thirtyDaysAgo);
        List<CartLogDocument> cartLogs =
            cartLogRepository.findAllByUserIdAndOccurredAtAfter(userId, thirtyDaysAgo);
        List<OrderLogDocument> orderLogs =
            orderLogRepository.findAllByUserIdAndOccurredAtAfter(userId, thirtyDaysAgo);

        return buildRepresentativeText(searchLogs, cartLogs, orderLogs);
    }

    /**
     * 테스트용 공개 메소드: 대표 텍스트 생성 로직을 테스트합니다.
     */
    public String buildRepresentativeTextForTest(
        List<SearchLogDocument> searchLogs,
        List<CartLogDocument> cartLogs,
        List<OrderLogDocument> orderLogs
    ) {
        return buildRepresentativeText(searchLogs, cartLogs, orderLogs);
    }

    /**
     * 테스트용 메소드: 제한된 로그 개수로 임베딩 생성을 테스트합니다.
     */
    public void updateUserProfileEmbeddingWithLimitedLogs(UUID userId,
        List<SearchLogDocument> searchLogs,
        List<CartLogDocument> cartLogs,
        List<OrderLogDocument> orderLogs) {

        // 총 로그 개수 검증
        int totalLogCount = searchLogs.size() + cartLogs.size() + orderLogs.size();
        if (totalLogCount < 5) {
            log.info("테스트용: 사용자 ID {}의 총 로그 개수({})가 5개 미만", userId, totalLogCount);
            return;
        }

        log.debug("테스트용: 사용자 ID {}의 로그 데이터: 검색={}, 장바구니={}, 주문={}",
                 userId, searchLogs.size(), cartLogs.size(), orderLogs.size());

        // 대표 텍스트 생성 및 임베딩
        String representativeText = buildRepresentativeText(searchLogs, cartLogs, orderLogs);

        if (!StringUtils.hasText(representativeText)) {
            log.info("테스트용: 사용자 ID {}에 대한 충분한 텍스트 데이터가 없음", userId);
            return;
        }

        List<Double> vector;
        try {
            vector = embedText(representativeText);
        } catch (Exception e) {
            log.error("테스트용: 사용자 ID {}의 임베딩 생성 실패: {}", userId, e.getMessage(), e);
            return;
        }

        UserProfileEmbeddingDocument embeddingDocument = UserProfileEmbeddingDocument.builder()
            .userId(userId)
            .userProfileVector(vector)
            .lastUpdatedAt(Instant.now())
            .build();

        userProfileEmbeddingRepository.save(embeddingDocument);
        log.info("테스트용: 사용자 ID {}의 프로필 벡터 업데이트 완료 (총 {}개 로그 사용)",
                userId, totalLogCount);
    }

    /**
     * 텍스트를 임베딩하여 List<Double> 벡터로 변환합니다.
     * @param text 임베딩할 텍스트
     * @return List<Double> 형태의 벡터
     */
    private List<Double> embedText(String text) {
        try {
            log.debug("🔄 [USER_PROFILE_EMBEDDING] Generating embedding for text length: {}", text.length());

            // EmbeddingModel은 List<String>을 받아서 List<float[]>를 반환
            var embeddings = embeddingModel.embed(List.of(text));

            if (embeddings.isEmpty()) {
                log.warn("⚠️ [USER_PROFILE_EMBEDDING] No embedding generated for text");
                throw new RuntimeException("Failed to generate embedding: empty result");
            }

            // float[]를 List<Double>로 변환
            float[] floatVector = embeddings.get(0);
            List<Double> doubleVector = new ArrayList<>(floatVector.length);
            for (float f : floatVector) {
                doubleVector.add((double) f);
            }

            log.debug("✅ [USER_PROFILE_EMBEDDING] Successfully generated embedding with {} dimensions",
                    doubleVector.size());
            return doubleVector;

        } catch (Exception e) {
            log.error("❌ [USER_PROFILE_EMBEDDING] Failed to generate embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate user profile embedding", e);
        }
    }
}
