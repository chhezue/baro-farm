package com.barofarm.support.search.application.dto;

import com.barofarm.support.common.response.CustomPage;
import com.barofarm.support.search.application.dto.experience.ExperienceSearchResponse;
import com.barofarm.support.search.application.dto.product.ProductSearchResponse;

// 통합 검색 응답 DTO
public record UnifiedSearchResponse(
    CustomPage<ProductSearchResponse> products,
    CustomPage<ExperienceSearchResponse> experiences) {
}
