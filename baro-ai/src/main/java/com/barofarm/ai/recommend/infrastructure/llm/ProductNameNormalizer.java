package com.barofarm.ai.recommend.infrastructure.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 상품명 정규화 유틸리티
 * LLM을 활용하여 상품명에서 핵심 재료를 추출합니다.
 * 다양한 신선품(과일, 채소, 육류, 유제품, 가공식품 등)에 적용 가능합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductNameNormalizer {

    private final ChatClient chatClient;

    /**
     * [메인 메서드] 상품명에서 핵심 재료 추출 (LLM 활용)
     * 복잡한 상품명에서 브랜드/수량/단위를 제거하고 실제 재료명만 추출
     *
     * @param productName 원본 상품명 (예: "곰곰 무항생제 신선한 대란, 30구, 1개")
     * @return 정규화된 재료명 (예: "계란") 또는 빈 문자열 (추출 실패시)
     */
    public String normalizeForRecipeIngredient(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return "";
        }

        String prompt = buildNormalizationPrompt(productName);

        try {
            NormalizationResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(new ParameterizedTypeReference<NormalizationResponse>() {});

            String normalized = response != null && response.normalizedIngredient() != null
                ? response.normalizedIngredient().trim()
                : "";

            if (!normalized.isEmpty()) {
                log.debug("상품명 정규화: '{}' -> '{}'", productName, normalized);
            }

            return normalized;

        } catch (Exception e) {
            log.warn("상품명 정규화 실패: '{}', 원본 유지", productName, e);
            return applyBasicNormalization(productName);
        }
    }

    /**
     * [폴백 메서드] 기본 정규화 (LLM 실패 시 사용)
     * 규칙 기반으로 브랜드/수량/단위를 제거 (LLM 호출 없이 빠른 처리)
     * 다양한 신선품(과일, 채소, 육류, 유제품 등)에 적용될 수 있도록 범용적으로 구성
     *
     * @param productName 원본 상품명
     * @return 기본 정규화된 상품명
     */
    private String applyBasicNormalization(String productName) {
        return productName
            // 수량 단위 제거 (개, 구, 마리, 팩, 송이, 등)
            .replaceAll("\\d+개\\s*", "")     // "1개", "2개" 제거
            .replaceAll("\\d+구\\s*", "")     // "30구" 제거
            .replaceAll("\\d+마리\\s*", "")   // "1마리" 제거
            .replaceAll("\\d+팩\\s*", "")     // "1팩" 제거
            .replaceAll("\\d+송이\\s*", "")   // "1송이" 제거
            .replaceAll("\\d+포기\\s*", "")   // "1포기" 제거
            .replaceAll("\\d+단\\s*", "")     // "1단" 제거
            .replaceAll("\\d+봉\\s*", "")     // "1봉" 제거
            .replaceAll("\\d+박스\\s*", "")   // "1박스" 제거

            // 무게 단위 제거 (g, kg, ml, l 등)
            .replaceAll("\\d+g\\s*", "")      // "500g" 제거
            .replaceAll("\\d+kg\\s*", "")     // "1kg" 제거
            .replaceAll("\\d+ml\\s*", "")     // "500ml" 제거
            .replaceAll("\\d+l\\s*", "")      // "1l" 제거

            // 브랜드/품질 표현 제거 (더 다양하게)
            .replaceAll("무항생제|신선한|곰곰|국산|수입|프리미엄|유기농|친환경|GAP|저탄소", "")
            .replaceAll("하림|농협|풀무원|CJ|오뚜기|대상|삼양|청정원|동원", "") // 주요 브랜드
            .replaceAll("특품|상품|상품|등급|1등급|2등급|특상|상|중|하", "") // 등급 표현

            // 기타 불필요한 표현 제거
            .replaceAll("\\([^)]*\\)", "")     // 괄호 안 내용 제거
            .replaceAll("\\s+", " ")           // 연속된 공백을 하나로
            .trim();
    }

    /**
     * [프롬프트 생성] LLM용 정규화 프롬프트 생성
     * 상품명을 입력받아 핵심 재료를 추출하도록 유도하는 상세한 프롬프트 작성
     *
     * @param productName 정규화할 상품명
     * @return LLM에게 전달할 완성된 프롬프트
     */
    private String buildNormalizationPrompt(String productName) {
        return String.format("""
            당신은 농산물 이커머스 상품명을 분석하여 실제 요리에 사용할 수 있는 '핵심 재료명'만 추출하는 전문가입니다.

            <상품명>
            %s

            <추출 규칙>
            - 상품명에서 브랜드, 수량, 단위, 포장 정보, 품질 표시 등을 모두 제거하세요.
            - 실제 요리에 사용되는 재료의 이름만 남기세요.
            - 한 단어로 응답하세요 (공백 없이).
            - 예시 (다양한 카테고리):
              * 계란/유제품: "곰곰 무항생제 신선한 대란, 30구, 1개" → "계란", "서울우유 흰우유 1L" → "우유"
              * 과일: "청송사과 프리미엄 1kg" → "사과", "바나나 1송이" → "바나나", "국산 딸기 500g" → "딸기"
              * 채소: "친환경 상추 100g" → "상추", "대파 1단" → "대파", "유기농 토마토 1kg" → "토마토"
              * 육류: "무항생제 닭가슴살 500g" → "닭가슴살", "한우 등심 300g" → "소고기"
              * 수산물: "생연어 필레 200g" → "연어", "국산 고등어 1마리" → "고등어"
              * 가공식품: "국산 된장 500g" → "된장", "CJ 고추장 1kg" → "고추장", "오뚜기 케찹 500g" → "케찹"

            <주의사항>
            - 반드시 상품명에 포함된 재료만 추출하세요.
            - 상상해서 재료를 추가하지 마세요.
            - 모호한 경우 빈 문자열("")을 반환하세요.
            - 브랜드명, 품질표시(프리미엄, 유기농 등), 수량/단위는 모두 제거하세요.

            <출력(JSON only)>
            {{
              "normalizedIngredient": "핵심재료명"
            }}
            """, productName);
    }

    private record NormalizationResponse(String normalizedIngredient) { }
}
