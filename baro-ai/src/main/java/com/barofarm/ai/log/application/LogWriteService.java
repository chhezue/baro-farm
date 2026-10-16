package com.barofarm.ai.log.application;

import com.barofarm.ai.log.domain.CartLogDocument;
import com.barofarm.ai.log.domain.OrderLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.CartLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.OrderLogRepository;
import com.barofarm.log.history.model.HistoryEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 로그 영속화 서비스.
 *
 * Kafka Consumer 및 상품 검색 서비스로부터 전달된 사용자 행동(장바구니, 주문, 검색) 데이터를
 * Elasticsearch 전용 인덱스('cart_event_logs', 'order_event_logs')에 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogWriteService {

    /** 장바구니 행동 로그 레포지토리 */
    private final CartLogRepository cartLogRepository;

    /** 주문 행동 로그 레포지토리 */
    private final OrderLogRepository orderLogRepository;

    /**
     * 장바구니 이벤트 로그를 Elasticsearch('cart_event_logs')에 저장합니다.
     *
     * @param documentId Elasticsearch 문서 고유 식별자 (Kafka partition-offset)
     * @param userId 행동을 수행한 사용자 ID
     * @param productId 장바구니에 담긴 대상 상품 ID
     * @param productName 상품명
     * @param categoryCode 상품 카테고리 코드
     * @param eventType 장바구니 행동 종류 (CART_ITEM_ADDED, CART_ITEM_REMOVED, CART_QUANTITY_UPDATED)
     * @param quantity 수량 (관심 강도)
     * @param occurredAt 이벤트 발생 일시
     */
    public void saveCartEventLog(String documentId,
                                 UUID userId,
                                 UUID productId,
                                 String productName,
                                 String categoryCode,
                                 HistoryEventType eventType,
                                 Integer quantity,
                                 Instant occurredAt) {
        // 전달받은 documentId('{partition}-{offset}')를 고유 ID로 지정하여,
        // Kafka 컨슈머 재시도 시에도 동일 문서를 덮어쓰도록 함으로써 중복 로그 적재를 방지합니다.
        CartLogDocument document = CartLogDocument.builder()
            .id(documentId)
            .userId(userId)
            .productId(productId)
            .productName(productName)
            .categoryCode(categoryCode)
            .eventType(eventType)
            .quantity(quantity)
            .occurredAt(occurredAt)
            .build();

        CartLogDocument saved = cartLogRepository.save(document);
        log.debug("[LOG_WRITE] Saved cart event log - ID: {}, User: {}, Product: {}, CategoryCode: {}",
            saved.getId(), userId, productName, categoryCode);
    }

    /**
     * 주문 이벤트 로그를 Elasticsearch('order_event_logs')에 저장합니다.
     *
     * @param documentId Elasticsearch 문서 고유 식별자 (Kafka partition-offset-index)
     * @param userId 주문을 수행한 사용자 ID
     * @param productId 주문된 상품 ID
     * @param productName 상품명
     * @param categoryCode 상품 카테고리 코드
     * @param eventType 주문 행동 종류 (ORDER_CONFIRMED, ORDER_CANCELLED)
     * @param quantity 주문 수량
     * @param occurredAt 주문 발생 일시
     */
    public void saveOrderEventLog(String documentId,
                                  UUID userId,
                                  UUID productId,
                                  String productName,
                                  String categoryCode,
                                  HistoryEventType eventType,
                                  Integer quantity,
                                  Instant occurredAt) {
        // 단일 주문 내 복수 상품 항목이 각각 보존되도록 '{partition}-{offset}-{itemIndex}'를 고유 ID로 지정합니다.
        OrderLogDocument document = OrderLogDocument.builder()
            .id(documentId)
            .userId(userId)
            .productId(productId)
            .productName(productName)
            .categoryCode(categoryCode)
            .eventType(eventType)
            .quantity(quantity)
            .occurredAt(occurredAt)
            .build();

        OrderLogDocument saved = orderLogRepository.save(document);
        log.debug("[LOG_WRITE] Saved order event log - ID: {}, User: {}, Product: {}, CategoryCode: {}",
            saved.getId(), userId, productName, categoryCode);
    }

}
