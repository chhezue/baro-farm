# 구현 가이드

## 📋 구현 개요

이 문서는 `baro-ai` 모듈의 **"로그 기반 개인화 추천 + 레시피 추천 + (보조) 검색/챗봇"** 기능을 구현하기 위한 상세 가이드입니다.

### 주요 구현 원칙
- **로그 중심 아키텍처**: 사용자 행동 로그를 기반으로 한 추천 엔진
- **이벤트 드리븐**: Kafka 기반 실시간 데이터 처리
- **벡터 기반 검색**: Elasticsearch 활용 의미 검색
- **모듈 분리**: 각 레이어의 책임 명확한 분리

## 🛠️ 개발 환경 설정

### 1. 필수 의존성

```gradle
// build.gradle
dependencies {
    // Spring AI
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M5'
    implementation 'org.springframework.ai:spring-ai-ollama-spring-boot-starter:1.0.0-M5'

    // 데이터베이스 & 검색
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // 메시징
    implementation 'org.springframework.kafka:spring-kafka'

    // 공통
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

### 2. 환경 설정

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
  ollama:
    base-url: http://localhost:11434
```

## 📝 패키지별 구현 예시

### 1. `event/` 패키지 - Kafka 이벤트 수신

**역할**: `ProductEvent`, `CartEvent`, `OrderEvent`, `SearchEvent`, `ExperienceEvent` 등 Kafka 이벤트 수신 → 내부 도메인으로 전달

#### 1.1 이벤트 Consumer

```java
@Component
@RequiredArgsConstructor
public class ExperienceEventConsumer {

    private final ExperienceSearchService experienceSearchService;

    @KafkaListener(
        topics = "experience-events",
        groupId = "search-service",
        containerFactory = "experienceEventListenerContainerFactory"
    )
    public void onMessage(ExperienceEvent event) {
        try {
            experienceSearchService.indexExperience(
                toRequest(event.getData())
            );
        } catch (Exception e) {
            log.error("Failed to process experience event", e);
        }
    }

    private ExperienceIndexRequest toRequest(ExperienceEvent.ExperienceEventData data) {
        return new ExperienceIndexRequest(
            data.getExperienceId(),
            data.getExperienceName(),
            data.getPricePerPerson(),
            data.getCapacity(),
            data.getDurationMinutes(),
            data.getAvailableStartDate().toLocalDate(),
            data.getAvailableEndDate().toLocalDate(),
            data.getStatus()
        );
    }
}
```

### 2. `log/` 패키지 - 사용자 행동 로그 저장소

**역할**: `CartEventLog`, `OrderEventLog`, `SearchLog`, `UserEventLog` 등 유저 행동을 RDB에 시간 순으로 저장

#### 2.1 로그 엔티티 예시

```java
@Entity
@Table(name = "cart_event_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartEventLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartEventType eventType; // ADD, REMOVE, UPDATE

    private Integer quantity;

    @Builder
    public CartEventLog(Long userId, Long productId, CartEventType eventType, Integer quantity) {
        this.userId = userId;
        this.productId = productId;
        this.eventType = eventType;
        this.quantity = quantity;
    }
}
```

#### 2.2 로그 서비스 예시

```java
@Service
@RequiredArgsConstructor
public class LogWriteService {

    private final CartEventLogRepository cartEventLogRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final SearchLogRepository searchLogRepository;

    @Transactional
    public void saveCartEvent(CartEvent event) {
        CartEventLog log = CartEventLog.builder()
            .userId(event.getUserId())
            .productId(event.getProductId())
            .eventType(CartEventType.valueOf(event.getEventType()))
            .quantity(event.getQuantity())
            .build();

        cartEventLogRepository.save(log);
    }

    @Transactional
    public void saveOrderEvent(OrderEvent event) {
        // 주문 항목별로 로그 저장
        for (OrderItem item : event.getOrderItems()) {
            OrderEventLog log = OrderEventLog.builder()
                .userId(event.getUserId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .totalPrice(item.getTotalPrice())
                .build();

            orderEventLogRepository.save(log);
        }
    }
}
```

