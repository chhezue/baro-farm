package com.barofarm.support.search.application.dto;

import java.util.List;

// 통합 자동완성 응답 DTO
public record UnifiedAutoCompleteResponse(
    List<ProductAutoItem> products,
    List<ExperienceAutoItem> experiences
) { }
