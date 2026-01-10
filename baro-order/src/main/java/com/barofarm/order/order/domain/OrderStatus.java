package com.barofarm.order.order.domain;

public enum OrderStatus {
    // 주문 생성됨
    PENDING,

    // 재고 예약 완료
    INVENTORY_RESERVED,

    // 주문 확정(결제/재고 확정 완료)
    CONFIRMED,

    // 주문 취소
    CANCELED,

    // 주문 실패(재고/결제 실패)
    FAILED,

    // 배송 대기 중
    PREPARING,

    // 배송 완료
    COMPLETED;

    public boolean isCancelable() {
        return this == PENDING || this == CONFIRMED || this == CANCELED;
    }

    public boolean isCanceled() {
        return this == CANCELED;
    }
}
