package com.barofarm.order.order.infrastructure.kafka.producer.dto;

import java.util.UUID;

public record OrderCancelRequestedEvent(
    UUID orderId,
    Long amount
) {
}
