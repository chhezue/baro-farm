package com.barofarm.order.order.infrastructure.kafka.producer.dto;

import com.barofarm.order.order.infrastructure.kafka.consumer.dto.PaymentCanceledEvent;
import java.util.UUID;

public record OrderCanceledEvent(
    UUID orderId,
    Long amount
) {

    public static OrderCanceledEvent of(PaymentCanceledEvent event) {
        return new OrderCanceledEvent(event.orderId(), event.amount());
    }
}

