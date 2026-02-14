package com.barofarm.shopping.inventory.infrastructure.kafka.producer.dto;

import java.util.UUID;

public record InventoryCanceledEvent(
    UUID orderId
) {
}
