package com.barofarm.shopping.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface InventoryReservationRepository {

    List<InventoryReservation> findAllByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
    InventoryReservation save(InventoryReservation inventoryReservation);
}
