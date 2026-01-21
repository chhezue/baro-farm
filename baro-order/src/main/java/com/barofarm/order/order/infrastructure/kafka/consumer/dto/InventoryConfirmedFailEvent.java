package com.barofarm.order.order.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record InventoryConfirmedFailEvent(
    UUID orderId,
    Long amount
) {
}
