package com.barofarm.payment.payment.application.dto.request;

public record TossPaymentRefundCommand(
    String paymentKey,
    String cancelReason
){
}
