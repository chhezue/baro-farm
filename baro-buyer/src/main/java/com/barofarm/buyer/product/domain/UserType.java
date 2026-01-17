package com.barofarm.buyer.product.domain;

public enum UserType {
    CUSTOMER,
    SELLER,
    ADMIN;

    public boolean isSeller() {
        return this == UserType.SELLER;
    }
}
