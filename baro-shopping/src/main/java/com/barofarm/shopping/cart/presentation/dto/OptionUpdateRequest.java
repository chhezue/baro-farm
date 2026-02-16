package com.barofarm.shopping.cart.presentation.dto;

import java.util.UUID;

// 장바구니 항목 옵션 변경 요청 DTO
public record OptionUpdateRequest(UUID inventoryId) {}
