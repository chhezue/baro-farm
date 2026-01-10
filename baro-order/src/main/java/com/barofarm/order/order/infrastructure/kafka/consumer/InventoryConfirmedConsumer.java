package com.barofarm.order.order.infrastructure.kafka.consumer;

import com.barofarm.order.common.exception.CustomException;
import com.barofarm.order.order.domain.*;
import com.barofarm.order.order.infrastructure.kafka.consumer.dto.InventoryConfirmedEvent;
import com.barofarm.order.order.infrastructure.kafka.producer.OrderEventFailProducer;
import com.barofarm.order.order.infrastructure.kafka.producer.OrderEventProducer;
import com.barofarm.order.order.infrastructure.kafka.producer.dto.OrderConfirmedEvent;
import com.barofarm.order.order.infrastructure.kafka.producer.dto.OrderConfirmedFailEvent;
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
    public void handle(InventoryConfirmedEvent event) {
        UUID orderId = event.orderId();

        try{
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));
            order.markConfirmed();

            orderEventProducer.send(OrderConfirmedEvent.from(event));
        } catch (Exception e){
            orderEventFailProducer.send(OrderConfirmedFailEvent.from(event));
        }
    }
}
