package com.barofarm.buyer.inventory.presentation.dto;

import com.barofarm.buyer.inventory.application.dto.request.InventoryCancelCommand;
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
