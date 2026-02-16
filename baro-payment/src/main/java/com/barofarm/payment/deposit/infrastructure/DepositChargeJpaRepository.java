package com.barofarm.payment.deposit.infrastructure;

import com.barofarm.payment.deposit.domain.DepositCharge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositChargeJpaRepository extends JpaRepository<DepositCharge, UUID> {
}
