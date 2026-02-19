package com.barofarm.order.review.application.dto.request;

import com.barofarm.order.review.domain.ReviewStatus;
import com.barofarm.order.review.presentation.dto.ReviewVisibility;
import java.util.UUID;

public record ReviewUpdateCommand(
    UUID reviewId,
    UUID userId,
    Integer rating,
    ReviewVisibility visibility,
    String content,
    ReviewImageUpdateMode imageUpdateMode) {

    public ReviewStatus toReviewStatus() {
        return ReviewStatus.fromVisibility(visibility);
    }
}
