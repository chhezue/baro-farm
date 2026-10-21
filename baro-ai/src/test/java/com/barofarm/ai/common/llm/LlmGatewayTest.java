package com.barofarm.ai.common.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 관문이 약속하는 두 가지를 고정한다 — 외부 값의 중괄호 제거, 그리고 실패가 지표에 {@code failed}로 남는 것.
 *
 * <p>Spring AI는 프롬프트 텍스트의 중괄호를 치환 자리로 읽는다. 판매자가 상품명에 중괄호를 넣으면
 * 우리가 의도하지 않은 자리가 생기거나 렌더링이 통째로 실패하는데, 재료명·상품명에 중괄호가 정당하게
 * 들어갈 일은 없으므로 지운다.
 *
 * <p>실패 지표는 2026-09-09까지 죽어 있었다 — 호출부가 예외를 삼켜 타임아웃·429·파싱 실패가 전부
 * {@code empty}로만 찍혔다(docs/audits/cart-recipe/010 B-1). 이 테스트가 그 분기를 살아 있게 지킨다.
 */
class LlmGatewayTest {

    private record Answer(String text) { }

    @Test
    @DisplayName("asData — 중괄호를 지워 치환 자리로 읽히지 않게 한다")
    void asData_stripsBraces() {
        assertThat(LlmGateway.asData("{ownedIngredients}")).isEqualTo("ownedIngredients");
        assertThat(LlmGateway.asData("사과 {2kg}")).isEqualTo("사과 2kg");
    }

    @Test
    @DisplayName("asData — 평범한 값과 null은 그대로 다룬다")
    void asData_leavesOrdinaryValues() {
        assertThat(LlmGateway.asData("횡성 한우 등심 300g")).isEqualTo("횡성 한우 등심 300g");
        assertThat(LlmGateway.asData(null)).isEmpty();
    }

    @Test
    @DisplayName("호출이 예외로 끝나면 outcome=failed로 기록하고 호출자에게는 빈 결과를 돌려준다")
    void call_recordsFailedAndReturnsEmpty_whenClientThrows() {
        ChatClient batch = mock(ChatClient.class);
        ChatClient interactive = mock(ChatClient.class);
        when(interactive.prompt()).thenThrow(new RuntimeException("timeout"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmGateway gateway = new LlmGateway(batch, interactive, registry);

        Optional<Answer> result = gateway.call(LlmCallKind.INTERACTIVE, "안녕", Answer.class, null);

        assertThat(result).isEmpty();
        assertThat(registry.get("llm.call").tag("kind", "interactive").tag("outcome", "failed").timer().count())
            .isEqualTo(1);
        assertThat(registry.find("llm.call").tag("outcome", "empty").timer()).isNull();
    }
}
