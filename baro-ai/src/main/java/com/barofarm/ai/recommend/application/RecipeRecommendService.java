package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.IngredientRecommendResponse;
import com.barofarm.ai.recommend.application.dto.RecipeRecommendReason;
import com.barofarm.ai.recommend.application.dto.RecipeRecommendResponse;
import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import com.barofarm.ai.recommend.domain.IngredientProcessingUtil;
import com.barofarm.ai.recommend.infrastructure.client.CartClient;
import com.barofarm.ai.recommend.infrastructure.client.dto.CartInfo;
import com.barofarm.ai.recommend.infrastructure.client.dto.CartItemInfo;
import com.barofarm.ai.recommend.infrastructure.llm.RecipeCandidateGenerator;
import com.barofarm.ai.search.application.ProductSearchService;
import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.dto.ResponseDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자의 장바구니에 담긴 식재료 상품들을 분석하여 최적의 요리 레시피를 추천하고,
 * 부족한 핵심 식재료를 구매할 수 있도록 연관 상품을 함께 추천하는 애플리케이션 서비스입니다.
 *
 * [핵심 프로세스]
 * 1. CartClient를 통해 사용자의 현재 장바구니 품목들을 조회합니다.
 * 2. 상품 식별자가 있는 항목만 남깁니다.
 * 3. Elasticsearch에 사전 색인된 상품 문서의 재료명(ProductDocument.ingredientName)을 일괄 조회하여 보유 재료 목록을 도출합니다.
 *    (요청 시점마다 LLM으로 재료를 파싱하지 않고 색인 시점에 미리 정규화된 재료명을 읽어 지연 시간과 비용을 획기적으로 절감)
 * 4. RecipeCandidateGenerator를 통해 LLM 기반으로 현재 보유 재료로 조리 가능한 레시피를 생성합니다.
 * 5. 생성된 레시피에 필요하지만 장바구니에 없는 '부족한 재료'를 Elasticsearch 키워드 검색으로 찾아 구매 후보 상품으로 매핑합니다.
 *
 * 레시피를 만들지 못한 경우는 예외가 아니라 200 + 빈 필드로 나가고, 왜 비었는지는
 * {@link RecipeRecommendReason}이 알린다. 빈 장바구니는 오류가 아니라 사용자 상태이기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    /** 부족한 식재료 1개당 추천하여 함께 노출할 연관 상품 최대 개수 (현재 2건) */
    private static final int MISSING_PRODUCTS_LIMIT_PER_INGREDIENT = 2;

    /** `baro-shopping`의 장바구니 API를 호출해 사용자의 장바구니 품목을 조회하는 Feign 클라이언트 */
    private final CartClient cartClient;

    /** 부족 재료 상품 검색과 장바구니 상품 문서 조회를 맡는 서비스 — 상품 인덱스 질의는 전부 이쪽을 거친다 */
    private final ProductSearchService productSearchService;

    /** Gemini LLM 구조화 출력을 활용해 식재료 기반 레시피를 생성하는 제너레이터 */
    private final RecipeCandidateGenerator recipeCandidateGenerator;

    /** 응답이 왜 그 모양이 됐는지 이유별로 지표에 남기는 컴포넌트 */
    private final RecommendationMetrics recommendationMetrics;

    /**
     * 특정 사용자의 장바구니 상품을 기반으로 레시피 및 부족한 재료 연관 상품을 추천합니다.
     *
     * @param userId 추천을 요청한 사용자의 고유 UUID
     * @return 레시피 추천 응답 DTO (만들지 못하면 이유가 담긴 빈 응답)
     */
    public RecipeRecommendResponse recommendFromCartWithMissing(UUID userId) {
        // [1단계: 장바구니 Feign 클라이언트 호출 및 예외 격리]
        // 장바구니 서비스와의 네트워크 오류 등 외부 연동 실패가 프론트엔드 오류로 번지지 않도록
        // 전체 과정을 try-catch로 감싸고, 실패 시 UPSTREAM_ERROR로 표시한 빈 응답을 반환합니다.
        // 이 catch는 기능 전체의 마지막 방어선이라 스택트레이스를 반드시 남깁니다(예외 객체를 마지막 인자로).
        RecipeRecommendResponse response;
        try {
            ResponseDto<CartInfo> cartResponse = cartClient.getCart(userId);
            CartInfo cart = cartResponse != null ? cartResponse.data() : null;
            response = recommendFromCart(cart, userId.toString());
        } catch (Exception e) {
            log.warn("레시피 추천 실패(장바구니 조회·재료 조회·후보 생성 중 예외). userId: {}", userId, e);
            response = RecipeRecommendResponse.empty(RecipeRecommendReason.UPSTREAM_ERROR);
        }

        // 이유를 여기 한 곳에서만 센다 — 아래 단계마다 흩어 놓으면 하나를 빠뜨렸을 때 알아채지 못한다.
        recommendationMetrics.recordRecipe(response.reason());
        return response;
    }

    /**
     * 장바구니 정보(CartInfo)로부터 레시피를 생성하고 부족한 식재료를 검색하는 내부 추천 메서드.
     */
    private RecipeRecommendResponse recommendFromCart(CartInfo cart, String userIdentifier) {
        // [1단계: 장바구니 데이터 유효성 검증]
        // 장바구니 객체가 null이거나 담긴 상품이 없는 경우 레시피 생성이 불가하므로 빈 응답을 반환합니다.
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            log.warn("장바구니가 비어있습니다. userId: {}", userIdentifier);
            return RecipeRecommendResponse.empty(RecipeRecommendReason.EMPTY_CART);
        }

        // [2단계: 상품 식별자가 있는 항목만 남긴다]
        // 2026-09-09까지는 여기에 "레시피 재료로 쓰지 않을 카테고리" 필터가 하나 더 있었습니다. 그런데 그 목록은
        // 2026-09-07부터 비어 있어 아무것도 걸러내지 않았고(이 쇼핑몰은 농산물만 팔아 "요리에 못 쓰는 상품"이라는
        // 분류 자체가 없습니다), 그 죽은 분기를 위해 설정 파일·프로퍼티 클래스·장바구니 DTO의 카테고리 필드가
        // 함께 남아 있었습니다. 필요해지면 그때 되살립니다.
        // 비운 경위: docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md M-2·M-3
        List<CartItemInfo> eligibleItems = cart.items().stream()
            .filter(Objects::nonNull)
            .filter(item -> item.productId() != null)
            .toList();

        if (eligibleItems.isEmpty()) {
            log.warn("유효한 장바구니 상품이 없습니다. userId: {}", userIdentifier);
            return RecipeRecommendResponse.empty(RecipeRecommendReason.NO_ELIGIBLE_ITEMS);
        }

        // [3단계: Elasticsearch 사전 색인 재료명(ingredientName) 일괄 조회]
        // 매 추천 요청마다 LLM을 호출하여 상품명에서 재료를 파싱하던 구 방식을 폐기하고,
        // 상품 색인 시점에 미리 정제하여 저장해 둔 ProductDocument.ingredientName을 읽어와 즉각적인 응답 속도를 보장합니다.
        List<String> ownedNames = loadOwnedIngredientNames(eligibleItems, userIdentifier);

        if (ownedNames.isEmpty()) {
            log.warn("색인된 재료명이 없어 레시피 추천을 건너뜁니다. userId: {}", userIdentifier);
            return RecipeRecommendResponse.empty(RecipeRecommendReason.INGREDIENTS_NOT_INDEXED);
        }

        // [4단계: Gemini LLM 기반 레시피 생성]
        Optional<CandidateRecipePlan> selectedOpt = recipeCandidateGenerator.generateRecipe(ownedNames);

        if (selectedOpt.isEmpty()) {
            log.warn("레시피 생성 실패. userId: {}", userIdentifier);
            return new RecipeRecommendResponse(
                RecipeRecommendReason.GENERATION_FAILED, "", ownedNames, List.of(), List.of(), "");
        }

        CandidateRecipePlan plan = selectedOpt.get();
        log.info("최종 선택된 레시피: {}", plan.recipeName());

        // [5단계: 부족한 핵심 식재료(missingIngredientsCore) 추출 및 연관 상품 검색]
        // 서버가 계산한 부족 핵심 재료 목록으로 구매 가능한 상품을 한 번의 multi-search로 조회합니다.
        List<String> missingCore = plan.missingIngredientsCore();
        List<IngredientRecommendResponse> missingRecommendations = searchMissingIngredientProducts(missingCore);

        // [6단계: 최종 레시피 및 재료 구매 추천 응답 DTO 조립]
        // 보유 재료로는 장바구니 전체(ownedNames)가 아니라 이 요리가 실제로 쓰는 것만 내보낸다.
        // 전체를 내보내면 보유 + 부족 != 핵심 재료가 되어, 11종이 담긴 장바구니에서 김치찌개를
        // 추천할 때 우유·사과까지 재료인 것처럼 읽힌다.
        return new RecipeRecommendResponse(
            RecipeRecommendReason.OK,
            plan.recipeName(),
            plan.usedOwnedIngredients(),
            missingCore,
            missingRecommendations,
            Optional.ofNullable(plan.instructions()).orElse("")
        );
    }

    /**
     * 부족한 식재료 전부를 키워드로 묶어 Elasticsearch에 한 번(`_msearch`)만 물어보고, 재료별 상품 카드로 나눕니다.
     *
     * 재료마다 검색을 따로 던지면 부족 재료 수만큼 ES 왕복이 순차로 늘어난다(2026-09-06 감사 cart-recipe/005 A-2).
     * 검색이 통째로 실패하면 레시피 본문은 살리고 상품 카드만 비운다 — 상품이 0건인 재료는 카드가 빠지되
     * `missingCoreIngredients`에는 그대로 남는다.
     */
    private List<IngredientRecommendResponse> searchMissingIngredientProducts(List<String> missingCore) {
        if (missingCore == null || missingCore.isEmpty()) {
            return List.of();
        }

        try {
            List<List<ProductSummaryResponse>> resultsPerIngredient =
                productSearchService.searchByKeywords(
                    // 검색어는 핵심 명사만 쓴다 — "국거리용 소고기"로는 색인된 재료명 "소고기"와 일치하지 않는다.
                    // 사용자에게 보이는 missingCoreIngredients는 원문 그대로 두므로 순서·개수는 그대로다.
                    missingCore.stream().map(IngredientProcessingUtil::toSearchKeyword).toList(),
                    MISSING_PRODUCTS_LIMIT_PER_INGREDIENT);

            // 검색 결과는 요청한 키워드 순서와 1:1로 정렬되어 돌아온다 — 길이가 다르면 ProductSearchService가
            // 전부 빈 목록으로 맞춰 준다(배치 안 쿼리 하나가 실패하면 순서를 알 수 없어서다).
            // 검색 결과가 곧 응답 항목이다 — 2026-09-09까지는 같은 모양의 타입 둘을 오가느라
            // 필드를 하나씩 옮겨 담는 반복문이 있었다.
            List<IngredientRecommendResponse> recommendations = new ArrayList<>();
            for (int i = 0; i < missingCore.size(); i++) {
                List<ProductSummaryResponse> products = resultsPerIngredient.get(i);
                if (!products.isEmpty()) {
                    recommendations.add(new IngredientRecommendResponse(missingCore.get(i), products));
                }
            }
            return recommendations;
        } catch (Exception e) {
            log.error("ES 부족 재료 상품 검색 실패. ingredients={}, message={}", missingCore, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 장바구니 상품 ID 목록으로 Elasticsearch에서 상품 문서를 한 번에 일괄 조회하고,
     * 각 문서에 미리 저장된 표준 재료명(ingredientName)을 추출하여 중복 없이 반환합니다.
     */
    private List<String> loadOwnedIngredientNames(List<CartItemInfo> items, String userIdentifier) {
        // [1단계: 중복 제거된 상품 ID 목록 수집]
        List<UUID> productIds = items.stream()
            .map(CartItemInfo::productId)
            .distinct()
            .toList();

        // [2단계: Elasticsearch 상품 문서 일괄 조회]
        // 상품 인덱스 질의는 ProductSearchService가 맡는다 — 벡터를 빼고 받는 것도 그쪽이 처리한다.
        List<ProductDocument> documents = productSearchService.findByIds(productIds);

        List<String> ingredientNames = new ArrayList<>();
        for (ProductDocument document : documents) {
            // 재료명 문자열 정규화 (공백 제거, 소문자화)
            String ingredientName =
                IngredientProcessingUtil.normalizeForCompare(document.getIngredientName());
            if (!ingredientName.isBlank()) {
                ingredientNames.add(ingredientName);
            }
        }

        // [3단계: 재료명 누락 상품 건수 로깅]
        // 색인 문서가 없거나 재료명이 비어있는 상품이 있다면 재색인이 필요함을 경고 로그로 기록합니다.
        int skipped = productIds.size() - ingredientNames.size();
        if (skipped > 0) {
            log.warn("보유 재료로 세지 못한 상품 {}건 (조회된 문서 {}/{}건). 상품 재색인이 필요할 수 있습니다. userId: {}",
                skipped, documents.size(), productIds.size(), userIdentifier);
        }

        // 중복 재료명을 제거한 최종 고유 재료 목록 반환
        return ingredientNames.stream().distinct().toList();
    }

}
