package com.barofarm.shopping.inventory.presentation.dto;

import com.barofarm.shopping.inventory.application.dto.request.InventoryCancelCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InventoryCancelRequest(
    @NotNull
    UUID orderId
) {
    public InventoryCancelCommand toCommand(){
        return new InventoryCancelCommand(
            orderId
        );
    }
}
