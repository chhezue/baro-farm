package com.barofarm.buyer.cart.application.dto;

import com.barofarm.buyer.cart.domain.CartItem;
import java.util.UUID;

// 장바구니의 개별 상품 조회용 DTO
public record CartItemInfo(
    UUID itemId,
    UUID productId,
    String productName,
    String productCategoryName,
    Integer quantity,
    Long unitPrice,
    Long lineTotalPrice,
    UUID inventoryId,
    Integer unit
) {

    // 실시간 상품명과 재고 단위로 CartInfo 생성
    public static CartItemInfo from(CartItem item, String productName, Integer unit) {
        return new CartItemInfo(
            item.getId(),
            item.getProductId(),
            productName != null
                ? productName
                : "(상품명을 불러올 수 없습니다)",
            null, // productCategoryName - 추후 구현 예정
            item.getQuantity(),
            item.getUnitPrice(),
            item.calculatePrice(),      // CartItem 도메인 메서드 사용
            item.getInventoryId(),
            unit != null ? unit : -1  // unit이 null이면 오류 표시 값 -1로 설정
        );
    }
}
