package com.barofarm.buyer.inventory.presentation.dto;

import com.barofarm.buyer.inventory.application.dto.request.InventoryReserveCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record InventoryReserveRequest(
    @NotNull
    UUID orderId,

    @NotEmpty
    List<Item> items
) {
    public record Item(
        @NotNull
        UUID productId,

        @NotNull
        UUID inventoryId,

        @NotNull
        @Positive
        Long quantity
    ) {}

    public InventoryReserveCommand toCommand() {
        return new InventoryReserveCommand(
            orderId,
            items.stream()
                .map(i -> new InventoryReserveCommand.Item(i.productId(), i.inventoryId, i.quantity()))
                .toList()
        );
    }
}
