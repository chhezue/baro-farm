package com.barofarm.support.experience.application.event;

import com.barofarm.support.experience.domain.Experience;
import com.barofarm.support.experience.event.ExperienceEvent;
import com.barofarm.support.experience.event.ExperienceEvent.ExperienceEventType;
import com.barofarm.support.experience.infrastructure.kafka.ExperienceEventProducer;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Experience 도메인을 Kafka 이벤트로 변환하고 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceEventPublisher {

    private final ExperienceEventProducer producer;

    // 체험 생성 시 발행
    public void publishExperienceCreated(Experience experience) {
        log.info(
            "📨 [EVENT_PUBLISHER] Building EXPERIENCE_CREATED - ID: {}, Name: {}, Price: {}",
            experience.getExperienceId(), experience.getTitle(), experience.getPricePerPerson());
        ExperienceEvent event = buildEvent(ExperienceEventType.EXPERIENCE_CREATED, experience);
        log.info("📨 [EVENT_PUBLISHER] Event built successfully - Type: {}, Experience ID: {}",
            event.getType(), event.getData().getExperienceId());
        producer.send(event);
    }

    // 체험 업데이트 시 발행
    public void publishExperienceUpdated(Experience experience) {
        producer.send(buildEvent(ExperienceEventType.EXPERIENCE_UPDATED, experience));
    }

    // 체험 삭제 시 발행
    public void publishExperienceDeleted(Experience experience) {
        producer.send(buildEvent(ExperienceEventType.EXPERIENCE_DELETED, experience));
    }

    private ExperienceEvent buildEvent(ExperienceEventType type, Experience experience) {
        return ExperienceEvent.builder()
            .type(type)
            .data(ExperienceEvent.ExperienceEventData.builder()
                .experienceId(experience.getExperienceId())
                .experienceName(experience.getTitle())
                .pricePerPerson(experience.getPricePerPerson())
                .capacity(experience.getCapacity())
                .durationMinutes(experience.getDurationMinutes())
                .availableStartDate(experience.getAvailableStartDate())
                .availableEndDate(experience.getAvailableEndDate())
                .status(experience.getStatus().name()) // enum
                .updatedAt(Instant.now())
                .build()
            )
            .build();
    }
}
