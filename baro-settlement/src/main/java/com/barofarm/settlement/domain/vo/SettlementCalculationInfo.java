package com.barofarm.settlement.domain.vo;

import com.barofarm.settlement.domain.SettlementStatus;
import java.time.LocalDate;

public record SettlementCalculationInfo(
    Long commissionAmount,
    Long settlementAmount,
    SettlementStatus status,
    LocalDate settlementMonth
) {}

