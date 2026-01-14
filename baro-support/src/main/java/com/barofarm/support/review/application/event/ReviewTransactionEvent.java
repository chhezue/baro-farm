package com.barofarm.support.review.application.event;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReviewTransactionEvent {

    private final ReviewOperation operation;
    private final UUID reviewId;
    private final UUID productId;
    private final Integer rating;
    private final String content;
    private final LocalDateTime occurredAt;

    public enum ReviewOperation {
        CREATED, UPDATED, DELETED
    }
}
