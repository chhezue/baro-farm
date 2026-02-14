package com.barofarm.shopping.inventory.presentation.dto;

import com.barofarm.shopping.inventory.application.dto.request.InventoryConfirmCommand;
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
