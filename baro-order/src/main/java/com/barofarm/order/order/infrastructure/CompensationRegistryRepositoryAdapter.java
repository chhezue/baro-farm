package com.barofarm.order.order.infrastructure;

import com.barofarm.order.order.domain.CompensationRegistry;
import com.barofarm.order.order.domain.CompensationRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CompensationRegistryRepositoryAdapter implements CompensationRegistryRepository {

    private final CompensationRegistryJpaRepository compensationRegistryJpaRepository;
    @Override
    public CompensationRegistry save(CompensationRegistry compensationRegistry) {
        return compensationRegistryJpaRepository.save(compensationRegistry);
    }
}
