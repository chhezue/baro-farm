package com.barofarm.buyer.inventory.infrastructure;

import com.barofarm.buyer.inventory.domain.InventoryOutboxEvent;
import com.barofarm.buyer.inventory.domain.InventoryOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryOutboxEventRepositoryAdapter implements InventoryOutboxEventRepository {

    private final InventoryOutboxEventJpaRepository inventoryOutboxEventJpaRepository;

    @Override
    public InventoryOutboxEvent save(InventoryOutboxEvent event) {
        return inventoryOutboxEventJpaRepository.save(event);
    }
}

