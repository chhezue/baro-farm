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
    api-key: ${SPRING_AI_OPENAI_API_KEY:}
    chat:
      options:
        model: ${SPRING_AI_OPENAI_MODEL:gpt-4o-mini}
        temperature: 0.7
    embedding:
      options:
        model: ${SPRING_AI_OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
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

### 사용자 프로필 임베딩 생성

> **[Application Layer]** 사용자 행동 로그를 기반으로 가중치를 적용하여 임베딩 벡터를 생성하는 서비스입니다.

```java
// embedding/application/UserProfileEmbeddingService.java

@Service
public class UserProfileEmbeddingService {

    private static final int MIN_TOTAL_LOGS_FOR_EMBEDDING = 3;
    private final EmbeddingModel embeddingModel;
    private final CartLogRepository cartLogRepository;
    private final OrderLogRepository orderLogRepository;
    private final SearchLogRepository searchLogRepository;
    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;

    public void updateUserProfileEmbedding(UUID userId) {
        // 1. 최근 30일간의 로그를 각 타입별로 최대 5개씩 가져옴
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Pageable top5 = PageRequest.of(0, 5);

        List<SearchLogDocument> searchLogs = searchLogRepository
            .findAllByUserIdAndSearchedAtAfterOrderBySearchedAtDesc(userId, thirtyDaysAgo, top5);
        List<CartLogDocument> cartLogs = cartLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);
        List<OrderLogDocument> orderLogs = orderLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);

        // 2. 품질 검증 (최소 3개 이상 필요)
        int totalLogCount = searchLogs.size() + cartLogs.size() + orderLogs.size();
        if (totalLogCount < MIN_TOTAL_LOGS_FOR_EMBEDDING) {
            return; // 임베딩 건너뜀
        }

        // 3. 가중치 적용 텍스트 생성
        String representativeText = buildRepresentativeText(searchLogs, cartLogs, orderLogs);

        // 4. 임베딩 벡터 생성
        List<Double> vector = embedText(representativeText);

        // 5. Elasticsearch에 저장
        UserProfileEmbeddingDocument document = UserProfileEmbeddingDocument.builder()
            .userId(userId)
            .userProfileVector(vector)
            .lastUpdatedAt(Instant.now())
            .build();
        userProfileEmbeddingRepository.save(document);
    }

    private String buildRepresentativeText(...) {
        // 수량 × 이벤트 타입 × 시간 가중치를 적용하여 텍스트 생성
        // 예: "청송사과" × (이벤트:2 × 수량:3 × 시간:2.8) = 17번 반복
    }
}
```

### 상품 임베딩 생성

> **[Application Layer]** 상품명을 기반으로 임베딩 벡터를 생성하는 서비스입니다.

```java
// embedding/application/ProductEmbeddingService.java

@Service
public class ProductEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embedProduct(String productName) {
        var embeddings = embeddingModel.embed(List.of(productName));
        return embeddings.get(0); // 1536차원 float 배열
    }
}
```

### 상품 인덱싱 서비스

> **[Application Layer]** 상품을 Elasticsearch에 인덱싱하고 임베딩을 생성하는 서비스입니다.

```java
// search/application/ProductIndexService.java

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private final ProductSearchRepository repository;
    private final ProductAutocompleteRepository autocompleteRepository;
    private final ProductEmbeddingService productEmbeddingService;

    public ProductDocument indexProduct(ProductIndexRequest request) {
        float[] vector = null;

        // 임베딩이 실패하더라도 인덱싱은 계속되어야 함
        try {
            // 상품 이름을 기반으로 임베딩 생성
            vector = productEmbeddingService.embedProduct(request.productName());
        } catch (Exception e) {
            log.error("❌ Product embedding failed. productId=" + request.productId(), e);
        }

        ProductDocument doc = new ProductDocument(
            request.productId(),
            request.productName(),
            request.productCategory(),
            request.price(),
            request.status(),
            Instant.now(),
            vector
        );

        // 자동완성 인덱스에도 저장
        ProductAutocompleteDocument autocompleteDoc =
            new ProductAutocompleteDocument(request.productId(), request.productName(), request.status());
        autocompleteRepository.save(autocompleteDoc);

        return repository.save(doc);
    }
}
```

### 레시피 추천 로직

> **[Application Layer]** 장바구니 상품을 기반으로 레시피를 추천하는 서비스입니다.

```java
// recommend/application/RecipeRecommendService.java

@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    private final RecipePromptService recipePromptService;
    private final ProductNameNormalizer productNameNormalizer;

    public RecipeRecommendResponse testRecommendFromCartWithMissing(CartInfo cart) {
        // 1. 장바구니 상품에서 보유 재료 추출
        List<OwnedIngredient> ownedIngredients =
            recipePromptService.extractOwnedIngredients(cartToCartItems(cart));

        // 2. 보유 재료 목록으로 레시피 후보 생성
        List<String> ownedNames = ownedIngredients.stream()
            .map(oi -> IngredientProcessingUtil.normalizeForCompare(oi.name()))
            .distinct()
            .toList();

        RecipeCandidates candidates = recipePromptService.generateRecipeCandidates(ownedNames);

        // 3. 첫 번째 레시피 선택 및 부족 재료 계산
        if (candidates.candidates().isEmpty()) {
            return createEmptyResponse(ownedNames);
        }

        CandidateRecipePlan selectedRecipe = candidates.candidates().get(0);

        // 4. 부족한 재료 검색
        List<String> missingCore = IngredientProcessingUtil.subtractNormalized(
            selectedRecipe.recipeIngredientsCore(), ownedNames);

        List<IngredientRecommendResponse> recommendations = missingCore.stream()
            .map(this::searchProductsForIngredient)
            .toList();

        return new RecipeRecommendResponse(
            selectedRecipe.recipeName(),
            ownedNames,
            missingCore,
            recommendations,
            selectedRecipe.instructions()
        );
    }
}
```

### LLM 기반 재료 추출

> **[Infrastructure Layer]** 상품명에서 실제 재료를 추출하는 LLM 서비스입니다.

```java
// recommend/infrastructure/llm/ProductNameNormalizer.java

@Component
@RequiredArgsConstructor
public class ProductNameNormalizer {

    private final ChatClient chatClient;

    public String normalizeForRecipeIngredient(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return "";
        }

        String prompt = buildNormalizationPrompt(productName);

        try {
            NormalizationResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(new ParameterizedTypeReference<NormalizationResponse>() {});

            return response != null && response.normalizedIngredient() != null
                ? response.normalizedIngredient().trim()
                : "";

        } catch (Exception e) {
            log.warn("상품명 정규화 실패: '{}', 기본 정규화 적용", productName, e);
            return applyBasicNormalization(productName);
        }
    }

    private String buildNormalizationPrompt(String productName) {
        return String.format("""
            당신은 농산물 이커머스 상품명을 분석하여 실제 요리에 사용할 수 있는 '핵심 재료명'만 추출하는 전문가입니다.

            <상품명>
            %s

            <출력(JSON only)>
            {{
              "normalizedIngredient": "핵심재료명"
            }}
            """, productName);
    }

    private record NormalizationResponse(String normalizedIngredient) { }
}
```

### 재료 정규화 유틸리티

> **[Domain Layer]** 재료명을 비교하고 정규화하는 도메인 유틸리티입니다.

```java
// recommend/domain/IngredientProcessingUtil.java

public final class IngredientProcessingUtil {

    private IngredientProcessingUtil() {}

    /**
     * 재료 이름을 비교를 위한 정규화 (소문자 변환, 공백 제거)
     */
    public static String normalizeForCompare(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /**
     * 정규화된 재료 목록에서 다른 목록의 재료들을 제거
     */
    public static List<String> subtractNormalized(List<String> a, List<String> b) {
        Set<String> bNorm = b.stream()
            .map(IngredientProcessingUtil::normalizeForCompare)
            .collect(Collectors.toSet());

        return normalizeList(a).stream()
            .filter(x -> !bNorm.contains(normalizeForCompare(x)))
            .toList();
    }
}
```

### 벡터 기반 추천 로직

> **[Application Layer]** 사용자 프로필 벡터와 상품 벡터의 유사도를 계산하여 추천합니다.

```java
// recommend/application/PersonalizedRecommendService.java

@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;
    private final VectorProductSearchService vectorProductSearchService;

    public List<ProductRecommendResponse> recommendProducts(UUID userId, int topK) {
        // 1. 사용자 프로필 벡터 조회
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null || profile.getUserProfileVector() == null) {
            return List.of();
        }

        // 2. 이미 경험한 상품 ID 목록 가져오기
        List<String> experiencedProductIds = profile.getSourceProductIds() != null
            ? profile.getSourceProductIds()
            : List.of();

        // 3. List<Double>을 float[]로 변환
        float[] userVector = convertToFloatArray(profile.getUserProfileVector());

        // 4. Elasticsearch 벡터 유사도 검색
        List<UUID> excludeProductIds = experiencedProductIds.stream()
            .map(UUID::fromString)
            .toList();

        return vectorProductSearchService.findSimilarProductsByVector(
            userVector,
            topK,
            excludeProductIds,  // 제외할 상품 ID들
            null,               // 자기 자신 제외하지 않음
            true                // 중복 제거 활성화
        );
    }
}
```

### 비슷한 상품 추천 로직

> **[Application Layer]** 특정 상품의 벡터를 기반으로 유사한 상품을 추천합니다.

```java
// recommend/application/SimilarProductService.java

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarProductService {

    private final ProductSearchRepository productSearchRepository;
    private final VectorProductSearchService vectorProductSearchService;

    public List<ProductRecommendResponse> recommendSimilarProducts(UUID productId, int topK) {
        // 1. 기준 상품 조회 및 벡터 추출
        ProductDocument product = productSearchRepository.findById(productId)
            .orElseThrow(() -> {
                log.warn("유사 상품 추천을 위한 기준 상품을 찾을 수 없습니다. productId: {}", productId);
                return new CustomException(RecommendErrorCode.PRODUCT_NOT_FOUND);
            });

        if (product.getVector() == null) {
            log.warn("기준 상품 '{}'의 벡터가 존재하지 않습니다. 임베딩이 필요합니다.", product.getProductName());
            return List.of();
        }

        float[] productVector = product.getVector();

        // 2. 벡터 유사도 검색 실행
        return findSimilarProducts(productVector, productId, topK);
    }

    private List<ProductRecommendResponse> findSimilarProducts(
        float[] vector, 
        UUID originalProductId, 
        int topK
    ) {
        // VectorProductSearchService의 메소드 사용
        return vectorProductSearchService.findSimilarProductsByVector(
            vector,
            topK,
            List.of(),          // 제외할 상품 ID 없음
            originalProductId,  // 자기 자신 제외
            false               // 중복 제거 비활성화
        );
    }
}
```

### 벡터 유사도 검색 서비스

> **[Application Layer]** Elasticsearch를 사용한 벡터 기반 유사도 검색의 공통 로직을 제공합니다.

```java
// search/application/VectorProductSearchService.java

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ProductRecommendResponse> findSimilarProductsByVector(
        float[] queryVector,
        int topK,
        List<UUID> excludeProductIds,
        UUID excludeSelfProductId,
        boolean removeDuplicates
    ) {
        // Elasticsearch script_score 쿼리를 사용한 코사인 유사도 계산
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q
                .scriptScore(ss -> ss
                    .query(q2 -> q2
                        .bool(b -> b
                            // 자기 자신 제외
                            .mustNot(mn -> {
                                if (excludeSelfProductId != null) {
                                    mn.ids(i -> i.values(excludeSelfProductId.toString()));
                                }
                                return mn;
                            })
                            // 판매 중인 상품만 필터링
                            .filter(f -> f
                                .terms(t -> t
                                    .field("status")
                                    .terms(v -> v.value(
                                        List.of(FieldValue.of("ON_SALE"), FieldValue.of("DISCOUNTED"))
                                    ))
                                )
                            )
                        )
                    )
                    .script(s -> s
                        .inline(i -> i
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params(Map.of("query_vector", JsonData.of(convertToDoubleList(queryVector))))
                        )
                    )
                )
            )
            .withPageable(PageRequest.of(0, fetchSize))
            .build();

        // 검색 실행 및 결과 변환
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
        // ... 결과 처리 및 필터링
    }
}
```

### Elasticsearch 도큐먼트 모델

> **[Infrastructure Layer]** Elasticsearch라는 특정 기술에 종속적인 데이터 모델입니다. 데이터 영속성을 담당하는 인프라 계층에 위치합니다.

#### 사용자 프로필 임베딩 문서

```java
// embedding/domain/UserProfileEmbeddingDocument.java

@Document(indexName = "user_profile_embeddings")
@Getter
@Builder
public class UserProfileEmbeddingDocument {

    @Id
    private UUID userId; // 사용자 ID를 문서 ID로 사용

    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private List<Double> userProfileVector; // 1536차원 벡터

    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;
}
```

#### 로그 도큐먼트

```java
// log/domain/CartLogDocument.java

@Document(indexName = "cart_event_logs")
@Getter
@NoArgsConstructor
public class CartLogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private UUID userId;

    @Field(type = FieldType.Keyword)
    private UUID productId;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String productName; // 임베딩 텍스트 데이터

    @Field(type = FieldType.Keyword)
    private String eventType; // ADD, REMOVE, UPDATE

    @Field(type = FieldType.Integer)
    private Integer quantity; // 관심 강도

    @Field(type = FieldType.Date)
    private Instant occurredAt; // 시간 가중치 계산용
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