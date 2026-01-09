package com.barofarm.ai.search.infrastructure.event;

import com.barofarm.ai.search.application.ExperienceSearchService;
import com.barofarm.ai.search.application.dto.experience.ExperienceIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Experience 이벤트 Kafka Consumer
 * baro-support에서 발행된 experience-events 토픽을 수신하여 Elasticsearch에 인덱싱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceEventConsumer {

    private final ExperienceSearchService experienceSearchService;

    // Experience 모듈에서 체험 CRUD 시 experience-events 토픽에 메세지 발행
    @KafkaListener(
        topics = "experience-events",
        groupId = "search-service",
        containerFactory = "experienceEventListenerContainerFactory"
    )
    public void onMessage(ExperienceEvent event) {
        ExperienceEvent.ExperienceEventData data = event.getData();
        log.info("📨 [CONSUMER] Received experience event - Type: {}, Experience ID: {}, Name: {}, Price: {}",
                event.getType(), data.getExperienceId(), data.getExperienceName(),
                data.getPricePerPerson());

        try {
            switch (event.getType()) {
                case EXPERIENCE_CREATED -> {
                    log.info(
                        "🆕 [CONSUMER] Processing EXPERIENCE_CREATED - ID: {}, Name: {}, Price: {}",
                        data.getExperienceId(), data.getExperienceName(), data.getPricePerPerson());
                    experienceSearchService.indexExperience(toRequest(data));
                    log.info("✅ [CONSUMER] Successfully indexed experience - ID: {}, Name: {}",
                        data.getExperienceId(), data.getExperienceName());
                }
                case EXPERIENCE_UPDATED -> {
                    log.info(
                        "🔄 [CONSUMER] Processing EXPERIENCE_UPDATED - ID: {}, Name: {}, Price: {}",
                        data.getExperienceId(), data.getExperienceName(), data.getPricePerPerson());
                    experienceSearchService.indexExperience(toRequest(data));
                    log.info("✅ [CONSUMER] Successfully updated experience - ID: {}, Name: {}",
                        data.getExperienceId(), data.getExperienceName());
                }
                case EXPERIENCE_DELETED -> {
                    log.info("🗑️ [CONSUMER] Processing EXPERIENCE_DELETED event - Experience ID: {}",
                        data.getExperienceId());
                    experienceSearchService.deleteExperience(data.getExperienceId());
                    log.info("✅ [CONSUMER] Successfully deleted experience - ID: {}",
                        data.getExperienceId());
                }
                default -> {
                    log.warn("⚠️ [CONSUMER] Unknown event type received - Type: {}, Experience ID: {}",
                        event.getType(), data.getExperienceId());
                }
            }
        } catch (Exception e) {
            log.error("❌ [CONSUMER] Failed to process experience event - " +
                    "Type: {}, Experience ID: {}, Name: {}, Error: {}",
                event.getType(), data.getExperienceId(), data.getExperienceName(), e.getMessage(), e);
            throw e; // 예외를 다시 던져서 Kafka가 재시도하도록 함
        }
    }

    private ExperienceIndexRequest toRequest(ExperienceEvent.ExperienceEventData data) {
        return new ExperienceIndexRequest(
            data.getExperienceId(),
            data.getExperienceName(),
            data.getPricePerPerson(),
            data.getCapacity(),
            data.getDurationMinutes(),
            data.getAvailableStartDate().toLocalDate(), // LocalDateTime -> LocalDate
            data.getAvailableEndDate().toLocalDate(), // LocalDateTime -> LocalDate
            data.getStatus());
    }
}
