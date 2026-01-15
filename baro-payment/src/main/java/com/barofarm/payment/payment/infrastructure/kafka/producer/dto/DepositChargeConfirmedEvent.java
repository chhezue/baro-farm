package com.barofarm.payment.payment.infrastructure.kafka.producer.dto;

import java.util.UUID;

public record DepositChargeConfirmedEvent(
    UUID userId,
    UUID chargeId,
    long amount
) {
}

