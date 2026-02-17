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

    private static final Logger log = LoggerFactory.getLogger(KafkaTestController.class);

    private static final String TOPIC_NAME = "sample-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AtomicReference<String> lastMessage = new AtomicReference<>(null);

    public KafkaTestController(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public record KafkaTestRequest(String message) {}

    public record KafkaTestResponse(
            String status, String sentMessage, String lastConsumedMessage, String timestamp) {}

    @PostMapping("/test")
    public ResponseEntity<KafkaTestResponse> sendMessage(@RequestBody KafkaTestRequest request) {
        String message =
                (request != null && request.message() != null && !request.message().isBlank())
                        ? request.message()
                        : "test-message-" + Instant.now();

        kafkaTemplate.send(new ProducerRecord<>(TOPIC_NAME, message));

        return ResponseEntity.ok(
                new KafkaTestResponse("SENT", message, lastMessage.get(), Instant.now().toString()));
    }

    @GetMapping("/test")
    public ResponseEntity<KafkaTestResponse> getLastMessage() {
        return ResponseEntity.ok(
                new KafkaTestResponse(
                        "OK", null, lastMessage.get(), Instant.now().toString()));
    }

    @KafkaListener(topics = TOPIC_NAME, groupId = "baro-sample-group")
    public void listen(String message) {
        log.info("Received message from Kafka topic {}: {}", TOPIC_NAME, message);
        lastMessage.set(message);
    }
}
