package com.barofarm.buyer.product.application;

import com.barofarm.buyer.product.domain.ReviewSummary;
import com.barofarm.buyer.product.domain.ReviewSummaryRepository;
import com.barofarm.buyer.product.domain.ReviewSummarySentiment;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

    private final ReviewSummaryRepository reviewSummaryRepository;

    @Transactional
    public void upsert(UUID productId, ReviewSummarySentiment sentiment, String summaryText) {
        ReviewSummary summary = reviewSummaryRepository
            .findByProductIdAndSentiment(productId, sentiment)
            .orElseGet(() -> ReviewSummary.create(productId, sentiment, summaryText));

        summary.updateSummary(summaryText);
        reviewSummaryRepository.save(summary);
    }
}
