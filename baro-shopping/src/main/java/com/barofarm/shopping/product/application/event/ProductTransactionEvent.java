package com.barofarm.shopping.product.application.event;

import com.barofarm.shopping.product.domain.Product;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProductTransactionEvent {

    public enum ProductOperation {
        CREATED, UPDATED, DELETED
    }

    private final Product product;
    private final ProductOperation operation;
}
