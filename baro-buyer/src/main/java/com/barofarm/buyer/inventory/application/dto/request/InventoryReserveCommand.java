package com.barofarm.buyer.inventory.application.dto.request;

import java.util.List;
import java.util.UUID;

public record InventoryReserveCommand(
    UUID orderId,
    List<Item> items
) {
    public record Item(
        UUID productId,
        UUID inventoryId,
        Long quantity
    ){ }
}
