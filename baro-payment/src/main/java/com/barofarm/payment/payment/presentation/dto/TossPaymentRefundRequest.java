package com.barofarm.payment.payment.presentation.dto;

import com.barofarm.payment.payment.application.dto.request.TossPaymentRefundCommand;

public record TossPaymentRefundRequest(
    String paymentKey,
    String cancelReason
) {
    public TossPaymentRefundCommand toCommand() {
        return new TossPaymentRefundCommand(
            paymentKey,
            cancelReason
        );
    }
}
