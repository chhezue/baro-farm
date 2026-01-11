package com.barofarm.order.order.infrastructure.kafka.consumer;

import com.barofarm.order.common.exception.CustomException;
import com.barofarm.order.order.domain.Order;
import com.barofarm.order.order.domain.OrderRepository;
import com.barofarm.order.order.domain.OrderStatus;
import com.barofarm.order.order.exception.OrderErrorCode;
import com.barofarm.order.order.infrastructure.kafka.consumer.dto.InventoryCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryCanceledConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
        topics = "inventory-canceled",
        groupId = "order-service.inventory-canceled",
        properties = {
            "spring.json.value.default.type=com.barofarm.order.order.infrastructure.kafka.consumer.dto.InventoryCanceledEvent"
        }
    )
    @Transactional
    public void handle(InventoryCanceledEvent event) {
        UUID orderId = event.orderId();

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.CANCELED) {
            return;
        }

        if (order.getStatus() == OrderStatus.CANCEL_PENDING) {
            order.markCancel();
        }
    }
}

