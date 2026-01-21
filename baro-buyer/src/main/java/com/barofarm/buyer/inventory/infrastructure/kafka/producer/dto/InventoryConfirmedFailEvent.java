package com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto;

import com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent;
import java.util.UUID;

public record InventoryConfirmedFailEvent(
    UUID orderId,
    Long amount
) {
    public static InventoryConfirmedFailEvent from(PaymentConfirmedEvent event){
        return new InventoryConfirmedFailEvent(event.orderId(), event.amount());
    }
}