### 3. `recommend/` 패키지 - 추천 도메인

```java
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    private final ElasticsearchOperations operations;
    private final RedisTemplate<String, List<Long>> redisTemplate;

    public List<Long> generatePersonalizedRecommendations(Long userId) {
        // 1. 사용자 행동 로그 조회
        List<UserEventLog> userLogs = getUserBehaviorLogs(userId);

        // 2. 행동 패턴 분석
        Map<String, Integer> categoryPreferences = analyzePreferences(userLogs);

        // 3. 선호 카테고리 기반 상품 검색
        List<Long> recommendations = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : categoryPreferences.entrySet()) {
            String category = entry.getKey();
            int weight = entry.getValue();

            // 카테고리별 상품 검색 (가중치만큼)
            List<Long> categoryProducts = searchProductsByCategory(category, weight);
            recommendations.addAll(categoryProducts);
        }

        // 4. 중복 제거 및 상위 15개 반환
        return recommendations.stream()
            .distinct()
            .limit(15)
            .collect(Collectors.toList());
    }

    private List<UserEventLog> getUserBehaviorLogs(Long userId) {
        // 최근 30일간의 사용자 행동 로그 조회
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        return userEventLogRepository.findByUserIdAndCreatedAtAfter(
            userId, thirtyDaysAgo);
    }

    private Map<String, Integer> analyzePreferences(List<UserEventLog> logs) {
        return logs.stream()
            .filter(log -> "VIEW_PRODUCT".equals(log.getEventType()) ||
                          "ADD_TO_CART".equals(log.getEventType()) ||
                          "PURCHASE".equals(log.getEventType()))
            .collect(Collectors.groupingBy(
                log -> extractCategory(log.getTargetId()),
                Collectors.summingInt(log -> getEventWeight(log.getEventType()))
            ));
    }

    private int getEventWeight(String eventType) {
        return switch (eventType) {
            case "PURCHASE" -> 5;
            case "ADD_TO_CART" -> 3;
            case "VIEW_PRODUCT" -> 1;
            default -> 0;
        };
    }
}
```

#### 3.2 RecipeRecommendService

**역할**: 장바구니 재료 → LLM 레시피 생성 → 부족 재료 검색

#### 2.1 레시피 추천 서비스

```java
@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    private final ChatModel chatModel;
    private final ProductSearchService productSearchService;

    public RecipeRecommendation suggestRecipe(List<CartItem> cartItems) {
        // 1. 장바구니 상품 목록 추출
        List<String> ingredients = cartItems.stream()
            .map(CartItem::getProductName)
            .collect(Collectors.toList());

        // 2. LLM으로 레시피 생성
        String recipePrompt = buildRecipePrompt(ingredients);
        String recipeResponse = chatModel.call(recipePrompt);

        // 3. 부족한 재료 추출 (간단한 파싱)
        List<String> missingIngredients = extractMissingIngredients(recipeResponse);

        // 4. 부족 재료로 상품 검색
        List<ProductSearchResponse> recommendedProducts = missingIngredients.stream()
            .flatMap(ingredient -> productSearchService
                .searchProducts(ingredient, PageRequest.of(0, 3))
                .getContent()
                .stream())
            .collect(Collectors.toList());

        return new RecipeRecommendation(
            parseRecipe(recipeResponse),
            recommendedProducts
        );
    }

    private String buildRecipePrompt(List<String> ingredients) {
        return String.format("""
            다음 재료들로 만들 수 있는 한국 요리를 추천해주세요:

            재료: %s

            다음 형식으로 답변해주세요:
            1. 요리명
            2. 필요한 추가 재료 (없으면 "없음"이라고 답변)
            3. 간단한 조리법

            예시:
            1. 된장찌개
            2. 된장, 감자, 양파
            3. 1. 재료를 썰어 물에 넣고 끓인다. 2. 된장을 풀어 맛을 낸다.
            """, String.join(", ", ingredients));
    }

    private List<String> extractMissingIngredients(String response) {
        // 간단한 파싱 로직 (실제로는 더 정교한 파싱 필요)
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.startsWith("2.")) {
                String ingredients = line.substring(2).trim();
                if ("없음".equals(ingredients)) {
                    return List.of();
                }
                return Arrays.asList(ingredients.split(","));
            }
        }
        return List.of();
    }
}
```

