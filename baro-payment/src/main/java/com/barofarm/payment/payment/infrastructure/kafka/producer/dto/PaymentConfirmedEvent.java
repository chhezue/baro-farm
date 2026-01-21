package com.barofarm.payment.payment.infrastructure.kafka.producer.dto;

import java.util.UUID;

public record PaymentConfirmedEvent(
    UUID orderId,
    Long amount
) {}
