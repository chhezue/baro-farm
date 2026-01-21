package com.barofarm.order.order.infrastructure;

import com.barofarm.order.order.domain.OrderItem;
import com.barofarm.order.order.domain.OrderItemRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryAdapter implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public Optional<OrderItem> findById(UUID id) {
        return orderItemJpaRepository.findById(id);
    }
}
