package com.barofarm.ai.event.model;

public enum HistoryEventType {
    // Cart Events
    CART_ITEM_ADDED, // 유저가 해당 상품에 관심을 보임
    CART_ITEM_REMOVED, // 유저가 해당 상품에 관심이 약해짐
    CART_QUANTITY_UPDATED, // 수량 변경

    // Order Events
    ORDER_CREATED, // TODO: 주문이 확정된 시점으로 변경
    ORDER_CANCELLED, // TODO: 주문이 취소되거나 환불된 시점으로 변경
}
