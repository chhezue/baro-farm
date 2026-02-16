package com.barofarm.shopping.cart.presentation.dto;

import com.barofarm.shopping.cart.application.dto.CartItemCreateCommand;
import java.util.UUID;

// 장바구니에 상품 추가 요청 DTO
public record CartItemCreateRequest(
    UUID productId, Integer quantity, Long unitPrice, UUID inventoryId) {

  public CartItemCreateCommand toCommand() {
    return new CartItemCreateCommand(productId, quantity, unitPrice, inventoryId);
  }
}
