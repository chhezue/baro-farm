package com.barofarm.ai.recommend.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 요리 재료 문자열의 정제, 소문자화, 공백 제거 및 목록 차집합 비교를 수행하는 유틸리티 클래스입니다.
 *
 * [주요 용도]
 * - 레시피 생성 시 보유 재료와 필요 재료 간의 오탈자/공백 차이로 인한 매칭 불일치를 방지하고,
 *   서버 측에서 정확한 사용 재료 및 부족한 재료를 계산하기 위해 사용됩니다.
 */
public final class IngredientProcessingUtil {

    private IngredientProcessingUtil() {
    }

    /**
     * 재료명 목록에서 null, 빈 문자열을 걸러내고 앞뒤 공백을 제거한 뒤 중복을 배제합니다.
     *
     * <p>예: `["  돼지고기", null, "돼지고기 ", "  ", "소금"]` -> `["돼지고기", "소금"]`
     *
     * @param list 원본 문자열 리스트
     * @return 정제된 고유 재료명 리스트 (null 입력 시 빈 리스트 반환)
     */
    public static List<String> normalizeList(List<String> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
    }

    /**
     * 상품을 찾을 때 쓸 검색어를 재료명에서 뽑습니다. 공백이 있으면 마지막 어절만 씁니다 —
     * `"국거리용 소고기"`는 `"소고기"`가 됩니다.
     *
     * <p>프롬프트는 재료명을 "일반적인 한 단어 명사"로 쓰라고 요구하지만 LLM이 늘 지키지는 않습니다.
     * 그 어긋남이 그대로 Elasticsearch 키워드가 되면 색인된 재료 표준명(`소고기`)과 정확 일치하지 않아
     * 부족 재료 상품 카드가 통째로 비어 버립니다. 실제로 `"국거리용 소고기"`가 그렇게 나왔습니다.
     * 근거: docs/audits/cart-recipe/006-missing-ingredient-product-match.md A-2
     *
     * <p>이 변환은 **상품 검색어를 만들 때만** 씁니다. 재료끼리 비교하는 {@link #normalizeForCompare}
     * 경로에는 쓰지 않습니다 — 거기서 `"돼지 고기"`를 `"고기"`로 줄이면 `"돼지고기"`와 같은 재료라는
     * 판정이 깨집니다(`CandidateRecipePlanTest`가 이 경우를 고정하고 있습니다). 사용자에게 보이는
     * `missingCoreIngredients`도 원문 그대로 둡니다 — "국거리용 소고기"가 더 친절한 안내라서입니다.
     */
    public static String toSearchKeyword(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        return lastSpace < 0 ? trimmed : trimmed.substring(lastSpace + 1);
    }

    /**
     * 재료명 동등 비교를 위해 소문자로 변환하고 내부의 모든 공백(띄어쓰기)을 제거합니다.
     *
     * <p>예: `"돼지 고기"` -> `"돼지고기"`, `"Kimchi "` -> `"kimchi"`
     *
     * @param s 비교할 원본 재료명
     * @return 공백이 제거되고 소문자화된 비교용 표준 문자열 (null 입력 시 빈 문자열 반환)
     */
    public static String normalizeForCompare(String s) {
        if (s == null) {
            return "";
        }
        return s.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "");
    }

    /**
     * 두 재료 목록을 비교하여 목록 a에서 목록 b에 포함된 재료들을 제거한 차집합(a - b)을 반환합니다.
     * 비교는 {@link #normalizeForCompare(String)} 기준으로 수행되므로 띄어쓰기나 대소문자가 달라도 정확히 일치로 처리됩니다.
     *
     * <p>예: `subtractNormalized(["돼지고기", "양파", "마늘"], ["돼지 고기", "소금"])`
     * -> `["양파", "마늘"]` ("돼지고기"는 "돼지 고기"와 일치하므로 제거됨)
     *
     * @param a 기준 재료 목록 (필요 재료)
     * @param b 차감할 재료 목록 (보유 재료)
     * @return b에 존재하지 않는 a의 재료 목록 (부족한 재료)
     */
    public static List<String> subtractNormalized(List<String> a, List<String> b) {
        Set<String> bNorm = (b == null ? List.<String>of() : b).stream()
            .map(IngredientProcessingUtil::normalizeForCompare)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

        return normalizeList(a).stream()
            .filter(x -> !bNorm.contains(normalizeForCompare(x)))
            .toList();
    }
}
