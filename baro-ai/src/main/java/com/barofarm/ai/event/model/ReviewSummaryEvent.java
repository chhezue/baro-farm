package com.barofarm.ai.event.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewSummaryEvent(UUID productId,
                                 String sentiment,
                                 String summaryText,
                                 LocalDateTime updatedAt) {
}
