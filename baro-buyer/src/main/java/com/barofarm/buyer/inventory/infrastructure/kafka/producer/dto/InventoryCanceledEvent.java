package com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto;

import java.util.UUID;

public record InventoryCanceledEvent(
    UUID orderId
) {
}

