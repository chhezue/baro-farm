package com.barofarm.order.order.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record PaymentCancelFailedEvent(
    UUID orderId,
    Long amount,
    String reason
) {
}
