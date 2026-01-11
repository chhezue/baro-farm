package com.barofarm.payment.payment.infrastructure.kafka.producer.dto;

import java.util.UUID;

public record DepositPaymentFailedEvent(
    UUID userId,
    UUID orderId,
    long amount,
    String reason
) {
}

