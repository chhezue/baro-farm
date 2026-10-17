package com.barofarm.ai.recommend.presentation;

import com.barofarm.ai.recommend.application.PersonalizedRecommendService;
import com.barofarm.ai.recommend.application.RecipeRecommendService;
import com.barofarm.ai.recommend.application.SimilarProductRecommendService;
import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.recommend.application.dto.RecipeRecommendResponse;
import com.barofarm.dto.ResponseDto;
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

/**
 * AI 기반 상품 및 레시피 추천 API 엔드포인트를 제공하는 프리젠테이션 컨트롤러입니다.
 *
 * [제공 기능]
 * 1. 개인화 상품 추천: 사용자의 누적 행동(장바구니·주문) 취향 벡터 기반 맞춤 상품 목록 제공 (`/personalized/{userId}`)
 * 2. 장바구니 기반 레시피 추천: 사용자의 장바구니 식재료로 조리 가능한 레시피 및 부족한 재료 구매 연관 상품 제공 (`/recipes/{userId}`)
 * 3. 유사 상품 추천: 특정 상품 상세 페이지에서 해당 상품과 성격이 가장 가까운 대체/연관 상품 목록 제공 (`/similar/{productId}`)
 */
@Tag(name = "추천", description = "개인화 추천 및 레시피 추천 API")
@RestController
@RequestMapping("${api.v1}/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    /** 사용자 취향 벡터 기반 개인화 추천 서비스 */
    private final PersonalizedRecommendService personalizedRecommendService;

    /** 장바구니 식재료 기반 레시피 생성 및 부족한 재료 검색 서비스 */
    private final RecipeRecommendService recipeRecommendService;

    /** 상품 벡터 기반 유사 상품 추천 서비스 */
    private final SimilarProductRecommendService similarProductRecommendService;

    /**
     * 특정 사용자의 취향 벡터를 기반으로 개인화된 상품 목록을 조회합니다.
     *
     * @param userId 추천 대상 사용자의 UUID
     * @param topK   추천받을 상품 수 (기본값: 5개)
     * @return 개인화 추천 상품 응답 DTO 목록
     */
    @Operation(
        summary = "개인화 추천 상품 조회",
        description = "사용자의 행동 로그를 기반으로 생성된 프로필 벡터와 상품 벡터의 유사도를 계산하여 개인화된 상품을 추천"
    )
    @GetMapping("/personalized/{userId}")
    public ResponseDto<List<ProductRecommendResponse>> getPersonalizedRecommendations(
        @Parameter(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable UUID userId,
        @Parameter(description = "추천할 상품 개수 (1~50)", example = "5")
        @RequestParam(required = false, defaultValue = "5") int topK
    ) {
        // [서비스 위임 및 응답 포장]
        // 개인화 추천 서비스에 사용자 ID와 topK를 전달하여 추천 목록을 가져온 후 표준 ResponseDto로 반환합니다.
        List<ProductRecommendResponse> result = personalizedRecommendService.recommendProducts(userId, topK);
        return ResponseDto.ok(result);
    }

    /**
     * 사용자의 현재 장바구니 품목을 분석하여 최적의 요리 레시피와 부족한 재료 상품을 조회합니다.
     *
     * @param userId 장바구니를 조회할 사용자의 UUID
     * @return 레시피 정보 및 부족한 재료 구매 추천 DTO
     */
    @Operation(
        summary = "사용자의 장바구니 기반 레시피 추천",
        description = "사용자의 실제 장바구니 상품을 기반으로 맞춤 레시피를 추천하고, 부족한 핵심 재료에 대한 상품 추천을 제공"
    )
    @GetMapping("/recipes/{userId}")
    public ResponseDto<RecipeRecommendResponse> recommendFromCartWithMissing(
        @Parameter(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable UUID userId
    ) {
        // [서비스 위임 및 응답 포장]
        // 장바구니 상품을 조회하여 LLM 레시피 생성 및 부족한 재료를 검색한 후 표준 ResponseDto로 반환합니다.
        RecipeRecommendResponse result = recipeRecommendService.recommendFromCartWithMissing(userId);
        return ResponseDto.ok(result);
    }

    /**
     * 특정 상품 상세 페이지에서 해당 상품과 가장 유사한 대체 상품들을 조회합니다.
     *
     * @param productId 기준 상품의 UUID
     * @param topK      추천받을 유사 상품 수 (기본값: 3개)
     * @return 유사 상품 추천 응답 DTO 목록
     */
    @Operation(
        summary = "특정 상품과 유사한 상품 추천",
        description = "상품 상세 페이지 등에서 사용. 상품의 임베딩 벡터를 기반으로 유사도가 높은 다른 상품들을 추천"
    )
    @GetMapping("/similar/{productId}")
    public ResponseDto<List<ProductRecommendResponse>> getSimilarProducts(
        @Parameter(description = "기준 상품 ID", example = "00a1a1a1-a1a1-1a1a-a1a1-a1a1a1a1a1a1")
        @PathVariable UUID productId,
        @Parameter(description = "추천할 상품 개수 (1~50)", example = "3")
        @RequestParam(required = false, defaultValue = "3") int topK
    ) {
        // [서비스 위임 및 응답 포장]
        // 기준 상품의 임베딩 벡터를 바탕으로 유사 상품 목록을 조회한 후 표준 ResponseDto로 반환합니다.
        List<ProductRecommendResponse> result =
            similarProductRecommendService.recommendSimilarProducts(productId, topK);
        return ResponseDto.ok(result);
    }
}
