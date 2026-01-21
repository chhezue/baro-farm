package com.barofarm.ai.season.application.dto;

import com.barofarm.ai.season.domain.SeasonalityType;
import java.time.LocalDateTime;

/**
 * 제철 정보 DTO
 */
public record SeasonalityInfo(
    String productName,  // 입력받은 상품명 (예: "천혜향", "딸기 설향")
    String category,     // 입력받은 카테고리 (예: "FRUIT")
    SeasonalityType type,
    String value,
    Double confidence,  // 0.0 ~ 1.0 사이의 신뢰도
    LocalDateTime detectedAt
) {
}
