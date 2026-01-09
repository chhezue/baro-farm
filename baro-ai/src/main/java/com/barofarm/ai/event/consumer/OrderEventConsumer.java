package com.barofarm.ai.event.consumer;

import com.barofarm.ai.event.model.OrderLogEvent;
import com.barofarm.ai.log.application.LogWriteService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Order 이벤트 Kafka Consumer
 * 개인화 추천을 위한 주문 행동 로그 수집
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final LogWriteService logWriteService;

    @KafkaListener(
        topics = "order-events",
        groupId = "ai-service-order",
        containerFactory = "orderEventListenerContainerFactory"
    )
    public void onMessage(OrderLogEvent event) {
        OrderLogEvent.OrderEventData data = event.payload();

        log.info("🛍️ [ORDER_CONSUMER] Received order event - Type: {}, User ID: {}, Order ID: {}",
                event.event(), event.userId(), data.orderId());

        try {
            switch (event.event()) {
                case ORDER_CREATED -> {
                    log.info("📝 [ORDER_CONSUMER] Processing ORDER_CREATED - User: {}, Order: {}",
                            event.userId(), data.orderId());

                    // 주문 생성 시 각 상품별로 로그 저장
                    if (data.orderItems() != null) {
                        for (OrderLogEvent.OrderEventData.OrderItemData item : data.orderItems()) {
                            logWriteService.saveOrderEventLog(event.userId(), item.productId(),
                                    item.productName(), "ORDER_CREATED", item.quantity(), convertToInstant(event.ts()));
                        }
                    }

                    log.info("✅ [ORDER_CONSUMER] Successfully saved order created event - User: {}, Items: {}",
                            event.userId(), data.orderItems() != null ? data.orderItems().size() : 0);
                }
                case ORDER_CANCELLED -> {
                    log.info("❌ [ORDER_CONSUMER] Processing ORDER_CANCELLED - User: {}, Order: {}",
                            event.userId(), data.orderId());

                    // 주문 취소 시 각 상품별로 로그 저장
                    if (data.orderItems() != null) {
                        for (OrderLogEvent.OrderEventData.OrderItemData item : data.orderItems()) {
                            logWriteService.saveOrderEventLog(
                                event.userId(),
                                item.productId(),
                                item.productName(),
                                "ORDER_CANCELLED",
                                item.quantity(),
                                convertToInstant(event.ts())
                            );
                        }
                    }

                    log.info("✅ [ORDER_CONSUMER] Successfully saved order cancelled event - User: {}, Items: {}",
                            event.userId(), data.orderItems() != null ? data.orderItems().size() : 0);
                }
                default -> {
                    log.warn("⚠️ [ORDER_CONSUMER] Unknown order event type received - Type: {}, User: {}, Order: {}",
                            event.event(), event.userId(), data.orderId());
                }
            }
        } catch (Exception e) {
            log.error("❌ [ORDER_CONSUMER] Failed to process order event - " +
                    "Type: {}, User: {}, Order: {}, Error: {}",
                event.event(), event.userId(), data.orderId(), e.getMessage(), e);
            throw e; // 예외를 다시 던져서 Kafka가 재시도하도록 함
        }
    }

    private Instant convertToInstant(java.time.OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant();
    }
}
