package com.barofarm.shopping.product.infrastructure.kafka.consumer;

import com.barofarm.shopping.product.application.ReviewSummaryService;
import com.barofarm.shopping.product.domain.reviewsummary.ReviewSummarySentiment;
import com.barofarm.shopping.product.infrastructure.kafka.consumer.dto.ReviewSummaryEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewSummaryConsumer {

    private final ReviewSummaryService reviewSummaryService;

    @KafkaListener(
        topics = "review-summary-events",
        groupId = "product-service.review-summary",
        properties = {
            "spring.json.use.type.headers=false",
            "spring.json.remove.type.headers=true",
            "spring.json.value.default.type="
                + "com.barofarm.shopping.product.infrastructure.kafka.consumer.dto.ReviewSummaryEvent",
            "spring.json.trusted.packages=*"
        }
    )
    public void onMessage(ReviewSummaryEvent event) {
        ReviewSummarySentiment sentiment = ReviewSummarySentiment.valueOf(event.sentiment());
        LocalDateTime updatedAt = LocalDateTime.ofInstant(event.updatedAt(), ZoneId.of("Asia/Seoul"));
        reviewSummaryService.upsert(event.productId(), sentiment, event.summaryText(), updatedAt);
    }
}
