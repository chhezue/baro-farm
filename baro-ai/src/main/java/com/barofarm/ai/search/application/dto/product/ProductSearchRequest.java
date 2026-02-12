package com.barofarm.ai.search.application.dto.product;

import java.util.List;

// 상품 단독 검색을 위한 요청 DTO
public record ProductSearchRequest(
    String keyword, // 키워드
    List<String> categoryCodes, // 상품 카테고리 코드
    Long priceMin, // 최소 가격 범위
    Long priceMax // 최대 가격 범위
) {
}
