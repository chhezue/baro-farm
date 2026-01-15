package com.barofarm.order.order.infrastructure.rest.dto;

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
}
