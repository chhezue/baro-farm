package com.barofarm.support.experience.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Experience 트랜잭션 이벤트 리스너
 * DB 트랜잭션 커밋 후 Kafka 이벤트 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceTransactionEventListener {

    private final ExperienceEventPublisher experienceEventPublisher;

    // DB 트랜잭션 성공 시에만 카프카 이벤트 발행됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExperienceTransactionEvent(ExperienceTransactionEvent event) {
        log.info("🔄 [TRANSACTION_LISTENER] Transaction committed successfully. " +
                "Publishing Kafka event for Experience ID: {}, Operation: {}",
                event.getExperience().getExperienceId(), event.getOperation());

        switch (event.getOperation()) {
            case CREATED -> {
                log.info("📤 [EXPERIENCE_SERVICE] Publishing EXPERIENCE_CREATED event to Kafka - " +
                        "Experience ID: {}, Name: {}",
                        event.getExperience().getExperienceId(), event.getExperience().getTitle());
                experienceEventPublisher.publishExperienceCreated(event.getExperience());
            }
            case UPDATED -> {
                log.info("📤 [EXPERIENCE_SERVICE] Publishing EXPERIENCE_UPDATED event to Kafka - " +
                        "Experience ID: {}, Name: {}",
                        event.getExperience().getExperienceId(), event.getExperience().getTitle());
                experienceEventPublisher.publishExperienceUpdated(event.getExperience());
            }
            case DELETED -> {
                log.info("📤 [EXPERIENCE_SERVICE] Publishing EXPERIENCE_DELETED event to Kafka - " +
                        "Experience ID: {}, Name: {}",
                        event.getExperience().getExperienceId(), event.getExperience().getTitle());
                experienceEventPublisher.publishExperienceDeleted(event.getExperience());
            }
            default -> {
                log.warn("⚠️ [TRANSACTION_LISTENER] Unknown operation type: {}", event.getOperation());
            }
        }
    }

    // DB 트랜잭션 롤백 시에는 카프카 이벤트 발행되지 않음
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleExperienceTransactionRollback(ExperienceTransactionEvent event) {
        log.warn("⚠️ [TRANSACTION_LISTENER] Transaction rolled back. " +
                "Skipping Kafka event publishing for Experience ID: {}, Operation: {}",
                event.getExperience().getExperienceId(), event.getOperation());
    }
}
