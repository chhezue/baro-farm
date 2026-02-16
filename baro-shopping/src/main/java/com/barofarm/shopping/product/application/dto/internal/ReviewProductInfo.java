package com.barofarm.shopping.product.application.dto.internal;

import com.barofarm.shopping.product.domain.Product;
import com.barofarm.shopping.product.domain.ProductStatus;
import java.util.UUID;

public record ReviewProductInfo(
    UUID productId,
    ProductStatus status
) {
    public static ReviewProductInfo from(Product product) {
        return new ReviewProductInfo(
            product.getId(),
            product.getProductStatus()
        );
    }
}
