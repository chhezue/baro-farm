package com.barofarm.order.order.infrastructure.kafka;

import java.util.UUID;

public record OrderCancelRequestedEvent(
    UUID orderId,
    UUID paymentId
) {
    public static OrderCancelRequestedEvent of(UUID orderId, UUID paymentId){
        return new OrderCancelRequestedEvent(orderId, paymentId);
    }
}
