package com.barofarm.shopping.product.presentation.dto;

import com.barofarm.shopping.product.domain.SeasonalityType;

/**
 * 제철 정보 업데이트 요청 DTO
 */
public record SeasonalityUpdateRequest(
    SeasonalityType seasonalityType,
    String seasonalityValue
) {
}
