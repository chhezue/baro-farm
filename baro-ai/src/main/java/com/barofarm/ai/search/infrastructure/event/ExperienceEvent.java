package com.barofarm.ai.search.infrastructure.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka에서 수신하는 Experience 이벤트
 * baro-support에서 발행된 이벤트를 수신하여 Elasticsearch에 인덱싱
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
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
