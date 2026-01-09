package com.barofarm.ai.event.model;

import java.time.Instant;
import java.util.List;
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

    // 개인화 추천에서 "가장 신뢰도 높은 행동 데이터"인 주문 상태 변화:
    // - ORDER_COMPLETED 이벤트만으로도 userId별 확정 구매 이력을 구성할 수 있음
    private OrderEventType type;

    private OrderEventData data;

    public enum OrderEventType {
        ORDER_CREATED,   // (선택) '구매를 고민한 적이 있다'는 약한 신호
        ORDER_CANCELLED  // 해당 상품/주문에 대한 부정적 신호 (추천에서 감점)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderEventData {

        private UUID orderId;

        // 개인화 추천의 기준 축:
        // - userId 기반으로 주문 이력을 쌓아 "이 유저가 실제로 산 상품/카테고리"를 파악
        private UUID userId;

//        // 이 유저의 평균 객단가를 계산하고,
//        // 그 범위에 맞는 상품/체험을 추천하는 데 활용
//        private Long totalAmount;
//
//        // 주문 상태 (예: COMPLETED, CANCELLED 등):
//        // - COMPLETED만 구매 확정 시그널로 사용하고
//        //   CANCELLED는 개인화 모델에서 감점 요인으로 쓸 수 있음
//        private String status;

        // 상태 이벤트 시각:
        // - 최근 주문일수록 개인화 추천에 더 큰 영향을 주도록 가중치 조정
        private Instant occurredAt;

        // 주문에 포함된 개별 상품/수량:
        // - userId-productId 매핑을 "실제 구매 기준"으로 구성
        // - 재구매 추천/유사 상품 추천의 핵심 데이터
        private List<OrderItemData> orderItems;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class OrderItemData {

            // userId-productId 매트릭스의 product 축:
            private UUID productId;

            private String productName;

            // (선택) 같은 판매자의 다른 상품을 이 유저에게 추천할 때 사용할 수 있으나,
            // 전체 인기 랭킹이 아니라 "이 유저가 자주 사는 판매자" 관점에서만 활용
//            private UUID sellerId;

            // 특정 상품을 얼마나 반복해서 많이 사는지에 대한 개인화 지표
            private Integer quantity;

            // 개인별 가격 민감도와 매출 기여도를 동시에 고려할 때 사용
//            private Long unitPrice;
//            private Long totalPrice;
        }
    }
}
