package com.barofarm.order.order.infrastructure;

import com.barofarm.order.order.domain.CompensationRegistry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationRegistryJpaRepository extends JpaRepository<CompensationRegistry, UUID> {
}
