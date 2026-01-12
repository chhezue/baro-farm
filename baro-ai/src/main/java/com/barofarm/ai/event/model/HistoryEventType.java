package com.barofarm.ai.event.model;

public enum HistoryEventType {
    // Cart Events
    CART_ITEM_ADDED, // 유저가 해당 상품에 관심을 보임
    CART_ITEM_REMOVED, // 유저가 해당 상품에 관심이 약해짐
    CART_QUANTITY_UPDATED, // 수량 변경

    // Order Events
    ORDER_CONFIRMED, // 주문 완료
    ORDER_CANCELLED, // 주문 취소
}
