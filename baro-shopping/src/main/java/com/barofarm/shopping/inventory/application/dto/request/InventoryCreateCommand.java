package com.barofarm.shopping.inventory.application.dto.request;

import java.util.UUID;

public record InventoryCreateCommand(
    UUID productId,
    Long quantity,
    Integer unit
) {
}
