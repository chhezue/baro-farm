package com.barofarm.buyer.product.infrastructure.kafka.consumer;

import com.barofarm.buyer.product.application.ReviewSummaryService;
import com.barofarm.buyer.product.domain.ReviewSummarySentiment;
import com.barofarm.buyer.product.infrastructure.kafka.consumer.dto.ReviewSummaryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryConsumer {

    private final ReviewSummaryService reviewSummaryService;

    @KafkaListener(
        topics = "review-summary-events",
        groupId = "product-service.review-summary",
        properties = {
            "spring.json.value.default.type="
                + "com.barofarm.buyer.product.infrastructure.kafka.consumer.dto.ReviewSummaryEvent",
            "spring.json.trusted.packages=*"
        }
    )
    public void onMessage(ReviewSummaryEvent event) {
        ReviewSummarySentiment sentiment = ReviewSummarySentiment.valueOf(event.sentiment());
        reviewSummaryService.upsert(event.productId(), sentiment, event.summaryText());
        log.info("✅ [CONSUMER] Review summary saved. productId={}, sentiment={}",
            event.productId(), event.sentiment());
    }
}
