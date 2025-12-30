package com.barofarm.buyer.cart.domain;

public enum CartEventStatus {
    ADDED,            // 추가 (병합 포함)
    REMOVED,          // 제거 (전체 비우기 포함)
    QUANTITY_CHANGED, // 수량 변경
    OPTION_CHANGED    // 옵션 변경
}
