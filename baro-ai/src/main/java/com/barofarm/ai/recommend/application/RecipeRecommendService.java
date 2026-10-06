package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.config.RecommendProperties;
import com.barofarm.ai.recommend.application.dto.IngredientRecommendResponse;
import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.recommend.application.dto.RecipeRecommendResponse;
import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import com.barofarm.ai.recommend.domain.IngredientProcessingUtil;
import com.barofarm.ai.recommend.domain.RecipeCandidates;
import com.barofarm.ai.recommend.infrastructure.client.CartClient;
import com.barofarm.ai.recommend.infrastructure.client.dto.CartInfo;
import com.barofarm.ai.recommend.infrastructure.client.dto.CartItemInfo;
import com.barofarm.ai.recommend.infrastructure.llm.RecipeCandidateGenerator;
import com.barofarm.ai.search.application.ProductSearchService;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
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
 * 2. 가공식품, 생필품 등 요리와 무관한 카테고리 상품을 필터링하여 제외합니다.
 * 3. Elasticsearch에 사전 색인된 상품 문서의 재료명(ProductDocument.ingredientName)을 일괄 조회하여 보유 재료 목록을 도출합니다.
 *    (요청 시점마다 LLM으로 재료를 파싱하지 않고 색인 시점에 미리 정규화된 재료명을 읽어 지연 시간과 비용을 획기적으로 절감)
 * 4. RecipeCandidateGenerator를 통해 LLM 기반으로 현재 보유 재료로 조리 가능한 최적의 레시피 후보를 생성합니다.
 * 5. 생성된 레시피에 필요하지만 장바구니에 없는 '부족한 재료'를 Elasticsearch 키워드 검색으로 찾아 구매 후보 상품으로 매핑합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    /** 부족한 식재료 1개당 추천하여 함께 노출할 연관 상품 최대 개수 (현재 2건) */
    private static final int MISSING_PRODUCTS_LIMIT_PER_INGREDIENT = 2;

    /** baro-cart 서비스와 통신하여 사용자의 장바구니 품목을 조회하는 Feign 클라이언트 */
    private final CartClient cartClient;

    /** 부족한 재료명을 키워드로 입력받아 실제 판매 중인 상품을 Elasticsearch에서 검색하는 서비스 */
    private final ProductSearchService productSearchService;

    /** Gemini LLM 구조화 출력을 활용해 식재료 기반 레시피 후보를 생성하는 제너레이터 */
    private final RecipeCandidateGenerator recipeCandidateGenerator;

    /** 레시피 추천에서 제외할 카테고리 코드 목록 등을 관리하는 설정 프로퍼티 */
    private final RecommendProperties recommendProperties;

    /** 장바구니 상품 ID 목록으로 Elasticsearch 상품 문서를 일괄 조회하여 ingredientName을 읽어오는 레포지토리 */
    private final ProductSearchRepository productSearchRepository;

    /**
     * 특정 사용자의 장바구니 상품을 기반으로 레시피 및 부족한 재료 연관 상품을 추천합니다.
     *
     * @param userId 추천을 요청한 사용자의 고유 UUID
     * @return 레시피 추천 응답 DTO (장바구니 비어있음 또는 오류 발생 시 안전한 빈 응답 반환)
     */
    public RecipeRecommendResponse recommendFromCartWithMissing(UUID userId) {
        // [1단계: 장바구니 Feign 클라이언트 호출 및 예외 격리]
        // 장바구니 서비스와의 네트워크 오류 또는 장바구니 미생성 시에도 프론트엔드 오류를 방지하기 위해
        // 전체 과정을 try-catch로 감싸고, 실패 시 조용히 빈 응답(Empty Response)을 반환합니다.
        try {
            ResponseDto<CartInfo> response = cartClient.getCart(userId);
            CartInfo cart = response != null ? response.data() : null;
            return recommendFromCart(cart, userId.toString());
        } catch (Exception e) {
            log.warn("레시피 추천 실패(장바구니 조회·재료 조회·후보 생성 중 예외). userId: {}, error: {}",
                userId, e.getMessage());
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }
    }

    /**
     * 장바구니 정보(CartInfo)로부터 레시피 후보를 생성하고 부족한 식재료를 검색하는 내부 추천 메서드.
     */
    private RecipeRecommendResponse recommendFromCart(CartInfo cart, String userIdentifier) {
        // [1단계: 장바구니 데이터 유효성 검증]
        // 장바구니 객체가 null이거나 담긴 상품이 없는 경우 레시피 생성이 불가하므로 빈 응답을 반환합니다.
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            log.warn("장바구니가 비어있습니다. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        // [2단계: 비(非)식재료 카테고리 상품 필터링]
        // 주방용품, 가전 등 레시피 식재료로 부적합한 카테고리(설정 파일에 정의된 exclude-recipe-category-codes)를 제외합니다.
        final List<String> categoriesToExcludeByCode =
            recommendProperties.getExcludeRecipeCategoryCodes();

        List<CartItemInfo> eligibleItems = cart.items().stream()
            .filter(Objects::nonNull)
            .filter(item -> item.productId() != null)
            .filter(item -> isRecipeEligible(item, categoriesToExcludeByCode))
            .toList();

        if (eligibleItems.isEmpty()) {
            log.warn("유효한 장바구니 상품이 없습니다. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        // [3단계: Elasticsearch 사전 색인 재료명(ingredientName) 일괄 조회]
        // 매 추천 요청마다 LLM을 호출하여 상품명에서 재료를 파싱하던 구 방식을 폐기하고,
        // 상품 색인 시점에 미리 정제하여 저장해 둔 ProductDocument.ingredientName을 읽어와 즉각적인 응답 속도를 보장합니다.
        List<String> ownedNames = loadOwnedIngredientNames(eligibleItems, userIdentifier);

        if (ownedNames.isEmpty()) {
            log.warn("색인된 재료명이 없어 레시피 추천을 건너뜁니다. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        // [4단계: Gemini LLM 기반 레시피 후보군 생성 및 최적 1건 선택]
        // 보유 재료 목록을 전달하여 조리 가능한 요리 후보를 생성하고, 가장 적합한 첫 번째 레시피를 선택합니다.
        RecipeCandidates candidates = recipeCandidateGenerator.generateRecipeCandidates(ownedNames);

        Optional<CandidateRecipePlan> selectedOpt = candidates.chooseFirst();

        if (selectedOpt.isEmpty()) {
            log.warn("레시피 후보 생성/선택 실패. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", ownedNames, List.of(), List.of(), "");
        }

        CandidateRecipePlan plan = selectedOpt.get();
        log.info("최종 선택된 레시피: {} ({}/{}/{})", plan.recipeName(),
            plan.cookingMethod(), plan.difficulty(), plan.mealType());

        // [5단계: 부족한 핵심 식재료(missingIngredientsCore) 추출 및 연관 상품 검색]
        // RecipeCandidateGenerator가 서버 측에서 계산한 부족한 핵심 재료 목록을 읽어옵니다.
        List<String> missingCore = plan.missingIngredientsCore();

        // 각 부족한 재료명으로 Elasticsearch 키워드 검색을 수행하여 구매 가능한 상품 정보를 조회합니다.
        List<IngredientRecommendResponse> missingRecommendations =
            missingCore.stream()
                .map(ingredient -> new IngredientRecommendResponse(
                    ingredient,
                    searchProductsByIngredientName(ingredient,
                        MISSING_PRODUCTS_LIMIT_PER_INGREDIENT)
                ))
                .filter(r -> r.products() != null && !r.products().isEmpty())
                .toList();

        // [6단계: 최종 레시피 및 재료 구매 추천 응답 DTO 조립]
        return new RecipeRecommendResponse(
            plan.recipeName(),
            ownedNames,
            missingCore,
            missingRecommendations,
            Optional.ofNullable(plan.instructions()).orElse("")
        );
    }

    /**
     * 부족한 식재료명을 키워드로 전달하여 Elasticsearch에서 판매 중인 실제 상품을 검색합니다.
     */
    private List<ProductRecommendResponse> searchProductsByIngredientName(String ingredientName,
        int size) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return List.of();
        }

        try {
            // [검색 실행] 상품 검색 서비스를 통해 재료명 키워드 매칭 상품 조회
            var searchResult = productSearchService.search(
                null,
                new ProductSearchRequest(ingredientName, null, null, null),
                0,
                size
            );

            // [DTO 변환] 검색된 상품 목록을 추천 응답 규격으로 매핑
            return searchResult.content().stream()
                .map(product -> new ProductRecommendResponse(
                    product.productId(),
                    product.productName(),
                    product.productCategoryName(),
                    product.price()
                ))
                .toList();

        } catch (Exception e) {
            log.error("ES 재료 상품 검색 실패. ingredientName={}, message={}", ingredientName,
                e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 특정 장바구니 상품이 레시피 추천 식재료로 적합한지 카테고리 코드로 판정합니다.
     */
    private boolean isRecipeEligible(CartItemInfo item, List<String> excludedCategoryCodes) {
        if (excludedCategoryCodes == null || excludedCategoryCodes.isEmpty()) {
            return true;
        }
        String categoryCode = safeTrim(item.productCategoryCode());
        if (categoryCode.isEmpty() || !excludedCategoryCodes.contains(categoryCode)) {
            return true;
        }
        log.info("레시피 추천에서 '{}' 상품 제외 (카테고리 코드: {})", item.productName(), categoryCode);
        return false;
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

        // [2단계: Elasticsearch 상품 문서 일괄 조회 (Multi-Get)]
        List<String> ingredientNames = new ArrayList<>();
        int found = 0;
        for (ProductDocument document : productSearchRepository.findAllById(productIds)) {
            found++;
            // 재료명 문자열 정규화 (공백 제거, 소문자화)
            String ingredientName = IngredientProcessingUtil.normalizeForCompare(document.getIngredientName());
            if (!ingredientName.isBlank()) {
                ingredientNames.add(ingredientName);
            }
        }

        // [3단계: 재료명 누락 상품 건수 로깅]
        // 색인 문서가 없거나 재료명이 비어있는 상품이 있다면 재색인이 필요함을 경고 로그로 기록합니다.
        int skipped = productIds.size() - ingredientNames.size();
        if (skipped > 0) {
            log.warn("보유 재료로 세지 못한 상품 {}건 (조회된 문서 {}/{}건). 상품 재색인이 필요할 수 있습니다. userId: {}",
                skipped, found, productIds.size(), userIdentifier);
        }

        // 중복 재료명을 제거한 최종 고유 재료 목록 반환
        return ingredientNames.stream().distinct().toList();
    }

    /**
     * null-safe 문자열 trim 헬퍼 함수
     */
    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
