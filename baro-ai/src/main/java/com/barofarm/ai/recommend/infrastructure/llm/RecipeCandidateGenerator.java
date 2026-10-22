package com.barofarm.ai.recommend.infrastructure.llm;

import com.barofarm.ai.common.llm.LlmCallKind;
import com.barofarm.ai.common.llm.LlmGateway;
import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gemini LLM을 호출하여 사용자가 보유한 장바구니 식재료로 만들 수 있는 레시피를 생성하는 생성기입니다.
 * 응답은 배열 형태를 유지하되(빈 이름 방어) 프롬프트는 후보 1개만 요구하고, 이 클래스는 그중
 * 이름이 채워진 첫 번째 것을 골라 {@link Optional}로 돌려줍니다.
 *
 * <p><b>요청 경로 호출이다.</b> 사용자가 화면 앞에서 기다리므로 {@link LlmCallKind#INTERACTIVE}로 나가고,
 * 3초 안에 답이 없거나 격벽이 가득 차면 기다리지 않고 빈 결과가 됩니다. 그러면 응답은
 * {@code GENERATION_FAILED} 이유와 함께 200으로 나갑니다 — 레시피를 못 만든 것은 오류가 아니라
 * 사용자 상태라는 이 도메인의 정책 그대로입니다.
 *
 * <p>서버 측 재료 정합성 계산: '사용한 재료'와 '부족한 핵심 재료'는 LLM의 환각에 의존하지 않고
 * 서버({@link CandidateRecipePlan#from})가 보유 재료 목록과 대조하여 계산합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeCandidateGenerator {

    private final LlmGateway llmGateway;

    /**
     * 사용자가 보유한 식재료 목록을 기반으로 요리 레시피 1개를 생성합니다.
     *
     * @param ownedIngredients 장바구니에서 추출된 보유 식재료명 목록 (예: ["계란", "애호박", "양파"])
     * @return 부족한 재료 및 사용 재료가 계산된 레시피 (호출 실패·빈 응답이면 {@link Optional#empty()})
     */
    public Optional<CandidateRecipePlan> generateRecipe(List<String> ownedIngredients) {
        // [1단계: 프롬프트 조립]
        String templateString = buildRecipeCandidateTemplate(ownedIngredients);

        // [2단계: 관문을 통해 요청 경로로 호출 — 예산·격벽·스키마·지표는 관문이 맡는다]
        // 온도는 전역 기본값(0.7)을 쓴다. 레시피 생성은 추출이 아니라 창작에 가깝기 때문이다.
        Optional<RecipeCandidateDrafts> drafts =
            llmGateway.call(LlmCallKind.INTERACTIVE, templateString, RecipeCandidateDrafts.class, null);

        if (drafts.isEmpty() || drafts.get().candidates() == null) {
            return Optional.empty();
        }

        // [3단계: 이름이 있는 첫 후보를 골라 서버 측 재료 매칭 연산]
        // 프롬프트는 1개를 요구하지만 응답은 배열이라, 이름이 빈 항목을 걸러 첫 번째를 택합니다.
        List<RecipeCandidateDraft> candidates = drafts.get().candidates();
        Optional<CandidateRecipePlan> plan = candidates.stream()
            .filter(draft -> draft != null && draft.recipeName() != null && !draft.recipeName().isBlank())
            .findFirst()
            .map(draft -> CandidateRecipePlan.from(
                draft.recipeName(), draft.recipeIngredientsCore(), draft.instructions(), ownedIngredients));

        plan.ifPresentOrElse(
            p -> log.debug("레시피 생성 완료: {}, core: {}, used: {}, missing: {}",
                p.recipeName(), p.recipeIngredientsCore(), p.usedOwnedIngredients(), p.missingIngredientsCore()),
            () -> log.info("레시피 응답에 이름이 있는 후보가 없습니다. 후보 수: {}", candidates.size()));

        return plan;
    }

    /**
     * Gemini LLM에 전달할 시스템 지침 및 보유 식재료 기반 레시피 추천 프롬프트 템플릿을 생성합니다.
     *
     * [주요 프롬프트 정책]
     * 1. 보유 재료 목록을 줄 단위로 포맷팅하여 프롬프트의 `{ownedIngredients}` 위치에 주입합니다.
     * 2. 후보를 1개만 생성합니다. 이전에는 3개를 만들고 첫 번째만 썼는데, 개수를 채우려는 압력이
     *    "오이 파스타" 같은 억지 요리를 밀어내고 있었습니다.
     * 3. 판매 유도는 비율(구 30~50%)이 아니라 "부족 재료 최소 1개"로 겁니다. 보유 재료만으로
     *    완성되는 요리라면 재료를 끼워 넣는 대신 요리 선택 자체를 돌리게 합니다.
     * 4. 판매 품목을 카테고리(농산물·축산물 6종, 지역 특산물 제외)로 알려 주고, 그 밖의 것(양념·장류·기름·가루·수산물·
     *    가공식품)은 핵심 재료에 넣지 않게 합니다. 2026-09-09까지는 기본 양념 7개만 나열했는데,
     *    부족 재료의 약 3할이 팔지 않는 종류로 나와 상품 카드가 빠졌습니다. 목록을 늘리는 대신
     *    판매 카테고리 자체를 기준으로 바꿨습니다 — 카테고리 SSOT는 scripts/init-db/02-init-categories.sql.
     *    그 파일의 Level 1 카테고리 7개 중 "지역 특산물"은 일부러 나열하지 않았습니다 — 무엇이 들어가는 칸인지
     *    미정이라 프롬프트에 넣으면 모델이 임의로 넓혀 읽을 수 있습니다. 해석이 정해지면 넣고 다시 잽니다.
     * 5. 규칙 1의 예시는 팔지 않는 카테고리를 쓰지 않습니다.
     *
     * 이 문구는 골든셋 15종 × 3회 측정으로 검증했습니다 — 실행 합격률 69.0% → 90.5%.
     * 예시 교체(2026-09-07)는 같은 골든셋을 두 팔 × 2회(각 90건) 돌려 차이가 없음을 확인한 뒤 반영했습니다.
     * 규칙 4의 카테고리 기준 전환(2026-09-09)은 바꾸기 전 프롬프트(`no-category-rule` 팔)와 42건씩 비교했습니다.
     * **문구를 고치면 같은 골든셋으로 다시 재야 합니다.**
     * 근거: docs/audits/cart-recipe/002-goldenset-before-after.md,
     *      docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md,
     *      docs/audits/cart-recipe/009-category-rule-in-prompt.md
     *
     * @param ownedIngredients 장바구니에서 추출된 보유 식재료명 목록
     * @return 완성된 LLM 사용자 프롬프트 텍스트
     */
    private String buildRecipeCandidateTemplate(List<String> ownedIngredients) {
        // 재료명은 색인 시점에 LLM이 만든 값이라 우리가 쓴 문자열이 아니다 — 그대로 끼워 넣지 않는다.
        String ownedText = ownedIngredients.stream()
            .map(s -> "- " + LlmGateway.asData(s))
            .collect(Collectors.joining("\n"));

        return """
            당신은 '집밥 레시피 추천 AI'입니다.
            아래 '보유 재료'로 만들 수 있는 실제 요리 레시피 1가지를 추천합니다.

            <보유 재료>
            {ownedIngredients}

            <지켜야 할 것>
            1. 실제로 사람들이 만들어 먹는 요리만 추천합니다. 없는 요리를 지어내지 마세요.
               보유 재료가 서로 어울리지 않으면 전부 쓰려 하지 말고 일부만 쓰세요.
               예: ["시금치", "땅콩"]이면 "시금치 땅콩 찌개"가 아니라 "시금치나물"이나 "땅콩조림"입니다.
            2. 보유 재료 중 최소 1개는 반드시 씁니다.
            3. 부족한 핵심 재료가 최소 1개는 반드시 있어야 합니다. 사용자가 추가로 살 재료를
               제안하기 위해서입니다. 보유 재료만으로 완성되는 요리라면 그 요리 대신, 보유 재료를
               살리면서 한두 가지가 더 필요한 요리를 고르세요.
               예: ["쌀", "보리"]면 "보리밥"이 아니라 "콩보리밥"이나 "보리비빔밥"입니다.
               다만 개수를 맞추려고 어울리지 않는 재료를 넣지는 마세요.
            4. 이 서비스는 신선 농산물·축산물만 팝니다: 과일, 채소, 곡류·잡곡, 버섯·특용작물, 견과류,
               계란·육류·유제품. 그 밖의 것 — 양념·장류·기름·가루·조미료(물·소금·후추·식용유·설탕·식초·
               간장·고추장·고춧가루·참기름 등), 수산물·건어물, 두부·김치·면·빵 같은 가공식품 — 은 팔지
               않으므로 핵심 재료 목록에 넣지 않고, 필요하면 조리법 안에서만 언급합니다.
               부족한 핵심 재료(규칙 3)는 반드시 위 판매 품목 안에서 고릅니다.
            5. 재료명은 일반적인 한 단어 명사로 씁니다. 예: "돼지고기", "김치", "양파"

            <출력 값>
            - recipeName: 요리 이름
            - recipeIngredientsCore: 요리의 정체성을 정하는 핵심 재료 3~5개
            - instructions: 3~6단계 조리법
            """.replace("{ownedIngredients}", ownedText);
    }
}
