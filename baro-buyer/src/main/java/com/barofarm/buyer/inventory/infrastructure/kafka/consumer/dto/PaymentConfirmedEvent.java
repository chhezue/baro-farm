package com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record PaymentConfirmedEvent(
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
