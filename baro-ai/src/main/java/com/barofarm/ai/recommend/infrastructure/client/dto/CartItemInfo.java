package com.barofarm.ai.recommend.infrastructure.client.dto;

import java.util.UUID;

/**
 * 장바구니에 담긴 개별 상품 품목의 상세 정보를 나타내는 DTO 레코드입니다.
 *
 * @param itemId              장바구니 아이템의 고유 UUID
 * @param productId           담긴 상품의 고유 UUID
 * @param productName         상품 명칭 (예: "친환경 흙대파 1단")
 * @param productCategoryName 상품 카테고리 표시명 (예: "채소")
 * @param productCategoryCode 상품 카테고리 코드 (비식재료 필터링 검사에 사용됨)
 * @param quantity            장바구니에 담긴 구매 수량
 * @param unitPrice           상품 단가 (원)
 * @param lineTotalPrice      수량이 곱해진 해당 품목의 총 합계 금액 (unitPrice * quantity)
 * @param inventoryId         연결된 재고 ID
 * @param unit                상품 포장/판매 단위
 */
public record CartItemInfo(
    UUID itemId,
    UUID productId,
    String productName,
    String productCategoryName,
    String productCategoryCode,
    Integer quantity,
    Long unitPrice,
    Long lineTotalPrice,
    UUID inventoryId,
    Integer unit
) {
}
