package com.barofarm.shopping.inventory.domain;

public interface InventoryOutboxEventRepository {

    InventoryOutboxEvent save(InventoryOutboxEvent event);
}
