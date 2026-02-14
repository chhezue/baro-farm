package com.barofarm.shopping.inventory.application.dto.request;

import java.util.UUID;

public record InventoryCancelCommand(
    UUID orderId
) {
    public static InventoryCancelCommand of(UUID orderId){
        return new InventoryCancelCommand(orderId);
    }
}
