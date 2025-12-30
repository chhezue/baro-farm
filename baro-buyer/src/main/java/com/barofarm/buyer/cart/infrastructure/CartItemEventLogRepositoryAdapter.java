package com.barofarm.buyer.cart.infrastructure;

import com.barofarm.buyer.cart.domain.CartItemEventLog;
import com.barofarm.buyer.cart.domain.CartItemEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartItemEventLogRepositoryAdapter implements CartItemEventLogRepository {

    private final CartItemEventLogJpaRepository jpaRepository;

    @Override
    public CartItemEventLog save(CartItemEventLog log) {
        return jpaRepository.save(log);
    }

    // 필요하면 조회 메소드도 구현
}
