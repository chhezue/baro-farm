package com.barofarm.ai.event.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartEvent {

    private CartEventType type;
    private CartEventData data;

    public enum CartEventType {
        CART_ITEM_ADDED,
        CART_ITEM_REMOVED,
        CART_QUANTITY_UPDATED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartEventData {
        private UUID userId;
        private UUID cartId;
        private UUID cartItemId;
        private UUID productId;
        private String productName;
        private Integer quantity;
        private Instant occurredAt;
    }
}
