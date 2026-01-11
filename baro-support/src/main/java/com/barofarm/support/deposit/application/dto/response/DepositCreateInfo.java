package com.barofarm.support.deposit.application.dto.response;

import com.barofarm.support.deposit.domain.Deposit;
import java.util.UUID;

public record DepositCreateInfo(
    UUID id,
    UUID userId,
    long amount
) {
    public static DepositCreateInfo from(Deposit deposit) {
        return new DepositCreateInfo(
            deposit.getId(),
            deposit.getUserId(),
            deposit.getAmount()
        );
    }
}
