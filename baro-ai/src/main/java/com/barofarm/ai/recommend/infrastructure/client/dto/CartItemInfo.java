package com.barofarm.ai.recommend.infrastructure.client.dto;

import java.util.UUID;

/**
 * 장바구니에 담긴 개별 상품 중 레시피 추천이 실제로 읽는 필드만 담는 DTO 레코드입니다.
 *
 * <p>원본(`baro-shopping`의 `cart.application.dto.CartItemInfo`)은 `itemId`·`productCategoryName`·
 * `quantity`·`unitPrice`·`lineTotalPrice`·`inventoryId`·`unit`을 함께 보냅니다. 추천은 "무엇이 담겼나"만
 * 쓰고 수량·가격은 보지 않으므로 옮겨 적지 않았습니다 — 생략 근거는 {@link CartInfo} 참고.
 *
 * <p>카테고리 코드는 2026-09-09에 뺐습니다. 그 값을 보던 것은 "레시피 재료로 쓰지 않을 카테고리" 필터
 * 하나뿐이었는데, 그 목록이 계속 비어 있어 아무것도 걸러내지 않는 죽은 분기였습니다. 필터를 지우면서
 * 이 필드도 함께 뺐습니다 — 필요해지면 원본에서 이름·타입을 확인해 다시 넣습니다.
 *
 * @param productId   담긴 상품의 고유 UUID — 색인된 재료명을 찾는 조회 키
 * @param productName 상품 명칭 — 로그 식별용. 재료명은 이 값이 아니라 `productId`로 색인에서 다시 찾는다
 */
public record CartItemInfo(
    UUID productId,
    String productName
) { }
