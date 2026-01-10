package com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto;

import com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent;
import java.util.UUID;

public record InventoryConfirmFailEvent(
    UUID orderId,
    OrderAddress address
) {

    public record OrderAddress(
        String receiverName,
        String phone,
        String email,
        String zipCode,
        String address,
        String addressDetail,
        String deliveryMemo
    ) {}

    public static InventoryConfirmFailEvent from(PaymentConfirmedEvent event){
        OrderAddress orderAddress = new OrderAddress(
            event.address().receiverName(),
            event.address().phone(),
            event.address().email(),
            event.address().zipCode(),
            event.address().address(),
            event.address().addressDetail(),
            event.address().deliveryMemo()
        );
        return new InventoryConfirmFailEvent(event.orderId(), orderAddress);
    }
}
