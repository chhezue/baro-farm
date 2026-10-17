package com.barofarm.ai.event.model;

import com.barofarm.log.history.model.HistoryEventType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Order Kafka에서 전달되는 데이터 구조
public record OrderLogEvent(
    HistoryEventType event,
    OffsetDateTime ts,
    UUID userId,
    OrderEventData payload) {

    public record OrderEventData(
        UUID orderId,
        List<OrderItemData> orderItems
    ) {
        // 상류는 수량·카테고리 코드도 보내지만 받지 않는다 — 둘 다 저장만 되고 읽는 곳이 없어 2026-09-07에 뺐다.
        // 근거: docs/audits/cross-cutting/003-stale-comments-and-write-only-fields.md
        public record OrderItemData(
            UUID productId,
            String productName
        ) { }
    }
}
