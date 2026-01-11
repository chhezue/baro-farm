package com.barofarm.support.deposit.infrastructure.kafka.dto;

import java.util.UUID;
import com.barofarm.order.order.domain.Address;

public record OrderPaidEvent(
    UUID orderId,
    OrderAddress address    // 이벤트용 Address DTO
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

    public static OrderPaidEvent of(
        UUID orderId,
        Address domainAddress
    ) {
        return new OrderPaidEvent(
            orderId,
            new OrderAddress(
                domainAddress.getReceiverName(),
                domainAddress.getPhone(),
                domainAddress.getEmail(),
                domainAddress.getZipCode(),
                domainAddress.getAddress(),
                domainAddress.getAddressDetail(),
                domainAddress.getDeliveryMemo()
            )
        );
    }
}
