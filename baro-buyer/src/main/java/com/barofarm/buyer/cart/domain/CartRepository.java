package com.barofarm.buyer.cart.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

  Cart save(Cart cart);

  Optional<Cart> findByBuyerId(UUID buyerId);

  Optional<Cart> findBySessionKey(String sessionKey);

  void delete(Cart cart);

  List<Cart> findExpiredGuestCarts(LocalDateTime expiredAt);
}
