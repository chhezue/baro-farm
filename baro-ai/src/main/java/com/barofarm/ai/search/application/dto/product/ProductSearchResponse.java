package com.barofarm.ai.search.application.dto.product;

import java.util.UUID;

/**
 * 상품 키워드 검색(`GET /api/v1/search`) 결과 목록의 개별 상품 아이템을 나타내는 응답 DTO 레코드입니다.
 *
 * @param productId           상품 고유 UUID
 * @param productName         상품 명칭
 * @param productCategoryName 상품 카테고리 표시명 (예: "채소", "과일" 등)
 * @param price               상품 판매 가격 (원)
 */
public record ProductSearchResponse(
    UUID productId,
    String productName,
    String productCategoryName,
    Long price
) {
}
