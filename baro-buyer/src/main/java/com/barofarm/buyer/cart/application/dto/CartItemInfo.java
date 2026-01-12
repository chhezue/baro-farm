package com.barofarm.buyer.cart.application.dto;

import com.barofarm.buyer.cart.domain.CartItem;
import java.util.UUID;

// 장바구니의 개별 상품 조회용 DTO
public record CartItemInfo(
    UUID itemId,
    UUID productId,
    String productName,
    Integer quantity,
    Long unitPrice,
    Long lineTotalPrice,
    UUID inventoryId
) {
    /**
     * 실시간 상품명 사용 (ProductService로 조회)
     * 장바구니 조회 시 항상 실시간 상품명을 반환
     */
    public static CartItemInfo from(CartItem item, String realTimeProductName) {
        return new CartItemInfo(
            item.getId(),
            item.getProductId(),
            realTimeProductName != null
                ? realTimeProductName
                : "(상품 정보를 불러올 수 없습니다)",
            item.getQuantity(),
            item.getUnitPrice(),
            item.calculatePrice(),      // CartItem 도메인 메서드 사용
            item.getInventoryId()
        );
    }
}
