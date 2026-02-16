package com.barofarm.shopping.inventory.infrastructure;

import com.barofarm.shopping.inventory.domain.InventoryOutboxEvent;
import com.barofarm.shopping.inventory.domain.InventoryOutboxEventRepository;
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
