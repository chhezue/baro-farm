package com.barofarm.order.order.infrastructure.kafka.producer.dto;

import com.barofarm.order.order.domain.Order;
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

    public static OrderConfirmedFailEvent of(InventoryConfirmedEvent event, Order order){

        OrderAddress orderAddress = new OrderAddress(
            order.getAddress().getReceiverName(),
            order.getAddress().getPhone(),
            order.getAddress().getEmail(),
            order.getAddress().getZipCode(),
            order.getAddress().getAddress(),
            order.getAddress().getAddressDetail(),
            order.getAddress().getDeliveryMemo()
        );
        return new OrderConfirmedFailEvent(event.orderId(), orderAddress);
    }
}
