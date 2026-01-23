package com.barofarm.buyer.cart.application.dto;

import com.barofarm.buyer.cart.domain.CartItem;
import java.util.UUID;

// 장바구니의 개별 상품 조회용 DTO
public record CartItemInfo(
    UUID itemId,
    UUID productId,
    UUID productCategoryId,
    String productName,
    String productCategoryName,
    Integer quantity,
    Long unitPrice,
    Long lineTotalPrice,
    UUID inventoryId,
    Integer unit
) {

    // 실시간 상품명, 카테고리명과 재고 단위로 CartInfo 생성
    public static CartItemInfo from(CartItem item, String productName, UUID categoryId, String categoryName, Integer unit) {
        return new CartItemInfo(
            item.getId(),
            item.getProductId(),
            categoryId,
            productName != null ? productName : "(상품명을 불러오지 못했습니다)",
            categoryName != null ? categoryName : "(카테고리 정보를 불러오지 못했습니다)",
            item.getQuantity(),
            item.getUnitPrice(),
            item.calculatePrice(),
            item.getInventoryId(),
            unit != null ? unit : -1
        );
    }
}
