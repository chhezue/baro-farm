package com.barofarm.order.order.infrastructure;

import com.barofarm.order.order.domain.CompensationRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompensationRegistryJpaRepository extends JpaRepository<CompensationRegistry, UUID> {
}
