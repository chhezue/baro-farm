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
import com.barofarm.ai.search.domain.ProductAutocompleteDocument;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 묶음 색인이 임베딩 1회 · 인덱스당 저장 1회로 끝나고, 벡터를 못 만든 상품도 색인에서 빠지지 않는지 고정한다.
 */
class ProductIndexServiceTest {

    private ProductSearchRepository repository;
    private ProductAutocompleteRepository autocompleteRepository;
    private ProductEmbeddingService embeddingService;
    private ProductIngredientNormalizer normalizer;
    private ProductIndexService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductSearchRepository.class);
        autocompleteRepository = mock(ProductAutocompleteRepository.class);
        embeddingService = mock(ProductEmbeddingService.class);
        normalizer = mock(ProductIngredientNormalizer.class);
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(normalizer.normalize(anyString(), any())).thenReturn("재료");
        service = new ProductIndexService(repository, autocompleteRepository, embeddingService, normalizer);
    }

    @Test
    @DisplayName("indexProducts — 상품 3개면 임베딩 1회 · 재료명 3회 · 인덱스당 saveAll 1회")
    void indexProducts_batchesEmbeddingAndSaves() {
        List<ProductIndexRequest> requests = List.of(request("사과"), request("배"), request("감귤"));
        when(embeddingService.embedProducts(List.of("사과", "배", "감귤")))
            .thenReturn(List.of(new float[] {1f}, new float[] {2f}, new float[] {3f}));

        List<ProductDocument> saved = service.indexProducts(requests);

        verify(embeddingService, times(1)).embedProducts(anyList());
        verify(normalizer, times(3)).normalize(anyString(), any());
        verify(repository, times(1)).saveAll(anyList());
        verify(autocompleteRepository, times(1)).saveAll(anyList());
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
    @DisplayName("indexProducts — 자동완성 문서도 같은 상품 순서로 함께 저장된다")
    void indexProducts_savesAutocompleteDocuments() {
        when(embeddingService.embedProducts(anyList())).thenReturn(Arrays.asList(null, null));

        service.indexProducts(List.of(request("사과"), request("배")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductAutocompleteDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(autocompleteRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ProductAutocompleteDocument::getProductName)
            .containsExactly("사과", "배");
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
        return new ProductIndexRequest(UUID.randomUUID(), name, null, "AGRI_X", "과일", 1000L, "ON_SALE", Instant.now());
    }
}
