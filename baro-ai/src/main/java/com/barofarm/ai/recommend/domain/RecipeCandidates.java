package com.barofarm.ai.recommend.domain;

import java.util.List;
import java.util.Optional;

/**
 * 생성된 복수의 레시피 계획 후보(CandidateRecipePlan)들을 캡슐화하는 일급 컬렉션 도메인 레코드입니다.
 *
 * [선택 정책]
 * - 프롬프트가 후보를 1개만 요구하므로(2026-09-06 재설계) 후보 간 비교가 필요 없고,
 *   임의의 가중치 점수 계산 대신 이름이 정상적으로 채워진 첫 번째 후보를 그대로 채택합니다.
 * - 후보가 1개여도 이 컬렉션을 유지하는 이유는 LLM이 여전히 배열로 응답하기 때문입니다 —
 *   이름이 빈 응답을 걸러내는 방어가 필요합니다.
 *
 * @param candidates 검증 완료된 레시피 계획 목록
 */
public record RecipeCandidates(List<CandidateRecipePlan> candidates) {

    /**
     * 후보 목록 중 레시피 명이 정상적으로 존재하는 첫 번째 유효 레시피를 선택합니다.
     *
     * @return 선택된 레시피 계획 (유효 후보가 없거나 비어있는 경우 Optional.empty())
     */
    public Optional<CandidateRecipePlan> chooseFirst() {
        // [1단계: 후보 목록 null 체크]
        if (candidates == null) {
            return Optional.empty();
        }

        // [2단계: 요리명이 비어있지 않은 첫 번째 유효 후보 탐색 및 반환]
        return candidates.stream()
            .filter(c -> c != null && c.recipeName() != null && !c.recipeName().isBlank())
            .findFirst();
    }
}
