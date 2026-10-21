package com.barofarm.ai.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barofarm.ai.recommend.application.dto.RecipeRecommendReason;
import com.barofarm.ai.recommend.application.dto.RecipeRecommendResponse;
import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import com.barofarm.ai.recommend.infrastructure.client.CartClient;
import com.barofarm.ai.recommend.infrastructure.client.dto.CartInfo;
import com.barofarm.ai.recommend.infrastructure.client.dto.CartItemInfo;
import com.barofarm.ai.recommend.infrastructure.llm.RecipeCandidateGenerator;
import com.barofarm.ai.search.application.ProductSearchService;
import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.dto.ResponseDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레시피를 만들지 못한 5가지 상황이 각각 다른 이유 코드로 나가는지, 정상 경로에서 부족 재료 상품 조회가
 * 한 번의 multi-search로 끝나는지 고정한다. 외부 의존(장바구니 Feign·ES·LLM)은 전부 mock이다.
 */
class RecipeRecommendServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private CartClient cartClient;
    private ProductSearchService productSearchService;
    private RecipeCandidateGenerator generator;
    private RecipeRecommendService service;

    @BeforeEach
    void setUp() {
        cartClient = mock(CartClient.class);
        productSearchService = mock(ProductSearchService.class);
        generator = mock(RecipeCandidateGenerator.class);

        service = new RecipeRecommendService(cartClient, productSearchService, generator,
            new RecommendationMetrics(new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("장바구니 서비스 호출이 실패하면 UPSTREAM_ERROR — 사용자 상태가 아니라 외부 장애다")
    void upstreamFailure_isUpstreamError() {
        when(cartClient.getCart(USER_ID)).thenThrow(new RuntimeException("connection refused"));

        RecipeRecommendResponse response = service.recommendFromCartWithMissing(USER_ID);

        assertThat(response.reason()).isEqualTo(RecipeRecommendReason.UPSTREAM_ERROR);
        assertThat(response.recipeName()).isEmpty();
        verify(generator, never()).generateRecipe(anyList());
    }

    @Test
    @DisplayName("장바구니가 없거나 비어 있으면 EMPTY_CART")
    void emptyCart_isEmptyCart() {
        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(new CartInfo(List.of())));

        assertThat(service.recommendFromCartWithMissing(USER_ID).reason())
            .isEqualTo(RecipeRecommendReason.EMPTY_CART);

        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(null));

        assertThat(service.recommendFromCartWithMissing(USER_ID).reason())
            .isEqualTo(RecipeRecommendReason.EMPTY_CART);
    }

    @Test
    @DisplayName("상품 식별자가 없는 항목뿐이면 NO_ELIGIBLE_ITEMS — 조회할 대상이 없다")
    void noIdentifiableItems_isNoEligibleItems() {
        // 2026-09-09까지는 "제외 카테고리" 필터가 이 이유 코드를 내는 주된 경로였는데,
        // 그 목록이 계속 비어 있어 죽은 분기였다. 지금 남은 도달 경로는 이것 하나다.
        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(new CartInfo(List.of(
            new CartItemInfo(null, "식별자 없는 상품")))));

        assertThat(service.recommendFromCartWithMissing(USER_ID).reason())
            .isEqualTo(RecipeRecommendReason.NO_ELIGIBLE_ITEMS);
        verify(productSearchService, never()).findByIds(anyList());
    }

    @Test
    @DisplayName("상품 문서에 재료명이 없으면 INGREDIENTS_NOT_INDEXED — 재색인이 필요하다는 신호")
    void noIngredientName_isNotIndexed() {
        UUID productId = UUID.randomUUID();
        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(new CartInfo(List.of(
            new CartItemInfo(productId, "곰곰 대란 30구")))));
        stubOwnedDocuments(document(productId, ""));

        assertThat(service.recommendFromCartWithMissing(USER_ID).reason())
            .isEqualTo(RecipeRecommendReason.INGREDIENTS_NOT_INDEXED);
        verify(generator, never()).generateRecipe(anyList());
    }

    @Test
    @DisplayName("LLM이 레시피를 못 만들면 GENERATION_FAILED — 보유 재료는 채워서 돌려준다")
    void generationFailed_keepsOwnedIngredients() {
        UUID egg = UUID.randomUUID();
        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(new CartInfo(List.of(
            new CartItemInfo(egg, "곰곰 대란 30구")))));
        stubOwnedDocuments(document(egg, "계란"));
        when(generator.generateRecipe(anyList())).thenReturn(Optional.empty());

        RecipeRecommendResponse response = service.recommendFromCartWithMissing(USER_ID);

        assertThat(response.reason()).isEqualTo(RecipeRecommendReason.GENERATION_FAILED);
        assertThat(response.ownedIngredients()).containsExactly("계란");
        verify(productSearchService, never()).searchByKeywords(anyList(), anyInt());
    }

    @Test
    @DisplayName("정상 경로 — OK, 부족 재료 전부를 한 번의 multi-search로 조회하고 상품이 있는 재료만 카드가 된다")
    void happyPath_searchesMissingIngredientsOnce() {
        UUID egg = UUID.randomUUID();
        UUID zucchini = UUID.randomUUID();
        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(new CartInfo(List.of(
            new CartItemInfo(egg, "곰곰 대란 30구"),
            new CartItemInfo(zucchini, "애호박 1개")))));
        stubOwnedDocuments(document(egg, "계란"), document(zucchini, "애호박"));

        when(generator.generateRecipe(List.of("계란", "애호박"))).thenReturn(Optional.of(new CandidateRecipePlan(
            "애호박 계란국", List.of("계란", "애호박", "대파", "새우젓"), "1. 끓인다",
            List.of("계란", "애호박"), List.of("대파", "새우젓"))));

        ProductSummaryResponse scallion = new ProductSummaryResponse(UUID.randomUUID(), "대파 1단", "채소", 2000L);
        when(productSearchService.searchByKeywords(List.of("대파", "새우젓"), 2))
            .thenReturn(List.of(List.of(scallion), List.of()));

        RecipeRecommendResponse response = service.recommendFromCartWithMissing(USER_ID);

        assertThat(response.reason()).isEqualTo(RecipeRecommendReason.OK);
        assertThat(response.recipeName()).isEqualTo("애호박 계란국");
        assertThat(response.ownedIngredients()).containsExactly("계란", "애호박");
        assertThat(response.missingCoreIngredients()).containsExactly("대파", "새우젓");
        // 새우젓은 상품이 0건이라 카드가 빠지되 부족 재료 목록에는 남는다
        assertThat(response.missingRecommendations()).hasSize(1);
        assertThat(response.missingRecommendations().get(0).ingredientName()).isEqualTo("대파");
        assertThat(response.missingRecommendations().get(0).products().get(0).productName()).isEqualTo("대파 1단");
        verify(productSearchService, times(1)).searchByKeywords(anyList(), anyInt());
    }

    @Test
    @DisplayName("부족 재료 상품 검색이 실패해도 레시피 본문은 살리고 카드만 비운다")
    void productSearchFailure_keepsRecipe() {
        UUID egg = UUID.randomUUID();
        when(cartClient.getCart(USER_ID)).thenReturn(ResponseDto.ok(new CartInfo(List.of(
            new CartItemInfo(egg, "곰곰 대란 30구")))));
        stubOwnedDocuments(document(egg, "계란"));
        when(generator.generateRecipe(anyList())).thenReturn(Optional.of(new CandidateRecipePlan(
            "계란찜", List.of("계란", "대파"), "1. 찐다", List.of("계란"), List.of("대파"))));
        when(productSearchService.searchByKeywords(anyList(), anyInt())).thenThrow(new RuntimeException("es down"));

        RecipeRecommendResponse response = service.recommendFromCartWithMissing(USER_ID);

        assertThat(response.reason()).isEqualTo(RecipeRecommendReason.OK);
        assertThat(response.recipeName()).isEqualTo("계란찜");
        assertThat(response.missingCoreIngredients()).containsExactly("대파");
        assertThat(response.missingRecommendations()).isEmpty();
    }

    // ---- helpers ----

    private static ProductDocument document(UUID productId, String ingredientName) {
        return new ProductDocument(productId, "상품", null, "AGRI_X", "카테고리", 1000L, "ON_SALE",
            null, ingredientName, null);
    }

    private void stubOwnedDocuments(ProductDocument... documents) {
        when(productSearchService.findByIds(anyList())).thenReturn(List.of(documents));
    }
}
