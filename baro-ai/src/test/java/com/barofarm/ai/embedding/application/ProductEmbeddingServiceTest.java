package com.barofarm.ai.embedding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 묶음 임베딩이 API 1회로 끝나고, 묶음이 실패하면 상품별 폴백으로 "실패한 상품만 null"이 되는지 고정한다.
 */
class ProductEmbeddingServiceTest {

    private EmbeddingModel embeddingModel;
    private ProductEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        service = new ProductEmbeddingService(embeddingModel);
    }

    @Test
    @DisplayName("embedProducts — 상품 3개를 embed 1회로 보내고 순서대로 돌려준다")
    void embedProducts_singleCall() {
        when(embeddingModel.embed(List.of("사과", "배", "감귤")))
            .thenReturn(List.of(new float[] {1f}, new float[] {2f}, new float[] {3f}));

        List<float[]> result = service.embedProducts(List.of("사과", "배", "감귤"));

        verify(embeddingModel, times(1)).embed(anyList());
        assertThat(result).hasSize(3);
        assertThat(result.get(2)).containsExactly(3f);
    }

    @Test
    @DisplayName("embedProducts — 묶음 호출이 실패하면 상품별로 다시 시도해 실패한 것만 null이다")
    void embedProducts_fallsBackPerProduct() {
        when(embeddingModel.embed(List.of("사과", "배", "감귤"))).thenThrow(new RuntimeException("429"));
        when(embeddingModel.embed(List.of("사과"))).thenReturn(List.of(new float[] {1f}));
        when(embeddingModel.embed(List.of("배"))).thenThrow(new RuntimeException("429"));
        when(embeddingModel.embed(List.of("감귤"))).thenReturn(List.of(new float[] {3f}));

        List<float[]> result = service.embedProducts(List.of("사과", "배", "감귤"));

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).containsExactly(1f);
        assertThat(result.get(1)).isNull();
        assertThat(result.get(2)).containsExactly(3f);
        verify(embeddingModel, times(4)).embed(anyList());
    }

    @Test
    @DisplayName("embedProducts — 묶음 결과 수가 입력과 다르면 믿지 않고 상품별 폴백으로 간다")
    void embedProducts_sizeMismatchFallsBack() {
        when(embeddingModel.embed(List.of("사과", "배"))).thenReturn(List.of(new float[] {1f}));
        when(embeddingModel.embed(List.of("사과"))).thenReturn(List.of(new float[] {1f}));
        when(embeddingModel.embed(List.of("배"))).thenReturn(List.of(new float[] {2f}));

        List<float[]> result = service.embedProducts(List.of("사과", "배"));

        assertThat(result).hasSize(2);
        assertThat(result.get(1)).containsExactly(2f);
    }

    @Test
    @DisplayName("embedProducts — 빈 목록이면 API를 부르지 않는다")
    void embedProducts_emptySkipsApi() {
        assertThat(service.embedProducts(List.of())).isEmpty();
        verify(embeddingModel, times(0)).embed(anyList());
    }
}
