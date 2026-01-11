package com.barofarm.order.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderOutboxEventRepository {
    OrderOutboxEvent save(OrderOutboxEvent orderOutboxEvent);
}
