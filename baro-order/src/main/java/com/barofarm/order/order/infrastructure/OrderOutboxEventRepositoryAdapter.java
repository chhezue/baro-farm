package com.barofarm.order.order.infrastructure;

import com.barofarm.order.order.domain.OrderOutboxEvent;
import com.barofarm.order.order.domain.OrderOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderOutboxEventRepositoryAdapter implements OrderOutboxEventRepository {

    private final OrderOutboxEventJpaRepository orderOutboxEventJpaRepository;

    @Override
    public OrderOutboxEvent save(OrderOutboxEvent orderOutboxEvent) {
        return orderOutboxEventJpaRepository.save(orderOutboxEvent);
    }
}
