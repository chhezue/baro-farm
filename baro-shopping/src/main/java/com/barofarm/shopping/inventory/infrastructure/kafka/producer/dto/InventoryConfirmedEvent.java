package com.barofarm.shopping.inventory.infrastructure.kafka.producer.dto;

import com.barofarm.shopping.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent;
import java.util.UUID;

public record InventoryConfirmedEvent(
    UUID orderId,
    Long amount
) {
    public static InventoryConfirmedEvent from(PaymentConfirmedEvent event){
        return new InventoryConfirmedEvent(event.orderId(), event.amount());
    }
}
