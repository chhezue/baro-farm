package com.barofarm.ai.recommend.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 재료 이름 처리 유틸리티 클래스
public final class IngredientProcessingUtil {

    private IngredientProcessingUtil() {
    }

    // 재료 목록을 정규화하여 null, 빈 문자열, 중복을 제거
    // 예: ["  돼지고기", null, "돼지고기 ", "  ", "소금"] -> ["돼지고기", "소금"]
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

    // 재료 이름을 비교를 위한 정규화 (소문자 변환, 공백 제거)
    // 예: "돼지 고기" -> "돼지고기", "Kimchi" -> "kimchi"
    public static String normalizeForCompare(String s) {
        if (s == null) {
            return "";
        }
        return s.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "");  // 공백만 제거 (LLM이 특수문자 사용하지 않음)
    }

    // 정규화된 재료 목록에서 다른 목록의 재료들을 제거
    // 예: subtractNormalized(["돼지고기", "양파", "마늘"], ["돼지 고기", "소금"])
    //     -> ["양파", "마늘"] (돼지고기는 정규화 후 일치하므로 제거됨)
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
