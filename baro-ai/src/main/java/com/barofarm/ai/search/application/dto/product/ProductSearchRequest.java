package com.barofarm.ai.search.application.dto.product;

import java.util.List;

/**
 * 상품 키워드 검색 서비스(`ProductSearchService.search`) 호출에 사용되는 검색 조건 요청 DTO 레코드입니다.
 *
 * @param keyword       사용자 입력 검색 키워드 (null 또는 빈 문자열 시 전체 상품 대상)
 * @param categoryCodes 필터링할 상품 카테고리 코드 목록 (선택)
 * @param priceMin      최소 상품 가격 필터 (원)
 * @param priceMax      최대 상품 가격 필터 (원)
 */
public record ProductSearchRequest(
    String keyword,
    List<String> categoryCodes,
    Long priceMin,
    Long priceMax
) {
}
