package com.barofarm.ai.event.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderLogEvent(
    HistoryEventType event,
    OffsetDateTime ts,
    UUID userId,
    OrderEventData payload) {


    public record OrderEventData(

        UUID orderId,

        // 주문에 포함된 개별 상품/수량:
        // - userId-productId 매핑을 "실제 구매 기준"으로 구성
        // - 재구매 추천/유사 상품 추천의 핵심 데이터
        List<OrderItemData> orderItems) {

        public record OrderItemData(

            // userId-productId 매트릭스의 product 축:
            UUID productId,

            String productName,

            // 특정 상품을 얼마나 반복해서 많이 사는지에 대한 개인화 지표
            Integer quantity
        ) { }
    }
}
