package com.barofarm.buyer.product.domain;

/**
 * 제철 정보 타입
 */
public enum SeasonalityType {
    // 월 범위 (예: "1-3", "6-8", "11-2")
    MONTH_RANGE,

    // 계절 (봄, 여름, 가을, 겨울)
    SEASON,

    // 연중 재배 가능
    YEAR_ROUND
}
