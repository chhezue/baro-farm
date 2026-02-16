package com.barofarm.shopping.cart.application;

import com.barofarm.shopping.cart.domain.Cart;
import com.barofarm.shopping.cart.domain.CartRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CartCleanupService {

    private final CartRepository cartRepository;

    public int cleanupExpiredGuestCarts(LocalDateTime now) {
        // 현재 시각으로부터 3일 전이면 삭제 대상
        LocalDateTime expiredAt = now.minusDays(3);

        List<Cart> expiredCarts = cartRepository.findExpiredGuestCarts(expiredAt);

        expiredCarts.forEach(cartRepository::delete);

        return expiredCarts.size();
    }
}
