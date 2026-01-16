package com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record PaymentCanceledEvent(
    UUID orderId,
    Long amount
) {
}
