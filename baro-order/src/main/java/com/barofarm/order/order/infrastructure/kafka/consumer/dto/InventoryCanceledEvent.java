package com.barofarm.order.order.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record InventoryCanceledEvent(
    UUID orderId
) {
}
