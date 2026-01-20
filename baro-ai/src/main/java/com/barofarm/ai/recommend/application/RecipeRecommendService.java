package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.response.IngredientRecommendResponse;
import com.barofarm.ai.recommend.application.dto.response.PersonalRecommendResponse;
import com.barofarm.ai.recommend.application.dto.response.RecipeRecommendResponse;
import com.barofarm.ai.recommend.client.CartClient;
import com.barofarm.ai.recommend.client.dto.CartInfo;
import com.barofarm.ai.search.application.ProductSearchService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    private static final int MISSING_PRODUCTS_LIMIT_PER_INGREDIENT = 2;

    private final CartClient cartClient;
    private final ChatClient chatClient;
    private final ProductSearchService productSearchService;

    /**
     * 장바구니 기반 레시피 추천 + 빠진 핵심 재료는 ES로 상품 추천
     */
    public RecipeRecommendResponse recommendFromCartWithMissing(UUID userId) {
        CartInfo cart = cartClient.getCart(userId);

        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            log.warn("장바구니가 비어있습니다. userId: {}", userId);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        // 0) FRUIT 카테고리 아이템은 레시피 입력에서 제외
        List<CartItemInput> cartItems = cart.items().stream()
            .filter(Objects::nonNull)
            .map(i -> new CartItemInput(
                safeTrim(i.productName()),
                safeTrim(i.productCategoryName())
            ))
            .filter(i -> !i.productName().isBlank())
            .filter(i -> !isFruitCategory(i.categoryName()))
            .toList();

        if (cartItems.isEmpty()) {
            log.warn("유효한 장바구니 상품이 없습니다(또는 FRUIT만 존재). userId: {}", userId);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        Set<String> originalProductNames = cartItems.stream()
            .map(CartItemInput::productName)
            .collect(Collectors.toSet());

        OwnedIngredientsResponse owned = extractOwnedIngredients(cartItems);

        List<String> ownedNames = owned.ownedIngredients() == null
            ? List.of()
            : owned.ownedIngredients().stream()
                .filter(oi -> oi != null
                    && oi.name() != null
                    && !oi.name().isBlank()
                    && oi.sourceProductName() != null
                    && originalProductNames.contains(oi.sourceProductName()))
                .map(OwnedIngredient::name)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (ownedNames.isEmpty()) {
            log.warn("보유 재료 추출 실패/빈 결과. userId: {}", userId);
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        // 1) 후보 3개(한식/일식/양식) 생성 후, 서버에서 1개 선택
        RecipeCandidates candidates = generateRecipeCandidates(ownedNames);
        CandidateRecipePlan plan = chooseBestCandidate(candidates, ownedNames);

        if (plan == null || plan.recipeName() == null || plan.recipeName().isBlank()) {
            log.warn("레시피 후보 생성/선택 실패. userId: {}", userId);
            return new RecipeRecommendResponse("", ownedNames, List.of(), List.of(), "");
        }

        List<String> core = normalizeList(plan.recipeIngredientsCore());
        List<String> missingCore = subtractNormalized(core, ownedNames);

        List<IngredientRecommendResponse> missingRecommendations =
            missingCore.stream()
                .map(ingredient -> new IngredientRecommendResponse(
                    ingredient,
                    searchProductsByIngredientName(ingredient, MISSING_PRODUCTS_LIMIT_PER_INGREDIENT)
                ))
                .filter(r -> r.products() != null && !r.products().isEmpty())
                .toList();

        return new RecipeRecommendResponse(
            plan.recipeName(),
            ownedNames,
            missingCore,
            missingRecommendations,
            Optional.ofNullable(plan.instructions()).orElse("")
        );
    }

    /**
     * 장바구니 기반 레시피 추천 + 빠진 핵심 재료는 ES로 상품 추천 (테스트용)
     * Feign 통신 없이 직접 CartInfo를 입력받아 레시피 추천
     */
    public RecipeRecommendResponse testRecommendFromCartWithMissing(CartInfo cart) {
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            log.warn("장바구니가 비어있습니다.");
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        // 0) FRUIT 카테고리 아이템은 레시피 입력에서 제외
        List<CartItemInput> cartItems = cart.items().stream()
            .filter(Objects::nonNull)
            .map(i -> new CartItemInput(
                safeTrim(i.productName()),
                safeTrim(i.productCategoryName())
            ))
            .filter(i -> !i.productName().isBlank())
            .filter(i -> !isFruitCategory(i.categoryName()))
            .toList();

        if (cartItems.isEmpty()) {
            log.warn("유효한 장바구니 상품이 없습니다(또는 FRUIT만 존재).");
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        Set<String> originalProductNames = cartItems.stream()
            .map(CartItemInput::productName)
            .collect(Collectors.toSet());

        OwnedIngredientsResponse owned = extractOwnedIngredients(cartItems);

        List<String> ownedNames = owned.ownedIngredients() == null
            ? List.of()
            : owned.ownedIngredients().stream()
                .filter(oi -> oi != null
                    && oi.name() != null
                    && !oi.name().isBlank()
                    && oi.sourceProductName() != null
                    && originalProductNames.contains(oi.sourceProductName()))
                .map(OwnedIngredient::name)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (ownedNames.isEmpty()) {
            log.warn("보유 재료 추출 실패/빈 결과.");
            return new RecipeRecommendResponse("", List.of(), List.of(), List.of(), "");
        }

        RecipeCandidates candidates = generateRecipeCandidates(ownedNames);
        CandidateRecipePlan plan = chooseBestCandidate(candidates, ownedNames);

        if (plan == null || plan.recipeName() == null || plan.recipeName().isBlank()) {
            log.warn("레시피 후보 생성/선택 실패.");
            return new RecipeRecommendResponse("", ownedNames, List.of(), List.of(), "");
        }

        List<String> core = normalizeList(plan.recipeIngredientsCore());
        List<String> missingCore = subtractNormalized(core, ownedNames);

        List<IngredientRecommendResponse> missingRecommendations =
            missingCore.stream()
                .map(ingredient -> new IngredientRecommendResponse(
                    ingredient,
                    searchProductsByIngredientName(ingredient, MISSING_PRODUCTS_LIMIT_PER_INGREDIENT)
                ))
                .filter(r -> r.products() != null && !r.products().isEmpty())
                .toList();

        return new RecipeRecommendResponse(
            plan.recipeName(),
            ownedNames,
            missingCore,
            missingRecommendations,
            Optional.ofNullable(plan.instructions()).orElse("")
        );
    }

    // -------------------------
    // LLM #1: cartItems -> ownedIngredients
    // -------------------------
    private OwnedIngredientsResponse extractOwnedIngredients(List<CartItemInput> cartItems) {
        String cartItemsText = cartItems.stream()
            .map(i -> "- productName: \"" + i.productName() + "\", category: \"" + i.categoryName() + "\"")
            .collect(Collectors.joining("\n"));

        String templateString = """
            당신은 농산물 이커머스 장바구니를 분석해 실제 요리에 쓰일 수 있는 '보유 재료'만 추출합니다.

            <입력(cartItems)>
            {cartItems}

            <중요 정책>
            - category가 FRUIT(과일)인 항목은 레시피 추천 재료에서 제외합니다. 과일은 추출하지 마세요.

            <핵심 규칙(엄격)>
            - 반드시 입력 cartItems의 productName 문자열에서 직접 근거를 찾을 수 있는 재료만 추출하세요.
            - 입력에 없는 재료(예: 양파, 마늘 등)를 상상으로 추가하면 안 됩니다.
            - 브랜드/산지/수량/단위/포장/세트/용기 표현은 제거하고 재료명만 남기세요.
            - 각 재료명은 한 단어(공백 없음)만 허용합니다.
            - 중복은 제거하세요.
            - 모호하면 제외하세요(추측 금지).

            <출력(JSON only)>
            {{
              "ownedIngredients": [
                {{
                  "name": "한단어재료명",
                  "sourceProductName": "입력에 존재하는 productName 원문 그대로",
                  "sourceCategoryName": "입력에 존재하는 category 원문 그대로"
                }}
              ]
            }}
            다른 텍스트는 절대 출력하지 마세요.
            """;

        try {
            OwnedIngredientsResponse response = chatClient.prompt()
                .user(p -> p.text(templateString).param("cartItems", cartItemsText))
                .call()
                .entity(new ParameterizedTypeReference<OwnedIngredientsResponse>() {});
            return response != null ? response : new OwnedIngredientsResponse(List.of());
        } catch (Exception e) {
            log.error("보유 재료 추출 실패: {}", e.getMessage(), e);
            return new OwnedIngredientsResponse(List.of());
        }
    }

    // -------------------------
    // LLM #2: ownedIngredients -> candidates (Korean/Japanese/Western)
    // -------------------------
    private RecipeCandidates generateRecipeCandidates(List<String> ownedIngredients) {
        String ownedText = ownedIngredients.stream()
            .map(s -> "- " + s)
            .collect(Collectors.joining("\n"));

        String templateString = """
            당신은 '집밥 레시피 추천 AI'입니다.
            아래 보유 재료로 만들 수 있는 요리 후보 3개를 추천합니다.

            <보유 재료(ownedIngredients)>
            {ownedIngredients}

            <정책>
            - FRUIT(과일)은 레시피에 사용하지 않습니다. (입력에 없다고 가정)
            - 기본 양념/물/기름 등은 집에 있다고 가정합니다.
              staples: 물, 소금, 후추, 식용유, 설탕, 식초, 간장
            - staples는 재료 목록에 포함하지 마세요(=core/extra에 넣지 마세요).

            <후보 생성 규칙>
            - 정확히 3개 후보를 생성하세요.
            - 각 후보의 cuisine은 서로 달라야 하며, 아래 셋을 각각 1번씩 사용:
              "KOREAN", "JAPANESE", "WESTERN"
            - 세 후보는 서로 최대한 다른 스타일의 요리여야 합니다(다양성).
            - 보유 재료 중 최소 1개는 각 후보의 recipeIngredientsCore에 반드시 포함하세요.
            - 억지 조합 금지: 서로 어울리지 않으면 재료를 과감히 제외하세요.

            <출력 필드 정의>
            - recipeIngredientsCore: 요리의 정체성을 결정하는 핵심 재료(최대 5개)
              * 한 단어, 중복 금지
            - recipeIngredientsExtra: 있으면 더 좋은 추가 재료(최대 5개)
              * 한 단어, 중복 금지
            - instructions: 3~6 단계, 간단 명료

            <출력(JSON only)>
            {{
              "candidates": [
                {{
                  "cuisine": "KOREAN|JAPANESE|WESTERN",
                  "recipeName": "요리명",
                  "recipeIngredientsCore": ["..."],
                  "recipeIngredientsExtra": ["..."],
                  "instructions": "1) ...\\n2) ...",
                  "staplesAssumed": true
                }}
              ]
            }}
            다른 텍스트는 절대 출력하지 마세요.
            """;

        try {
            RecipeCandidates result = chatClient.prompt()
                .user(p -> p.text(templateString).param("ownedIngredients", ownedText))
                .call()
                .entity(new ParameterizedTypeReference<RecipeCandidates>() {});

            if (result != null && result.candidates() != null) {
                log.info("레시피 후보 생성 완료. 후보 개수: {}", result.candidates().size());
                result.candidates().forEach(candidate ->
                    log.info("  - 후보: {} ({}), core: {}, extra: {}",
                        candidate.recipeName(),
                        candidate.cuisine(),
                        candidate.recipeIngredientsCore(),
                        candidate.recipeIngredientsExtra())
                );
            }

            return result != null ? result : new RecipeCandidates(List.of());
        } catch (Exception e) {
            log.error("레시피 후보 생성 실패: {}", e.getMessage(), e);
            return new RecipeCandidates(List.of());
        }
    }

    private CandidateRecipePlan chooseBestCandidate(RecipeCandidates candidates, List<String> ownedNames) {
        if (candidates == null || candidates.candidates() == null || candidates.candidates().isEmpty()) {
            return null;
        }

        Set<String> ownedNorm = (ownedNames == null ? List.<String>of() : ownedNames).stream()
            .map(RecipeRecommendService::normalizeForCompare)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

        log.info("레시피 후보 평가 시작. 보유 재료: {}", ownedNames);

        CandidateRecipePlan best = candidates.candidates().stream()
            .filter(c -> c != null && c.recipeName() != null && !c.recipeName().isBlank())
            .peek(c -> {
                int score = scoreCandidate(c, ownedNorm);
                List<String> core = normalizeList(c.recipeIngredientsCore());
                List<String> missing = subtractNormalized(core, ownedNames);
                log.info("  - 후보 평가: {} ({}), 점수: {}, 보유: {}, 부족: {}",
                    c.recipeName(),
                    c.cuisine(),
                    score,
                    core.stream().filter(ing -> ownedNorm.contains(normalizeForCompare(ing))).toList(),
                    missing);
            })
            .max(Comparator.comparingInt(c -> scoreCandidate(c, ownedNorm)))
            .orElse(null);

        if (best != null) {
            log.info("최종 선택된 레시피: {} ({})", best.recipeName(), best.cuisine());
        } else {
            log.warn("선택된 레시피 후보가 없습니다.");
        }

        return best;
    }

    private int scoreCandidate(CandidateRecipePlan c, Set<String> ownedNorm) {
        List<String> core = normalizeList(c.recipeIngredientsCore());
        int overlap = (int) core.stream()
            .map(RecipeRecommendService::normalizeForCompare)
            .filter(ownedNorm::contains)
            .count();

        int missing = (int) core.stream()
            .map(RecipeRecommendService::normalizeForCompare)
            .filter(s -> !s.isBlank())
            .filter(s -> !ownedNorm.contains(s))
            .count();

        // 많이 가지고 있는 재료를 쓰는 후보 우선 + missing 적은 후보 우선
        return overlap * 10 - missing * 2;
    }

    // -------------------------
    // ES: missing core ingredient -> products (top N)
    // -------------------------
    private List<PersonalRecommendResponse> searchProductsByIngredientName(String ingredientName, int size) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return List.of();
        }

        try {
            Pageable pageable = PageRequest.of(0, size);
            // userId는 null로 전달하여 검색 로그 저장하지 않음
            var searchResult = productSearchService.searchProducts(null, ingredientName, pageable);

            return searchResult.content().stream()
                .map(product -> new PersonalRecommendResponse(
                    product.productId(),
                    product.productName(),
                    product.productCategory(),
                    product.price()
                ))
                .limit(size)
                .toList();

        } catch (Exception e) {
            log.error("ES 재료 상품 검색 실패. ingredientName={}, message={}", ingredientName, e.getMessage(), e);
            return List.of();
        }
    }

    // -------------------------
    // helpers
    // -------------------------
    private static boolean isFruitCategory(String categoryName) {
        if (categoryName == null) {
            return false;
        }
        return "FRUIT".equalsIgnoreCase(categoryName.trim());
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> normalizeList(List<String> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
    }

    private record CartItemInput(String productName, String categoryName) { }

    private record OwnedIngredientsResponse(List<OwnedIngredient> ownedIngredients) { }

    private record OwnedIngredient(
        String name,
        String sourceProductName,
        String sourceCategoryName
    ) { }

    private record RecipeCandidates(List<CandidateRecipePlan> candidates) { }

    private record CandidateRecipePlan(
        String cuisine,
        String recipeName,
        List<String> recipeIngredientsCore,
        List<String> recipeIngredientsExtra,
        String instructions,
        boolean staplesAssumed
    ) { }

    // TODO: 정규화 부족
    private static String normalizeForCompare(String s) {
        if (s == null) {
            return "";
        }
        return s.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "")
            .replaceAll("[()\\[\\]{}]", "")
            .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHangul}]", "");
    }

    private static List<String> subtractNormalized(List<String> a, List<String> b) {
        Set<String> bNorm = (b == null ? List.<String>of() : b).stream()
            .map(RecipeRecommendService::normalizeForCompare)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

        return normalizeList(a).stream()
            .filter(x -> !bNorm.contains(normalizeForCompare(x)))
            .toList();
    }
}
