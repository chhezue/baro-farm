package com.barofarm.ai.search.infrastructure.llm;

import com.barofarm.ai.common.llm.LlmCallKind;
import com.barofarm.ai.common.llm.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 상품명에서 요리 재료명을 뽑아 {@code ProductDocument.ingredientName}에 넣을 값을 만든다.
 * 색인 시점(Kafka 상품 이벤트)에만 호출되며, 추천 요청 경로에서는 호출하지 않는다.
 * 근거: docs/decisions/002-ingredient-name-at-index-time.md
 *
 * <p>호출 자체(타임아웃·재시도·구조화 출력·지표)는 {@link LlmGateway}가 맡는다. 이 클래스가 정하는 것은
 * 프롬프트 문구와 빈 값의 의미뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIngredientNormalizer {

    // 상품명에서 재료명을 뽑는 건 창작이 아니라 추출이다. 전역 기본값(0.7)은 레시피 후보 생성에
    // 맞춘 값이라, 같은 상품에 매번 다른 재료명이 나오지 않도록 이 호출만 0으로 낮춘다.
    private static final double DETERMINISTIC_TEMPERATURE = 0.0;

    private static final String NO_CATEGORY = "(카테고리 없음)";

    private final LlmGateway llmGateway;

    /**
     * 상품명과 카테고리명을 함께 넘겨 요리 재료명을 뽑는다.
     * 카테고리는 상품명만으로 애매한 경우를 좁혀 주는 힌트다. 예를 들어 카테고리가 "제철 과일"로
     * 함께 오면 산지·당도 같은 수식어를 떼고 과일 이름만 남기기가 쉬워진다.
     * 호출이 실패하거나 빈 답이 오면 예외를 던지지 않고 빈 문자열을 돌려준다.
     * 빈 값은 "아직 재료명을 모른다"는 표시다. 레시피 추천은 빈 값인 상품을 보유 재료에서 건너뛰고
     * 재색인이 필요한 건수로 센다. 정규식으로 대충 만든 값을 넣으면 그 표시가 사라지고
     * "대란" 같은 틀린 재료명이 정상 값처럼 남으므로 폴백은 두지 않는다.
     */
    public String normalize(String productName, String categoryName) {
        // [1단계: 상품명 유효성 검증]
        if (productName == null || productName.isBlank()) {
            return "";
        }
        String category = categoryName == null || categoryName.isBlank() ? NO_CATEGORY : categoryName;

        // [2단계: 관문을 통해 호출 — 상품명·카테고리는 판매자가 쓴 값이라 그대로 끼워 넣지 않는다]
        String prompt = PROMPT
            .replace("{productName}", LlmGateway.asData(productName))
            .replace("{categoryName}", LlmGateway.asData(category));

        String normalized = llmGateway
            .call(LlmCallKind.BATCH, prompt, NormalizationResponse.class, DETERMINISTIC_TEMPERATURE)
            .map(NormalizationResponse::normalizedIngredient)
            .map(String::trim)
            .orElse("");

        // 이 쇼핑몰은 농산물과 그 가공·발효식품만 팔아 "재료가 아닌 상품"이라는 경우가 없다.
        // 그래서 빈 답은 재료 아님이 아니라 추출 실패로만 해석한다.
        if (normalized.isBlank()) {
            log.warn("재료명 추출 결과가 비어 빈 값으로 색인합니다. 재색인 대상: '{}'", productName);
            return "";
        }

        log.debug("상품명 정규화: '{}'에서 '{}'을(를) 추출", productName, normalized);
        return normalized;
    }

    // 패키지 가시성: 같은 패키지의 단위 테스트가 관문의 응답을 흉내 낼 때 만든다
    record NormalizationResponse(String normalizedIngredient) {
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
