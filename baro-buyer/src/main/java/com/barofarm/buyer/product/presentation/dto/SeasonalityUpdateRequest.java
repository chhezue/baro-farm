package com.barofarm.buyer.product.presentation.dto;

import com.barofarm.buyer.product.domain.SeasonalityType;

/**
 * 제철 정보 업데이트 요청 DTO
 */
public record SeasonalityUpdateRequest(
    SeasonalityType seasonalityType,
    String seasonalityValue
) {
}
