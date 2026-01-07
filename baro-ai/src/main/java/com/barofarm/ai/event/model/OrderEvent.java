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
public class OrderEvent {
    private OrderEventType type;
    private OrderEventData data;

    public enum OrderEventType {
        ORDER_CREATED,
        ORDER_COMPLETED,
        ORDER_CANCELLED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderEventData {
        private UUID orderId;
        private UUID userId;
        private Long totalAmount;
        private Instant occurredAt;
    }
}
