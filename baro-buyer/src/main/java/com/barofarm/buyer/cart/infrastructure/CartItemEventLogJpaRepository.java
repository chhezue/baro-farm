package com.barofarm.buyer.cart.infrastructure;

import com.barofarm.buyer.cart.domain.CartItemEventLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemEventLogJpaRepository
    extends JpaRepository<CartItemEventLog, UUID> {

    // 필요 시 조회 메소드: findByBuyerId, findByOccurredAtBetween, ...
}
