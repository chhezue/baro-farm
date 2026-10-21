package com.barofarm.ai.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * 재시도를 다 쓰고도 처리하지 못한 메시지를 어디로 보낼지 정한다.
 *
 * <p>2026-09-08까지는 갈 곳이 없었다. 1초 간격 2회를 다시 시도한 뒤에는 로그 한 줄만 남기고 그
 * 메시지를 버렸다. 그래서 상품 하나가 영영 색인되지 않아도 아무도 알 수 없었다 — 검색에 안 뜨는 상품이
 * 생기는데 원인을 되짚을 자료가 남지 않았다는 뜻이다.
 *
 * <p>이제 실패한 메시지는 원래 토픽 이름 뒤에 {@code .DLT}가 붙은 토픽으로 그대로 옮겨 간다.
 * 원인을 고친 뒤 그 토픽에서 다시 읽어 처리할 수 있고, 무엇이 몇 건 실패했는지는
 * {@code kafka.records.dead_lettered} 지표로 본다.
 *
 * <p>보내는 방법이 둘인 이유: 메시지를 Java 객체로 바꾸는 데 성공한 뒤 처리에서 실패한 경우는 그 객체를
 * JSON으로 다시 써서 보내면 되지만, 애초에 형식이 깨져 객체로 못 바꾼 경우에는 받은 바이트 원문을
 * 그대로 보내야 한다. 원문을 JSON으로 감싸면 나중에 다시 읽을 때 한 겹을 더 벗겨야 해서 손이 간다.
 *
 * <p><b>리뷰 이벤트는 여기 해당하지 않는다.</b> 그 컨슈머는 애초에 에러 핸들러가 없어 이 설정을 타지
 * 않고, 담당이 다른 팀원이라 이번 변경에서 건드리지 않았다.
 */
@Slf4j
@Configuration
public class KafkaDeadLetterConfig {

    /** Kafka 클러스터 접속 주소 */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> producerConfig(Class<?> valueSerializer) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        // 실패한 메시지를 옮기는 일이라 유실되면 의미가 없다 — 모든 복제본이 받을 때까지 기다리고,
        // 재시도가 중복 발행이 되지 않게 멱등 발행을 켠다.
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return config;
    }

    /** 객체로 바꾸는 데 성공한 메시지를 DLT로 보낼 때 쓴다. */
    @Bean
    public KafkaTemplate<String, Object> deadLetterJsonTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerConfig(JsonSerializer.class)));
    }

    /** 형식이 깨져 객체로 못 바꾼 메시지의 바이트 원문을 그대로 DLT로 보낼 때 쓴다. */
    @Bean
    public KafkaTemplate<String, byte[]> deadLetterByteTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerConfig(ByteArraySerializer.class)));
    }

    /**
     * 재시도를 다 쓴 메시지를 DLT로 옮기고, 그 사실을 지표와 로그에 남긴다.
     *
     * DLT 발행이 실패하더라도(토픽이 없거나 브로커에 못 닿는 경우) 컨슈머는 멈추지 않는다 — 그때의 동작은
     * 이 설정을 넣기 전과 같아서, 최악이라도 전보다 나빠지지 않는다.
     */
    @Bean
    public ConsumerRecordRecoverer deadLetterRecoverer(
        KafkaTemplate<String, Object> deadLetterJsonTemplate,
        KafkaTemplate<String, byte[]> deadLetterByteTemplate,
        MeterRegistry meterRegistry
    ) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new java.util.LinkedHashMap<>();
        templates.put(byte[].class, deadLetterByteTemplate);
        templates.put(Object.class, deadLetterJsonTemplate);

        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(templates);

        return (record, exception) -> {
            Counter.builder("kafka.records.dead_lettered")
                .tag("topic", record.topic())
                .description("재시도를 다 쓰고 DLT로 옮긴 메시지 수")
                .register(meterRegistry)
                .increment();

            log.error("재시도를 다 써 메시지를 {}.DLT로 옮깁니다. partition={}, offset={}, 원인={}",
                record.topic(), record.partition(), record.offset(), exception.getMessage(), exception);

            try {
                publisher.accept(record, exception);
            } catch (Exception e) {
                // 여기서 다시 던지면 컨슈머가 같은 메시지에 갇힌다. DLT에 못 넣었어도 로그는 남았으므로 넘어간다.
                log.error("DLT 발행까지 실패했습니다. topic={}, offset={}", record.topic(), record.offset(), e);
            }
        };
    }
}
