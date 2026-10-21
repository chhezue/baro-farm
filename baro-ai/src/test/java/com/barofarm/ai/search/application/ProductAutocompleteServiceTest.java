package com.barofarm.ai.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * 자동완성이 Elasticsearch를 부르기 전에 끊는 조건을 고정한다.
 *
 * 접두어가 실제로 매칭되는지는 색인 분석기가 있어야 알 수 있어 통합 테스트
 * (`ProductIndexAndSearchIntegrationTest`)가 맡고, 여기서는 "부르지 않아야 할 때 부르지 않는지"만 본다.
 */
class ProductAutocompleteServiceTest {

    private ElasticsearchOperations operations;
    private ProductAutocompleteService service;

    @BeforeEach
    void setUp() {
        operations = mock(ElasticsearchOperations.class);
        service = new ProductAutocompleteService(operations);
    }

    @Test
    @DisplayName("autocomplete — size는 1~20, 0과 21은 INVALID_AUTOCOMPLETE_SIZE")
    void autocomplete_sizeOutOfRange_rejected() {
        assertThatThrownBy(() -> service.autocomplete("토마", 0))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_AUTOCOMPLETE_SIZE);
        assertThatThrownBy(() -> service.autocomplete("토마", 21))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(SearchErrorCode.INVALID_AUTOCOMPLETE_SIZE);
        verifyNoInteractions(operations);
    }

    @Test
    @DisplayName("autocomplete — 2글자 미만이면 ES를 부르지 않고 빈 목록이다")
    void autocomplete_shortQuery_emptyWithoutLookup() {
        assertThat(service.autocomplete("토", 5)).isEmpty();
        assertThat(service.autocomplete(null, 5)).isEmpty();
        verifyNoInteractions(operations);
    }
}
