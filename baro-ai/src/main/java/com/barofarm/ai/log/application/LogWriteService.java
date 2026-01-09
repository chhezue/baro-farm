package com.barofarm.ai.log.application;

import com.barofarm.ai.log.domain.CartEventDocument;
import com.barofarm.ai.log.domain.OrderEventDocument;
import com.barofarm.ai.log.domain.SearchDocument;
import com.barofarm.ai.log.repository.CartEventLogRepository;
import com.barofarm.ai.log.repository.OrderEventLogRepository;
import com.barofarm.ai.log.repository.SearchLogRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 개인화 추천을 위한 로그 쓰기 서비스
 * Kafka Consumer에서 호출되어 사용자 행동 로그를 Elasticsearch에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogWriteService {

    private final CartEventLogRepository cartEventLogRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final SearchLogRepository searchLogRepository;

    /**
     * 장바구니 이벤트 로그 저장
     */
    public void saveCartEventLog(UUID userId, UUID productId, String productName,
                                String eventType, Integer quantity, Instant occurredAt) {
        CartEventDocument document = CartEventDocument.builder()
                .userId(userId)
                .productId(productId)
                .productName(productName)
                .eventType(eventType)
                .quantity(quantity)
                .occurredAt(occurredAt)
                .build();

        CartEventDocument saved = cartEventLogRepository.save(document);
        log.info("✅ [LOG_WRITE] Saved cart event log - ID: {}, User: {}, Product: {}",
                saved.getId(), userId, productName);
    }

    /**
     * 주문 이벤트 로그 저장
     */
    public void saveOrderEventLog(UUID userId, UUID productId, String productName,
                                 String eventType, Integer quantity, Instant occurredAt) {
        OrderEventDocument document = OrderEventDocument.builder()
                .userId(userId)
                .productId(productId)
                .productName(productName)
                .eventType(eventType)
                .quantity(quantity)
                .occurredAt(occurredAt)
                .build();

        OrderEventDocument saved = orderEventLogRepository.save(document);
        log.info("✅ [LOG_WRITE] Saved order event log - ID: {}, User: {}, Product: {}",
                saved.getId(), userId, productName);
    }

    /**
     * 검색 로그 저장
     */
    public void saveSearchLog(UUID userId, String searchQuery, String category, Instant searchedAt) {
        SearchDocument document = SearchDocument.builder()
                .userId(userId)
                .searchQuery(searchQuery)
                .category(category)
                .searchedAt(searchedAt)
                .build();

        SearchDocument saved = searchLogRepository.save(document);
        log.info("✅ [LOG_WRITE] Saved search log - ID: {}, User: {}, Query: '{}'",
                saved.getId(), userId, searchQuery);
    }
}
