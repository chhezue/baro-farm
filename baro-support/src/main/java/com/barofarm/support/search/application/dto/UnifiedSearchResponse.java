package com.barofarm.support.search.application.dto;

import com.barofarm.support.common.response.CustomPage;

// 통합 검색 응답 DTO
public record UnifiedSearchResponse(
    CustomPage<ProductSearchItem> products,
    CustomPage<ExperienceSearchItem> experiences) {
}
