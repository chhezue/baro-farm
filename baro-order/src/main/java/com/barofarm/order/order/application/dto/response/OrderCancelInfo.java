package com.barofarm.order.order.application.dto.response;

import com.barofarm.order.order.domain.Order;
import com.barofarm.order.order.domain.OrderStatus;
import java.util.List;
import java.util.UUID;

public record OrderCancelInfo(
    UUID orderId,
    Long totalAmount,
    OrderStatus status,
    String receiverName,
    String phone,
    String email,
    String zipCode,
    String address,
    String addressDetail,
    String deliveryMemo,
    int itemCount,
    List<OrderItemInfo> items
) {
    public static OrderCancelInfo from(Order order) {
        return new OrderCancelInfo(
            order.getId(),
            order.getTotalAmount(),
            order.getStatus(),
            order.getAddress().getReceiverName(),
            order.getAddress().getPhone(),
            order.getAddress().getEmail(),
            order.getAddress().getZipCode(),
            order.getAddress().getAddress(),
            order.getAddress().getAddressDetail(),
            order.getAddress().getDeliveryMemo(),
            order.getOrderItems().size(),
            order.getOrderItems().stream()
                .map(OrderItemInfo::from)
                .toList()
        );
    }
}
