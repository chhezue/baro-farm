package com.barofarm.buyer.cart.infrastructure;

import com.barofarm.buyer.cart.domain.Cart;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartJpaRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByBuyerId(UUID buyerId);

    Optional<Cart> findBySessionKey(String sessionKey);

    @Query("""
          select c from Cart c
          where c.buyerId is null
            and c.sessionKey is not null
            and c.updatedAt < :expiredAt
        """)
    List<Cart> findExpiredGuestCarts(@Param("expiredAt") LocalDateTime expiredAt);
}
