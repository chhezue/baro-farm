package com.barofarm.payment.payment.application.dto.response;

import com.barofarm.payment.payment.domain.Payment;
import com.barofarm.payment.payment.domain.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TossPaymentConfirmInfo(
    UUID id,
    UUID orderId,
    String paymentKey,
    Long amount,
    PaymentStatus status,
    String method,
    LocalDateTime requestedAt,
    LocalDateTime approvedAt,
    String failReason
) {
    public static TossPaymentConfirmInfo from(Payment payment) {
        return new TossPaymentConfirmInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getPaymentKey(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getMethod(),
            payment.getRequestedAt(),
            payment.getApprovedAt(),
            payment.getFailReason()
        );
    }
}
