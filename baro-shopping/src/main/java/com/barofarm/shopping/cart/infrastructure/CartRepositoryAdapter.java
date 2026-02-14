package com.barofarm.shopping.cart.infrastructure;

import com.barofarm.shopping.cart.domain.Cart;
import com.barofarm.shopping.cart.domain.CartRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartRepositoryAdapter implements CartRepository {

    private final CartJpaRepository jpaRepository;

    @Override
    public Cart save(Cart cart) {
        return jpaRepository.save(cart);
    }

    @Override
    public Optional<Cart> findByBuyerId(UUID buyerId) {
        return jpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public Optional<Cart> findBySessionKey(String sessionKey) {
        return jpaRepository.findBySessionKey(sessionKey);
    }

    @Override
    public void delete(Cart cart) {
        jpaRepository.delete(cart);
    }

    @Override
    public List<Cart> findExpiredGuestCarts(LocalDateTime expiredAt) {
        return jpaRepository.findExpiredGuestCarts(expiredAt);
    }
}
