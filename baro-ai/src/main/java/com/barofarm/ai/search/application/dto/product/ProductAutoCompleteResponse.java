package com.barofarm.ai.search.application.dto.product;

import java.util.UUID;

/**
 * 검색어 자동완성 질의(`GET /api/v1/search/autocomplete`)의 응답 항목을 나타내는 DTO 레코드입니다.
 *
 * @param productId   사용자가 자동완성 추천 키워드를 클릭했을 때 해당 상품 상세 페이지로 이동하기 위한 상품 고유 UUID
 * @param productName 자동완성 목록에 노출할 상품 명칭
 */
public record ProductAutoCompleteResponse(
    UUID productId,
    String productName
) {}
