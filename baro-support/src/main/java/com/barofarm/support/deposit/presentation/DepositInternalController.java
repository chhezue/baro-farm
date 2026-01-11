package com.barofarm.support.deposit.presentation;

import com.barofarm.support.common.response.ResponseDto;
import com.barofarm.support.deposit.application.DepositService;
import com.barofarm.support.deposit.application.dto.response.DepositCreateInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/internal/deposits")
@RequiredArgsConstructor
public class DepositInternalController {

    private final DepositService depositService;

    // 완료
    @PostMapping("/create")
    public ResponseDto<DepositCreateInfo> createDeposit(@RequestHeader("X-User-Id") UUID userId) {
        return depositService.createDeposit(userId);
    }

}
