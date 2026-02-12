package com.barofarm.auth.seller.infrastructure;

import com.barofarm.auth.seller.domain.Seller;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerJpaRepository extends JpaRepository<Seller, UUID> {
    boolean existsByBusinessRegNo(String businessRegNo);
    List<Seller> findByIdIn(List<UUID> ids);
}
