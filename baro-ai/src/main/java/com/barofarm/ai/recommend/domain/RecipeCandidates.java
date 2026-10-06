package com.barofarm.ai.recommend.domain;

import java.util.List;
import java.util.Optional;

/**
 * 생성된 복수의 레시피 계획 후보(CandidateRecipePlan)들을 캡슐화하는 일급 컬렉션 도메인 레코드입니다.
 *
 * [선택 정책]
 * - 프롬프트 수준에서 현실적인 조리 가능성과 적절한 부족 재료(30~50%) 비율을 엄격히 통제하여 생성하므로,
 *   임의의 가중치 점수 계산 대신 LLM이 추천 우선순위로 생성한 첫 번째 유효 후보를 신뢰하여 채택합니다.
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
