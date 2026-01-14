package com.barofarm.ai.event.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CartLogEvent(
    HistoryEventType event,
    OffsetDateTime ts,
    UUID userId,
    CartEventData payload) {

    public record CartEventData(

        // 동일 userId 안에서 여러 cart가 있을 수 있으나,
        // 개인화 추천에서는 주로 "최근 활성 장바구니" 관점에서 행동 시퀀스를 분석할 때 사용
        UUID cartId,

        // 장바구니 항목의 식별자:
        // - 특정 상품/옵션 조합에 대한 행동 히스토리를 추적할 때만 사용
        UUID cartItemId,

        // 유저가 관심을 보이는 상품 축:
        // - userId-productId 매트릭스를 구성해 협업 필터링/연관 분석의 기본 키로 사용
        UUID productId,

        // 레시피 추천/프롬프트용 텍스트:
        // - 장바구니 기반 레시피 추천에서, productId만 전달하기 어렵다면
        //   상품명을 그대로 LLM 프롬프트에 사용할 수 있음 (예: "고당도 토마토, 양파, 달걀...")
        String productName,

        // userId-productId 선호 강도를 나타내는 단순 지표:
        // - 많이 담을수록 "이 재료를 여러 번 사용하는 요리를 선호"한다고 간주
        Integer quantity
    ) { }
}
