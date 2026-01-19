package com.barofarm.order.order.domain;

import com.barofarm.order.order.application.dto.request.OrderCreateCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "address")
public class Address {

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "address_detail", nullable = false)
    private String addressDetail;

    @Column(name = "delivery_memo")
    private String deliveryMemo;

    private Address(
        String receiverName,
        String phone,
        String email,
        String zipCode,
        String address,
        String addressDetail,
        String deliveryMemo
    ) {
        this.receiverName = receiverName;
        this.phone = phone;
        this.email = email;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.deliveryMemo = deliveryMemo;
    }

    public static Address from(OrderCreateCommand command) {
        return new Address(
            command.receiverName(),
            command.phone(),
            command.email(),
            command.zipCode(),
            command.address(),
            command.addressDetail(),
            command.deliveryMemo()
        );
    }
}
