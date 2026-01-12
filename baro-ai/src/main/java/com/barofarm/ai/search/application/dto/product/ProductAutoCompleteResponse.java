package com.barofarm.ai.search.application.dto.product;

import java.util.UUID;

// 상품 자동완성 응답 DTO
public record ProductAutoCompleteResponse(
    UUID productId, // 프론트에서 클릭 시 상품으로 바로 이동
    String productName // 상품명
) {}
