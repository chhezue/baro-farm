package com.barofarm.ai.recommend.infrastructure.client.dto;

import java.util.UUID;

/**
 * 장바구니에 담긴 개별 상품 중 레시피 추천이 실제로 읽는 필드만 담는 DTO 레코드입니다.
 *
 * <p>원본(`baro-shopping`의 `cart.application.dto.CartItemInfo`)은 `itemId`·`productCategoryName`·
 * `quantity`·`unitPrice`·`lineTotalPrice`·`inventoryId`·`unit`을 함께 보냅니다. 추천은 "무엇이 담겼나"만
 * 쓰고 수량·가격은 보지 않으므로 옮겨 적지 않았습니다 — 생략 근거는 {@link CartInfo} 참고.
 *
 * @param productId           담긴 상품의 고유 UUID — 색인된 재료명을 찾는 조회 키
 * @param productName         상품 명칭 — 로그 식별용. 재료명은 이 값이 아니라 `productId`로 색인에서 다시 찾는다
 * @param productCategoryCode 상품 카테고리 코드 — 비식재료 제외 판정에 쓴다. 상류가 카테고리 조회에 실패하면
 *                            코드 대신 안내 문구를 넣어 보내므로, 그 경우 어떤 제외 코드와도 일치하지 않는다
 */
public record CartItemInfo(
    UUID productId,
    String productName,
    String productCategoryCode
) { }
