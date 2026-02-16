package com.barofarm.shopping.product.application.dto;

import com.barofarm.shopping.product.domain.UserType;
import java.util.List;
import java.util.UUID;

public record ProductCreateCommand(
    UUID sellerId,
    UserType role,
    String productName,
    String description,
    UUID categoryId,
    Long price,
    List<ProductInventoryOptionCommand> inventoryOptions) {}
