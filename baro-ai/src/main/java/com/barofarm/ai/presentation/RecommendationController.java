package com.barofarm.ai.presentation;

import com.barofarm.ai.recommend.model.PersonalRecommendResponse;
import com.barofarm.ai.recommend.service.PersonalizedRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "추천", description = "개인화 추천 및 레시피 추천 API")
@RestController
@RequestMapping("${api.v1}/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final PersonalizedRecommendService personalizedRecommendService;

    @Operation(
        summary = "개인화 추천 상품 조회",
        description = "사용자의 행동 로그를 기반으로 생성된 프로필 벡터와 상품 벡터의 유사도를 계산하여 개인화된 상품을 추천합니다."
    )
    @GetMapping("/personalized/{userId}")
    public List<PersonalRecommendResponse> getPersonalizedRecommendations(
        @Parameter(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable UUID userId,
        @Parameter(description = "추천할 상품 개수", example = "5")
        @RequestParam(required = false, defaultValue = "5") int topK
    ) {
        return personalizedRecommendService.recommendProducts(userId, topK);
    }
}
