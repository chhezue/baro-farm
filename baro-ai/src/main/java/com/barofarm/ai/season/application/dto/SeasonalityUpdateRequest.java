package com.barofarm.ai.season.application.dto;

import com.barofarm.ai.season.domain.SeasonalityType;

/**
 * 제철 정보 업데이트 요청 DTO
 * buyer-service에 제철 정보를 업데이트할 때 사용
 */
public record SeasonalityUpdateRequest(
    SeasonalityType seasonalityType,
    String seasonalityValue
) {
}
