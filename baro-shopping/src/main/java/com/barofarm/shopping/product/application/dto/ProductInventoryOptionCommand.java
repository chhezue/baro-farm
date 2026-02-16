package com.barofarm.shopping.product.application.dto;

public record ProductInventoryOptionCommand(
    Long quantity,
    Integer unit
) {
}
