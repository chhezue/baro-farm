package com.barofarm.buyer.cart.history;

import com.barofarm.buyer.cart.domain.Cart;
import com.barofarm.buyer.cart.domain.CartItem;
import com.barofarm.buyer.cart.domain.CartRepository;
import com.barofarm.log.history.mapper.HistoryPayloadMapper;
import com.barofarm.log.history.model.HistoryEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CartQuantityUpdateHistoryPayloadMapper implements HistoryPayloadMapper {

    private final CartRepository cartRepository;

    public CartQuantityUpdateHistoryPayloadMapper(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Override
    public HistoryEventType supports() {
        return HistoryEventType.CART_QUANTITY_UPDATE;
    }

    @Override
    public Map<String, Object> payload(Object[] args, Object returnValue) {
        Map<String, Object> payload = new LinkedHashMap<>();

        UUID buyerId = null;
        String sessionKey = null;
        UUID itemId = null;
        Integer quantity = null;
        if (args != null && args.length >= 4) {
            buyerId = (UUID) args[0];
            sessionKey = (String) args[1];
            itemId = (UUID) args[2];
            quantity = (Integer) args[3];
        }

        if (buyerId != null) {
            payload.put("buyerId", buyerId);
        }
        if (sessionKey != null) {
            payload.put("sessionKey", sessionKey);
        }
        if (itemId != null) {
            payload.put("itemId", itemId);
        }
        if (quantity != null) {
            payload.put("quantity", quantity);
        }

        if (itemId != null) {
            resolveProductId(buyerId, sessionKey, itemId)
                .ifPresent(productId -> payload.put("productId", productId));
        }

        return payload;
    }

    private Optional<UUID> resolveProductId(UUID buyerId, String sessionKey, UUID itemId) {
        Optional<Cart> cart = resolveCart(buyerId, sessionKey);
        if (cart.isEmpty()) {
            return Optional.empty();
        }
        Optional<CartItem> item = cart.get().getItems().stream()
            .filter(cartItem -> cartItem.getId().equals(itemId))
            .findFirst();
        return item.map(CartItem::getProductId);
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
