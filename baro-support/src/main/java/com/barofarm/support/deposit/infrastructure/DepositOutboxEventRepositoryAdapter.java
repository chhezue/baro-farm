package com.barofarm.support.deposit.infrastructure;

import com.barofarm.support.deposit.domain.DepositOutboxEvent;
import com.barofarm.support.deposit.domain.DepositOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DepositOutboxEventRepositoryAdapter implements DepositOutboxEventRepository {

    private final DepositOutboxEventJpaRepository depositOutboxEventJpaRepository;

    @Override
    public DepositOutboxEvent save(DepositOutboxEvent event) {
        return depositOutboxEventJpaRepository.save(event);
    }
}

