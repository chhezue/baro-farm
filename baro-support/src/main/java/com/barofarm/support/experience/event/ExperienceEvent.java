package com.barofarm.support.experience.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka로 발행되는 Experience 이벤트
 * ai-service에서 소비하여 Elasticsearch에 인덱싱
 */
@Getter
@Builder
public class ExperienceEvent {

    private ExperienceEventType type;
    private ExperienceEventData data;

    public enum ExperienceEventType {
        EXPERIENCE_CREATED,
        EXPERIENCE_UPDATED,
        EXPERIENCE_DELETED
    }

    @Getter
    @Builder
    public static class ExperienceEventData {
        private UUID experienceId;
        private String experienceName; // title
        private Long pricePerPerson;
        private Integer capacity;
        private Integer durationMinutes;
        private LocalDateTime availableStartDate;
        private LocalDateTime availableEndDate;
        private String status;
        private Instant updatedAt;
    }
}
