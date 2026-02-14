package com.barofarm.shopping.inventory.infrastructure;

import com.barofarm.shopping.inventory.domain.InventoryReservation;
import com.barofarm.shopping.inventory.domain.InventoryReservationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryReservationRepositoryAdapter implements InventoryReservationRepository {

    private final InventoryReservationJpaRepository jpaRepository;

    @Override
    public List<InventoryReservation> findAllByOrderId(UUID orderId) {
        return jpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public boolean existsByOrderId(UUID orderId) {
        return jpaRepository.existsByOrderId(orderId);
    }

    @Override
    public InventoryReservation save(InventoryReservation inventoryReservation) {
        return jpaRepository.save(inventoryReservation);
    }
}
