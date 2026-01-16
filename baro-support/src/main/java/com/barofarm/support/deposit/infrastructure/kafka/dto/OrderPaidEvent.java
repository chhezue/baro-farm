package com.barofarm.support.deposit.infrastructure.kafka.dto;

import java.util.UUID;

public record OrderPaidEvent(
    UUID orderId,
    OrderAddress address    // 이벤트용 Address DTO
) {
    public record OrderAddress(
        String receiverName,
        String phone,
        String email,
        String zipCode,
        String address,
        String addressDetail,
        String deliveryMemo
    ) {}
}
