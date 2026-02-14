package com.barofarm.shopping.product.application.dto;

import com.barofarm.shopping.product.domain.ProductStatus;
import com.barofarm.shopping.product.domain.UserType;
import java.util.List;
import java.util.UUID;

public record ProductUpdateCommand(
    UUID memberId,
    UserType role,
    String productName,
    String description,
    UUID categoryId,
    Long price,
    List<ProductInventoryOptionCommand> inventoryOptions,
    ProductStatus productStatus) {}
