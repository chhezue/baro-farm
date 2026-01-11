package com.barofarm.support.deposit.application;

import com.barofarm.order.common.response.ResponseDto;
import com.barofarm.order.order.application.OrderService;
import com.barofarm.order.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepositPaymentOrchestrator {

    private final DepositService depositService;
    private final OrderService orderService;
    private final PaymentService paymentService;

//    public ResponseDto<DepositPaymentInfo> payDeposit(UUID userId, DepositPaymentCommand command) {
//
//
//
//    }
}
