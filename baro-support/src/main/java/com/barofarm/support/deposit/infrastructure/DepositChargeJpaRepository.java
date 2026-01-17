package com.barofarm.support.deposit.infrastructure;

import com.barofarm.support.deposit.domain.DepositCharge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositChargeJpaRepository extends JpaRepository<DepositCharge, UUID> {
}
