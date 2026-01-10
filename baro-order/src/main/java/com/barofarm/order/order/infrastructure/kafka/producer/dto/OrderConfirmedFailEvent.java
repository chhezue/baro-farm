package com.barofarm.order.order.infrastructure.kafka.producer.dto;

import com.barofarm.order.order.infrastructure.kafka.consumer.dto.InventoryConfirmedEvent;

import java.util.UUID;

public record OrderConfirmedFailEvent(
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

    public static OrderConfirmedFailEvent from(InventoryConfirmedEvent event){

        OrderAddress orderAddress = new OrderAddress(
            event.address().receiverName(),
            event.address().phone(),
            event.address().email(),
            event.address().zipCode(),
            event.address().address(),
            event.address().addressDetail(),
            event.address().deliveryMemo()
        );
        return new OrderConfirmedFailEvent(event.orderId(), orderAddress);
    }
}

