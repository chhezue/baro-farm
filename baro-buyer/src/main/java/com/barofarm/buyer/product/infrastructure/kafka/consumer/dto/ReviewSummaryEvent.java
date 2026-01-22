package com.barofarm.buyer.product.infrastructure.kafka.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewSummaryEvent(UUID productId,
                                 String sentiment,
                                 String summaryText,
                                 LocalDateTime updatedAt) {
}
