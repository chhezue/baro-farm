package com.barofarm.buyer.inventory.infrastructure;

import com.barofarm.buyer.inventory.domain.InventoryOutboxEvent;
import com.barofarm.buyer.inventory.domain.InventoryOutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryOutboxEventJpaRepository extends JpaRepository<InventoryOutboxEvent, UUID> {

    List<InventoryOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(InventoryOutboxStatus status);
}
