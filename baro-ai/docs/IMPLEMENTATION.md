# 🛠️ 구현 가이드

> baro-ai 모듈의 개발 환경 설정과 핵심 구현 패턴

## 📋 목차

- [개발 환경](#-개발-환경)
- [핵심 구현 패턴](#-핵심-구현-패턴)
- [테스트 전략](#-테스트-전략)
- [배포 및 운영](#-배포-및-운영)

---

## 🛠️ 개발 환경

### 필수 요구사항

```text
- Java: 17 이상
- Gradle: 7.6 이상
- Elasticsearch: 8.x
- Redis: 7.x
- Kafka: 3.x
- Docker & Docker Compose
```

### 의존성 설정

```gradle
// build.gradle
dependencies {
    // AI/ML
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M5'
    implementation 'org.springframework.ai:spring-ai-ollama-spring-boot-starter:1.0.0-M5'

    // 데이터/검색
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // 메시징
    implementation 'org.springframework.kafka:spring-kafka'

    // 웹/검증
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

### 환경 설정

```yaml
# application.yml
server:
  port: 8092

spring:
  application:
    name: ai-service

  # 데이터베이스
  datasource:
    url: jdbc:mysql://localhost:3306/baro_ai
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  # Elasticsearch
  elasticsearch:
    uris: http://localhost:9200

  # Redis
  data:
    redis:
      host: localhost
      port: 6379

  # Kafka
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: ai-service

# AI 설정
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    chat:
      model: gpt-4
    embedding:
      model: text-embedding-ada-002
```

### 로컬 개발 실행

```bash
# 1. 인프라 서비스 실행
docker-compose -f docker-compose.data.yml up -d

# 2. 애플리케이션 빌드 및 실행
./gradlew build
./gradlew bootRun

# 3. API 테스트
curl http://localhost:8092/actuator/health
```

---

## 🚀 핵심 구현 패턴

### 이벤트 기반 데이터 수집

> **[Infrastructure Layer]** Kafka와 같은 외부 메시징 시스템과의 연동을 담당하는 인프라 계층의 일부입니다.

```java
// infrastructure/messaging/consumer/CartEventConsumer.java

@Component
@RequiredArgsConstructor
public class CartEventConsumer {

    private final LogWriteService logWriteService; // Application Service 호출

    @KafkaListener(
        topics = "cart-events",
        groupId = "ai-service",
        containerFactory = "cartEventListenerContainerFactory"
    )
    public void onMessage(CartEvent event) {
        try {
            // 인프라 계층은 애플리케이션 계층의 서비스를 호출하여 유스케이스를 실행합니다.
            logWriteService.saveCartEvent(event);
        } catch (Exception e) {
            log.error("Failed to process cart event", e);
        }
    }
}
```

### 벡터 기반 추천 로직

> **[Application Layer]** 도메인 로직과 인프라를 조율하여 '개인화 추천'이라는 유스케이스를 완성하는 애플리케이션 계층입니다.

```java
// application/recommendation/PersonalizedRecommendService.java

@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    // Domain 계층의 Repository 인터페이스 또는 다른 Application Service에 의존
    private final LogReadService logReadService;
    private final UserProfileEmbeddingService embeddingService;
    // Infrastructure 계층의 구현체는 직접 의존하지 않음 (DI를 통해 주입)
    private final RedisTemplate<String, List<Long>> redisTemplate;

    public List<Long> recommendForUser(Long userId) {
        String cacheKey = "recommend:user:" + userId;

        // 1. 인프라(캐시) 조회
        List<Long> cached = redisTemplate.opsForList().range(cacheKey, 0, -1);
        if (!cached.isEmpty()) return cached;

        // 2. 도메인 로직/데이터를 활용하여 추천 계산
        List<Long> recommendations = calculateRecommendations(userId);

        // 3. 인프라(캐시)에 결과 저장
        redisTemplate.opsForList().rightPushAll(cacheKey, recommendations);
        redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);

        return recommendations;
    }

    private List<Long> calculateRecommendations(Long userId) {
        // 1. 사용자 행동 로그 조회 (Domain/Infra)
        List<UserEventLog> logs = logReadService.getRecentLogs(userId, 30);

        // 2. 취향 벡터 생성/조회 (Domain/Application)
        float[] userVector = embeddingService.getUserProfileVector(userId);

        // 3. 상품 벡터 유사도 계산 및 랭킹 (Domain)
        return findSimilarProducts(userVector, logs).stream()
            .limit(15)
            .collect(Collectors.toList());
    }
}
```

### Elasticsearch 도큐먼트 모델

> **[Infrastructure Layer]** Elasticsearch라는 특정 기술에 종속적인 데이터 모델입니다. 데이터 영속성을 담당하는 인프라 계층에 위치합니다.

```java
// infrastructure/persistence/elasticsearch/document/CartEventDocument.java

@Document(indexName = "cart_event_logs")
@Getter
@NoArgsConstructor
public class CartEventDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private Long userId;

    @Field(type = FieldType.Keyword)
    private Long productId;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String productName;

    @Field(type = FieldType.Keyword)
    private String eventType;

    @Field(type = FieldType.Integer)
    private Integer quantity;

    @Field(type = FieldType.Date)
    private Instant occurredAt;

    @Builder
    public CartEventDocument(Long userId, Long productId, String productName,
                            String eventType, Integer quantity, Instant occurredAt) {
        this.userId = userId;
        this.productId = productId;
        this.productName = productName;
        this.eventType = eventType;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
    }
}
```

---

## 🧪 테스트 전략

계층형 아키텍처는 각 계층의 역할을 명확히 하므로, 테스트 전략 또한 체계적으로 수립할 수 있습니다.

### 단위 테스트 (Domain Layer)

> **[Domain Layer]** 도메인 계층의 순수한 비즈니스 로직을 검증합니다. 외부 의존성 없이 가장 빠르고 안정적으로 실행되어야 합니다.

```java
// domain/model/recommendation/RecommendationTest.java

class RecommendationTest {

    @Test
    void shouldCreateRecommendationSuccessfully() {
        // Given
        Product product = new Product("사과", 1000);
        User user = new User("testUser");

        // When
        Recommendation recommendation = Recommendation.of(user, product);

        // Then
        assertThat(recommendation.getUser()).isEqualTo(user);
        assertThat(recommendation.getProduct()).isEqualTo(product);
    }
}
```

### 통합 테스트 (Application & Infrastructure Layers)

> **[Application Layer Test]** 애플리케이션 서비스가 도메인과 인프라를 올바르게 조율하는지 검증합니다. `@SpringBootTest`를 사용하여 필요한 의존성을 주입받아 테스트합니다.

```java
// application/recommendation/PersonalizedRecommendServiceTest.java

@SpringBootTest
class PersonalizedRecommendServiceTest {

    @Autowired
    private PersonalizedRecommendService service;

    @MockBean // infrastructure/persistence 계층은 Mocking 처리
    private RecommendationRepository recommendationRepository;

    @Test
    void shouldReturnRecommendationsWhenUserHasLogs() {
        // Given: 사용자 행동 로그가 있는 경우
        Long userId = 1L;
        // when(recommendationRepository.findByUser(..)).thenReturn(..);

        // When: 추천 요청
        List<Long> recommendations = service.recommendForUser(userId);

        // Then: 추천 결과 반환
        assertThat(recommendations).isNotEmpty();
    }
}
```

> **[Infrastructure Layer Test]** Kafka, Elasticsearch 등 외부 인프라와의 연동이 올바르게 동작하는지 검증합니다. `@EmbeddedKafka` 등을 사용하여 실제와 유사한 환경에서 테스트합니다.

```java
// infrastructure/messaging/consumer/RecommendationIntegrationTest.java

@SpringBootTest
@EmbeddedKafka
class RecommendationIntegrationTest {

    @Autowired
    private KafkaTemplate<String, CartEvent> kafkaTemplate;

    @Autowired
    private PersonalizedRecommendService service;

    @Test
    void shouldUpdateRecommendationsAfterCartEvent() {
        // Given: 카트 이벤트 발행
        CartEvent event = createCartEvent();
        kafkaTemplate.send("cart-events", event);

        // When: 추천 로직이 비동기로 실행되고
        await().atMost(5, SECONDS).until(() -> {
            List<Long> recommendations = service.recommendForUser(event.getUserId());
            return !recommendations.isEmpty();
        });

        // Then: 추천 결과가 올바르게 업데이트되었는지 확인
        List<Long> recommendations = service.recommendForUser(event.getUserId());
        assertThat(recommendations).contains(event.getProductId());
    }
}
```

### E2E 테스트 (End-to-End)

> **[API E2E Test]** API 엔드포인트부터 실제 데이터베이스 연동까지 전체 흐름을 테스트합니다. 사용자 관점에서 시스템이 올바르게 동작하는지 최종적으로 검증합니다.

```java
// infrastructure/web/RecommendationControllerTest.java

@SpringBootTest(webEnvironment = RANDOM_PORT)
class RecommendationApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturnPersonalizedRecommendations() {
        // Given
        Long userId = 1L;

        // When: API 호출
        ResponseEntity<RecommendationResponse> response = restTemplate
            .getForEntity("/api/v1/recommendations/personalized/{userId}",
                         RecommendationResponse.class, userId);

        // Then: 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isNotEmpty();
    }
}
```

---

## 🚀 배포 및 운영

### Docker 구성

```yaml
# docker-compose.yml
version: '3.8'
services:
  ai-service:
    image: baro-ai:latest
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    depends_on:
      - elasticsearch
      - redis
      - kafka
    ports:
      - "8092:8092"

  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

