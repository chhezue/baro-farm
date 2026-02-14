package com.barofarm.payment.deposit.application;

import static com.barofarm.payment.deposit.exception.DepositErrorCode.DEPOSIT_ALREADY_EXISTS;

import com.barofarm.dto.ResponseDto;
import com.barofarm.exception.CustomException;
import com.barofarm.payment.deposit.application.dto.request.DepositChargeCreateCommand;
import com.barofarm.payment.deposit.application.dto.request.DepositRefundCommand;
import com.barofarm.payment.deposit.application.dto.response.DepositChargeCreateInfo;
import com.barofarm.payment.deposit.application.dto.response.DepositCreateInfo;
import com.barofarm.payment.deposit.application.dto.response.DepositInfo;
import com.barofarm.payment.deposit.application.dto.response.DepositRefundInfo;
import com.barofarm.payment.deposit.domain.Deposit;
import com.barofarm.payment.deposit.domain.DepositCharge;
import com.barofarm.payment.deposit.domain.DepositChargeRepository;
import com.barofarm.payment.deposit.domain.DepositRepository;
import com.barofarm.payment.deposit.exception.DepositErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final DepositRepository depositRepository;
    private final DepositChargeRepository depositChargeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResponseDto<DepositCreateInfo> createDeposit(UUID userId) {
        boolean exists = depositRepository.findByUserId(userId).isPresent();
        if (exists) {
            throw new CustomException(DEPOSIT_ALREADY_EXISTS);
        }
        Deposit saved = depositRepository.save(Deposit.of(userId));
        return ResponseDto.ok(DepositCreateInfo.from(saved));
    }

    @Transactional
    public ResponseDto<DepositChargeCreateInfo> createCharge(UUID userId, DepositChargeCreateCommand command) {

        Deposit deposit = depositRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_NOT_FOUND));

        DepositCharge charge = DepositCharge.of(command.amount(), deposit);
        DepositCharge saved = depositChargeRepository.save(charge);
        return ResponseDto.ok(DepositChargeCreateInfo.from(saved));
    }

    @Transactional
    public void markDepositCharge(UUID userId, UUID chargeId) {

        DepositCharge charge = depositChargeRepository.findById(chargeId)
            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_CHARGE_NOT_FOUND));

        if (!charge.isPending()) {
            throw new CustomException(DepositErrorCode.DEPOSIT_CHARGE_INVALID_STATUS);
        }
        Deposit deposit = charge.getDeposit();
        if (!deposit.getUserId().equals(userId)) {
            throw new CustomException(DepositErrorCode.DEPOSIT_ACCESS_DENIED);
        }

        deposit.increase(charge.getAmount());
        charge.success();
    }

    @Transactional(readOnly = true)
    public ResponseDto<DepositInfo> findDeposit(UUID userId) {
        Deposit deposit = depositRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_NOT_FOUND));

        return ResponseDto.ok(DepositInfo.from(deposit));
    }

    @Transactional
    public Deposit withdraw(UUID userId, long amount) {
        Deposit deposit = depositRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_NOT_FOUND));

        if (deposit.getAmount() < amount) {
            throw new CustomException(DepositErrorCode.INSUFFICIENT_DEPOSIT_BALANCE);
        }
        deposit.decrease(amount);
        return deposit;
    }

    @Transactional
    public ResponseDto<DepositRefundInfo> refundDeposit(UUID userId, DepositRefundCommand command) {

        Deposit deposit = depositRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_NOT_FOUND));

        deposit.increase(command.amount());

        return ResponseDto.ok(
            DepositRefundInfo.of(command.orderId(), command.amount(), deposit.getAmount())
        );
    }
}
