package com.barofarm.support.review.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka로 발행되는 Review 이벤트
 * ai-service에서 소비하여 감정 분석에 활용
 */
@Getter
@Builder
public class ReviewEvent {

    private ReviewEventType type;
    private ReviewEventData data;

    @Getter
    @Builder
    public static class ReviewEventData {
        private UUID reviewId;
        private UUID productId;
        private Integer rating;
        private String content;
        private Instant occurredAt;
    }
}
