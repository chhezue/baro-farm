package com.barofarm.buyer.inventory.presentation.dto;

import com.barofarm.buyer.inventory.application.dto.request.InventoryConfirmCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InventoryConfirmRequest(
    @NotNull
    UUID orderId
) {
    public InventoryConfirmCommand toCommand() {
        return new InventoryConfirmCommand(
            orderId
        );
    }
}
