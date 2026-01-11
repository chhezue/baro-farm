package com.barofarm.buyer.inventory.domain;

public interface InventoryOutboxEventRepository {

    InventoryOutboxEvent save(InventoryOutboxEvent event);
}

