package com.barofarm.ai.review.application.bestreview;

import com.barofarm.ai.review.domain.bestreview.BestReviewCandidateDocument;
import com.barofarm.ai.review.domain.review.Sentiment;
import com.barofarm.ai.review.infrastructure.bestreview.BestReviewCandidateRepository;
import com.barofarm.ai.review.infrastructure.bestreview.ReviewCandidateQueryRepository;
import com.barofarm.ai.review.infrastructure.bestreview.ReviewCandidateQueryRepository.CandidateResult;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BestReviewCandidateService {

    private static final int CANDIDATE_SIZE = 10;
    private static final double REPLACE_THRESHOLD = 0.5;

    private final ReviewCandidateQueryRepository queryRepository;    // 쿼리 그러면
    private final BestReviewCandidateRepository candidateRepository; // 후보
    private final BestReviewReplacementPolicy replacementPolicy;     // 교체 정책

    public void refreshProduct(String productId) {
        CandidateResult result;
        try {
            result = queryRepository.fetchCandidates(productId, CANDIDATE_SIZE);   // productId의 후보 생성
        } catch (IOException e) {
            log.warn("Failed to fetch candidates. productId={}", productId, e);
            return;
        }

        refreshBySentiment(productId, Sentiment.POSITIVE, result.positiveCount(), result.positiveReviewIds());
        refreshBySentiment(productId, Sentiment.NEGATIVE, result.negativeCount(), result.negativeReviewIds());
    }

    private void refreshBySentiment(String productId, Sentiment sentiment, long count, List<String> newIds) {
        if (count < CANDIDATE_SIZE) {
            return;
        }

        String docId = BestReviewCandidateDocument.buildId(productId, sentiment);
        Optional<BestReviewCandidateDocument> existing = candidateRepository.findById(docId);
        List<String> existingIds = existing.map(BestReviewCandidateDocument::getReviewIds).orElse(List.of());

        boolean shouldReplace = replacementPolicy.shouldReplace(existingIds, newIds, REPLACE_THRESHOLD);
        if (!shouldReplace) {
            return;
        }

        BestReviewCandidateDocument updated =
            new BestReviewCandidateDocument(productId, sentiment, newIds, LocalDateTime.now());
        candidateRepository.save(updated);
    }
}
