package com.barofarm.payment.deposit.presentation;

import com.barofarm.dto.ResponseDto;
import com.barofarm.payment.deposit.application.DepositService;
import com.barofarm.payment.deposit.application.dto.response.DepositChargeCreateInfo;
import com.barofarm.payment.deposit.application.dto.response.DepositInfo;
import com.barofarm.payment.deposit.presentation.dto.DepositChargeCreateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.v1}/deposits")
@RequiredArgsConstructor
public class DepositController implements DepositSwaggerApi {

    private final DepositService depositService;

    @PostMapping("/charges")
    public ResponseDto<DepositChargeCreateInfo> createCharge(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody DepositChargeCreateRequest request) {
        return depositService.createCharge(userId, request.toCommand());
    }

    @GetMapping
    public ResponseDto<DepositInfo> findDeposit(@RequestHeader("X-User-Id") UUID userId) {
        return depositService.findDeposit(userId);
    }
}
