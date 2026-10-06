package com.barofarm.ai.recommend.infrastructure.llm;

import com.barofarm.ai.recommend.domain.CandidateRecipePlan;
import com.barofarm.ai.recommend.domain.RecipeCandidates;
import java.util.List;
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
 * 맞춤형 요리 레시피 후보군을 생성하는 생성기 컴포넌트입니다.
 *
 * [주요 특징]
 * 1. Structured Outputs: Gemini API의 JSON 스키마 강제 기능(responseSchema)을 적용하여
 *    마크다운 코드펜스나 추가 텍스트 없이 100% 신뢰할 수 있는 JSON 형태로 응답을 수신합니다.
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
     * 사용자가 보유한 식재료 목록을 기반으로 요리 레시피 후보 목록을 생성합니다.
     *
     * @param ownedIngredients 장바구니에서 추출된 보유 식재료명 목록 (예: ["계란", "애호박", "양파"])
     * @return 부족한 재료 및 사용 재료가 계산된 레시피 후보군 객체 (호출 실패 시 안전하게 빈 목록 반환)
     */
    public RecipeCandidates generateRecipeCandidates(List<String> ownedIngredients) {
        try {
            // [1단계: 프롬프트 템플릿 조립]
            // 보유 재료 목록을 주입하고, 현실적인 요리 및 부족한 재료 유도 규칙이 포함된 시스템 프롬프트를 작성합니다.
            String templateString = buildRecipeCandidateTemplate(ownedIngredients);

            // [2단계: Gemini LLM 호출 및 구조화된 JSON 응답 획득]
            // ChatClient를 통해 JSON 스키마를 전달하고 Gemini 모델로부터 레시피 초안 목록(Drafts)을 수신합니다.
            RecipeCandidateDrafts drafts = callRecipeCandidateLLM(templateString);

            // [3단계: LLM 응답 유효성 검증]
            // 응답이 null이거나 후보 목록이 비어 있는 경우 빈 후보군 객체를 반환합니다.
            if (drafts == null || drafts.candidates() == null) {
                return new RecipeCandidates(List.of());
            }

            // [4단계: 도메인 모델 변환 및 서버 측 재료 매칭 연산]
            // LLM이 반환한 초안(Draft)과 보유 재료를 대조하여 '실제 사용된 재료'와 '부족한 핵심 재료'를 서버에서 계산합니다.
            return toCandidates(drafts, ownedIngredients);
        } catch (Exception e) {
            log.error("레시피 후보 생성 실패: {}", e.getMessage(), e);
            return new RecipeCandidates(List.of());
        }
    }

    /**
     * Gemini LLM에 전달할 시스템 지침 및 보유 식재료 기반 레시피 추천 프롬프트 템플릿을 생성합니다.
     *
     * [주요 프롬프트 정책]
     * 1. 보유 재료 목록을 줄 단위로 포맷팅하여 프롬프트의 `{ownedIngredients}` 위치에 주입합니다.
     * 2. 기본 양념(소금, 간장, 물 등)은 사용자가 이미 보유하고 있다고 가정하여 필수 재료에서 제외하도록 지시합니다.
     * 3. 판매 기회 창출: 핵심 재료 중 30~50%는 보유하지 않은 새로운 식재료를 요구하도록 하여 자연스러운 추가 구매를 유도합니다.
     * 4. 현실성 검증: 억지 조합을 배제하고 실제로 조리 가능한 현실적인 요리명을 도출하도록 강제합니다.
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
            아래 '보유 재료'를 활용하여 만들 수 있는 실제 요리 레시피 3가지를 추천합니다.

            <보유 재료(ownedIngredients)>
            {ownedIngredients}

            <정책>
            - 기본 양념(물, 소금, 후추, 식용유, 설탕, 식초, 간장 등)은 사용자가 이미 보유하고 있다고 가정합니다.
            - 따라서 기본 양념은 'recipeIngredientsCore'나 'recipeIngredientsExtra' 목록에 포함시키지 마세요.

            <재료 조합 가이드>
            - '보유 재료'들이 서로 잘 어울리지 않는 경우, 모든 재료를 한 번에 사용하려고 하지 마세요.
            - 대신, '보유 재료'의 일부만 사용하여 현실적이고 맛있는 요리를 만드세요.
            - 예시: '보유 재료'가 ["사과", "계란"]일 때, "사과 계란 찌개" 같은 이상한 요리 대신 "애플파이"(사과 사용)나 "계란찜"(계란 사용)을 제안하는 것이 훨씬 좋습니다.
            - 핵심은 '억지 조합'이 아닌 '현실적인 요리'를 추천하는 것입니다.

            <후보 생성 규칙>
            - 판매 기회를 고려하여, 완벽한 재료가 아닌 '적절히 부족한' 레시피를 우선 생성하세요.
            - 'recipeIngredientsCore' 재료의 30-50% 정도는 '보유 재료'에 없는 새로운 재료로 구성하여, 사용자에게 추가 구매를 자연스럽게 유도하세요.
            - 반드시 3개의 독립적인 레시피 후보를 생성해야 합니다.
            - 각 레시피는 한식, 양식, 중식, 일식 등 다양한 스타일을 가져야 합니다.
            - cookingMethod/difficulty/mealType 조합은 후보마다 달라야 합니다.
            - 각 후보 레시피는 '보유 재료' 중 최소 1개는 반드시 활용해야 합니다.
            - 'recipeIngredientsCore'에는 해당 요리의 정체성을 나타내는 가장 중요한 재료들을 3~5개 선정하세요.
            - 'recipeIngredientsExtra'에는 있으면 요리의 풍미를 더하지만 필수적이지는 않은 재료들을 1~3개 선정하세요.
            - 억지스러운 재료 조합을 피하고, 실제로 사람들이 즐겨 만드는 일반적인 요리를 제안해야 합니다.
            - 재료명은 일반적인 한 단어 명사(예: "돼지고기", "김치", "양파")로 통일하세요. '보유 재료'에 있는 이름에 얽매일 필요 없습니다.

            <현실성 검증>
            - 제안하는 레시피(recipeName)가 실제로 존재하는 요리인지 반드시 확인하세요.
            - 재료 조합이 현실적인지 확인하세요. (예: '삼겹살'과 '양파'는 볶음 요리에 잘 어울리지만, '삼겹살'과 '사과'로 찌개를 만드는 것은 비현실적입니다.)

            <출력 필드 정의>
            - cookingMethod: 조리 방식 ("ONE_POT", "STIR_FRY", "NO_COOK", "OVEN_BAKE")
            - difficulty: 난이도 ("EASY", "NORMAL", "CHALLENGE")
            - mealType: 식사 유형 ("MAIN", "SIDE", "SOUP")
            - recipeName: 요리 이름 (예: "돼지고기 김치찌개")
            - recipeIngredientsCore: 요리의 정체성을 결정하는 핵심 재료 (3~5개).
            - recipeIngredientsExtra: 있으면 더 좋은 추가 재료 (1~3개).
            - instructions: 3~6 단계의 간단한 조리법.
            - staplesAssumed: 기본 양념을 가정했는지 여부 (항상 true로 설정).
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

    /**
     * LLM이 생성한 원시 초안(Draft)을 받아, 서버 측 보유 재료 정합성 검증을 거친 최종 CandidateRecipePlan 목록으로 변환합니다.
     *
     * @param drafts           LLM이 생성한 레시피 초안 래퍼
     * @param ownedIngredients 사용자가 장바구니에 담아둔 실제 보유 식재료 목록
     * @return 서버 검증이 완료된 레시피 후보군 일급 컬렉션
     */
    private RecipeCandidates toCandidates(RecipeCandidateDrafts drafts, List<String> ownedIngredients) {
        // [1단계: 초안 목록 순회 및 서버 측 재료 정합성 계산]
        // CandidateRecipePlan.from()에서 보유 재료와의 교집합(used) 및 차집합(missing)을 직접 연산합니다.
        List<CandidateRecipePlan> candidates = drafts.candidates().stream()
            .filter(draft -> draft != null)
            .map(draft -> CandidateRecipePlan.from(draft, ownedIngredients))
            .toList();

        // [2단계: 생성 결과 상세 로깅]
        log.info("레시피 후보 생성 완료. 후보 개수: {}", candidates.size());
        candidates.forEach(candidate ->
            log.debug("  - 후보: {} ({}/{}/{}), core: {}, extra: {}, used: {}, missing: {}",
                candidate.recipeName(),
                candidate.cookingMethod(),
                candidate.difficulty(),
                candidate.mealType(),
                candidate.recipeIngredientsCore(),
                candidate.recipeIngredientsExtra(),
                candidate.usedOwnedIngredients(),
                candidate.missingIngredientsCore())
        );

        // [3단계: 일급 컬렉션(RecipeCandidates)으로 포장하여 반환]
        return new RecipeCandidates(candidates);
    }
}
