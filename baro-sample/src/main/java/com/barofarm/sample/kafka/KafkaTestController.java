package com.barofarm.sample.kafka;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kafka")
public class KafkaTestController {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaTestController.class);

    private static final String TOPIC_NAME = "sample-topic";

    private final KafkaTemplate<String, KafkaTestRequest> kafkaTemplate;
    private final AtomicReference<KafkaTestRequest> lastMessage = new AtomicReference<>(null);

    public KafkaTestController(KafkaTemplate<String, KafkaTestRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public record KafkaTestRequest(String message) {}

    public record KafkaTestResponse(
            String status, String sentMessage, String lastConsumedMessage, String timestamp) {}

    @PostMapping("/test")
    public ResponseEntity<KafkaTestResponse> sendMessage(@RequestBody KafkaTestRequest request) {
        KafkaTestRequest payload =
                (request != null && request.message() != null && !request.message().isBlank())
                        ? request
                        : new KafkaTestRequest("test-message-" + Instant.now());

        kafkaTemplate.send(new ProducerRecord<>(TOPIC_NAME, payload));

        return ResponseEntity.ok(
                new KafkaTestResponse(
                        "SENT",
                        payload.message(),
                        lastMessage.get() != null ? lastMessage.get().message() : null,
                        Instant.now().toString()));
    }

    @GetMapping("/test")
    public ResponseEntity<KafkaTestResponse> getLastMessage() {
        return ResponseEntity.ok(
                new KafkaTestResponse(
                        "OK",
                        null,
                        lastMessage.get() != null ? lastMessage.get().message() : null,
                        Instant.now().toString()));
    }

    @KafkaListener(
            topics = TOPIC_NAME,
            groupId = "baro-sample-group",
            containerFactory = "kafkaTestListenerContainerFactory")
    public void listen(KafkaTestRequest payload) {
        LOG.info("Received message from Kafka topic {}: {}", TOPIC_NAME, payload);
        lastMessage.set(payload);
    }
}
