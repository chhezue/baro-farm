package com.barofarm.log.history.writer;

import com.barofarm.log.history.model.HistoryEnvelope;
import com.barofarm.log.history.model.HistoryEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class HistoryLogWriter {

    private static final Logger CART_HISTORY_LOG =
        LoggerFactory.getLogger("CART_HISTORY");

    private static final Logger ORDER_HISTORY_LOG =
        LoggerFactory.getLogger("ORDER_HISTORY");

    private static final Logger INTERNAL_LOG =
        LoggerFactory.getLogger(HistoryLogWriter.class);

    private static final String AI_HISTORY_TOPIC = "ai-history";

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public HistoryLogWriter(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void write(HistoryEventType type, HistoryEnvelope<?> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);

            routeToFile(type, json);

            sendToKafka(envelope, json);
        } catch (Exception e) {
            /**
             * history / Kafka는 "부가 기능(side-effect)" 이다.
             * 여기서 예외가 터졌다고:
             *  - 주문 생성이 실패하면 x
             *  - 장바구니 추가가 롤백되면 x
             */
            INTERNAL_LOG.warn("History write failed", e);
        }
    }

    /**
     * 이벤트 타입에 따라 적절한 history 로그 파일로 라우팅
     *
     * 실제 파일 생성/롤링/S3 업로드는
     * logback + FluentBit 이 담당
     */
    private void routeToFile(HistoryEventType type, String json) {
        switch (type) {
            case CART_ADD, CART_REMOVE -> CART_HISTORY_LOG.info(json);

            case ORDER_CREATED, ORDER_CANCELLED -> ORDER_HISTORY_LOG.info(json);

            default ->
                INTERNAL_LOG.warn("Unhandled history event type: {}", type);
        }
    }

    /**
     * Kafka로 history 이벤트 전송
     *
     * key:
     *  - userId 기반 (파티션 분산 + 사용자 단위 순서 보장)
     *
     * value:
     *  - file 로그와 동일한 JSON
     */
    private void sendToKafka(HistoryEnvelope<?> envelope, String json) {
        if (envelope.userId() == null) {
            // userId 없는 이벤트는 Kafka에 안 보내도 됨 (정책)
            INTERNAL_LOG.warn("History event skipped (no userId): {}", envelope.event());
            return;
        }

        kafkaTemplate.send(
            AI_HISTORY_TOPIC,
            envelope.userId().toString(),
            json
        );
    }
}
