package com.barofarm.payment.payment.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record InventoryConfirmedFailEvent(
    UUID orderId,
    Long amount
) {
}
