# 바로팜 OpenAI API + AWS 활용 가이드

## 💰 예산 제약 상황 (72달러)
**OpenAI API 사용 가능량 (예상):**
- GPT-3.5-turbo: ~35,000회 호출 가능
- GPT-4: ~1,200회 호출 가능 (비용 비효율적)
- Text Embeddings: ~7억 토큰 가능

**전략**: GPT-3.5-turbo + Embeddings 위주로 핵심 기능 구현

---

## 1️⃣ 이미지 생성 기능 (제한적 활용)

### 💡 OpenAI API + AWS 활용 방안 (최소 사용)

**OpenAI DALL-E 3 (월 1-2회 사용만 가능)**
- **활용법**: 상품 등록 시에만 제한적으로 사용
- **장점**: 고품질 이미지, API 연동 간단
- **제약**: 72달러 예산으로는 빈번한 사용 불가
- **코드 예시**:
```java
@Autowired
private OpenAiImageModel imageModel;

public String generateProductImage(String productName, String description) {
    // 관리자 승인 후에만 실행
    if (isAdminApproved && monthlyUsageUnderLimit()) {
        String prompt = String.format("신선한 농산물 %s: %s, 현실적이고 appetizing한 이미지", productName, description);
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        return response.getResult().getOutput().getUrl();
    }
    return null; // 예산 부족 시 기본 이미지 사용
}
```

**AWS Bedrock (Stable Diffusion) 우선 추천**
- **활용법**: AWS 유료 계정으로 주력 이미지 생성 솔루션
- **장점**: 무제한 사용 가능, 한국어 프롬프트 지원
- **활용 사례**: 상품 등록 시 자동 이미지 생성 + S3 저장

---

## 2️⃣ 추천 시스템 (핵심 활용 영역)

### 2.1 상품/예약 상세 페이지 추천

#### 💡 OpenAI API + AWS 활용 방안

**OpenAI Embeddings + AWS OpenSearch**
- **활용법**: OpenAI API로 임베딩 생성 → AWS OpenSearch에 벡터 저장
- **장점**: 저비용으로 고품질 유사도 검색 가능
- **비용 효율**: Embeddings 비용이 매우 저렴
- **코드 예시**:
```java
@Autowired
private OpenAiEmbeddingModel embeddingModel;

@Autowired
private OpenSearchOperations searchOps;

public List<Product> findSimilarProducts(String productId) {
    Product product = productRepository.findById(productId);
    List<Double> embedding = embeddingModel.embed(product.getName() + " " + product.getDescription());

    // OpenSearch에서 벡터 유사도 검색
    return searchOps.similaritySearch(embedding, 10);
}
```

### 2.2 장바구니 기반 레시피 추천

#### 💡 OpenAI API + AWS 활용 방안

**OpenAI GPT-3.5-turbo + AWS Lambda (Tool Calling)**
- **활용법**: 저비용 GPT-3.5-turbo로 레시피 추천 → AWS Lambda로 재고 확인
- **장점**: 비용 효율적, 실시간 재고 반영
- **비용 절감**: GPT-4 대신 3.5-turbo 사용
- **코드 예시**:
```java
@Autowired
private ChatModel chatModel; // GPT-3.5-turbo 설정

@Tool("재고 확인")
public boolean checkInventory(List<String> ingredients) {
    return inventoryService.checkStock(ingredients);
}

public RecipeSuggestion suggestRecipe(List<CartItem> cartItems) {
    String prompt = buildRecipePrompt(cartItems);

    return chatModel.call(prompt)
        .withTool("checkInventory", this::checkInventory)
        .execute(RecipeSuggestion.class);
}
```

### 2.3 검색어 기록 기반 개인화 추천

#### 💡 OpenAI API + AWS 활용 방안

**OpenAI Embeddings + AWS Personalize**
- **활용법**: 저비용 임베딩으로 사용자 프로필 생성 → Personalize로 추천
- **장점**: 실시간 학습, 비용 효율적
- **코드 예시**:
```java
@Autowired
private OpenAiEmbeddingModel embeddingModel;

public UserEmbedding createUserProfile(List<SearchHistory> searches) {
    String combinedQuery = searches.stream()
        .map(SearchHistory::getQuery)
        .collect(Collectors.joining(" "));

    List<Double> embedding = embeddingModel.embed(combinedQuery);
    return new UserEmbedding(embedding);
}
```

---

## 3️⃣ 챗봇 AI 기능 (제한적 활용)

### 💡 OpenAI API + AWS 활용 방안

