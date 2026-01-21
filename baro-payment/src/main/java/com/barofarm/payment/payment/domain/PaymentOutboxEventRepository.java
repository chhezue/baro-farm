package com.barofarm.payment.payment.domain;

import java.util.List;

public interface PaymentOutboxEventRepository {

    PaymentOutboxEvent save(PaymentOutboxEvent paymentOutboxEvent);

    List<PaymentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus status);
}