### 4. `embedding/` 패키지 - 벡터/임베딩 관리

**역할**: 로그/도메인 이벤트 → 벡터 표현으로 변환, RAG용 정책 임베딩

#### 3.1 정책 문서 임베딩

```java
@Service
public class PolicyEmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ElasticsearchVectorStore vectorStore;

    public void initializePolicyDocuments() {
        List<Document> policyDocuments = loadPolicyDocuments();

        // 정책 문서들을 벡터로 변환하여 저장
        vectorStore.add(policyDocuments.stream()
            .map(doc -> {
                List<Double> embedding = embeddingModel.embed(doc.getContent());
                return new Document(doc.getContent(),
                    Map.of("embedding", embedding, "type", "policy"));
            })
            .collect(Collectors.toList()));
    }

    private List<Document> loadPolicyDocuments() {
        return List.of(
            new Document("환불 정책: 상품 수령 후 7일 이내에 환불 가능합니다.", Map.of("category", "refund")),
            new Document("배송비 정책: 30,000원 이상 구매 시 무료배송입니다.", Map.of("category", "shipping")),
            new Document("신선도 보장: 모든 농산물은 수확 후 24시간 이내 배송됩니다.", Map.of("category", "freshness"))
        );
    }
}
```

#### 3.2 챗봇 서비스

```java
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    public String answerQuestion(String question) {
        // 1. 질문과 유사한 정책 문서 검색
        List<Document> relevantDocs = vectorStore.similaritySearch(question, 3);

        // 2. 검색된 문서를 컨텍스트로 활용
        String context = relevantDocs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));

        // 3. 컨텍스트 기반 답변 생성
        String prompt = String.format("""
            다음은 바로팜 서비스 정책입니다:

            %s

            위 정책 정보를 바탕으로 다음 질문에 답변해주세요.
            정책에 없는 내용은 "정확한 답변을 위해 고객센터로 문의해주세요"라고 안내하세요.

            질문: %s

            답변은 친절하고 정확하게 작성해주세요.
            """, context, question);

        return chatModel.call(prompt);
    }
}
```

### 5. `search/` 패키지 - AI 관점 검색 뷰

**역할**: ES 기반 상품/체험 검색 + 자동완성, 추천/레시피에서 내부 클라이언트로 사용

#### 4.1 하이브리드 검색 서비스

