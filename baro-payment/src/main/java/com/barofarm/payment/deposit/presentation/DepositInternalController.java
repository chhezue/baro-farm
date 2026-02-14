package com.barofarm.payment.deposit.presentation;

import com.barofarm.dto.ResponseDto;
import com.barofarm.payment.deposit.application.DepositService;
import com.barofarm.payment.deposit.application.dto.response.DepositCreateInfo;
import com.barofarm.payment.deposit.application.dto.response.DepositRefundInfo;
import com.barofarm.payment.deposit.presentation.dto.DepositRefundRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/deposits")
@RequiredArgsConstructor
public class DepositInternalController {

    private final DepositService depositService;

    @PostMapping("/create")
    public ResponseDto<DepositCreateInfo> createDeposit(@RequestHeader("X-User-Id") UUID userId) {
        return depositService.createDeposit(userId);
    }

    @PostMapping("/refund")
    public ResponseDto<DepositRefundInfo> refundDeposit(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody DepositRefundRequest request) {
        return depositService.refundDeposit(userId, request.toCommand());
    }
}
