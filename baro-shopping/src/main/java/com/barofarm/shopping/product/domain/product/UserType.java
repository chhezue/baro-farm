package com.barofarm.shopping.product.domain.product;

public enum UserType {
    CUSTOMER,
    SELLER,
    ADMIN;

    public boolean isSeller() {
        return this == UserType.SELLER;
    }
}
