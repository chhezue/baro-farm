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
     * 상품 이벤트 한 번의 poll()에서 가져올 최대 레코드 수 (1건).
     * 상품 색인 과정에서 외부 API 호출(임베딩 생성, LLM 재료명 정규화)이 포함되어 건당 처리 시간이 길어질 수 있으므로,
     * 단일 건씩 소비하여 max.poll.interval.ms 초과로 인한 컨슈머 리밸런싱을 방지합니다.
     */
    private static final int PRODUCT_MAX_POLL_RECORDS = 1;

    /** 처리 실패 시 재시도 대기 간격 (1초) */
    private static final long RETRY_INTERVAL_MS = 1_000L;

    /** 최대 재시도 횟수 (총 2회 재시도 후 건너뜀) */
    private static final long RETRY_MAX_ATTEMPTS = 2L;

    /**
     * 상품 이벤트(ProductEvent) 수신용 ConsumerFactory 빈을 등록합니다.
     */
    @Bean
    public ConsumerFactory<String, ProductEvent> productEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // 상품 색인 시 외부 모델(임베딩, LLM) 호출 지연으로 인한 max.poll.interval.ms 초과 및 리밸런싱 방지를 위해 1건 제한
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, PRODUCT_MAX_POLL_RECORDS);

        // MSA 환경: 메시지 발행 모듈의 클래스 패키지 종속을 제거하기 위해 타입 헤더를 무시하고 ProductEvent로 매핑
        JsonDeserializer<ProductEvent> jsonDeserializer = new JsonDeserializer<>(ProductEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.setUseTypeHeaders(false);

        jsonDeserializer.addTrustedPackages(
            "*",
            "com.barofarm.shopping.product.event",
            "com.barofarm.shopping",
            "com.barofarm"
        );

        // 역직렬화 실패 시 파티션 정지(Poison Pill)를 방지하기 위해 null을 반환하여 스킵 처리
        ErrorHandlingDeserializer<ProductEvent> deserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);
        deserializer.setFailedDeserializationFunction(record -> null);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    /**
     * 상품 이벤트 리스너 컨테이너 팩토리를 등록합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductEvent> productEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProductEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productEventConsumerFactory());

        // 색인 또는 외부 호출 실패 시 1초 간격으로 최대 2회 추가 재시도(FixedBackOff) 적용
        CommonErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(RETRY_INTERVAL_MS, RETRY_MAX_ATTEMPTS)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // === 개인화 추천 로그 수집 Consumer 설정 (장바구니, 주문, 리뷰) ===

    /**
     * 장바구니 행동 로그 이벤트(CartLogEvent) 수신용 ConsumerFactory 빈을 등록합니다.
     */
    @Bean
    public ConsumerFactory<String, CartLogEvent> cartEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // 장바구니 전용 독립 컨슈머 그룹으로 분리하여 오프셋 격리
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-cart");
        // 개인화 데이터 누적 수집을 위해 최초 시작 시 처음부터 수집
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<CartLogEvent> jsonDeserializer = new JsonDeserializer<>(CartLogEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages("*");

        // 역직렬화 에러 발생 시 파티션이 멈추지 않고 에러 핸들러로 위임되도록 래핑
        return new DefaultKafkaConsumerFactory<>(
            config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    /**
     * 장바구니 이벤트 리스너 컨테이너 팩토리를 등록합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CartLogEvent> cartEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CartLogEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cartEventConsumerFactory());

        // 일시적 장애 시 1초 간격 최대 2회 재시도 적용
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, RETRY_MAX_ATTEMPTS)));

        return factory;
    }

    /**
     * 주문 행동 로그 이벤트(OrderLogEvent) 수신용 ConsumerFactory 빈을 등록합니다.
     */
    @Bean
    public ConsumerFactory<String, OrderLogEvent> orderEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // 주문 전용 독립 컨슈머 그룹으로 분리하여 오프셋 격리
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-order");
        // 과거 주문 이력 누락 없이 수집
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<OrderLogEvent> jsonDeserializer = new JsonDeserializer<>(OrderLogEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages("*");

        // 역직렬화 실패 메시지로 인한 파티션 정지 방지
        return new DefaultKafkaConsumerFactory<>(
            config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    /**
     * 주문 이벤트 리스너 컨테이너 팩토리를 등록합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderLogEvent> orderEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderLogEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderEventConsumerFactory());

        // 1초 간격 최대 2회 재시도 정책 적용
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, RETRY_MAX_ATTEMPTS)));

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
