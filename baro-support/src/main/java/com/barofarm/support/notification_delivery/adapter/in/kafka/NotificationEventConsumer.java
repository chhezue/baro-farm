package com.barofarm.support.notification_delivery.adapter.in.kafka;

import com.barofarm.support.notification_delivery.application.handler.NotificationEventHandler;
import com.barofarm.support.notification_delivery.domain.event.NotificationEventPayload;
import com.barofarm.support.notification_delivery.infrastructure.util.Jsons;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer
 *
 * [핵심 정책]
 * - 메시지를 처리 성공하면 ack.acknowledge()로 커밋
 * - 처리 실패하면 예외를 던져 ErrorHandler가 DLQ로 보내게 하거나 재시도
 * */

@Slf4j
@Component
@Profile("!mock & !local-mail")
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationEventHandler handler;

    @KafkaListener(
        topics = "${notification.delivery.kafka.topic:notification-events}",
        groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String json = record.value();

            // String 기반 수신 -> Consumer에 안전하게 DTO로 변환
            // Jsons : infrastructure/util 내에
            NotificationEventPayload payload = Jsons.fromJson(json, NotificationEventPayload.class);

            handler.handle(payload);

            // 성공 시 오프셋 커밋
            ack.acknowledge();
        } catch (Exception e) {
            // 여기서 ack 하면 "실패했는데 커밋됨"이 되어서 재처리 불가능
            // 따라서 예외를 던져서 error handler로 흐르게
            // TODO: 직접 e.getMessage()하면 내부 로직 노출 위험? 이건 단순 로그라 괜찮?
            log.error("Notification delivery failed. key={}, offset={}, err={}",
                record.key(), record.offset(), e.getMessage(), e);
            throw e;
        }
    }
}
