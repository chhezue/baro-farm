package com.barofarm.ai.search.application.dto.experience;

// 체험 단독 검색을 위한 요청 DTO
public record ExperienceSearchRequest(
    String keyword, // 검색 키워드
    Long pricePerPersonMin, // 최소 가격 범위
    Long pricePerPersonMax, // 최대 가격 범위
    Integer capacityMin, // 최소 수용 인원
    Integer capacityMax, // 최대 수용 인원
    Integer durationMin, // 최소 소요 시간 (분)
    Integer durationMax // 최대 소요 시간 (분)
) {}
