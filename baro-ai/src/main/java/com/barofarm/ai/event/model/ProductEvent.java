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
public class ProductEvent {

    private ProductEventType type;

    // 개인화 추천/검색 인덱싱/로그 적재에 공통으로 쓰이는 상품 스냅샷:
    // - userId와 직접 연결되지는 않지만,
    //   userId-productId 행동 로그를 해석할 때 필요한 메타데이터 역할
    private ProductEventData data;

    public enum ProductEventType {
        PRODUCT_CREATED,
        PRODUCT_UPDATED,
        PRODUCT_DELETED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductEventData {

        private UUID productId;

        // 장바구니/주문 로그의 productId와 매칭해서
        // "어떤 이름의 상품을 선호하는지"를 임베딩 관점에서 해석할 때 사용
        private String productName;

        // 개인별 카테고리 선호도 벡터를 만들 때 사용:
        // - userId별로 장바구니/주문에 등장한 productCategory를 집계해서,
        //   "이 유저는 어떤 카테고리를 많이 산다/담는다"를 파악
        private String productCategory;

        // 개인별 가격대 선호를 보정할 때 사용:
        // - '장바구니 담은 상품 가격'과 '실제 구매한 상품 가격'을 비교해
        //   추천 시 적절한 가격 레인지만 노출 가능
        private Long price;

        // 추천 결과에 노출 가능한지 여부:
        // - 품절/판매중지 등은 userId와 관계없이 추천 후보에서 제외
        private String status;

        // 임베딩/캐시를 갱신할 필요가 있는지 판단하는 기준 시각:
        private Instant updatedAt;
    }
}
