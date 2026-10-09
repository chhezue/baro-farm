package com.barofarm.ai.recommend.infrastructure.client.dto;

import java.util.List;

/**
 * `baro-shopping`의 장바구니 조회 응답 중 이 모듈이 실제로 읽는 부분만 담는 DTO 레코드입니다.
 *
 * <p>원본은 `baro-shopping`의 `cart.application.dto.CartInfo`이며 `cartId`·`buyerId`·`totalPrice`·
 * `createdAt`·`updatedAt`을 함께 보냅니다. 레시피 추천은 담긴 품목만 쓰므로 옮겨 적지 않았습니다 —
 * Jackson이 모르는 필드를 무시하도록 돼 있어(Spring Boot 기본값, `spring.jackson` 미설정) 수신에 지장이 없고,
 * 쓰지도 않는 필드를 옮겨 적으면 상류가 바뀔 때 이쪽까지 같이 고쳐야 하는 것처럼 보이기 때문입니다.
 * 다른 값이 필요해지면 원본에서 이름·타입을 확인해 추가하세요.
 *
 * @param items 장바구니에 담긴 개별 상품 항목 목록
 */
public record CartInfo(
    List<CartItemInfo> items
) { }
