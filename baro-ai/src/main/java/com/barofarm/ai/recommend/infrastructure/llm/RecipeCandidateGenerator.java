package com.barofarm.ai.recommend.infrastructure.llm;

import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Gemini LLM(구글 생성형 AI)을 호출하여 사용자가 보유한 장바구니 식재료를 활용할 수 있는
 * 맞춤형 요리 레시피를 생성하는 생성기 컴포넌트입니다.
 * 응답은 배열 형태를 유지하되(빈 이름 방어) 프롬프트는 후보 1개만 요구하고, 이 클래스는 그중
 * 이름이 채워진 첫 번째 것을 골라 {@link Optional}로 돌려줍니다.
 *
 * [주요 특징]
 * 1. Structured Outputs: Gemini API의 JSON 스키마 강제 기능(responseSchema)을 적용해
 *    마크다운 코드펜스나 설명 문장이 섞이지 않은 JSON을 받습니다. 프롬프트로 부탁하던 방식보다 훨씬 안정적이지만
 *    파싱 실패가 0이 된다는 보장은 아니라서, 아래 호출부는 여전히 예외를 잡아 빈 결과로 되돌립니다.
 * 2. 서버 측 재료 정합성 계산: 부족한 핵심 재료 및 사용된 재료는 LLM의 환각(Hallucination)에 의존하지 않고,
 *    서버(CandidateRecipePlan.from)에서 보유 재료 목록과 대조하여 정확히 계산합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeCandidateGenerator {

    /**
     * Gemini API로부터 수신할 JSON 응답을 Java DTO(RecipeCandidateDrafts) 객체로 파싱하고,
     * API 호출 시 JSON 스키마를 제공하는 변환기.
     */
    private static final BeanOutputConverter<RecipeCandidateDrafts> RESPONSE_CONVERTER =
        new BeanOutputConverter<>(RecipeCandidateDrafts.class);

    /** Spring AI를 통해 Gemini LLM과 통신하는 대화형 클라이언트 */
    private final ChatClient chatClient;

    /**
     * 사용자가 보유한 식재료 목록을 기반으로 요리 레시피 1개를 생성합니다.
     *
     * @param ownedIngredients 장바구니에서 추출된 보유 식재료명 목록 (예: ["계란", "애호박", "양파"])
     * @return 부족한 재료 및 사용 재료가 계산된 레시피 (호출 실패·빈 응답이면 {@link Optional#empty()})
     */
    public Optional<CandidateRecipePlan> generateRecipe(List<String> ownedIngredients) {
        try {
            // [1단계: 프롬프트 템플릿 조립]
            // 보유 재료 목록을 주입하고, 현실적인 요리 및 부족한 재료 유도 규칙이 포함된 시스템 프롬프트를 작성합니다.
            String templateString = buildRecipeCandidateTemplate(ownedIngredients);

            // [2단계: Gemini LLM 호출 및 구조화된 JSON 응답 획득]
            // ChatClient를 통해 JSON 스키마를 전달하고 Gemini 모델로부터 레시피 초안 목록(Drafts)을 수신합니다.
            RecipeCandidateDrafts drafts = callRecipeCandidateLLM(templateString);

            if (drafts == null || drafts.candidates() == null) {
                return Optional.empty();
            }

            // [3단계: 이름이 있는 첫 후보를 골라 서버 측 재료 매칭 연산]
            // 프롬프트는 1개를 요구하지만 응답은 배열이라, 이름이 빈 항목을 걸러 첫 번째를 택합니다.
            // LLM이 반환한 초안(Draft)과 보유 재료를 대조하여 '실제 사용된 재료'와 '부족한 핵심 재료'를 서버에서 계산합니다.
            Optional<CandidateRecipePlan> plan = drafts.candidates().stream()
                .filter(draft -> draft != null && draft.recipeName() != null && !draft.recipeName().isBlank())
                .findFirst()
                .map(draft -> CandidateRecipePlan.from(draft, ownedIngredients));

            plan.ifPresentOrElse(
                p -> log.debug("레시피 생성 완료: {}, core: {}, used: {}, missing: {}",
                    p.recipeName(), p.recipeIngredientsCore(), p.usedOwnedIngredients(), p.missingIngredientsCore()),
                () -> log.info("레시피 응답에 이름이 있는 후보가 없습니다. 후보 수: {}", drafts.candidates().size()));

            return plan;
        } catch (Exception e) {
            log.error("레시피 생성 실패: {}", e.getMessage(), e);
            return Optional.empty();
        }
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
     * 4. 기본 양념은 이미 보유한 것으로 가정하여 재료 목록에서 제외하도록 지시합니다.
     * 5. 규칙 1의 예시는 팔지 않는 카테고리를 쓰지 않습니다. 과일은 레시피 재료에서 제외돼 있어
     *    보유 재료로 들어올 수 없는데, 예전 예시가 사과였습니다 — 일어날 수 없는 상황을 가르치고 있었습니다.
     *
     * 이 문구는 골든셋 15종 × 3회 측정으로 검증했습니다 — 실행 합격률 69.0% → 90.5%.
     * 예시 교체(2026-09-07)는 같은 골든셋을 두 팔 × 2회(각 90건) 돌려 차이가 없음을 확인한 뒤 반영했습니다.
     * 근거: docs/audits/cart-recipe/002-goldenset-before-after.md,
     *      docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md
     *
     * @param ownedIngredients 장바구니에서 추출된 보유 식재료명 목록
     * @return 완성된 LLM 사용자 프롬프트 텍스트
     */
    private String buildRecipeCandidateTemplate(List<String> ownedIngredients) {
        String ownedText = ownedIngredients.stream()
            .map(s -> "- " + s)
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
            4. 기본 양념(물·소금·후추·식용유·설탕·식초·간장 등)은 이미 있다고 가정하고
               재료 목록에 넣지 않습니다.
            5. 재료명은 일반적인 한 단어 명사로 씁니다. 예: "돼지고기", "김치", "양파"

            <출력 값>
            - recipeName: 요리 이름
            - recipeIngredientsCore: 요리의 정체성을 정하는 핵심 재료 3~5개
            - instructions: 3~6단계 조리법
            """.replace("{ownedIngredients}", ownedText);
    }

    /**
     * Gemini LLM을 호출하여 정형화된 JSON 형태의 레시피 초안 목록(RecipeCandidateDrafts)을 수신합니다.
     *
     * @param templateString 완성된 사용자 및 시스템 프롬프트 텍스트
     * @return 파싱된 레시피 초안 객체
     */
    private RecipeCandidateDrafts callRecipeCandidateLLM(String templateString) {
        // [구조화 출력 설정]
        // BeanOutputConverter를 통해 JSON Schema를 LLM 옵션에 등록하고 application/json MIME 타입을 지정합니다.
        // 이를 통해 모델이 마크다운이나 잡담 없이 순수 JSON만 출력하도록 보장합니다.
        String json = chatClient.prompt()
            .options(GoogleGenAiChatOptions.builder()
                .responseMimeType(MediaType.APPLICATION_JSON_VALUE)
                .responseSchema(RESPONSE_CONVERTER.getJsonSchema())
                .build())
            .user(p -> p.text(templateString))
            .call()
            .content();

        // JSON 응답을 DTO 객체로 변환하여 반환
        return RESPONSE_CONVERTER.convert(json);
    }
}
