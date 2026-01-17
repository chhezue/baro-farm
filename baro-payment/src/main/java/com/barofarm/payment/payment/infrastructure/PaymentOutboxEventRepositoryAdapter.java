package com.barofarm.payment.payment.infrastructure;

import com.barofarm.payment.payment.domain.PaymentOutboxEvent;
import com.barofarm.payment.payment.domain.PaymentOutboxEventRepository;
import com.barofarm.payment.payment.domain.PaymentOutboxStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
