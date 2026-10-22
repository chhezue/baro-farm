package com.barofarm.ai.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barofarm.ai.embedding.application.ProductEmbeddingService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 묶음 색인이 임베딩 1회 · 저장 1회로 끝나는지, 벡터를 못 만든 상품도 색인에서 빠지지 않는지,
 * 그리고 **상품명이 그대로면 외부 AI를 다시 부르지 않는지**를 고정한다.
 *
 * 마지막 항목이 이 클래스의 핵심이다 — 가격이 100원 바뀔 때마다 임베딩과 재료명 추출을 다시 부르면
 * 색인 처리량과 Gemini 한도를 함께 갉아먹는다. 그 낭비가 다시 들어오지 않게 여기서 막는다.
 */
class ProductIndexServiceTest {

    private ProductSearchRepository repository;
    private ProductEmbeddingService embeddingService;
    private ProductIngredientNormalizer normalizer;
    private ProductIndexService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductSearchRepository.class);
        embeddingService = mock(ProductEmbeddingService.class);
        normalizer = mock(ProductIngredientNormalizer.class);
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findAllById(anyList())).thenReturn(List.of());
        when(normalizer.normalize(anyString(), any())).thenReturn("재료");
        service = new ProductIndexService(repository, embeddingService, normalizer, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("indexProducts — 상품 3개면 임베딩 1회 · 재료명 3회 · saveAll 1회")
    void indexProducts_batchesEmbeddingAndSaves() {
        List<ProductIndexRequest> requests = List.of(request("사과"), request("배"), request("감귤"));
        when(embeddingService.embedProducts(List.of("사과", "배", "감귤")))
            .thenReturn(List.of(new float[] {1f}, new float[] {2f}, new float[] {3f}));

        List<ProductDocument> saved = service.indexProducts(requests);

        verify(embeddingService, times(1)).embedProducts(anyList());
        verify(normalizer, times(3)).normalize(anyString(), any());
        verify(repository, times(1)).saveAll(anyList());
        assertThat(saved).hasSize(3);
        assertThat(saved.get(1).getVector()).containsExactly(2f);
        assertThat(saved.get(1).getIngredientName()).isEqualTo("재료");
    }

    @Test
    @DisplayName("indexProducts — 벡터를 못 만든 상품은 vector=null로 색인되고 나머지는 정상이다")
    void indexProducts_keepsProductWithoutVector() {
        List<ProductIndexRequest> requests = List.of(request("사과"), request("배"));
        when(embeddingService.embedProducts(anyList())).thenReturn(Arrays.asList(new float[] {1f}, null));

        List<ProductDocument> saved = service.indexProducts(requests);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getVector()).containsExactly(1f);
        assertThat(saved.get(1).getVector()).isNull();
        assertThat(saved.get(1).getProductName()).isEqualTo("배");
    }

    @Test
    @DisplayName("indexProducts — 상품명이 그대로면 임베딩·재료명을 다시 만들지 않고 색인된 값을 옮겨 쓴다")
    void indexProducts_reusesEnrichmentWhenNameUnchanged() {
        UUID productId = UUID.randomUUID();
        ProductIndexRequest priceChanged =
            new ProductIndexRequest(productId, "사과", null, "AGRI_X", "과일", 2000L, "ON_SALE", Instant.now());
        when(repository.findAllById(anyList())).thenReturn(List.of(
            indexed(productId, "사과", new float[] {9f}, "사과재료")));

        List<ProductDocument> saved = service.indexProducts(List.of(priceChanged));

        verify(embeddingService, never()).embedProducts(anyList());
        verify(normalizer, never()).normalize(anyString(), any());
        assertThat(saved.get(0).getVector()).containsExactly(9f);
        assertThat(saved.get(0).getIngredientName()).isEqualTo("사과재료");
        // 바뀐 값(가격)은 새 이벤트 것으로 들어간다
        assertThat(saved.get(0).getPrice()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("indexProducts — 상품명이 바뀌면 임베딩·재료명을 다시 만든다")
    void indexProducts_recomputesWhenNameChanged() {
        UUID productId = UUID.randomUUID();
        ProductIndexRequest renamed =
            new ProductIndexRequest(productId, "햇사과", null, "AGRI_X", "과일", 1000L, "ON_SALE", Instant.now());
        when(repository.findAllById(anyList())).thenReturn(List.of(
            indexed(productId, "사과", new float[] {9f}, "사과재료")));
        when(embeddingService.embedProducts(List.of("햇사과"))).thenReturn(List.of(new float[] {1f}));

        List<ProductDocument> saved = service.indexProducts(List.of(renamed));

        verify(embeddingService, times(1)).embedProducts(List.of("햇사과"));
        verify(normalizer, times(1)).normalize("햇사과", "과일");
        assertThat(saved.get(0).getVector()).containsExactly(1f);
        assertThat(saved.get(0).getIngredientName()).isEqualTo("재료");
    }

    @Test
    @DisplayName("indexProducts — 지난번에 못 만든 값만 다시 시도한다. 벡터와 재료명은 따로 판정된다")
    void indexProducts_retriesOnlyTheMissingValue() {
        UUID noVector = UUID.randomUUID();
        UUID noIngredient = UUID.randomUUID();
        when(repository.findAllById(anyList())).thenReturn(List.of(
            indexed(noVector, "사과", null, "사과재료"),
            indexed(noIngredient, "배", new float[] {9f}, "")));
        when(embeddingService.embedProducts(List.of("사과"))).thenReturn(List.of(new float[] {1f}));

        List<ProductDocument> saved =
            service.indexProducts(List.of(named(noVector, "사과"), named(noIngredient, "배")));

        // 벡터가 없던 "사과"만 임베딩을 다시 만든다 — "배"는 벡터가 있으므로 그대로 쓴다
        verify(embeddingService, times(1)).embedProducts(List.of("사과"));
        // 재료명이 비어 있던 "배"만 다시 뽑는다 — "사과"는 재료명이 있으므로 그대로 쓴다
        verify(normalizer, times(1)).normalize("배", "과일");
        assertThat(saved.get(0).getVector()).containsExactly(1f);
        assertThat(saved.get(0).getIngredientName()).isEqualTo("사과재료");
        assertThat(saved.get(1).getVector()).containsExactly(9f);
        assertThat(saved.get(1).getIngredientName()).isEqualTo("재료");
    }

    @Test
    @DisplayName("indexProducts — 카테고리만 바뀌면 재료명은 다시 뽑고 임베딩은 재사용한다")
    void indexProducts_recomputesIngredientNameWhenCategoryChanged() {
        UUID productId = UUID.randomUUID();
        // 재료명은 상품명과 카테고리를 함께 보고 뽑으므로 카테고리가 바뀌면 옛 값을 쓸 수 없다.
        // 임베딩은 상품명만 보므로 그대로 써도 된다.
        ProductIndexRequest recategorized = new ProductIndexRequest(
            productId, "한라봉", null, "AGRI_FRUIT", "제철 과일", 1000L, "ON_SALE", Instant.now());
        when(repository.findAllById(anyList())).thenReturn(List.of(new ProductDocument(
            productId, "한라봉", null, "AGRI_X", "채소", 1000L, "ON_SALE", Instant.now(), "옛재료", new float[] {9f})));

        List<ProductDocument> saved = service.indexProducts(List.of(recategorized));

        verify(embeddingService, never()).embedProducts(anyList());
        verify(normalizer, times(1)).normalize("한라봉", "제철 과일");
        assertThat(saved.get(0).getVector()).containsExactly(9f);
        assertThat(saved.get(0).getIngredientName()).isEqualTo("재료");
    }

    @Test
    @DisplayName("indexProducts — 일부만 바뀌었으면 바뀐 상품만 임베딩에 넣고 결과가 뒤섞이지 않는다")
    void indexProducts_embedsOnlyChangedProducts() {
        UUID unchanged = UUID.randomUUID();
        UUID changed = UUID.randomUUID();
        when(repository.findAllById(anyList())).thenReturn(List.of(
            indexed(unchanged, "사과", new float[] {9f}, "사과재료")));
        when(embeddingService.embedProducts(List.of("배"))).thenReturn(List.of(new float[] {2f}));

        List<ProductDocument> saved =
            service.indexProducts(List.of(named(unchanged, "사과"), named(changed, "배")));

        verify(embeddingService, times(1)).embedProducts(List.of("배"));
        assertThat(saved.get(0).getVector()).containsExactly(9f);
        assertThat(saved.get(1).getVector()).containsExactly(2f);
    }

    @Test
    @DisplayName("indexProducts — 색인된 문서를 읽지 못해도 전부 새로 만들어 색인은 계속된다")
    void indexProducts_continuesWhenLookupFails() {
        when(repository.findAllById(anyList())).thenThrow(new RuntimeException("ES 조회 실패"));
        when(embeddingService.embedProducts(anyList())).thenReturn(List.of(new float[] {1f}));

        List<ProductDocument> saved = service.indexProducts(List.of(request("사과")));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getVector()).containsExactly(1f);
    }

    @Test
    @DisplayName("indexProducts — 빈 목록이면 아무 외부 호출도 하지 않는다")
    void indexProducts_emptyDoesNothing() {
        assertThat(service.indexProducts(List.of())).isEmpty();
        verify(embeddingService, never()).embedProducts(anyList());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("indexProducts — updatedAt이 없는 이벤트만 현재 시각으로 채운다")
    void indexProducts_updatedAtFallback() {
        Instant sent = Instant.parse("2026-09-01T00:00:00Z");
        when(embeddingService.embedProducts(anyList())).thenReturn(Arrays.asList(null, null));

        List<ProductDocument> saved = service.indexProducts(List.of(
            new ProductIndexRequest(UUID.randomUUID(), "사과", null, "C", "과일", 1000L, "ON_SALE", sent),
            new ProductIndexRequest(UUID.randomUUID(), "배", null, "C", "과일", 1000L, "ON_SALE", null)));

        assertThat(saved.get(0).getUpdatedAt()).isEqualTo(sent);
        assertThat(saved.get(1).getUpdatedAt()).isAfter(sent);
    }

    private static ProductIndexRequest request(String name) {
        return named(UUID.randomUUID(), name);
    }

    private static ProductIndexRequest named(UUID productId, String name) {
        return new ProductIndexRequest(productId, name, null, "AGRI_X", "과일", 1000L, "ON_SALE", Instant.now());
    }

    private static ProductDocument indexed(UUID productId, String name, float[] vector, String ingredientName) {
        return new ProductDocument(
            productId, name, null, "AGRI_X", "과일", 1000L, "ON_SALE", Instant.now(), ingredientName, vector);
    }
}
