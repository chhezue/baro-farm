package com.barofarm.support.deposit.infrastructure;

import com.barofarm.support.deposit.domain.Deposit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositJpaRepository extends JpaRepository<Deposit, UUID> {

    Optional<Deposit> findByUserId(UUID userId);
}
