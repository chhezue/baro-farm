package com.barofarm.shopping.inventory.application.dto.request;

import java.util.UUID;

public record InventoryConfirmCommand(
    UUID orderId
) {
    public static InventoryConfirmCommand of(UUID orderId){
        return new InventoryConfirmCommand(orderId);
    }
}
