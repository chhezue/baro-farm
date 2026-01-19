package com.barofarm.payment.payment.presentation.dto;


import com.barofarm.payment.payment.application.dto.request.TossPaymentConfirmCommand;

public record TossPaymentConfirmRequest(
    String paymentKey,
    String orderId,
    Long amount
) {

    public TossPaymentConfirmCommand toCommand() {
        return new TossPaymentConfirmCommand(
            paymentKey,
            orderId,
            amount
        );
    }
}
