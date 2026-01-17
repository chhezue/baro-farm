package com.barofarm.ai.recommend.client.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 장바구니 정보 DTO (Buyer Service의 CartInfo와 동일한 구조)
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
}
