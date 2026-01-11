package com.barofarm.buyer.cart.application.dto;

import com.barofarm.buyer.cart.domain.Cart;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 장바구니 조회 Info DTO (Service 출력용)
public record CartInfo(
    UUID cartId,
    UUID buyerId,
    List<CartItemInfo> items,
    Long totalPrice,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CartInfo empty() {
        return new CartInfo(
            null,
            null,
            List.of(),
            0L,
            null,
            null
        );
    }

    /**
     * 실시간 상품명으로 CartInfo 생성
     * 장바구니 조회 시 항상 실시간 상품명을 함께 반환
     * @param cart 장바구니 도메인 객체
     * @param productNameMap 상품ID -> 상품명 매핑 (실시간 조회 결과)
     */
    public static CartInfo from(Cart cart, java.util.Map<UUID, String> productNameMap) {
        return new CartInfo(
            cart.getId(),
            cart.getBuyerId(),
            cart.getItems().stream()
                .map(item -> {
                    String realTimeName = productNameMap.get(item.getProductId());
                    return CartItemInfo.from(item, realTimeName);  // 실시간 상품명 사용
                })
                .toList(),
            cart.calculateTotalPrice(),
            cart.getCreatedAt(),
            cart.getUpdatedAt()
        );
    }
}