### 헬스체크 및 모니터링

```java
@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
            .status("UP")
            .components(Map.of(
                "elasticsearch", checkElasticsearch(),
                "redis", checkRedis(),
                "openai", checkOpenAI()
            ))
            .build());
    }
}
```

### 로그 및 메트릭

```java
@Configuration
public class MonitoringConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new CompositeMeterRegistry();
    }

    @Bean
    public AIMetricsService aiMetricsService(MeterRegistry registry) {
        return new AIMetricsService(registry);
    }
}

@Service
@RequiredArgsConstructor
public class AIMetricsService {

    private final MeterRegistry registry;

    public void recordRecommendation(String type, long durationMs, boolean success) {
        registry.timer("ai.recommendation.duration",
            Tags.of("type", type, "success", String.valueOf(success)))
            .record(durationMs, MILLISECONDS);
    }

    public void recordCost(String model, double cost) {
        registry.counter("ai.cost.total", Tags.of("model", model))
            .increment(cost);
    }
}
```

### 배포 파이프라인

```yaml
# .github/workflows/deploy.yml
name: Deploy AI Service

on:
  push:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run tests
        run: ./gradlew test

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker image
        run: docker build -t baro-ai:latest .

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: kubectl apply -f k8s/
```

---

*이 구현 가이드를 따라 baro-ai 모듈의 핵심 기능을 개발할 수 있습니다.*