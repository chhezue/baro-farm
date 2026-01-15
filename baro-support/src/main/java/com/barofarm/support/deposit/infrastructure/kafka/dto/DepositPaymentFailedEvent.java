package com.barofarm.support.deposit.infrastructure.kafka.dto;

import java.util.UUID;

public record DepositPaymentFailedEvent(
    UUID userId,
    UUID orderId,
    long amount,
    String reason
) {
}

