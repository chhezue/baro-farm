package com.barofarm.shopping.product.domain.reviewsummary;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSummaryRepository extends JpaRepository<ReviewSummary, Long> {
    Optional<ReviewSummary> findByProductIdAndSentiment(UUID productId, ReviewSummarySentiment sentiment);
}