```java
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final ChatModel chatModel;
    private final ElasticsearchOperations elasticsearchOps;
    private final VectorStore vectorStore;

    public SearchResult hybridSearch(String query) {
        // 1. LLM으로 쿼리 의도 분석
        String intent = analyzeIntent(query);

        // 2. 다중 검색 전략 실행
        CompletableFuture<List<Product>> keywordResults = searchByKeywords(query);
        CompletableFuture<List<Product>> vectorResults = searchByVector(query);

        // 3. 결과 병합 및 랭킹
        List<ScoredProduct> allResults = Stream.of(keywordResults, vectorResults)
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .distinct()
            .map(product -> calculateScore(product, query, intent))
            .sorted(Comparator.comparing(ScoredProduct::getScore).reversed())
            .limit(50)
            .collect(Collectors.toList());

        return new SearchResult(allResults);
    }

    private String analyzeIntent(String query) {
        String prompt = String.format("""
            다음 검색어의 의도를 분석하여 한 단어로 답변해주세요:

            검색어: %s

            의도: 상품명, 카테고리, 브랜드, 가격, 기타 중 하나
            """, query);

        return chatModel.call(prompt).trim();
    }

    private CompletableFuture<List<Product>> searchByKeywords(String query) {
        return CompletableFuture.supplyAsync(() -> {
            // Elasticsearch 키워드 검색
            NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.should(m -> m.matchPhrase(mp ->
                        mp.field("productName").query(query).boost(3.0f)));
                    b.should(m -> m.match(m ->
                        m.field("productName").query(query).boost(2.0f)));
                    b.should(m -> m.prefix(p ->
                        p.field("productNameChosung").value(query).boost(1.5f)));
                    b.minimumShouldMatch("1");
                    return b;
                }))
                .withPageable(PageRequest.of(0, 20))
                .build();

            SearchHits<ProductDocument> hits = elasticsearchOps.search(searchQuery, ProductDocument.class);
            return hits.getSearchHits().stream()
                .map(hit -> convertToProduct(hit.getContent()))
                .collect(Collectors.toList());
        });
    }

    private CompletableFuture<List<Product>> searchByVector(String query) {
        return CompletableFuture.supplyAsync(() -> {
            // 벡터 유사도 검색
            return vectorStore.similaritySearch(query, 20).stream()
                .map(this::convertToProduct)
                .collect(Collectors.toList());
        });
    }

    private ScoredProduct calculateScore(Product product, String query, String intent) {
        double score = 0.0;

        // 정확도 점수
        if (product.getName().contains(query)) score += 3.0;
        if (product.getName().toLowerCase().contains(query.toLowerCase())) score += 2.0;

        // 의도 기반 가중치
        switch (intent) {
            case "카테고리" -> score += product.getCategory().contains(query) ? 1.5 : 0;
            case "가격" -> score += query.matches(".*\\d+.*") ? 1.0 : 0;
        }

        return new ScoredProduct(product, score);
    }
}
```

## 🧪 테스트 및 배포

### 단위 테스트

```java
@SpringBootTest
class RecommendationServiceTest {

    @Autowired
    private PersonalizedRecommendService recommendationService;

    @Autowired
    private UserEventLogRepository logRepository;

    @Test
    void shouldGeneratePersonalizedRecommendations() {
        // Given
        Long userId = 1L;
        logRepository.save(createMockLog(userId, "VIEW_PRODUCT", 100L));
        logRepository.save(createMockLog(userId, "ADD_TO_CART", 101L));
        logRepository.save(createMockLog(userId, "PURCHASE", 102L));

        // When
        List<Long> recommendations = recommendationService
            .generatePersonalizedRecommendations(userId);

        // Then
        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.size()).isLessThanOrEqualTo(15);
    }
}
```

### 통합 테스트

```java
@SpringBootTest
@EmbeddedKafka
class AiIntegrationTest {

    @Autowired
    private KafkaTemplate<String, ExperienceEvent> kafkaTemplate;

    @Autowired
    private ExperienceSearchRepository repository;

    @Test
    void shouldIndexExperienceWhenEventReceived() {
        // Given
        ExperienceEvent event = createTestExperienceEvent();

        // When
        kafkaTemplate.send("experience-events", event);

        // Then
        await().atMost(5, SECONDS).until(() -> {
            Optional<ExperienceDocument> doc = repository.findById(event.getData().getExperienceId());
            return doc.isPresent();
        });
    }
}
```

### 6. `presentation/` 패키지 - 외부 API 진입점

**역할**: `/api/v1/recommendations/**`, `/api/v1/chatbot/**` 등 외부로 노출되는 REST API

#### 6.1 RecommendationController

