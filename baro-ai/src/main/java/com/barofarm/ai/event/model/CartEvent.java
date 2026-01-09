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

    // 개인화 추천에서 사용할 장바구니 상호작용 타입:
    // - 각 이벤트는 특정 userId에 귀속되며, 유저별 장바구니 행동 패턴을 학습하는 용도로만 사용
    private CartEventType type;

    private CartEventData data;

    public enum CartEventType {
        CART_ITEM_ADDED,   // 특정 유저가 해당 상품에 강한 관심을 보이기 시작한 시점
        CART_ITEM_REMOVED, // 해당 상품에 대한 관심이 약해졌음을 나타내는 시점
        CART_QUANTITY_UPDATED // 수량 변경
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartEventData {

        // "로그인한 사용자" 기준 개인화 추천만 지원:
        // - userId가 없는 이벤트(게스트/세션)는 수집 대상이 아님
        // - 장바구니/검색/주문 로그를 userId 기준으로 연결해 하나의 유저 프로필을 만듦
        private UUID userId;

        // 동일 userId 안에서 여러 cart가 있을 수 있으나,
        // 개인화 추천에서는 주로 "최근 활성 장바구니" 관점에서 행동 시퀀스를 분석할 때 사용
        private UUID cartId;

        // 장바구니 항목의 식별자:
        // - 특정 상품/옵션 조합에 대한 행동 히스토리를 추적할 때만 사용
        private UUID cartItemId;

        // 유저가 관심을 보이는 상품 축:
        // - userId-productId 매트릭스를 구성해 협업 필터링/연관 분석의 기본 키로 사용
        private UUID productId;

        // 레시피 추천/프롬프트용 텍스트:
        // - 장바구니 기반 레시피 추천에서, productId만 전달하기 어렵다면
        //   상품명을 그대로 LLM 프롬프트에 사용할 수 있음 (예: "고당도 토마토, 양파, 달걀...")
        private String productName;

        // userId-productId 선호 강도를 나타내는 단순 지표:
        // - 많이 담을수록 "이 재료를 여러 번 사용하는 요리를 선호"한다고 간주
        private Integer quantity;

//        // 가격대 선호를 개인화 관점에서 추적:
//        // - 이 유저가 장바구니에 담는 평균 단가/총액을 기반으로
//        //   비슷한 가격대 상품만 추천하도록 필터링
//        private Long unitPrice;
//
//        // 옵션 정보:
//        // - 같은 productId라도 옵션(패키지/맛/사이즈)에 따라 선호가 다를 수 있어
//        //   옵션 차원을 포함한 세밀한 개인화가 필요할 때 사용
//        private String optionInfoJson;

        // 해당 장바구니 액션이 언제 일어났는지:
        // - 개인화 모델에서 최근 행동에 더 높은 가중치를 두기 위한 time-decay 인자
        private Instant occurredAt;
    }
}
