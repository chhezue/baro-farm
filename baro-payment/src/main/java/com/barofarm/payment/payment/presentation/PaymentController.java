package com.barofarm.payment.payment.presentation;

import com.barofarm.dto.ResponseDto;
import com.barofarm.payment.deposit.application.dto.response.DepositPaymentInfo;
import com.barofarm.payment.deposit.presentation.dto.DepositPaymentRequest;
import com.barofarm.payment.payment.application.PaymentService;
import com.barofarm.payment.payment.application.dto.response.TossPaymentConfirmInfo;
import com.barofarm.payment.payment.presentation.dto.TossPaymentConfirmRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.v1}/payments")
@RequiredArgsConstructor
public class PaymentController implements PaymentSwaggerApi {

    private final PaymentService paymentService;

    // o
    @PostMapping("/toss/confirm")
    public ResponseDto<TossPaymentConfirmInfo> confirmPayment(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody TossPaymentConfirmRequest confirmRequest) {
        return paymentService.confirmPayment(userId, confirmRequest.toCommand());
    }

    @PostMapping("/toss/confirm/deposit")
    public ResponseDto<TossPaymentConfirmInfo> confirmDeposit(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody TossPaymentConfirmRequest request) {
        return paymentService.confirmDeposit(userId, request.toCommand());
    }

    @PostMapping("/pay/deposit")
    public ResponseDto<DepositPaymentInfo> payWithDeposit(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody DepositPaymentRequest request
    ) {
        return paymentService.payDeposit(userId, request.toCommand());
    }
}
