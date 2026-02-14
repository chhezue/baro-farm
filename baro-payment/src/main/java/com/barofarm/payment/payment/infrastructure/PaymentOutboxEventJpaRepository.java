package com.barofarm.payment.payment.infrastructure;

import com.barofarm.payment.payment.domain.PaymentOutboxEvent;
import com.barofarm.payment.payment.domain.PaymentOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxEventJpaRepository extends JpaRepository<PaymentOutboxEvent, UUID> {

    List<PaymentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(PaymentOutboxStatus status);
}
