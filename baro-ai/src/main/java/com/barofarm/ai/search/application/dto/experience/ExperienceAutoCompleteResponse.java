package com.barofarm.ai.search.application.dto.experience;

import java.util.UUID;

// 체험 자동완성 응답 DTO
public record ExperienceAutoCompleteResponse(
    UUID experienceId, // 프론트에서 클릭 시 체험으로 바로 이동
    String experienceName // 체험명
) {}
