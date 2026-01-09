package com.barofarm.ai.search.application.dto;

import com.barofarm.ai.search.application.dto.experience.ExperienceAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import java.util.List;

// 통합 자동완성 응답 DTO
public record UnifiedAutoCompleteResponse(
    List<ProductAutoCompleteResponse> products,
    List<ExperienceAutoCompleteResponse> experiences
) { }
