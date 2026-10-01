package com.barofarm.ai.search.infrastructure.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.ParameterizedTypeReference;
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

    private final ChatClient chatClient;

    /**
     * 상품명과 카테고리명을 함께 넘겨 요리 재료명을 뽑는다.
     * 카테고리는 상품명만으로 애매한 경우를 좁혀 주는 힌트다. 예를 들어 카테고리가 "제철 과일"로
     * 함께 오면 산지·당도 같은 수식어를 떼고 과일 이름만 남기기가 쉬워진다.
     * LLM 호출이 실패하면 정규식 폴백을 쓰므로 이 메서드는 예외를 던지지 않는다.
     */
    public String normalize(String productName, String categoryName) {
        if (productName == null || productName.isBlank()) {
            return "";
        }
        String category = categoryName == null || categoryName.isBlank() ? NO_CATEGORY : categoryName;
        try {
            NormalizationResponse response = chatClient.prompt()
                .options(ChatOptions.builder().temperature(DETERMINISTIC_TEMPERATURE).build())
                .user(u -> u.text(PROMPT)
                    .param("productName", productName)
                    .param("categoryName", category))
                .call()
                .entity(new ParameterizedTypeReference<NormalizationResponse>() {});

            String normalized = response == null || response.normalizedIngredient() == null
                ? "" : response.normalizedIngredient().trim();

            // 이 쇼핑몰의 카테고리는 전부 농산물(AGRI_*)이라 "재료가 아닌 상품"이라는 경우가 없다.
            // 그래서 빈 값은 재료 아님이 아니라 추출 실패로만 해석하고 정규식 폴백을 태운다.
            if (normalized.isBlank()) {
                log.warn("재료명 추출 결과가 비어 정규식 폴백을 사용합니다: '{}'", productName);
                return applyBasicNormalization(productName);
            }
            log.debug("상품명 정규화: '{}'에서 '{}'을(를) 추출", productName, normalized);
            return normalized;
        } catch (Exception e) {
            log.warn("상품명 정규화 실패, 정규식 폴백 사용: '{}'", productName, e);
            return applyBasicNormalization(productName);
        }
    }

    // LLM을 쓰지 않고 규칙만으로 브랜드·수량·단위·등급 표현을 걷어낸다.
    private String applyBasicNormalization(String productName) {
        return productName
            .replaceAll("\\d+(개|구|마리|팩|송이|포기|단|봉|박스)\\s*", "")
            .replaceAll("(?i)\\d+(kg|g|ml|l)\\s*", "")
            .replaceAll("무항생제|신선한|곰곰|국산|수입|프리미엄|유기농|친환경|GAP|저탄소", "")
            .replaceAll("하림|농협|풀무원|CJ|오뚜기|대상|삼양|청정원|동원", "")
            .replaceAll("특품|등급|1등급|2등급|특상", "")
            .replaceAll("\\([^)]*\\)", "")
            .replaceAll("[,·]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private record NormalizationResponse(String normalizedIngredient) {
    }

    private static final String PROMPT = """
        당신은 신선 농산물 이커머스 상품명에서 '요리에 쓰이는 재료명'만 뽑아내는 역할입니다.
        이 쇼핑몰은 농산물만 취급하므로 입력으로 들어오는 상품은 모두 식재료입니다.

        <입력>
        상품명: {productName}
        카테고리: {categoryName}

        <규칙>
        - 브랜드명, 산지, 수량, 무게, 단위, 포장 형태, 등급·마케팅 수식어는 모두 제거하세요.
        - 결과는 공백 없는 한 단어의 일반 명사로 쓰세요. 예를 들어 "계란", "돼지고기", "애호박"처럼 씁니다.
        - 카테고리는 힌트로만 쓰고 그대로 답으로 쓰지는 마세요.
          카테고리가 "뿌리채소"여도 답은 "감자"처럼 구체적인 재료명이어야 합니다.
        - 상품명에 근거가 없는 재료를 지어내지 마세요.

        <예시>
        상품명 "곰곰 무항생제 신선한 대란, 30구, 1개" / 카테고리 "계란" -> "계란"
        상품명 "제주 서귀포 고당도 노지 감귤 3kg (박스)" / 카테고리 "제철 과일" -> "감귤"
        상품명 "횡성 한우 등심 300g 1등급" / 카테고리 "육류" -> "소고기"
        """;
}
