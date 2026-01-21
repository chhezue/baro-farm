package com.barofarm.support.deposit.application.dto.response;

import com.barofarm.support.deposit.domain.Deposit;
import java.util.UUID;

public record DepositInfo(
    UUID userId,
    long amount
) {
    public static DepositInfo from(Deposit deposit) {
        return new DepositInfo(
            deposit.getUserId(),
            deposit.getAmount()
        );
    }
}
