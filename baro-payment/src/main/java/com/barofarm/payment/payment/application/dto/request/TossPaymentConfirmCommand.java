package com.barofarm.payment.payment.application.dto.request;

public record TossPaymentConfirmCommand(
    String paymentKey,
    String orderId,
    Long amount
) {
}
