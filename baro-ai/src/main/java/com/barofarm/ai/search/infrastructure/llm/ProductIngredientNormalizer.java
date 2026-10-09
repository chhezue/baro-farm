package com.barofarm.ai.search.infrastructure.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 상품명에서 요리 재료명을 뽑아 {@code ProductDocument.ingredientName}에 넣을 값을 만든다.
 * 색인 시점(Kafka 상품 이벤트)에만 호출되며, 추천 요청 경로에서는 호출하지 않는다.
 * 근거: docs/decisions/002-ingredient-name-at-index-time.md
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIngredientNormalizer {

    // 상품명에서 재료명을 뽑는 건 창작이 아니라 추출이다. 전역 기본값(0.7)은 레시피 후보 생성에
    // 맞춘 값이라, 같은 상품에 매번 다른 재료명이 나오지 않도록 이 호출만 0으로 낮춘다.
    private static final double DETERMINISTIC_TEMPERATURE = 0.0;

    private static final String NO_CATEGORY = "(카테고리 없음)";

    // 응답 형식을 Gemini API에 JSON 스키마로 못 박는다. 근거: docs/audits/cross-cutting/001-llm-call-infra.md A-2
    private static final BeanOutputConverter<NormalizationResponse> RESPONSE_CONVERTER =
        new BeanOutputConverter<>(NormalizationResponse.class);

    private final ChatClient chatClient;

    /**
     * 상품명과 카테고리명을 함께 넘겨 요리 재료명을 뽑는다.
     * 카테고리는 상품명만으로 애매한 경우를 좁혀 주는 힌트다. 예를 들어 카테고리가 "제철 과일"로
     * 함께 오면 산지·당도 같은 수식어를 떼고 과일 이름만 남기기가 쉬워진다.
     * LLM 호출이 실패하거나 빈 답이 오면 예외를 던지지 않고 빈 문자열을 돌려준다.
     * 빈 값은 "아직 재료명을 모른다"는 표시다. 레시피 추천은 빈 값인 상품을 보유 재료에서 건너뛰고
     * 재색인이 필요한 건수로 센다. 정규식으로 대충 만든 값을 넣으면 그 표시가 사라지고
     * "대란" 같은 틀린 재료명이 정상 값처럼 남으므로 폴백은 두지 않는다.
     */
    public String normalize(String productName, String categoryName) {
        // [1단계: 상품명 유효성 검증]
        // 상품명이 비어있는 경우 재료를 추출할 수 없으므로 즉시 빈 문자열을 반환합니다.
        if (productName == null || productName.isBlank()) {
            return "";
        }
        String category = categoryName == null || categoryName.isBlank() ? NO_CATEGORY : categoryName;

        try {
            // [2단계: Gemini LLM 호출을 통한 재료명 추출]
            // temperature=0.0(결정적 추출) 및 JSON 스키마를 강제하여 상품명과 카테고리로부터 재료명을 추출합니다.
            String json = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                    .temperature(DETERMINISTIC_TEMPERATURE)
                    .responseMimeType(MediaType.APPLICATION_JSON_VALUE)
                    .responseSchema(RESPONSE_CONVERTER.getJsonSchema())
                    .build())
                .user(u -> u.text(PROMPT)
                    .param("productName", productName)
                    .param("categoryName", category))
                .call()
                .content();

            // [3단계: JSON 응답 파싱 및 문자열 정제]
            NormalizationResponse response = RESPONSE_CONVERTER.convert(json);

            String normalized = response == null || response.normalizedIngredient() == null
                ? "" : response.normalizedIngredient().trim();

            // 이 쇼핑몰은 농산물과 그 가공·발효식품만 팔아 "재료가 아닌 상품"이라는 경우가 없다.
            // 그래서 빈 답은 재료 아님이 아니라 추출 실패로만 해석한다.
            if (normalized.isBlank()) {
                log.warn("재료명 추출 결과가 비어 빈 값으로 색인합니다. 재색인 대상: '{}'", productName);
                return "";
            }

            // [4단계: 정규화된 재료명 반환]
            log.debug("상품명 정규화: '{}'에서 '{}'을(를) 추출", productName, normalized);
            return normalized;
        } catch (Exception e) {
            // 외부 AI API 호출 실패 시 색인 전체 실패를 막기 위해 빈 문자열을 반환하고 경고 로그를 남깁니다.
            log.warn("재료명 추출 실패로 빈 값으로 색인합니다. 재색인 대상: '{}'", productName, e);
            return "";
        }
    }

    private record NormalizationResponse(String normalizedIngredient) {
    }

    private static final String PROMPT = """
        당신은 신선 농산물 이커머스 상품명에서 '요리에 쓰이는 재료명'만 뽑아내는 역할입니다.
        이 쇼핑몰은 농산물과 그것으로 만든 가공·발효식품만 취급하므로 입력으로 들어오는 상품은 모두 식재료입니다.

        <입력>
        상품명: {productName}
        카테고리: {categoryName}

        <규칙>
        - 브랜드명, 산지, 수량, 무게, 단위, 포장 형태, 등급·마케팅 수식어는 모두 제거하세요.
        - 이미 만들어진 식품은 그 완성품 이름이 답입니다. 원재료로 되돌리지 마세요.
          김치의 답은 "배추"가 아니라 김치이고, 된장의 답은 "콩"이 아니라 된장입니다.
        - 레시피에 적히는 이름으로 쓰세요. 담는 모양이나 써는 방식을 가리키는 말은 빼고,
          요리에서 서로 바꿔 쓸 수 없는 종류를 가리키는 말은 남깁니다.
        - 결과는 공백 없는 한 단어의 일반 명사로 쓰세요. 예를 들어 "계란", "돼지고기", "애호박"처럼 씁니다.
        - 카테고리는 힌트로만 쓰고 그대로 답으로 쓰지는 마세요.
          카테고리가 "뿌리채소"여도 답은 "감자"처럼 구체적인 재료명이어야 합니다.
          다만 답이 카테고리와 비슷해지는 것 자체는 괜찮습니다. 그게 그 상품의 재료명이라면 그대로 쓰세요.
        - 상품명에 근거가 없는 재료를 지어내지 마세요.

        <예시>
        상품명 "곰곰 무항생제 신선한 대란, 30구, 1개" / 카테고리 "계란" -> "계란"
        상품명 "제주 서귀포 고당도 노지 감귤 3kg (박스)" / 카테고리 "제철 과일" -> "감귤"
        상품명 "횡성 한우 등심 300g 1등급" / 카테고리 "육류" -> "소고기"
        상품명 "아삭 총각김치 1kg" / 카테고리 "김치류" -> "총각김치"
        상품명 "새콤한 맛김치 1kg" / 카테고리 "김치류" -> "김치"
        상품명 "구수한 청국장 500g" / 카테고리 "장류" -> "청국장"
        상품명 "고소한 콩비지 300g" / 카테고리 "두부·콩가공" -> "콩비지"
        """;
}
