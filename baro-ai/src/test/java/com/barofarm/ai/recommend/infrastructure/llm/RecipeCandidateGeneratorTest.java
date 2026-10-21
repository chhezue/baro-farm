package com.barofarm.ai.recommend.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.barofarm.ai.common.llm.LlmCallKind;
import com.barofarm.ai.common.llm.LlmGateway;
import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 생성기의 코드 분기를 고정한다 — 프롬프트 문구는 골든셋이 담당하므로 여기서는 단언하지 않는다.
 *
 * <p>2026-09-08부터 생성기는 {@link LlmGateway} 하나만 주입받아 mock 한 줄로 흉내 낼 수 있다
 * (docs/audits/cart-recipe/010 B-2). 후보 선택·빈 값 처리·외부 값 정제는 프롬프트가 아니라 코드라
 * 골든셋이 잡아 주지 않는다.
 */
class RecipeCandidateGeneratorTest {

    private final LlmGateway gateway = mock(LlmGateway.class);
    private final RecipeCandidateGenerator generator = new RecipeCandidateGenerator(gateway);

    private void gatewayReturns(Optional<RecipeCandidateDrafts> drafts) {
        doReturn(drafts).when(gateway)
            .call(eq(LlmCallKind.INTERACTIVE), anyString(), eq(RecipeCandidateDrafts.class), any());
    }

    @Test
    @DisplayName("관문이 빈 결과를 돌려주면 레시피도 없다")
    void generateRecipe_emptyWhenGatewayEmpty() {
        gatewayReturns(Optional.empty());

        assertThat(generator.generateRecipe(List.of("계란"))).isEmpty();
    }

    @Test
    @DisplayName("후보 배열이 null이면 빈 결과다 — NPE로 요청이 죽지 않는다")
    void generateRecipe_emptyWhenCandidatesNull() {
        gatewayReturns(Optional.of(new RecipeCandidateDrafts(null)));

        assertThat(generator.generateRecipe(List.of("계란"))).isEmpty();
    }

    @Test
    @DisplayName("이름이 빈 후보는 건너뛰고 이름이 있는 첫 후보를 고른다")
    void generateRecipe_skipsNamelessCandidates() {
        gatewayReturns(Optional.of(new RecipeCandidateDrafts(Arrays.asList(
            null,
            new RecipeCandidateDraft("  ", List.of("계란"), "..."),
            new RecipeCandidateDraft("계란말이", List.of("계란", "대파"), "1. 풀고 2. 만다")))));

        Optional<CandidateRecipePlan> plan = generator.generateRecipe(List.of("계란"));

        assertThat(plan).isPresent();
        assertThat(plan.get().recipeName()).isEqualTo("계란말이");
        assertThat(plan.get().missingIngredientsCore()).containsExactly("대파");
    }

    @Test
    @DisplayName("보유 재료의 중괄호는 프롬프트에 들어가기 전에 지워진다 — 정제가 호출 지점에서 실제로 적용된다")
    void generateRecipe_stripsBracesFromOwnedIngredients() {
        gatewayReturns(Optional.empty());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);

        generator.generateRecipe(List.of("사과 {2kg}", "계란"));

        verify(gateway).call(eq(LlmCallKind.INTERACTIVE), prompt.capture(), eq(RecipeCandidateDrafts.class), any());
        assertThat(prompt.getValue()).contains("- 사과 2kg").contains("- 계란").doesNotContain("{2kg}");
    }

    @Test
    @DisplayName("요청 경로로 나가고 온도는 전역 기본값(null)을 쓴다")
    void generateRecipe_usesInteractivePathWithDefaultTemperature() {
        gatewayReturns(Optional.empty());

        generator.generateRecipe(List.of("계란"));

        verify(gateway).call(eq(LlmCallKind.INTERACTIVE), anyString(), eq(RecipeCandidateDrafts.class), eq(null));
    }
}
