package com.barofarm.support.deposit.infrastructure.kafka.consumer;

import com.barofarm.support.common.exception.CustomException;
import com.barofarm.support.deposit.domain.Deposit;
import com.barofarm.support.deposit.domain.DepositCharge;
import com.barofarm.support.deposit.domain.DepositChargeRepository;
import com.barofarm.support.deposit.exception.DepositErrorCode;
import com.barofarm.support.deposit.infrastructure.kafka.dto.DepositChargeConfirmedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepositChargeConfirmedConsumer {

    private final DepositChargeRepository depositChargeRepository;

    @KafkaListener(
        topics = "deposit-charge-confirmed",
        groupId = "support-service.deposit-charge-confirmed",
        properties = {
            "spring.json.value.default.type=com.barofarm.support.deposit.infrastructure.kafka.dto.DepositChargeConfirmedEvent"
        }
    )
    @Transactional
    public void handle(DepositChargeConfirmedEvent event) {
        UUID chargeId = event.chargeId();

        DepositCharge charge = depositChargeRepository.findById(chargeId)
            .orElse(null);

        if (charge == null) {
            log.warn(
                "DepositCharge not found for confirmed event. chargeId={}",
                chargeId
            );
            return;
        }

        if (!charge.isPending()) {
            // 이미 처리된 충전이면 무시
            return;
        }

        Deposit deposit = charge.getDeposit();
        if (!deposit.getUserId().equals(event.userId())) {
            throw new CustomException(DepositErrorCode.DEPOSIT_ACCESS_DENIED);
        }

        deposit.increase(event.amount());
        charge.success();

        log.info(
            "Deposit charge confirmed. userId={}, chargeId={}, amount={}",
            event.userId(),
            chargeId,
            event.amount()
        );
    }
}