**OpenAI GPT-3.5-turbo + AWS Lex**
- **활용법**: 저비용 모델로 기본 챗봇 구현 → Lex로 대화 관리
- **장점**: 비용 효율적, 안정적인 대화 흐름
- **제약**: 복잡한 레시피 설명은 제한적
- **코드 예시**:
```java
@Autowired
private ChatModel chatModel; // GPT-3.5-turbo

@PostMapping("/chat")
public ChatResponse handleChat(@RequestBody ChatRequest request) {
    // 기본적인 재고/배송 문의만 처리
    String response = chatModel.call(buildSimpleResponsePrompt(request.getMessage()));
    return new ChatResponse(response);
}
```

---

## 4️⃣ 판매자 대시보드 AI 활용 (핵심 활용)

### 💡 OpenAI API + AWS 활용 방안

**OpenAI GPT-3.5-turbo + AWS QuickSight**
- **활용법**: 저비용 분석으로 판매 데이터 요약 → QuickSight로 시각화
- **장점**: 경제적인 분석 + 강력한 BI 도구
- **코드 예시**:
```java
@Autowired
private ChatModel chatModel; // GPT-3.5-turbo

public TrendReport generateTrendReport(List<SalesData> salesData) {
    String analysisPrompt = buildTrendAnalysisPrompt(salesData);
    String insights = chatModel.call(analysisPrompt);

    return new TrendReport(insights);
}
```

---

## 5️⃣ 리뷰 AI 요약 기능 (핵심 활용)

### 💡 OpenAI API + AWS 활용 방안

**OpenAI GPT-3.5-turbo + AWS Comprehend**
- **활용법**: 저비용 요약으로 리뷰 분석 → Comprehend로 키워드 추출
- **장점**: 비용 효율적, 정확한 키워드 추출
- **코드 예시**:
```java
@Autowired
private ChatModel chatModel; // GPT-3.5-turbo

public ReviewSummary summarizeReviews(List<Review> reviews) {
    String summaryPrompt = buildReviewSummaryPrompt(reviews);
    String summary = chatModel.call(summaryPrompt);

    return new ReviewSummary(summary);
}
```

---

## 📊 72달러 예산으로 가능한 기능 우선순위

### 🟢 **최우선 구현 (저비용 고효율)**
1. **추천 시스템**: Embeddings 기반 유사 상품 추천
2. **리뷰 요약**: GPT-3.5-turbo로 기본 요약
3. **판매자 대시보드**: 간단한 트렌드 분석

### 🟡 **중간 우선순위 (제한적 사용)**
1. **챗봇**: 기본 문의 응대만
2. **이미지 생성**: 월 1-2회 특수 케이스만

### 🔴 **보류 (고비용)**
1. **GPT-4 활용 기능**: 레시피 상세 설명, 복합 챗봇
2. **빈번한 이미지 생성**: 상품 등록 시 마다

---

## 🔧 OpenAI API 설정 (72달러 예산 최적화)

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        model: gpt-3.5-turbo  # 비용 절감을 위해 3.5-turbo 우선 사용
        temperature: 0.7
      embedding:
        model: text-embedding-ada-002  # 저비용 임베딩

# 비용 모니터링 설정
openai:
  budget:
    monthly-limit: 72
    alert-threshold: 50  # 50달러 사용 시 알림
```

---

## 💡 비용 최적화 전략

### 1. **모델 선택 전략**
- **GPT-3.5-turbo**: 일반적인 텍스트 작업 (요약, 분석, 기본 챗봇)
- **Embeddings**: 추천 시스템의 핵심 (매우 저비용)
- **GPT-4**: 정말 필요한 경우에만 (월 50회 이내)

### 2. **사용량 최적화**
- **캐싱**: 자주 사용되는 응답 Redis 캐시
- **배치 처리**: 실시간 대신 배치로 분석
- **프롬프트 최적화**: 불필요한 토큰 사용 최소화

### 3. **모니터링 및 관리**
```java
@Component
public class OpenAIBudgetMonitor {

    @Autowired
    private MeterRegistry meterRegistry;

    public void trackUsage(String model, int tokens, double cost) {
        meterRegistry.counter("openai.tokens.used", "model", model).increment(tokens);
        meterRegistry.counter("openai.cost", "model", model).increment(cost);

        // 예산 초과 시 알림
        if (getMonthlyCost() > 50) {
            sendBudgetAlert();
        }
    }
}
```

---

## 🎯 결론: 72달러로 구현 가능한 바로팜 AI 기능

**핵심 성공 포인트:**
1. **Embeddings 기반 추천 시스템**으로 핵심 가치를 제공
2. **GPT-3.5-turbo**로 보조 기능 (요약, 분석) 구현
3. **AWS 서비스**로 비용 부담 최소화

**예상 월간 사용량:**
- 상품 추천: 10,000회 (임베딩)
- 리뷰 요약: 500회 (GPT-3.5)
- 판매 분석: 300회 (GPT-3.5)
- **총 비용: ~$25-35** (예산 내 안정적 운영 가능)

이 전략으로 **MVP 수준의 AI 기능**을 안정적으로 운영할 수 있습니다! 🚀