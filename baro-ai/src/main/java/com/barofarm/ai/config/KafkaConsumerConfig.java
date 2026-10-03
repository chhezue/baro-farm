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
 * ProductEvent, CartEvent, OrderEvent를 각각 처리하기 위한 별도의 ConsumerFactory 설정
 * 실패 메시지 보관 토픽(DLQ)은 두지 않는다. 재처리 절차와 보존할 운영 데이터가 아직 없어서
 * 짧은 재시도 뒤 로그로 남기고 건너뛴다. 근거: docs/audits/search/001-index-path.md D-1
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // === Product Event Consumer (기존) ===

    // 상품 1건 색인에 원격 호출이 2번(임베딩·재료명) 있고 각각 30초 상한이라 최악 60초가 걸린다.
    // 재시도까지 합치면 한 건이 3분 가까이 붙잡힐 수 있어, 한 번의 poll에 여러 건을 받으면
    // Kafka 기본 처리 제한(5분)을 넘겨 컨슈머가 그룹에서 쫓겨나고 같은 묶음을 다시 받는 반복에 빠진다.
    // 한 번에 1건만 받으면 최악의 경우에도 그 제한 안에 든다. 근거: docs/audits/search/001-index-path.md A-1
    private static final int PRODUCT_MAX_POLL_RECORDS = 1;

    // 색인 실패는 대부분 ES·Gemini의 일시 장애라 잠깐 뒤 다시 하면 된다. 1초 간격으로 2번 더 시도한 뒤
    // 그래도 안 되면 로그를 남기고 건너뛴다. 값이 크면 잘못된 레코드 1건이 컨슈머를 그만큼 붙잡는다.
    private static final long PRODUCT_RETRY_INTERVAL_MS = 1_000L;
    private static final long PRODUCT_RETRY_MAX_ATTEMPTS = 2L;

    @Bean
    public ConsumerFactory<String, ProductEvent> productEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, PRODUCT_MAX_POLL_RECORDS);

        // MSA 환경: 메시지에 포함된 타입 정보를 무시하고 직접 지정한 타입으로 역직렬화
        JsonDeserializer<ProductEvent> jsonDeserializer = new JsonDeserializer<>(ProductEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(true);  // 타입 헤더 제거 (다른 모듈의 클래스 정보 무시)
        jsonDeserializer.setUseTypeHeaders(false);     // 타입 헤더 사용 안 함

        // buyer 모듈의 ProductEvent 클래스를 신뢰된 패키지에 추가 (메시지 본문의 @class 필드 처리용)
        // 와일드카드(*)가 작동하지 않을 수 있으므로 명시적으로 패키지 추가
        jsonDeserializer.addTrustedPackages(
            "*",
            "com.barofarm.shopping.product.event",
            "com.barofarm.shopping",
            "com.barofarm"
        );

        // ErrorHandlingDeserializer로 감싸서 역직렬화 실패 시 에러를 무시하고 계속 진행
        // (오래된 메시지의 타입 정보로 인한 ClassNotFoundException 무시)
        ErrorHandlingDeserializer<ProductEvent> deserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);
        deserializer.setFailedDeserializationFunction(record -> null);  // 실패 시 null 반환하여 건너뛰기

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductEvent> productEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProductEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productEventConsumerFactory());

        // 색인 중 예외가 나면 같은 레코드를 잠깐 뒤 다시 처리하고, 횟수를 다 쓰면 건너뛴다.
        // 역직렬화 실패는 위 팩토리가 null로 바꿔 넘기므로 여기까지 오지 않는다.
        CommonErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(PRODUCT_RETRY_INTERVAL_MS, PRODUCT_RETRY_MAX_ATTEMPTS)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // === 개인화 추천을 위한 이벤트 Consumer들 ===

    @Bean
    public ConsumerFactory<String, CartLogEvent> cartEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-cart"); // 별도 그룹으로 분리
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 개인화 데이터는 처음부터 수집

        // MSA 환경: 메시지에 포함된 타입 정보를 무시하고 직접 지정한 타입으로 역직렬화
        JsonDeserializer<CartLogEvent> deserializer = new JsonDeserializer<>(CartLogEvent.class);
        deserializer.setRemoveTypeHeaders(true);  // 타입 헤더 제거
        deserializer.setUseTypeHeaders(false);     // 타입 헤더 사용 안 함
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CartLogEvent> cartEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CartLogEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cartEventConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, OrderLogEvent> orderEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-order"); // 별도 그룹으로 분리
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 개인화 데이터는 처음부터 수집

        // MSA 환경: 메시지에 포함된 타입 정보를 무시하고 직접 지정한 타입으로 역직렬화
        JsonDeserializer<OrderLogEvent> deserializer = new JsonDeserializer<>(OrderLogEvent.class);
        deserializer.setRemoveTypeHeaders(true);  // 타입 헤더 제거
        deserializer.setUseTypeHeaders(false);     // 타입 헤더 사용 안 함
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderLogEvent> orderEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderLogEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderEventConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ReviewEvent> reviewEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-review"); // 별도 그룹으로 분리
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 개인화 데이터는 처음부터 수집

        JsonDeserializer<ReviewEvent> deserializer = new JsonDeserializer<>(ReviewEvent.class);
        deserializer.setRemoveTypeHeaders(true);  // 타입 헤더 제거 (다른 모듈의 클래스 정보 무시)
        deserializer.setUseTypeHeaders(false);     // 타입 헤더 사용 안 함
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReviewEvent> reviewEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ReviewEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reviewEventConsumerFactory());
        return factory;
    }
}
