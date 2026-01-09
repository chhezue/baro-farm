package com.barofarm.buyer.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    Optional<Inventory> findById(UUID inventoryId);
}
