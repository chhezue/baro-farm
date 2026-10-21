package com.barofarm.ai.search.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barofarm.ai.search.application.ProductReindexService;
import com.barofarm.ai.search.application.ProductReindexService.ReindexPageResult;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 재색인 API의 토큰 검사를 고정한다.
 *
 * 이 경로는 부를 때마다 상품 수만큼 외부 AI 호출이 나간다. baro-ai는 인증을 직접 하지 않으므로
 * 이 검사가 유일한 방어선이고, 특히 **토큰을 설정하지 않은 서버에서 열려 있으면 안 된다** —
 * 열어 두는 쪽이 기본값이 되는 실수는 이 저장소에 이미 한 번 있었다(무인증 리뷰 배치 엔드포인트).
 */
class ProductReindexControllerTest {

    private ProductReindexService reindexService;
    private ProductReindexController controller;

    @BeforeEach
    void setUp() {
        reindexService = mock(ProductReindexService.class);
        when(reindexService.reindexPage(anyInt(), anyInt()))
            .thenReturn(new ReindexPageResult(0, 20, 100, true));
        controller = new ProductReindexController(reindexService);
        ReflectionTestUtils.setField(controller, "adminToken", "secret");
    }

    @Test
    @DisplayName("토큰이 맞으면 재색인을 위임한다")
    void reindex_withValidToken() {
        assertThatCode(() -> controller.reindex("secret", 0, 20)).doesNotThrowAnyException();
        verify(reindexService).reindexPage(0, 20);
    }

    @Test
    @DisplayName("토큰이 없거나 다르면 403 — 서비스를 부르지 않는다")
    void reindex_withWrongToken() {
        for (String token : new String[] {null, "", "다른값"}) {
            assertThatThrownBy(() -> controller.reindex(token, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(SearchErrorCode.ADMIN_TOKEN_INVALID);
        }
        verify(reindexService, never()).reindexPage(anyInt(), anyInt());
    }

    @Test
    @DisplayName("서버에 토큰이 설정되지 않았으면 어떤 요청도 거절한다 — 빈 토큰끼리 맞는 것으로 보면 안 된다")
    void reindex_whenServerTokenNotConfigured() {
        for (String serverToken : new String[] {null, "", "   "}) {
            ReflectionTestUtils.setField(controller, "adminToken", serverToken);
            assertThatThrownBy(() -> controller.reindex(serverToken, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(SearchErrorCode.ADMIN_TOKEN_INVALID);
        }
        verify(reindexService, never()).reindexPage(anyInt(), anyInt());
    }

    @Test
    @DisplayName("서비스가 돌려준 진행 상황을 그대로 실어 보낸다 — 부르는 쪽이 hasNext로 반복한다")
    void reindex_passesThroughResult() {
        var body = controller.reindex("secret", 0, 20).data();
        assertThat(body.indexed()).isEqualTo(20);
        assertThat(body.totalProducts()).isEqualTo(100);
        assertThat(body.hasNext()).isTrue();
    }
}
