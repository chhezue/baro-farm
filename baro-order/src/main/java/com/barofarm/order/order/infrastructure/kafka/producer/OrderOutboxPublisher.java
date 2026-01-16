package com.barofarm.order.order.infrastructure.kafka.producer;

import com.barofarm.order.order.domain.OrderOutboxEvent;
import com.barofarm.order.order.domain.OrderOutboxStatus;
import com.barofarm.order.order.infrastructure.OrderOutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPublisher {

    private final OrderOutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000L)
    @Transactional
    public void publishOrderEvents() {
        List<OrderOutboxEvent> events =
            outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OrderOutboxStatus.PENDING);

        for (OrderOutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                    event.getTopic(),
                    event.getCorrelationId(),
                    event.getPayload()
                );
                event.markSent();
            } catch (Exception e) {
                log.error("Failed to publish order outbox event. id={}, topic={}",
                    event.getId(), event.getTopic(), e);
                event.markFailed();
            }
        }
    }
}

