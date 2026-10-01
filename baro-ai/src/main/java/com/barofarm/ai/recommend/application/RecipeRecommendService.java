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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    private static final int MISSING_PRODUCTS_LIMIT_PER_INGREDIENT = 2;

    private final CartClient cartClient;
    private final ProductSearchService productSearchService;
    private final RecipeCandidateGenerator recipeCandidateGenerator;
    private final RecommendProperties recommendProperties;
    private final ProductSearchRepository productSearchRepository;

    public RecipeRecommendResponse recommendFromCartWithMissing(UUID userId) {
        try {
            CartInfo cart = cartClient.getCart(userId);
            return recommendFromCart(cart, userId != null ? userId.toString() : "null");
        } catch (Exception e) {
            // AI 서비스는 필수가 아니므로 장바구니 조회 실패 시 빈 응답 반환
            log.warn("장바구니 조회 실패로 레시피 추천을 건너뜁니다. userId: {}, error: {}", userId, e.getMessage());
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }
    }

    public RecipeRecommendResponse testRecommendFromCartWithMissing(CartInfo cart) {
        return recommendFromCart(cart, "test-user");
    }

    private RecipeRecommendResponse recommendFromCart(CartInfo cart, String userIdentifier) {
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            log.warn("장바구니가 비어있습니다. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

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

        // 보유 재료는 색인 시점에 미리 계산해 둔 ProductDocument.ingredientName을 그대로 읽는다.
        // 예전에는 이 자리에서 상품명 정규화와 재료 추출을 각각 LLM으로 호출했는데, 상품이 바뀔 때만
        // 달라지는 값을 추천 요청마다 다시 계산하던 셈이라 색인 시점으로 옮겼다.
        // 근거: docs/decisions/002-ingredient-name-at-index-time.md
        List<String> ownedNames = loadOwnedIngredientNames(eligibleItems, userIdentifier);

        if (ownedNames.isEmpty()) {
            log.warn("색인된 재료명이 없어 레시피 추천을 건너뜁니다. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        RecipeCandidates candidates = recipeCandidateGenerator.generateRecipeCandidates(ownedNames);
        log.info("레시피 후보 평가 시작. 보유 재료: {}", ownedNames);

        Optional<CandidateRecipePlan> bestCandidateOpt = candidates.chooseBest(ownedNames);

        if (bestCandidateOpt.isEmpty()) {
            log.warn("레시피 후보 생성/선택 실패. userId: {}", userIdentifier);
            return new RecipeRecommendResponse("", ownedNames, List.of(), List.of(), "");
        }

        CandidateRecipePlan plan = bestCandidateOpt.get();
        log.info("최종 선택된 레시피: {} ({}/{}/{})", plan.getRecipeName(),
            plan.getCookingMethod(), plan.getDifficulty(), plan.getMealType());

        // 부족한 재료는 RecipeCandidateGenerator가 서버 계산으로 이미 채워 둔 값을 그대로 쓴다.
        // 예전에는 같은 계산을 여기서 한 번 더 했는데, 두 계산의 비교 기준이 달라 결과가 갈릴 수 있었다.
        List<String> missingCore = IngredientProcessingUtil.normalizeList(plan.getMissingIngredientsCore());

        List<IngredientRecommendResponse> missingRecommendations =
            missingCore.stream()
                .map(ingredient -> new IngredientRecommendResponse(
                    ingredient,
                    searchProductsByIngredientName(ingredient,
                        MISSING_PRODUCTS_LIMIT_PER_INGREDIENT)
                ))
                .filter(r -> r.products() != null && !r.products().isEmpty())
                .toList();

        return new RecipeRecommendResponse(
            plan.getRecipeName(),
            ownedNames,
            missingCore,
            missingRecommendations,
            Optional.ofNullable(plan.getInstructions()).orElse("")
        );
    }

    private List<ProductRecommendResponse> searchProductsByIngredientName(String ingredientName,
        int size) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return List.of();
        }

        try {
            Pageable pageable = PageRequest.of(0, size);
            var searchResult = productSearchService.search(
                null,
                new ProductSearchRequest(ingredientName, null, null, null),
                pageable
            );

            return searchResult.content().stream()
                .map(product -> new ProductRecommendResponse(
                    product.productId(),
                    product.productName(),
                    product.productCategoryName(),
                    product.price()
                ))
                .limit(size)
                .toList();

        } catch (Exception e) {
            log.error("ES 재료 상품 검색 실패. ingredientName={}, message={}", ingredientName,
                e.getMessage(), e);
            return List.of();
        }
    }

    // 카테고리 코드가 레시피 제외 목록에 들어 있으면 그 상품은 추천 재료에서 뺀다.
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

    // 장바구니 상품들의 재료명을 Elasticsearch에서 한 번에 읽어 온다. 상품 문서 조회 한 번으로
    // 끝나므로 장바구니에 상품이 몇 개든 외부 호출 횟수는 늘지 않는다.
    // 재료명이 비어 있는 상품은 아직 이 필드가 없는 문서이므로 건너뛰고, 몇 건이 빠졌는지 로그로 남긴다.
    private List<String> loadOwnedIngredientNames(List<CartItemInfo> items, String userIdentifier) {
        List<UUID> productIds = items.stream()
            .map(CartItemInfo::productId)
            .distinct()
            .toList();

        List<String> ingredientNames = new ArrayList<>();
        int found = 0;
        for (ProductDocument document : productSearchRepository.findAllById(productIds)) {
            found++;
            String ingredientName = IngredientProcessingUtil.normalizeForCompare(document.getIngredientName());
            if (!ingredientName.isBlank()) {
                ingredientNames.add(ingredientName);
            }
        }

        // 재료명이 빈 문서뿐 아니라, 인덱스에 문서 자체가 없는 상품도 함께 센다.
        // 문서가 없는 쪽이 재색인이 필요한 더 흔한 신호인데 조회 결과에는 나타나지 않는다.
        int skipped = productIds.size() - ingredientNames.size();
        if (skipped > 0) {
            log.warn("보유 재료로 세지 못한 상품 {}건 (조회된 문서 {}/{}건). 상품 재색인이 필요할 수 있습니다. userId: {}",
                skipped, found, productIds.size(), userIdentifier);
        }
        return ingredientNames.stream().distinct().toList();
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
