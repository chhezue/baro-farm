package com.barofarm.order.order.infrastructure.kafka.consumer;

import com.barofarm.order.common.exception.CustomException;
import com.barofarm.order.order.domain.Order;
import com.barofarm.order.order.domain.OrderRepository;
import com.barofarm.order.order.domain.OrderStatus;
import com.barofarm.order.order.exception.OrderErrorCode;
import com.barofarm.order.order.infrastructure.kafka.consumer.dto.PaymentCanceledEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentCanceledConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
        topics = "payment-canceled",
        groupId = "order-service.payment-canceled",
        properties = {
            "spring.json.value.default.type=com.barofarm.order.order.infrastructure.kafka.consumer.dto.PaymentCanceledEvent"
        }
    )
    @Transactional
    public void handle(PaymentCanceledEvent event) {
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

