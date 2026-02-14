package com.barofarm.payment.deposit.infrastructure;

import com.barofarm.payment.deposit.domain.DepositCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepositChargeJpaRepository extends JpaRepository<DepositCharge, UUID> {
}
