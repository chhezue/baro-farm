package com.barofarm.order.order.infrastructure;

import com.barofarm.order.order.domain.OrderOutboxEvent;
import com.barofarm.order.order.domain.OrderOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderOutboxEventJpaRepository extends JpaRepository<OrderOutboxEvent, UUID> {

    List<OrderOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OrderOutboxStatus status);
}
