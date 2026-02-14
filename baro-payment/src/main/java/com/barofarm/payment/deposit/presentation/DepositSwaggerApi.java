package com.barofarm.payment.deposit.presentation;

import com.barofarm.dto.ResponseDto;
import com.barofarm.payment.deposit.application.dto.response.DepositChargeCreateInfo;
import com.barofarm.payment.deposit.application.dto.response.DepositInfo;
import com.barofarm.payment.deposit.presentation.dto.DepositChargeCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Deposit", description = "예치금 관련 API")
@RequestMapping("${api.v1}/deposits")
public interface DepositSwaggerApi {

    @Operation(
        summary = "예치금 충전 요청 생성",
        description = "예치금 충전 요청(DepositCharge)을 생성합니다. 실제 결제는 Toss 결제 승인 후 반영됩니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "충전 요청 생성 성공",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "예치금 계정을 찾을 수 없음 (DEPOSIT_NOT_FOUND)",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/charges")
    ResponseDto<DepositChargeCreateInfo> createCharge(
        @Parameter(
            description = "요청 사용자 ID (X-User-Id 헤더)",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @RequestHeader("X-User-Id") UUID userId,

        @Parameter(description = "충전 요청 정보", required = true)
        @Valid @RequestBody DepositChargeCreateRequest request
    );

    @Operation(
        summary = "예치금 잔액 조회",
        description = "현재 사용자의 예치금 잔액을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "예치금 조회 성공",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "예치금 계정을 찾을 수 없음 (DEPOSIT_NOT_FOUND)",
            content = @Content(mediaType = "application/json")
        )
    })
    @GetMapping
    ResponseDto<DepositInfo> findDeposit(
        @Parameter(
            description = "요청 사용자 ID (X-User-Id 헤더)",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @RequestHeader("X-User-Id") UUID userId
    );

    // 예치금 결제(pay)는 PaymentController에서 처리
}
