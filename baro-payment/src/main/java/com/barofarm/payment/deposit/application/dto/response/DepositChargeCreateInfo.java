package com.barofarm.payment.deposit.application.dto.response;

import com.barofarm.payment.deposit.domain.DepositCharge;

import java.util.UUID;

public record DepositChargeCreateInfo(
    UUID chargeId,
    long amount
) {
    public static DepositChargeCreateInfo from(DepositCharge charge) {
        return new DepositChargeCreateInfo(
            charge.getId(),
            charge.getAmount()
        );
    }
}
