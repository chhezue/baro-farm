package com.barofarm.order.review.application.dto.request;

import com.barofarm.order.review.domain.ReviewStatus;
import com.barofarm.order.review.presentation.dto.ReviewVisibility;
import java.util.UUID;

public record ReviewCreateCommand(
    UUID orderItemId,
    UUID userId,
    UUID productId,
    Integer rating,
    ReviewVisibility visibility,
    String content) {

    public ReviewStatus toReviewStatus() {
        return ReviewStatus.fromVisibility(visibility);
    }
}
