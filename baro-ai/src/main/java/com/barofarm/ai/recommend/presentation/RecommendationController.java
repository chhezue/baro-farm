package com.barofarm.ai.recommend.presentation;

import com.barofarm.ai.recommend.application.PersonalizedRecommendService;
import com.barofarm.ai.recommend.application.dto.response.PersonalRecommendResponse;
import com.barofarm.ai.recommend.application.dto.response.PersonalRecommendWithScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @Operation(
        summary = "개인화 추천 상품 조회 (검증용 - 유사도 점수 포함)",
        description = "사용자의 행동 로그를 기반으로 생성된 프로필 벡터와 상품 벡터의 유사도를 계산하여 개인화된 상품을 추천합니다. "
            + "검증 및 디버깅을 위해 유사도 점수와 매칭 이유를 포함합니다."
    )
    @GetMapping("/personalized/{userId}/with-score")
    public List<PersonalRecommendWithScoreResponse> getPersonalizedRecommendationsWithScore(
        @Parameter(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable UUID userId,
        @Parameter(description = "추천할 상품 개수", example = "15")
        @RequestParam(required = false, defaultValue = "15") int topK
    ) {
        return personalizedRecommendService.recommendProductsWithScore(userId, topK);
    }
}
