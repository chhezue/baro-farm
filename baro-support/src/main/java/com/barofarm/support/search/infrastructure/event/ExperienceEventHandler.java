package com.barofarm.support.search.infrastructure.event;

import com.barofarm.support.experience.application.event.ExperienceTransactionEvent;
import com.barofarm.support.experience.domain.Experience;
import com.barofarm.support.search.application.ExperienceSearchService;
import com.barofarm.support.search.application.dto.ExperienceIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceEventHandler {

    private final ExperienceSearchService experienceSearchService;

    // 트랜잭션 이벤트가 성공적으로 실행되면 호출됨.
    @Async
    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        classes = ExperienceTransactionEvent.class
    )
    @Retryable(
        value = Exception.class,
        maxAttempts = 3, // 최대 3번까지 retry
        backoff = @Backoff(delay = 1000)
    )
    public void onMessage(ExperienceTransactionEvent event) {
        Experience experience = event.getExperience();
        ExperienceTransactionEvent.ExperienceOperation operation = event.getOperation();

        log.info("📨 [CONSUMER] Received experience transaction event - Operation: {}, Experience ID: {}, Name: {}",
            operation, experience.getExperienceId(), experience.getTitle());

        try {
            switch (operation) {
                case CREATED -> {
                    log.info(
                        "🆕 [CONSUMER] Processing EXPERIENCE_CREATED - ID: {}, Name: {}",
                        experience.getExperienceId(), experience.getTitle());
                    experienceSearchService.indexExperience(toRequest(experience));
                    log.info("✅ [CONSUMER] Successfully indexed experience - ID: {}, Name: {}",
                        experience.getExperienceId(), experience.getTitle());
                }
                case UPDATED -> {
                    log.info(
                        "🔄 [CONSUMER] Processing EXPERIENCE_UPDATED - ID: {}, Name: {}",
                        experience.getExperienceId(), experience.getTitle());
                    experienceSearchService.indexExperience(toRequest(experience));
                    log.info("✅ [CONSUMER] Successfully updated experience - ID: {}, Name: {}",
                        experience.getExperienceId(), experience.getTitle());
                }
                case DELETED -> {
                    log.info("🗑️ [CONSUMER] Processing EXPERIENCE_DELETED event - Experience ID: {}",
                        experience.getExperienceId());
                    experienceSearchService.deleteExperience(experience.getExperienceId());
                    log.info("✅ [CONSUMER] Successfully deleted experience - ID: {}",
                        experience.getExperienceId());
                }
                default -> {
                    log.warn("⚠️ [CONSUMER] Unknown operation received - Operation: {}, Experience ID: {}",
                        operation, experience.getExperienceId());
                }
            }
        } catch (Exception e) {
            log.error("❌ [CONSUMER] Failed to process experience transaction event - " +
                    "Operation: {}, Experience ID: {}, Name: {}, Error: {}",
                operation,
                experience.getExperienceId(),
                experience.getTitle(),
                e.getMessage(),
                e);
            throw e; // Spring Retry(@Retryable)에 의해 재시도 트리거
        }
    }

    private ExperienceIndexRequest toRequest(Experience experience) {
        return new ExperienceIndexRequest(
            experience.getExperienceId(),
            experience.getTitle(),
            experience.getPricePerPerson().longValue(),  // BigInteger → Long 변환
            experience.getCapacity(),
            experience.getDurationMinutes(),
            experience.getAvailableStartDate().toLocalDate(), // LocalDateTime -> LocalDate
            experience.getAvailableEndDate().toLocalDate(), // LocalDateTime -> LocalDate
            experience.getStatus().name()
        );
    }
}
