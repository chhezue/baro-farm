package com.barofarm.ai.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSearchResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.exception.CustomException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.query.Query;

/**
 * 검색 서비스의 ES 앞단 계약을 고정한다 — 페이징·자동완성 입력 검증(ES를 부르기 전에 400으로 끊는다)과
 * 키워드 여러 개 조회가 `multiSearch` 한 번으로 끝나는 것. ES·리포지토리는 전부 mock이다.
 *
 * 검색 로그 적재·프로필 갱신 테스트는 2026-09-07에 지웠다 — 검색을 취향 신호에서 빼면서 그 경로 자체가
 * 없어졌다(docs/audits/taste-vector/005-signal-and-weight-simplification.md).
 */
class ProductSearchServiceTest {

    private ElasticsearchOperations operations;
    private ProductAutocompleteRepository autocompleteRepository;
    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        operations = mock(ElasticsearchOperations.class);
        autocompleteRepository = mock(ProductAutocompleteRepository.class);
        service = new ProductSearchService(operations, autocompleteRepository);
    }

    // ---- 페이징 검증 (keyword-search/001 A-4 계약) ----

    @Test
    @DisplayName("search — page가 음수면 INVALID_PAGE, ES는 부르지 않는다")
    void search_negativePage_rejected() {
        assertThatThrownBy(() -> service.search(keyword("사과"), -1, 10))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_PAGE);
        verifyNoInteractions(operations);
    }

    @Test
    @DisplayName("search — size는 1~100, 0과 101은 INVALID_PAGE_SIZE")
    void search_sizeOutOfRange_rejected() {
        assertThatThrownBy(() -> service.search(keyword("사과"), 0, 0))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_PAGE_SIZE);
        assertThatThrownBy(() -> service.search(keyword("사과"), 0, 101))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_PAGE_SIZE);
        verifyNoInteractions(operations);
    }

    @Test
    @DisplayName("search — (page+1)×size가 10,000을 넘으면 PAGE_RANGE_EXCEEDED (ES max_result_window)")
    void search_beyondResultWindow_rejected() {
        // page 99 × size 101은 size에서 먼저 걸리므로, size 100 · page 100 → 10,100
        assertThatThrownBy(() -> service.search(keyword("사과"), 100, 100))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.PAGE_RANGE_EXCEEDED);
        verifyNoInteractions(operations);
    }

    @Test
    @DisplayName("search — 경계값 page 99 · size 100(정확히 10,000)은 통과한다")
    void search_exactlyResultWindow_allowed() {
        SearchHits<ProductDocument> empty = hits();
        when(operations.search(any(Query.class), eq(ProductDocument.class))).thenReturn(empty);

        service.search(keyword("사과"), 99, 100);

        verify(operations, times(1)).search(any(Query.class), eq(ProductDocument.class));
    }

    // ---- 자동완성 검증 ----

    @Test
    @DisplayName("autocomplete — size는 1~20, 0과 21은 INVALID_AUTOCOMPLETE_SIZE")
    void autocomplete_sizeOutOfRange_rejected() {
        assertThatThrownBy(() -> service.autocomplete("토마", 0))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_AUTOCOMPLETE_SIZE);
        assertThatThrownBy(() -> service.autocomplete("토마", 21))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_AUTOCOMPLETE_SIZE);
        verifyNoInteractions(autocompleteRepository);
    }

    @Test
    @DisplayName("autocomplete — 2글자 미만이면 리포지토리를 부르지 않고 빈 목록이다")
    void autocomplete_shortQuery_emptyWithoutLookup() {
        assertThat(service.autocomplete("토", 5)).isEmpty();
        assertThat(service.autocomplete(null, 5)).isEmpty();
        verifyNoInteractions(autocompleteRepository);
    }

    // ---- multi-search (cart-recipe/005 A-2) ----

    @Test
    @DisplayName("searchByKeywords — 키워드 3개면 쿼리 3개를 multiSearch 한 번으로 보내고 search는 부르지 않는다")
    void searchByKeywords_usesSingleMultiSearch() {
        // hits()가 안에서 mock을 stub하므로 when(...) 인자 안에서 만들면 Mockito가 중첩 stubbing으로 본다 — 먼저 만든다
        List<SearchHits<ProductDocument>> perKeyword =
            List.of(hits(document("대파 1단")), hits(), hits(document("새우젓 500g")));
        when(operations.multiSearch(anyList(), eq(ProductDocument.class))).thenReturn(perKeyword);

        List<List<ProductSearchResponse>> result = service.searchByKeywords(List.of("대파", "겨자", "새우젓"), 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<? extends Query>> captor = ArgumentCaptor.forClass(List.class);
        verify(operations, times(1)).multiSearch(captor.capture(), eq(ProductDocument.class));
        verify(operations, never()).search(any(Query.class), eq(ProductDocument.class));
        assertThat(captor.getValue()).hasSize(3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).extracting(ProductSearchResponse::productName).containsExactly("대파 1단");
        assertThat(result.get(1)).isEmpty();
        assertThat(result.get(2)).extracting(ProductSearchResponse::productName).containsExactly("새우젓 500g");
    }

    @Test
    @DisplayName("searchByKeywords — 색인해 둔 재료명(ingredientName) 정확 일치 절을 상품명보다 높은 가중치로 넣는다")
    void searchByKeywords_includesIngredientNameTermClause() {
        // 이 절이 빠지면 상품명이 재료를 다른 말로 부르는 상품(한우 등심 300g ← 소고기)을 영영 못 찾는다.
        // 감사 cart-recipe/006 A-1이 "검증 방법"으로 고정하기로 한 계약이다.
        List<SearchHits<ProductDocument>> perKeyword = List.of(hits());
        when(operations.multiSearch(anyList(), eq(ProductDocument.class))).thenReturn(perKeyword);

        service.searchByKeywords(List.of("소고기"), 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<? extends Query>> captor = ArgumentCaptor.forClass(List.class);
        verify(operations).multiSearch(captor.capture(), eq(ProductDocument.class));

        NativeQuery built = (NativeQuery) captor.getValue().get(0);
        List<co.elastic.clients.elasticsearch._types.query_dsl.Query> shoulds =
            built.getQuery().bool().should();

        var term = shoulds.stream()
            .filter(co.elastic.clients.elasticsearch._types.query_dsl.Query::isTerm)
            .map(co.elastic.clients.elasticsearch._types.query_dsl.Query::term)
            .filter(t -> "ingredientName".equals(t.field()))
            .findFirst();

        assertThat(term).as("ingredientName term 절이 있어야 한다").isPresent();
        assertThat(term.get().value().stringValue()).isEqualTo("소고기");
        assertThat(term.get().boost())
            .as("상품명 정확 문구 매칭(2.0)보다 높아야 정답이 1위로 온다")
            .isNotNull().isGreaterThan(2.0f);
    }

    @Test
    @DisplayName("searchByKeywords — 결과 수가 키워드 수와 다르면(배치 내 부분 실패) 전부 빈 목록으로 맞춘다")
    void searchByKeywords_partialFailure_returnsEmptyPerKeyword() {
        // Spring Data ES는 실패한 항목을 빼고 자리를 당기므로 2개만 돌아온 상황
        List<SearchHits<ProductDocument>> shorter = List.of(hits(document("대파 1단")), hits(document("새우젓 500g")));
        when(operations.multiSearch(anyList(), eq(ProductDocument.class))).thenReturn(shorter);

        List<List<ProductSearchResponse>> result = service.searchByKeywords(List.of("대파", "겨자", "새우젓"), 2);

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(perKeyword -> assertThat(perKeyword).isEmpty());
    }

    @Test
    @DisplayName("searchByKeywords — 빈 목록이면 ES를 부르지 않는다")
    void searchByKeywords_emptyKeywordsSkipsEs() {
        assertThat(service.searchByKeywords(List.of(), 2)).isEmpty();
        verify(operations, never()).multiSearch(anyList(), eq(ProductDocument.class));
    }

    // ---- helpers ----

    private static ProductSearchRequest keyword(String keyword) {
        return new ProductSearchRequest(keyword, null, null, null);
    }

    private static ProductDocument document(String name) {
        return new ProductDocument(UUID.randomUUID(), name, null, "AGRI_X", "채소", 1000L, "ON_SALE",
            null, "", null);
    }

    @SuppressWarnings("unchecked")
    private static SearchHits<ProductDocument> hits(ProductDocument... documents) {
        List<SearchHit<ProductDocument>> hitList = java.util.Arrays.stream(documents)
            .map(doc -> {
                SearchHit<ProductDocument> hit = mock(SearchHit.class);
                when(hit.getContent()).thenReturn(doc);
                return hit;
            })
            .toList();
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(hitList);
        when(hits.getTotalHits()).thenReturn((long) documents.length);
        return hits;
    }
}
