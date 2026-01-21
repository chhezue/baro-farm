package com.barofarm.support.deposit.infrastructure.kafka.dto;

import java.util.UUID;

public record DepositPaymentRequestedEvent(
    UUID userId,
    UUID orderId,
    long amount
) {
}
