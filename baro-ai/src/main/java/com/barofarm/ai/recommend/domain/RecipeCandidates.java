package com.barofarm.ai.recommend.domain;


import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;

// AI가 생성한 레시피 후보 목록을 관리하는 도메인
@Data
@NoArgsConstructor
public class RecipeCandidates {

    private List<CandidateRecipePlan> candidates; // AI가 추천한 레시피 후보 목록

    // AI가 추천한 레시피 후보 목록을 관리하는 도메인
    public RecipeCandidates(List<CandidateRecipePlan> candidates) {
        this.candidates = candidates;
    }

    // AI가 추천한 레시피 후보 목록을 반환
    public List<CandidateRecipePlan> candidates() {
        return candidates;
    }

    // 여러 레시피 후보 중 최적의 하나를 선택
    public Optional<CandidateRecipePlan> chooseBest(List<String> ownedNames) {
        if (this.candidates == null || this.candidates.isEmpty()) {
            return Optional.empty();
        }

        Set<String> ownedNorm = (ownedNames == null ? List.<String>of() : ownedNames).stream()
            .map(IngredientProcessingUtil::normalizeForCompare)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

        // 동점자 처리를 위한 난이도 점수화 (쉬운 난이도가 높은 점수)
        Map<String, Integer> difficultyScores = Map.of("EASY", 3, "NORMAL", 2, "CHALLENGE", 1);

        List<ScoredCandidate> scoredCandidates = this.candidates.stream()
            .filter(c -> c != null && c.getRecipeName() != null && !c.getRecipeName().isBlank())
            .map(c -> new ScoredCandidate(c, c.calculateScore(ownedNorm)))
            .collect(Collectors.toList());

        // 동점자 발생 시 랜덤 선택을 위해 목록 셔플 (동점자 처리)
        Collections.shuffle(scoredCandidates);

        return scoredCandidates.stream()
            .max(Comparator.comparingInt(ScoredCandidate::score)
                .thenComparingInt(
                    sc -> difficultyScores.getOrDefault(sc.candidate().getDifficulty(), 0)))
            .map(ScoredCandidate::candidate);
    }

    // 레시피 후보의 점수를 함께 저장하는 내부 record
    private record ScoredCandidate(CandidateRecipePlan candidate, int score) {

    }
}
