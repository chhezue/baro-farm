package com.barofarm.buyer.cart.domain;

public interface CartItemEventLogRepository {

    CartItemEventLog save(CartItemEventLog log);

    // 필요하면 조회 메소드도: findByBuyerId, findByOccurredAtBetween 등
}
