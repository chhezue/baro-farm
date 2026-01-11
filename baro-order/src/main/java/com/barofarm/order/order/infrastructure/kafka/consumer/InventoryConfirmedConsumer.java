package com.barofarm.order.order.infrastructure.kafka.consumer;

import com.barofarm.order.common.exception.CustomException;
import com.barofarm.order.order.domain.*;
import com.barofarm.order.order.exception.OrderErrorCode;
import com.barofarm.order.order.infrastructure.kafka.consumer.dto.InventoryConfirmedEvent;
import com.barofarm.order.order.infrastructure.kafka.producer.OrderEventFailProducer;
import com.barofarm.order.order.infrastructure.kafka.producer.OrderEventProducer;
import com.barofarm.order.order.infrastructure.kafka.producer.dto.OrderConfirmedEvent;
import com.barofarm.order.order.infrastructure.kafka.producer.dto.OrderConfirmedFailEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static com.barofarm.order.order.exception.OrderErrorCode.ORDER_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class InventoryConfirmedConsumer {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final OrderEventFailProducer orderEventFailProducer;
    private final ObjectMapper objectMapper;
    private final OrderOutboxEventRepository orderOutboxEventRepository;

    @KafkaListener(
        topics = "inventory-confirmed",
        groupId = "order-service.inventory-confirmed",
        properties = {
            "spring.json.value.default.type=com.barofarm.order.order.infrastructure.kafka.consumer.dto.InventoryConfirmedEvent"
        }
    )
    @RetryableTopic(
        // 총 시도 횟수 (최초 시도 1회 + 재시도 4회)
        attempts = "5",
        // 재시도 간격 (1000ms -> 2000ms -> 4000ms -> 8000ms 순으로 재시도 시간이 증가한다.)
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void handle(InventoryConfirmedEvent event) throws JsonProcessingException {
        UUID orderId = event.orderId();
        System.out.println("inventory-confirmed!!!!!!!!!!!!!!!!!!!!!!");
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));
        order.markConfirmed(); // 주문 상태만 변경

        try {
            OrderConfirmedEvent dto = OrderConfirmedEvent.of(event, order);
            String payload = objectMapper.writeValueAsString(dto);

            OrderOutboxEvent outbox = OrderOutboxEvent.pending(
                "ORDER",
                orderId.toString(),
                "order-confirmed",
                orderId.toString(),
                payload
            );
            orderOutboxEventRepository.save(outbox);
            System.out.println("inventory-confirmedㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌ");
        } catch (JsonProcessingException e) {
            System.out.println("inventory-confirmedㅕㅕㅕㅕㅕㅕㅕㅕㅕㅕㅕㅕㅕㅕ");
            throw new CustomException(OrderErrorCode.OUTBOX_SERIALIZATION_FAILED);
        }
    }
}
