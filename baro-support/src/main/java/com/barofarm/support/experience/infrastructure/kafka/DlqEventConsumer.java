package com.barofarm.support.experience.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * DLQ(Dead Letter Queue) 토픽을 구독하여 실패한 메시지를 모니터링하는 Consumer
 *
 * 목적: DLQ로 전송된 메시지를 로그로 확인하여 문제를 추적
 *
 * DLQ 토픽 명명 규칙:
 * - Consumer DLQ: 원본 토픽명 + ".DLQ" (예: "farm-events" → "farm-events.DLQ")
 * - Producer DLQ: 원본 토픽명 + ".producer.DLQ" (예: "farm-events" → "farm-events.producer.DLQ")
 */
@Slf4j
@Component
public class DlqEventConsumer {

    /**
     * farm-events.DLQ 토픽 구독 (Consumer 처리 실패용)
     * Consumer가 메시지 처리 실패 후 재시도도 실패한 경우
     */
    @KafkaListener(topics = "farm-events.DLQ", groupId = "support-service-dlq")
    public void handleFarmEventDlq(ConsumerRecord<String, Object> record) {
        log.error(
            "💀 [DLQ_MONITOR] Failed message received from Consumer DLQ - "
                + "Topic: {}, Partition: {}, Offset: {}, Key: {}, Value: {}",
            record.topic(), record.partition(), record.offset(), record.key(), record.value());

        // TODO: 필요시 여기에 알림 발송, 메트릭 수집 등의 로직 추가 예정
    }

    /**
     * farm-events.producer.DLQ 토픽 구독 (Producer 전송 실패용)
     * Producer가 메시지 전송 실패 후 재시도도 실패한 경우
     */
    @KafkaListener(topics = "farm-events.producer.DLQ", groupId = "support-service-producer-dlq")
    public void handleFarmEventProducerDlq(ConsumerRecord<String, Object> record) {
        log.error(
            "💀 [PRODUCER_DLQ_MONITOR] Failed message received from Producer DLQ - "
                + "Topic: {}, Partition: {}, Offset: {}, Key: {}, Value: {}",
            record.topic(), record.partition(), record.offset(), record.key(), record.value());

        // TODO: 필요시 여기에 알림 발송, 메트릭 수집 등의 로직 추가 예정
    }

}
