package com.barofarm.ai.season.application.dto;

import com.barofarm.ai.season.domain.SeasonalityType;

/**
 * LLM 응답 파싱용 DTO
 */
public record SeasonalityDetectionResponse(
    String detectedProductName,  // LLM이 판단한 전체 상품명 (예: "귤 타이벡", "감귤 타이벡")
    SeasonalityType seasonalityType,
    String seasonalityValue,  // "1-3" 또는 "봄" 등
    Double confidence,  // 0.0 ~ 1.0 사이의 신뢰도
    String reasoning,  // 판단 근거 (디버깅용)
    String farmExperienceNote  // 농장체험 가능 여부 ("농장체험 가능" 또는 "농장체험 불가")
) {
}

