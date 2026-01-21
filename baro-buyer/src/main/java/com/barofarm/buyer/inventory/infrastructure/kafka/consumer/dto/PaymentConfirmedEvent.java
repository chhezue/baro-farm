package com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record PaymentConfirmedEvent(
    UUID orderId,
    Long amount   // 이벤트용 Address DTO
) {}
