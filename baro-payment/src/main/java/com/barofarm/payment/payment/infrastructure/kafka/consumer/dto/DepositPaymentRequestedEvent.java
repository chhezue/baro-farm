package com.barofarm.payment.payment.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record DepositPaymentRequestedEvent(
    UUID userId,
    UUID orderId,
    long amount
) {
}
