package com.barofarm.buyer.cart.history;

import com.barofarm.buyer.cart.domain.Cart;
import com.barofarm.buyer.cart.domain.CartItem;
import com.barofarm.buyer.cart.domain.CartRepository;
import com.barofarm.buyer.product.domain.ProductRepository;
import com.barofarm.log.history.mapper.HistoryPayloadMapper;
import com.barofarm.log.history.model.HistoryEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CartRemoveHistoryPayloadMapper implements HistoryPayloadMapper {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartRemoveHistoryPayloadMapper(
        CartRepository cartRepository,
        ProductRepository productRepository
    ) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Override
    public HistoryEventType supports() {
        return HistoryEventType.CART_REMOVE;
    }

    @Override
    public boolean mapBeforeProceed() {
        return true;
    }

    @Override
    public Map<String, Object> payload(Object[] args, Object returnValue) {
        Map<String, Object> payload = new LinkedHashMap<>();

        UUID buyerId = null;
        String sessionKey = null;
        UUID itemId = null;
        if (args != null && args.length >= 3) {
            buyerId = (UUID) args[0];
            sessionKey = (String) args[1];
            itemId = (UUID) args[2];
        }

        if (buyerId != null) {
            payload.put("buyerId", buyerId);
        }
        if (sessionKey != null) {
            payload.put("sessionKey", sessionKey);
        }

        if (itemId != null) {
            payload.put("itemId", itemId);
            resolveProductInfo(buyerId, sessionKey, itemId, payload);
        }

        return payload;
    }

    private void resolveProductInfo(
        UUID buyerId,
        String sessionKey,
        UUID itemId,
        Map<String, Object> payload
    ) {
        Optional<Cart> cart = resolveCart(buyerId, sessionKey);
        if (cart.isEmpty()) {
            return;
        }

        Optional<CartItem> item = cart.get().getItems().stream()
            .filter(cartItem -> cartItem.getId().equals(itemId))
            .findFirst();
        if (item.isEmpty()) {
            return;
        }

        UUID productId = item.get().getProductId();
        payload.put("productId", productId);
        productRepository.findById(productId)
            .map(p -> p.getProductName())
            .ifPresent(name -> payload.put("productName", name));
    }

    private Optional<Cart> resolveCart(UUID buyerId, String sessionKey) {
        if (buyerId != null) {
            return cartRepository.findByBuyerId(buyerId);
        }
        if (sessionKey != null) {
            return cartRepository.findBySessionKey(sessionKey);
        }
        return Optional.empty();
    }
}
