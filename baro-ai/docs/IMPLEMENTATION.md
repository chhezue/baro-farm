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
- Java: 21 이상
- Gradle: 7.6 이상
- Elasticsearch: 8.x
- Kafka: 3.x
- Docker & Docker Compose
```

### 의존성 설정

```gradle
// build.gradle (핵심만 발췌 — 전체는 baro-ai/build.gradle)
dependencies {
    // AI/ML
    implementation 'org.springframework.ai:spring-ai-starter-model-google-genai'
    implementation 'org.springframework.ai:spring-ai-starter-model-google-genai-embedding'

    // 데이터/검색 — 이 모듈은 RDB를 쓰지 않는다(JPA·MySQL 드라이버 없음)
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'

    // 메시징
    implementation 'org.springframework.kafka:spring-kafka'

    // 웹
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // 다른 모듈 호출
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'

    // 캐시(자동완성 전용) · 지표
    implementation 'com.github.ben-manes.caffeine:caffeine'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

> `baro-common`이 JPA 스타터를 끌어오므로 Hibernate는 여전히 클래스패스에 있다.
> `application.yml`이 `DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration`을 제외해 기동을 막지 않는다.

### 환경 설정

```yaml
# application.yml (프로필별 파일 없음 — 환경 차이는 전부 환경변수)
server:
  port: ${SERVER_PORT:8092}

api:
  v1: /api/v1

spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://localhost:9200}

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ai-service

  ai:
    google:
      genai:
        api-key: ${BARO_AI_GEMINI_KEY:}
        chat:
          options:
            model: ${SPRING_AI_GEMINI_MODEL:gemini-2.5-flash-lite}
            temperature: 0.7
        embedding:
          api-key: ${BARO_AI_GEMINI_KEY:}
          text:
            options:
              model: ${SPRING_AI_GEMINI_EMBEDDING_MODEL:gemini-embedding-001}
              dimensions: 1536
```

### 로컬 개발 실행

```bash
# 1. 인프라 서비스 실행
docker-compose -f docker-compose.data.yml up -d

# 1.1. (필수) 환경변수 설정
# 프로필을 지정할 필요가 없다 — 설정 파일이 application.yml 하나다(2026-09-05).
# Config Server도 쓰지 않으므로 SPRING_CLOUD_CONFIG_* 를 따로 끌 필요가 없다.
export BARO_AI_GEMINI_KEY="YOUR_GEMINI_API_KEY"
export ELASTICSEARCH_URIS="http://localhost:9200"
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
# baro-shopping을 함께 띄웠다면 (레시피 추천 확인용)
export SHOPPING_SERVICE_URL="http://localhost:8082"

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
    private final CartLogRepository cartLogRepository;
    private final OrderLogRepository orderLogRepository;
    private final SearchLogRepository searchLogRepository;
    private final ProductSearchRepository productSearchRepository; // 상품 벡터를 읽는다
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

        // 3. 로그마다 "어떤 상품을 얼마나 좋아하는지" 계산 (비중 0인 삭제·취소는 여기서 빠짐)
        List<WeightedProduct> interests = ...;

        // 4. 상품 벡터를 한 번에 조회해 비중대로 섞고 길이를 1로 맞춤
        Map<UUID, float[]> productVectors = loadProductVectors(...);
        List<Double> profileVector = blendIntoProfileVector(interests, productVectors);

        // 5. Elasticsearch에 저장
        UserProfileEmbeddingDocument document = UserProfileEmbeddingDocument.builder()
            .userId(userId)
            .userProfileVector(profileVector)
            .lastUpdatedAt(Instant.now())
            .sourceProductIds(sourceProductIds)
            .build();
        userProfileEmbeddingRepository.save(document);
    }

    private double behaviorWeight(HistoryEventType eventType, Integer quantity, Instant occurredAt) {
        // 이벤트 종류 × 수량(5까지) × 시간(7일마다 절반)
        // 예: 담기 × 수량 3 × 1시간 전 = 2 × 3 × 2.99 = 17.93
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
    private final ProductIngredientNormalizer ingredientNormalizer;

    public ProductDocument indexProduct(ProductIndexRequest request) {
        float[] vector = null;

        // 임베딩이 실패하더라도 인덱싱은 계속되어야 함
        try {
            // 상품 이름을 기반으로 임베딩 생성
            vector = productEmbeddingService.embedProduct(request.productName());
        } catch (Exception e) {
            log.error("❌ Product embedding failed. productId=" + request.productId(), e);
        }

        // 레시피 추천이 읽을 표준 재료명을 색인 시점에 1회 추출
        String ingredientName =
            ingredientNormalizer.normalize(request.productName(), request.productCategoryName());

        // 정렬 2순위인 updatedAt에는 이벤트가 실어 보낸 값(= 발행 시각)을 그대로 쓴다.
        // 색인 시각을 넣으면 위의 임베딩·LLM 호출 시간만큼 상품마다 제각각 밀린다.
        Instant updatedAt = request.updatedAt() != null ? request.updatedAt() : Instant.now();

        ProductDocument doc = new ProductDocument(
            request.productId(),
            request.productName(),
            request.productCategoryId(),
            request.productCategoryCode(),
            request.productCategoryName(),
            request.price(),
            request.status(),
            updatedAt,
            ingredientName,
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

    private final RecipeCandidateGenerator recipeCandidateGenerator;
    private final ProductSearchRepository productSearchRepository;

    private RecipeRecommendResponse recommendFromCart(CartInfo cart, String userIdentifier) {
        // 1. 장바구니 상품 중 레시피 대상만 남긴다 (카테고리 제외 필터)
        List<CartItemInfo> eligibleItems = ...;

        // 2. 보유 재료는 색인 시점에 계산해 둔 ingredientName을 ES에서 한 번에 읽는다 (LLM 호출 없음)
        //    근거: docs/decisions/002-ingredient-name-at-index-time.md
        List<String> ownedNames = loadOwnedIngredientNames(eligibleItems, userIdentifier);

        // 3. 레시피 후보 생성 — 요청 경로에 남은 유일한 LLM 호출
        //    후보 중 무엇을 쓸지는 서버가 점수로 다시 고르지 않고 LLM이 낸 순서를 존중한다
        RecipeCandidates candidates = recipeCandidateGenerator.generateRecipeCandidates(ownedNames);
        CandidateRecipePlan plan = candidates.chooseFirst().orElseThrow(...);

        // 4. 부족한 재료는 CandidateRecipePlan.from이 서버 계산으로 채워 둔 값을 그대로 쓴다
        List<String> missingCore = plan.missingIngredientsCore();

        // 5. 부족 재료별로 대체 상품을 ES에서 검색
        ...
    }
}
```

### 색인 시점 재료명 추출

> **[Infrastructure Layer]** 상품명에서 요리 재료명을 뽑아 `ProductDocument.ingredientName`에 넣는다.
> 상품 색인(Kafka `product-events` 소비) 시점에만 호출되고, 추천 요청 경로에서는 호출하지 않는다.
> 근거: [`decisions/002-ingredient-name-at-index-time.md`](decisions/002-ingredient-name-at-index-time.md)

```java
// search/infrastructure/llm/ProductIngredientNormalizer.java

@Component
@RequiredArgsConstructor
public class ProductIngredientNormalizer {

    private final ChatClient chatClient;

    // 카테고리는 상품명만으로 애매한 경우를 좁혀 주는 힌트다
    public String normalize(String productName, String categoryName) {
        if (productName == null || productName.isBlank()) {
            return "";
        }
        String category = categoryName == null || categoryName.isBlank() ? NO_CATEGORY : categoryName;
        try {
            String json = chatClient.prompt()
                // 응답 형식은 프롬프트로 부탁하지 않고 Gemini의 responseSchema로 강제한다
                // (docs/audits/cross-cutting/001-llm-call-infra.md A-2)
                .options(GoogleGenAiChatOptions.builder()
                    .temperature(DETERMINISTIC_TEMPERATURE)
                    .responseMimeType(MediaType.APPLICATION_JSON_VALUE)
                    .responseSchema(RESPONSE_CONVERTER.getJsonSchema())
                    .build())
                // 상품명은 .param()으로 넘긴다 — 문자열에 직접 이어붙이면 상품명 안의 중괄호가
                // 템플릿 문법으로 해석돼 렌더링이 실패한다
                .user(u -> u.text(PROMPT)
                    .param("productName", productName)
                    .param("categoryName", category))
                .call()
                .content();
            NormalizationResponse response = RESPONSE_CONVERTER.convert(json);

            String normalized = response == null || response.normalizedIngredient() == null
                ? "" : response.normalizedIngredient().trim();

            // 빈 답·실패는 빈 값으로 색인한다. 추천이 빈 값을 건너뛰고 재색인 대상으로 세므로
            // 정규식으로 만든 그럴듯한 오답을 넣지 않는다 (docs/audits/search/001-index-path.md B-2)
            if (normalized.isBlank()) {
                log.warn("재료명 추출 결과가 비어 빈 값으로 색인합니다. 재색인 대상: '{}'", productName);
                return "";
            }
            return normalized;
        } catch (Exception e) {
            log.warn("재료명 추출 실패로 빈 값으로 색인합니다. 재색인 대상: '{}'", productName, e);
            return "";
        }
    }
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
    private final VectorRecommendPipeline vectorRecommendPipeline;

    public List<ProductRecommendResponse> recommendProducts(UUID userId, int topK) {
        // 1. 사용자 프로필 벡터 조회
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null
            || profile.getUserProfileVector() == null
            || profile.getUserProfileVector().length == 0) {
            return List.of();
        }

        // 2. 이미 경험한 상품 ID 목록 가져오기
        List<UUID> experiencedProductIds = profile.getSourceProductIds() != null
            ? profile.getSourceProductIds()
            : List.of();

        // 3. 후보 조회 → 다양화 → 응답 변환은 공통 경로가 맡는다
        return vectorRecommendPipeline.recommend(
            profile.getUserProfileVector(), experiencedProductIds, topK);
    }
}
```

### 개인화 추천 캐싱

개인화 추천·유사 상품 추천·레시피 추천은 캐싱하지 않는다 — 사용자·상품별로 결과가 갈려 캐시 효율이 낮고, 방금 담은 장바구니·방금 완료한 주문이 바로 반영돼야 하는 최신성 요구가 더 크다고 판단했다. 실제로 캐싱하는 건 `autocomplete` 하나뿐이다(Caffeine, `CacheConfig.java`). 근거·기각한 대안: [`decisions/001-autocomplete-caching.md`](decisions/001-autocomplete-caching.md).

### 비슷한 상품 추천 로직

> **[Application Layer]** 특정 상품의 벡터를 기반으로 유사한 상품을 추천합니다.

```java
// recommend/application/SimilarProductRecommendService.java

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarProductRecommendService {

    private final ProductSearchRepository productSearchRepository;
    private final VectorRecommendPipeline vectorRecommendPipeline;

    public List<ProductRecommendResponse> recommendSimilarProducts(UUID productId, int topK) {
        // 1. 기준 상품 조회 및 벡터 추출
        // 상품 디테일 페이지의 다른 부분이 함께 죽지 않도록 예외 대신 빈 목록을 돌려준다.
        ProductDocument product = productSearchRepository.findById(productId)
            .orElse(null);

        if (product == null) {
            log.warn("유사 상품 추천을 위한 기준 상품을 찾을 수 없습니다. productId: {}", productId);
            return List.of();
        }

        if (product.getVector() == null) {
            log.warn("기준 상품 '{}'의 벡터가 존재하지 않습니다. 임베딩이 필요합니다.", product.getProductName());
            return List.of();
        }

        // 2. 기준 상품 자신을 뺀 후보에서 벡터 유사도 검색 + 다양화
        return vectorRecommendPipeline.recommend(product.getVector(), List.of(productId), topK);
    }
}
```

### 추천 공통 경로

> **[Application Layer]** 유사 상품·개인화 추천이 공유하는 후보 조회 → 다양화 → 응답 변환 경로입니다.

```java
// recommend/application/VectorRecommendPipeline.java

@Service
@RequiredArgsConstructor
public class VectorRecommendPipeline {

    private static final int CANDIDATE_MULTIPLIER = 3;
    private static final int MAX_CANDIDATE_SIZE = 100;

    private final VectorProductSearchService vectorProductSearchService;
    private final MaxMinDiversityService maxMinDiversityService;

    public List<ProductRecommendResponse> recommend(
        float[] queryVector, List<UUID> excludeProductIds, int topK
    ) {
        int candidateSize = Math.min(topK * CANDIDATE_MULTIPLIER, MAX_CANDIDATE_SIZE);

        List<ProductDocument> candidates =
            vectorProductSearchService.findSimilarProductDocumentsByVector(
                queryVector, candidateSize, excludeProductIds);

        if (candidates.isEmpty()) {
            return List.of();
        }

        return maxMinDiversityService.selectDiverse(queryVector, candidates, topK).stream()
            .map(ProductRecommendResponse::from)
            .toList();
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

    public List<ProductDocument> findSimilarProductDocumentsByVector(
        float[] queryVector,
        int candidateSize,
        List<UUID> excludeProductIds
    ) {
        // Elasticsearch script_score 쿼리를 사용한 코사인 유사도 계산
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q
                .scriptScore(ss -> ss
                    .query(q2 -> q2
                        .bool(b -> b
                            // 자기 자신 또는 이미 경험한 상품 제외
                            .mustNot(mn -> { /* excludeProductIds -> ids 쿼리 */ return mn; })
                            // 판매 중인 상품만 필터링
                            .filter(f -> f
                                .terms(t -> t
                                    .field("status")
                                    .terms(v -> v.value(
                                        List.of(FieldValue.of("ON_SALE"), FieldValue.of("DISCOUNTED"))
                                    ))
                                )
                            )
                            // 벡터 없는 문서 제외 — cosineSimilarity가 오류를 던지는 것을 막는다
                            .filter(f -> f.exists(e -> e.field("vector")))
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
            .withPageable(PageRequest.of(0, candidateSize))
            .build();

        // 검색 실행 및 결과 변환
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
        // ... 결과 처리, 실패 시 빈 목록
        return List.of();
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
    private float[] userProfileVector; // 1536차원 벡터 (상품 벡터와 동일한 타입)

    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;

    @Field(type = FieldType.Keyword)
    private List<UUID> sourceProductIds; // 추천에서 제외할 기경험 상품
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
    private String eventType; // CART_ITEM_ADDED, CART_QUANTITY_UPDATED, CART_ITEM_REMOVED

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

인프라(Elasticsearch, Kafka, MySQL)는 프로젝트 루트의 `docker-compose.data.yml`로 실행합니다.

- `baro-ai`(API 서버)는 로컬에서는 `./gradlew bootRun`으로 실행하거나
- Kubernetes에서는 `k8s/apps/baro-ai/` 매니페스트를 배포합니다.

### 헬스체크 및 모니터링

- Health: `GET /actuator/health`
- 메트릭: `GET /actuator/metrics` (및 `GET /actuator/metrics/{metric.name}`)

### 로그 및 메트릭

이 서비스는 기본적으로 Micrometer + Spring Boot Actuator를 통해 로그/메트릭을 수집합니다.

커스텀 메트릭 수집 코드는 문서에 포함하지 않고, 기본 엔드포인트를 기준으로 확인하는 것을 권장합니다.

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
        run: kubectl apply -f k8s/apps/baro-ai/
```

---

*이 구현 가이드를 따라 baro-ai 모듈의 핵심 기능을 개발할 수 있습니다.*
