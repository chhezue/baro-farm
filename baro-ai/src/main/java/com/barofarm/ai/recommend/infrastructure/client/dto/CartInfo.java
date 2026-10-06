package com.barofarm.ai.recommend.infrastructure.client.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 쇼핑 서비스(shopping-service)에서 조회된 사용자의 장바구니 전체 정보를 담는 DTO 레코드입니다.
 *
 * @param cartId     장바구니의 고유 UUID
 * @param buyerId    장바구니 소유자인 구매자(사용자)의 UUID
 * @param items      장바구니에 담긴 개별 상품 항목 목록 (CartItemInfo)
 * @param totalPrice 장바구니에 담긴 모든 상품의 총 결제 예상 금액
 * @param createdAt  장바구니 최초 생성 일시
 * @param updatedAt  장바구니 최근 갱신 일시
 */
public record CartInfo(
    UUID cartId,
    UUID buyerId,
    List<CartItemInfo> items,
    Long totalPrice,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) { }
