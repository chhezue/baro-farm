package com.barofarm.payment.payment.infrastructure;

import com.barofarm.payment.payment.domain.PaymentOutboxEvent;
import com.barofarm.payment.payment.domain.PaymentOutboxEventRepository;
import com.barofarm.payment.payment.domain.PaymentOutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentOutboxEventRepositoryAdapter implements PaymentOutboxEventRepository {

    private final PaymentOutboxEventJpaRepository paymentOutboxEventJpaRepository;

    @Override
    public PaymentOutboxEvent save(PaymentOutboxEvent paymentOutboxEvent) {
        return paymentOutboxEventJpaRepository.save(paymentOutboxEvent);
    }

    @Override
    public List<PaymentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus status) {
        return paymentOutboxEventJpaRepository.findTop100ByStatusOrderByCreatedAtAsc(status);
    }
}