```java
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationFacade recommendationFacade;

    @GetMapping("/personalized/{userId}")
    public ResponseDto<List<Long>> getPersonalizedRecommendations(
            @PathVariable Long userId) {

        List<Long> recommendations = recommendationFacade.recommendPersonalized(userId);
        return ResponseDto.success(recommendations);
    }

    @PostMapping("/recipes/from-cart")
    public ResponseDto<RecipeRecommendation> recommendRecipeFromCart(
            @RequestBody @Valid RecipeFromCartRequest request) {

        RecipeRecommendation recommendation =
            recommendationFacade.recommendRecipeFromCart(request.getUserId());

        return ResponseDto.success(recommendation);
    }
}
```

#### 6.2 ChatbotController

```java
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/ask")
    public ResponseDto<String> askQuestion(@RequestBody @Valid ChatbotRequest request) {

        String answer = chatbotService.answerQuestion(request.getQuestion());
        return ResponseDto.success(answer);
    }
}
```

### 테스트 및 배포

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

## 📊 모니터링 및 운영

### 메트릭 수집

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Service
    public static class AIMetricsService {

        @Autowired
        private MeterRegistry meterRegistry;

        public void recordAICall(String model, long durationMs, boolean success) {
            meterRegistry.timer("ai.call.duration",
                Tags.of("model", model, "success", String.valueOf(success)))
                .record(durationMs, MILLISECONDS);

            meterRegistry.counter("ai.call.total",
                Tags.of("model", model, "success", String.valueOf(success)))
                .increment();
        }
    }
}
```

### 비용 모니터링

```java
@Service
public class CostTracker {

    @Autowired
    private MeterRegistry meterRegistry;

    public void trackCost(String model, double cost) {
        meterRegistry.counter("ai.cost.total", Tags.of("model", model)).increment(cost);

        // 월별 비용 알림
        if (getMonthlyCost() > 50) {
            sendBudgetAlert();
        }
    }
}
```

이 가이드를 따라 각 기능을 구현하면 바로팜 AI 시스템의 핵심 기능을 완성할 수 있습니다! 🚀

## 🔢 임베딩 계층 설계

### 1. TextEmbeddingService (중앙 임베딩 엔진)

패키지: `embedding.service.TextEmbeddingService`

- 역할:
  - Spring AI `EmbeddingModel`을 감싸는 공통 유틸
  - 텍스트 → float[] 벡터 변환 담당
- 제공 메서드:
  - `float[] embedText(String text)`
  - `float[] embedProduct(String productName, String description, Long price)`
  - `float[] embedExperience(String title, Long pricePerPerson, Integer capacity, Integer durationMinutes)`

> 이 클래스는 **LLM/임베딩 모델에 대한 모든 직접 의존성을 한 곳에 모으는 것**이 목표다.  
> 나머지 도메인별 서비스(ProductEmbeddingService, ExperienceEmbeddingService, UserProfileEmbeddingService)는 
> 이 엔진을 주입받아 “무엇을 어떻게 텍스트로 합칠지”만 결정한다.

### 2. 도메인별 임베딩 서비스

- `ProductEmbeddingService`
  - `ProductEvent`를 입력으로 받아 상품 이름/설명/카테고리/가격을 TextEmbeddingService에 넘김
  - 결과 벡터를 `ProductEmbeddingDocument`로 만들어 ES 인덱스에 저장

- `ExperienceEmbeddingService`
  - `ExperienceEvent`를 입력으로 받아 체험 제목/설명/가격/소요시간 등을 EmbeddingService에 넘김
  - 결과 벡터를 `ExperienceEmbeddingDocument`로 만들어 ES 인덱스에 저장

- `UserProfileEmbeddingService`
  - `LogReadService`를 통해 userId별 검색/장바구니/주문 로그를 조회
  - 검색어/상품명/카테고리를 이어붙인 텍스트를 EmbeddingService에 넘김
  - 결과 벡터를 `UserProfileEmbeddingDocument`로 만들어 ES 인덱스에 저장
