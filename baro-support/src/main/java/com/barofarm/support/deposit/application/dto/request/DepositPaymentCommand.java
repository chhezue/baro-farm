package com.barofarm.support.deposit.application.dto.request;

import java.util.UUID;

public record DepositPaymentCommand(
    UUID orderId,
    Long amount
) {}
