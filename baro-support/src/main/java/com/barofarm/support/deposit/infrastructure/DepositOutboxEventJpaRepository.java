package com.barofarm.support.deposit.infrastructure;

import com.barofarm.support.deposit.domain.DepositOutboxEvent;
import com.barofarm.support.deposit.domain.DepositOutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositOutboxEventJpaRepository extends JpaRepository<DepositOutboxEvent, UUID> {

    List<DepositOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(DepositOutboxStatus status);
}
