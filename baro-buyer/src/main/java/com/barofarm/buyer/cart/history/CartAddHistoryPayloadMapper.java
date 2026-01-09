package com.barofarm.buyer.cart.history;

import com.barofarm.buyer.cart.application.dto.AddItemCommand;
import com.barofarm.buyer.cart.application.dto.CartInfo;
import com.barofarm.log.history.mapper.HistoryPayloadMapper;
import com.barofarm.log.history.model.HistoryEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CartAddHistoryPayloadMapper implements HistoryPayloadMapper {

    @Override
    public HistoryEventType supports() {
        return HistoryEventType.CART_ADD;
    }

    @Override
    public Map<String, Object> payload(Object[] args, Object returnValue) {
        Map<String, Object> payload = new LinkedHashMap<>();

        UUID buyerId = null;
        String sessionKey = null;
        AddItemCommand command = null;
        if (args != null && args.length >= 3) {
            buyerId = (UUID) args[0];
            sessionKey = (String) args[1];
            command = (AddItemCommand) args[2];
        }

        if (buyerId != null) {
            payload.put("buyerId", buyerId);
        }
        if (sessionKey != null) {
            payload.put("sessionKey", sessionKey);
        }

        if (command != null) {
            payload.put("productId", command.productId());
            payload.put("quantity", command.quantity());
            payload.put("unitPrice", command.unitPrice());
            payload.put("optionInfoJson", command.optionInfoJson());
        }

        if (returnValue instanceof CartInfo cartInfo) {
            payload.put("cartId", cartInfo.cartId());
        }

        return payload;
    }
}
