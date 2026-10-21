package com.barofarm.ai.search.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.barofarm.ai.common.llm.LlmCallKind;
import com.barofarm.ai.common.llm.LlmGateway;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 재료명 추출기의 코드 분기를 고정한다 — 프롬프트 문구는 색인 골든셋(`scripts/goldenset/check_normalizer.py`)이 담당한다.
 *
 * <p>빈 값의 의미가 중요하다: 빈 문자열은 "아직 재료명을 모른다"는 표시라 레시피 추천이 그 상품을 건너뛰고
 * 재색인 대상으로 센다. 그럴듯한 오답을 넣지 않는 것이 이 클래스의 계약이다.
 */
class ProductIngredientNormalizerTest {

    private final LlmGateway gateway = mock(LlmGateway.class);
    private final ProductIngredientNormalizer normalizer = new ProductIngredientNormalizer(gateway);

    private void gatewayAnswers(String ingredient) {
        Optional<ProductIngredientNormalizer.NormalizationResponse> answer = ingredient == null
            ? Optional.empty()
            : Optional.of(new ProductIngredientNormalizer.NormalizationResponse(ingredient));
        doReturn(answer).when(gateway).call(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("상품명이 비면 관문을 부르지 않고 빈 값이다")
    void normalize_blankName_skipsCall() {
        assertThat(normalizer.normalize("  ", "계란")).isEmpty();
        verify(gateway, never()).call(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("배치 경로·온도 0으로 부른다 — 같은 상품에 매번 다른 재료명이 나오지 않게")
    void normalize_callsBatchPathDeterministically() {
        gatewayAnswers("계란");

        assertThat(normalizer.normalize("곰곰 무항생제 대란 30구", "계란")).isEqualTo("계란");
        verify(gateway).call(eq(LlmCallKind.BATCH), anyString(),
            eq(ProductIngredientNormalizer.NormalizationResponse.class), eq(0.0));
    }

    @Test
    @DisplayName("카테고리가 없으면 대체 문구가 들어가고, 상품명의 중괄호는 지워진다")
    void normalize_substitutesCategoryAndStripsBraces() {
        gatewayAnswers("사과");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);

        normalizer.normalize("청송 사과 {5kg}", null);

        verify(gateway).call(any(), prompt.capture(), any(), any());
        assertThat(prompt.getValue()).contains("(카테고리 없음)").contains("청송 사과 5kg").doesNotContain("{5kg}");
    }

    @Test
    @DisplayName("관문이 빈 결과를 주면 빈 값이다 — 정규식 폴백으로 오답을 만들지 않는다")
    void normalize_emptyWhenGatewayEmpty() {
        gatewayAnswers(null);

        assertThat(normalizer.normalize("횡성 한우 등심 300g", "육류")).isEmpty();
    }

    @Test
    @DisplayName("답이 공백뿐이면 빈 값이고, 앞뒤 공백은 잘라 낸다")
    void normalize_trimsAndTreatsBlankAsEmpty() {
        gatewayAnswers("   ");
        assertThat(normalizer.normalize("아삭 총각김치 1kg", "김치류")).isEmpty();

        gatewayAnswers(" 총각김치 ");
        assertThat(normalizer.normalize("아삭 총각김치 1kg", "김치류")).isEqualTo("총각김치");
    }
}
