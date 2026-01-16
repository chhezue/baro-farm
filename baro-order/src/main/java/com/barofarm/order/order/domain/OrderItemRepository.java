package com.barofarm.order.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository {
//    CustomPage<OrderItemSettlementResponse> findOrderItemsForSettlement(
//        LocalDateTime startDateTime, LocalDateTime endDateTime, Pageable pageable);
    Optional<OrderItem> findById(UUID id);
}
