package com.barofarm.ai.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.client.ProductCatalogClient;
import com.barofarm.ai.search.infrastructure.client.dto.CatalogProduct;
import com.barofarm.dto.CustomPage;
import com.barofarm.dto.ResponseDto;
import com.barofarm.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 재색인이 상류 응답을 어떻게 다루는지, 그리고 잘못 부르면 어디서 끊는지를 고정한다.
 *
 * 이 경로는 한 번 부를 때마다 상품 수만큼 외부 AI 호출이 나가므로, 입력 검증과 중복 실행 차단이
 * 곧 비용 방어다. 실제 상류 호출과 Gemini 호출은 이 테스트가 다루지 않는다.
 */
class ProductReindexServiceTest {

    private ProductCatalogClient catalogClient;
    private ProductIndexService indexService;
    private ProductReindexService service;

    @BeforeEach
    void setUp() {
        catalogClient = mock(ProductCatalogClient.class);
        indexService = mock(ProductIndexService.class);
        service = new ProductReindexService(catalogClient, indexService);
    }

    @Test
    @DisplayName("상류에서 읽은 상품을 색인 요청으로 바꿔 넘기고, 다음 페이지 여부를 그대로 알린다")
    void reindexPage_passesProductsToIndexService() {
        UUID productId = UUID.randomUUID();
        LocalDateTime changedAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        stubCatalog(new CatalogProduct(
            productId, "제주 감귤 3kg", UUID.randomUUID(), "AGRI_FRUIT", "제철 과일", 12000L, "ON_SALE", changedAt));

        var result = service.reindexPage(0, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductIndexRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexService).indexProducts(captor.capture());
        ProductIndexRequest request = captor.getValue().get(0);
        assertThat(request.productId()).isEqualTo(productId);
        assertThat(request.productName()).isEqualTo("제주 감귤 3kg");
        assertThat(request.status()).isEqualTo("ON_SALE");
        // 상류는 시간대 없는 시각을 주므로 서버 시간대를 붙여 절대 시각으로 바꾼다
        assertThat(request.updatedAt()).isEqualTo(changedAt.atZone(java.time.ZoneId.systemDefault()).toInstant());

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("상품 식별자가 없는 항목은 색인하지 않는다 — 덮어쓸 대상을 특정할 수 없다")
    void reindexPage_skipsProductsWithoutId() {
        stubCatalog(new CatalogProduct(
            null, "식별자 없음", null, "AGRI_X", "과일", 1000L, "ON_SALE", LocalDateTime.now()));

        assertThat(service.reindexPage(0, 20).indexed()).isZero();
    }

    @Test
    @DisplayName("상류 응답이 비어 있어도 예외 없이 0건으로 끝난다")
    void reindexPage_handlesEmptyResponse() {
        when(catalogClient.getProducts(anyInt(), anyInt())).thenReturn(ResponseDto.ok(null));

        var result = service.reindexPage(0, 20);

        assertThat(result.indexed()).isZero();
        assertThat(result.hasNext()).isFalse();
        verify(indexService, never()).indexProducts(anyList());
    }

    @Test
    @DisplayName("페이지 번호·크기가 범위를 벗어나면 상류를 부르기 전에 끊는다")
    void reindexPage_rejectsInvalidPaging() {
        for (int[] bad : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> service.reindexPage(bad[0], bad[1]))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_PAGE_SIZE);
        }
        verify(catalogClient, never()).getProducts(anyInt(), anyInt());
    }

    @Test
    @DisplayName("이미 돌고 있으면 두 번째 요청은 409 — 겹치면 같은 상품에 외부 AI 호출이 두 배로 나간다")
    void reindexPage_rejectsConcurrentRun() {
        UUID productId = UUID.randomUUID();
        stubCatalog(new CatalogProduct(
            productId, "사과", null, "AGRI_X", "과일", 1000L, "ON_SALE", LocalDateTime.now()));

        // 색인이 도는 도중에 같은 서비스를 다시 부르는 상황을 만든다
        when(indexService.indexProducts(anyList())).thenAnswer(invocation -> {
            assertThatThrownBy(() -> service.reindexPage(1, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(SearchErrorCode.REINDEX_ALREADY_RUNNING);
            return List.of();
        });

        service.reindexPage(0, 20);
    }

    @Test
    @DisplayName("한 번 끝나면 잠금이 풀려 다음 페이지를 이어서 부를 수 있다")
    void reindexPage_releasesLockAfterRun() {
        stubCatalog(new CatalogProduct(
            UUID.randomUUID(), "사과", null, "AGRI_X", "과일", 1000L, "ON_SALE", LocalDateTime.now()));

        service.reindexPage(0, 20);

        assertThat(service.reindexPage(1, 20).indexed()).isEqualTo(1);
    }

    private void stubCatalog(CatalogProduct... products) {
        CustomPage<CatalogProduct> page =
            new CustomPage<>(List.of(products), 0, 20, 100, 5, true, false, true, false);
        when(catalogClient.getProducts(anyInt(), anyInt())).thenReturn(ResponseDto.ok(page));
    }
}
