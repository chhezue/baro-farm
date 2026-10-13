package com.barofarm.ai.config;

import com.barofarm.ai.event.model.CartLogEvent;
import com.barofarm.ai.event.model.OrderLogEvent;
import com.barofarm.ai.event.model.ReviewEvent;
import com.barofarm.ai.search.infrastructure.event.ProductEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * [Kafka Consumer 설정]
 * 도메인별 이벤트(ProductEvent, CartLogEvent, OrderLogEvent, ReviewEvent)를 개별적으로
 * 처리하기 위한 ConsumerFactory 및 ConcurrentKafkaListenerContainerFactory 설정 클래스.
 *
 * 각 도메인의 처리 특성(처리 소요 시간, 오프셋 격리 필요성 등)에 맞춰 poll 레코드 수와
 * 컨슈머 그룹 ID, 재시도 정책을 독립적으로 분리 구성합니다.
 *
 * 팩토리 4개가 타입·그룹 접미사·offset 초기값만 다르고 나머지가 같아 공통 메서드로 모았다.
 * 다만 리뷰 팩토리는 팀원 소유라 이번 정리에서 제외했다 — 실패 동작이 바뀌기 때문이다.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /** Kafka 클러스터 접속 주소 */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** 기본 컨슈머 그룹 ID */
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // === Product Event Consumer 설정 상수 ===

    /**
     * 상품 이벤트 한 번의 poll()에서 가져올 최대 레코드 수 (5건) — 묶음 리스너가 이 단위로 색인한다.
     *
     * 2026-09-06 이전에는 1건이었다. 건당 외부 호출(임베딩·재료명 LLM)이 길어 max.poll.interval.ms를 넘길까 봐였는데,
     * 그 대신 처리량이 건당 지연의 역수로 고정됐다. 묶으면 임베딩은 묶음당 1회, ES 저장은 인덱스당 1회가 된다.
     * 5로 정한 근거: 상품 1건의 최악 지연은 임베딩·재료명 호출 각각 30초 × 3회 재시도 = 90초씩 180초다(LlmClientConfig).
     * 5건이면 최악 900초라 아래 PRODUCT_MAX_POLL_INTERVAL_MS(15분)와 같다 — 1건일 때 지키던 "최악에도 리밸런싱 없음"을
     * 그대로 유지하는 최대값이다. 정상 지연(임베딩 묶음 1.5초 + 재료명 건당 약 1.5초)에서는 여유가 크다.
     * 근거: docs/audits/keyword-search/002-index-throughput-and-search-log.md A-1·M-3
     */
    private static final int PRODUCT_MAX_POLL_RECORDS = 5;

    /** 상품 묶음 하나를 처리하는 데 허용하는 최대 시간 (15분). 넘기면 브로커가 이 컨슈머를 죽은 것으로 보고 리밸런싱한다 */
    private static final int PRODUCT_MAX_POLL_INTERVAL_MS = 900_000;

    /** 처리 실패 시 재시도 대기 간격 (1초) */
    private static final long RETRY_INTERVAL_MS = 1_000L;

    /** 최대 재시도 횟수 (총 2회 재시도 후 건너뜀) */
    private static final long RETRY_MAX_ATTEMPTS = 2L;

    /**
     * 컨슈머 팩토리 공통 생성.
     *
     * 발행 모듈의 클래스명 헤더를 무시하고 지정한 타입으로 읽는다 — 모듈끼리 패키지 경로를 공유하지 않기 위해서다.
     * 역직렬화에 실패한 메시지는 {@link ErrorHandlingDeserializer}가 예외를 헤더에 담아 넘기고, 컨테이너가
     * 리스너를 부르기 전에 그 헤더를 보고 예외를 던진다. 아래 {@link #retryingErrorHandler()}는 역직렬화 예외를
     * 재시도해도 소용없는 종류로 분류하므로 그 레코드만 건너뛴다. 감싸지 않으면 같은 레코드를 무한히 다시 읽어
     * 그 파티션의 뒤 이벤트가 영영 처리되지 않는다.
     */
    private <T> ConsumerFactory<String, T> consumerFactory(
        Class<T> eventType,
        String groupSuffix,
        String autoOffsetReset,
        Map<String, Object> extraConfig
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + groupSuffix);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        config.putAll(extraConfig);

        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(eventType);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
            config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    /** 1초 간격으로 최대 2회 다시 시도한 뒤 건너뛰는 공통 에러 핸들러. */
    private CommonErrorHandler retryingErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, RETRY_MAX_ATTEMPTS));
    }

    /**
     * 상품 이벤트(ProductEvent) 수신용 ConsumerFactory.
     * 색인 시 임베딩·재료명 LLM 호출이 끼어 처리 시간이 길므로 poll당 건수와 처리 허용 시간을 함께 정한다.
     */
    @Bean
    public ConsumerFactory<String, ProductEvent> productEventConsumerFactory() {
        return consumerFactory(
            ProductEvent.class,
            "",
            "latest",
            Map.of(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, PRODUCT_MAX_POLL_RECORDS,
                ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, PRODUCT_MAX_POLL_INTERVAL_MS));
    }

    /**
     * 상품 이벤트 리스너 컨테이너 팩토리 — **묶음 리스너**다. 리스너는 `List<ProductEvent>`를 받는다.
     * 리스너가 {@code BatchListenerFailedException}에 실패 위치를 담아 던지므로, 에러 핸들러는 그 앞 레코드를 커밋하고
     * 실패 레코드부터 1초 간격 2회 다시 시도한 뒤 그 레코드만 건너뛴다. 색인은 상품 ID로 덮어쓰므로 재처리가 안전하다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductEvent> productEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProductEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productEventConsumerFactory());
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(retryingErrorHandler());
        return factory;
    }

    // === 개인화 추천 로그 수집 Consumer 설정 (장바구니, 주문) ===
    // 두 팩토리 모두 전용 그룹으로 오프셋을 격리하고, 개인화 데이터를 처음부터 모으기 위해 earliest로 읽는다.

    /**
     * 장바구니 이벤트(CartLogEvent) 수신용 ConsumerFactory 빈 등록.
     * 오프셋 격리를 위해 그룹 ID에 '-cart' 접미사를 부여하고, 누적 이력을 모두 반영하기 위해 auto.offset.reset=earliest를 적용합니다.
     */
    @Bean
    public ConsumerFactory<String, CartLogEvent> cartEventConsumerFactory() {
        return consumerFactory(CartLogEvent.class, "-cart", "earliest", Map.of());
    }

    /**
     * 장바구니 이벤트 리스너 컨테이너 팩토리 빈 등록.
     * 1초 간격 최대 2회 재시도(retryingErrorHandler) 정책을 적용합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CartLogEvent> cartEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CartLogEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cartEventConsumerFactory());
        factory.setCommonErrorHandler(retryingErrorHandler());
        return factory;
    }

    /**
     * 주문 이벤트(OrderLogEvent) 수신용 ConsumerFactory 빈 등록.
     * 오프셋 격리를 위해 그룹 ID에 '-order' 접미사를 부여하고, auto.offset.reset=earliest를 적용합니다.
     */
    @Bean
    public ConsumerFactory<String, OrderLogEvent> orderEventConsumerFactory() {
        return consumerFactory(OrderLogEvent.class, "-order", "earliest", Map.of());
    }

    /**
     * 주문 이벤트 리스너 컨테이너 팩토리 빈 등록.
     * 1초 간격 최대 2회 재시도(retryingErrorHandler) 정책을 적용합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderLogEvent> orderEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderLogEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderEventConsumerFactory());
        factory.setCommonErrorHandler(retryingErrorHandler());
        return factory;
    }

    /**
     * 리뷰 이벤트(ReviewEvent) 수신용 ConsumerFactory 빈을 등록합니다.
     */
    @Bean
    public ConsumerFactory<String, ReviewEvent> reviewEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-review");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<ReviewEvent> deserializer = new JsonDeserializer<>(ReviewEvent.class);
        deserializer.setRemoveTypeHeaders(true);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    /**
     * 리뷰 이벤트 리스너 컨테이너 팩토리.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReviewEvent> reviewEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ReviewEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reviewEventConsumerFactory());
        return factory;
    }
}
