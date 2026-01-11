package com.barofarm.support.deposit.application;

import com.barofarm.support.common.exception.CustomException;
import com.barofarm.support.common.response.ResponseDto;
import com.barofarm.support.deposit.application.dto.request.DepositChargeCreateCommand;
import com.barofarm.support.deposit.application.dto.request.DepositPaymentCommand;
import com.barofarm.support.deposit.application.dto.response.DepositChargeCreateInfo;
import com.barofarm.support.deposit.application.dto.response.DepositCreateInfo;
import com.barofarm.support.deposit.application.dto.response.DepositInfo;
import com.barofarm.support.deposit.application.dto.response.DepositPaymentInfo;
import com.barofarm.support.deposit.domain.Deposit;
import com.barofarm.support.deposit.domain.DepositCharge;
import com.barofarm.support.deposit.domain.DepositChargeRepository;
import com.barofarm.support.deposit.domain.DepositRepository;
import com.barofarm.support.deposit.exception.DepositErrorCode;
import com.barofarm.support.deposit.infrastructure.kafka.OrderEventProducer;
import com.barofarm.support.deposit.infrastructure.kafka.dto.OrderPaidEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.barofarm.support.deposit.exception.DepositErrorCode.DEPOSIT_ALREADY_EXISTS;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final DepositRepository depositRepository;
    private final OrderEventProducer orderEventProducer;
    private final DepositChargeRepository depositChargeRepository;


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
    public ResponseDto<DepositPaymentInfo> payDeposit(UUID userId, DepositPaymentCommand command) {

        Deposit deposit = depositRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_NOT_FOUND));

        if (deposit.getAmount() < command.amount()) {
            throw new CustomException(DepositErrorCode.INSUFFICIENT_DEPOSIT_BALANCE);
        }
        deposit.decrease(command.amount());

        Order order = orderService.completeOrder(userId, command.orderId());
        paymentService.createPayment(command.orderId(), command.amount());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderEventProducer.send(OrderPaidEvent.of(command.orderId(), order.getAddress()));
            }
        });

        return ResponseDto.ok(
            DepositPaymentInfo.of(
                command.orderId(),
                command.amount(),
                deposit.getAmount()
            )
        );
    }
}

//    @Transactional
//    public ResponseDto<DepositRefundInfo> refundDeposit(UUID userId, DepositRefundCommand command) {
//
//        Deposit deposit = depositRepository.findByUserId(userId)
//            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_NOT_FOUND));
//
//        String paymentKey = "DEPOSIT:" + command.orderId();
//        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
//            .orElseThrow(() -> new CustomException(DepositErrorCode.DEPOSIT_PAYMENT_NOT_FOUND));
//
//        // 멱등: 이미 환불 처리된 경우 그대로 응답(또는 return)
//        if (payment.getStatus() == PaymentStatus.REFUNDED) {
//            return ResponseDto.ok(
//                DepositRefundInfo.of(command.orderId(), 0L, deposit.getAmount())
//            );
//        }
//
//        // 예치금 복구
//        deposit.increase(command.amount());
//
//        orderService.cancelOrder(userId, command.orderId());
//
//        // 결제 상태 환불 처리
//        payment.refund();
//
//        return ResponseDto.ok(
//            DepositRefundInfo.of(command.orderId(), command.amount(), deposit.getAmount())
//        );
//    }
//}
