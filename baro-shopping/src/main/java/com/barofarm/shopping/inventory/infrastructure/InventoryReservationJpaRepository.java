package com.barofarm.shopping.inventory.infrastructure;

import com.barofarm.shopping.inventory.domain.InventoryReservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationJpaRepository extends JpaRepository<InventoryReservation, UUID> {

    List<InventoryReservation> findAllByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
