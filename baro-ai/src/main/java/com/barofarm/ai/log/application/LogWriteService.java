package com.barofarm.ai.log.application;

import com.barofarm.ai.log.domain.CartLogDocument;
import com.barofarm.ai.log.domain.OrderLogDocument;
import com.barofarm.ai.log.domain.SearchLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.CartLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.OrderLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.SearchLogRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogWriteService {

    private final CartLogRepository cartLogRepository;
    private final OrderLogRepository orderLogRepository;
    private final SearchLogRepository searchLogRepository;

    public void saveCartEventLog(UUID userId,
                                 UUID productId,
                                 String productName,
                                 String eventType,
                                 Integer quantity,
                                 Instant occurredAt) {
        CartLogDocument document = CartLogDocument.builder()
            .userId(userId)
            .productId(productId)
            .productName(productName)
            .eventType(eventType)
            .quantity(quantity)
            .occurredAt(occurredAt)
            .build();

        CartLogDocument saved = cartLogRepository.save(document);
        log.info("[LOG_WRITE] Saved cart event log - ID: {}, User: {}, Product: {}",
            saved.getId(), userId, productName);
    }

    public void saveOrderEventLog(UUID userId,
                                  UUID productId,
                                  String productName,
                                  String categoryName,
                                  String eventType,
                                  Integer quantity,
                                  Instant occurredAt) {
        OrderLogDocument document = OrderLogDocument.builder()
            .userId(userId)
            .productId(productId)
            .productName(productName)
            .categoryName(categoryName)
            .eventType(eventType)
            .quantity(quantity)
            .occurredAt(occurredAt)
            .build();

        OrderLogDocument saved = orderLogRepository.save(document);
        log.info("[LOG_WRITE] Saved order event log - ID: {}, User: {}, Product: {}, Category: {}",
            saved.getId(), userId, productName, categoryName);
    }

    public void saveSearchLog(UUID userId,
                              String searchQuery,
                              String category,
                              Instant searchedAt) {
        SearchLogDocument document = SearchLogDocument.builder()
            .userId(userId)
            .searchQuery(searchQuery)
            .category(category)
            .searchedAt(searchedAt)
            .build();

        SearchLogDocument saved = searchLogRepository.save(document);
        log.info("[LOG_WRITE] Saved search log - ID: {}, User: {}, Query: '{}'",
            saved.getId(), userId, searchQuery);
    }
}

