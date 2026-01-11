package com.barofarm.support.deposit.infrastructure.kafka.dto;

import java.util.UUID;

public record DepositChargeConfirmedEvent(
    UUID userId,
    UUID chargeId,
    long amount
) {
}

