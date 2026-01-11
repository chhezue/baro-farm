package com.barofarm.payment.payment.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record InventoryCanceledEvent(
    UUID orderId
) {
}

