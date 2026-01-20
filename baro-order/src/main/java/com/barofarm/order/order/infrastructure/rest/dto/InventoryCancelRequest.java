package com.barofarm.order.order.infrastructure.rest.dto;

import java.util.UUID;

public record InventoryCancelRequest(
    UUID orderId
) {
}
