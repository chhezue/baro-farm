package com.barofarm.shopping.inventory.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record OrderCancelRequestedEvent(
    UUID orderId
) {
}
