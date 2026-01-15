# 🎯 기능 명세 및 API 설계

> baro-ai 모듈이 제공하는 기능들의 상세 명세와 API 인터페이스

## 📋 목차

- [개인화 추천 API](#-개인화-추천-api)
- [임베딩 서비스](#-임베딩-서비스)
- [레시피 추천 API](#-레시피-추천-api)
- [상품 검색 API](#-상품-검색-api)
- [체험 검색 API](#-체험-검색-api)
- [통합 검색 API](#-통합-검색-api)
- [챗봇 API](#-챗봇-api)

---

## 🛒 개인화 추천 API

사용자의 행동 로그를 기반으로 개인화된 상품을 추천합니다.

### GET /api/v1/recommendations/personalized/{userId}

사용자의 개인화 추천 상품 목록을 조회합니다.

#### 요청 파라미터
```json
{
  "userId": "UUID (필수) - 사용자 ID"
}
```

#### 응답 형식
```json
{
  "success": true,
  "data": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "productName": "청송사과 프리미엄 1kg",
      "productCategory": "과일",
      "price": 15000
    },
    {
      "productId": "550e8400-e29b-41d4-a716-446655440002",
      "productName": "바나나 1송이",
      "productCategory": "과일",
      "price": 5000
    }
  ]
}
```

#### 응답 필드 설명
- `data`: 추천 상품 목록 (기본값: 5개, 최대 15개)
  - `productId`: 상품 ID (UUID)
  - `productName`: 상품명
  - `productCategory`: 상품 카테고리
  - `price`: 가격
- 추천 순서는 유사도 점수 기반 내림차순
- 이미 주문하거나 장바구니에 담은 상품은 제외됨

### GET /api/v1/recommendations/personalized/{userId}/with-score

개인화 추천 상품을 조회합니다 (검증용 - 유사도 점수 포함).

#### 요청 파라미터
- `userId`: 사용자 ID (UUID, 필수)
- `topK`: 추천할 상품 개수 (기본값: 15)

#### 응답 형식
```json
{
  "success": true,
  "data": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "productName": "청송사과 프리미엄 1kg",
      "productCategory": "과일",
      "price": 15000,
      "similarityScore": 1.85,
      "matchReason": "사용자가 최근 '청송사과'를 검색하고 장바구니에 추가했습니다."
    }
  ]
}
```

#### 응답 필드 설명
- `similarityScore`: 유사도 점수 (0.0 ~ 2.0)
  - 1.9 ~ 2.0: 매우 높은 유사도
  - 1.7 ~ 1.9: 높은 유사도
  - 1.5 ~ 1.7: 중간 유사도
  - 1.0 ~ 1.5: 낮은 유사도
- `matchReason`: 매칭 이유 설명

#### 임베딩 생성 로직
1. **로그 수집**: 최근 30일간의 로그를 각 타입별로 최대 5개씩 (총 최대 15개)
   - 검색 로그 (SearchLogDocument)
   - 장바구니 로그 (CartLogDocument)
   - 주문 로그 (OrderLogDocument)

2. **품질 검증**: 최소 3개 이상의 로그 필요

3. **가중치 적용 텍스트 생성**:
   - 검색어: 시간 가중치 적용
   - 장바구니 상품: 이벤트 타입(ADD=2, UPDATE=1, REMOVE=0) × 수량 × 시간 가중치
   - 주문 상품: 이벤트 타입(ORDER_CREATED=3, ORDER_CANCELLED=0) × 수량 × 시간 가중치

4. **임베딩 벡터 생성**: OpenAI `text-embedding-3-small` 모델 사용 (1536차원)

5. **Elasticsearch 저장**: `user_profile_embeddings` 인덱스에 저장

#### 캐싱 전략
- Redis TTL: 1시간
- 키 형식: `recommend:user:{userId}`

#### 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "사용자를 찾을 수 없습니다"
  }
}
```

#### 임베딩 생성 실패 시
- 로그 개수가 3개 미만인 경우: 임베딩 생성 건너뜀
- OpenAI API 오류: 로그 기록 후 실패 처리

---

## 🤖 임베딩 서비스

### 사용자 프로필 임베딩 생성

사용자의 행동 로그를 기반으로 가중치를 적용하여 임베딩 벡터를 생성합니다.

#### 처리 흐름

1. **로그 수집** (각 타입별 최대 5개씩)
   ```java
   List<SearchLogDocument> searchLogs;  // 최대 5개
   List<CartLogDocument> cartLogs;      // 최대 5개
   List<OrderLogDocument> orderLogs;    // 최대 5개
   ```

2. **품질 검증**
   - 총 로그 개수가 3개 미만이면 임베딩 생성 건너뜀

3. **가중치 적용 텍스트 생성**
   - 검색어: 시간 가중치만 적용
   - 장바구니: 이벤트 타입 × 수량 × 시간 가중치
   - 주문: 이벤트 타입 × 수량 × 시간 가중치

4. **임베딩 벡터 생성**
   - OpenAI Embedding API 호출
   - 1536차원 벡터 생성

5. **Elasticsearch 저장**
   - 인덱스: `user_profile_embeddings`
   - 문서 ID: `userId`

#### 가중치 계산 규칙

| 로그 타입 | 이벤트 타입 | 가중치 | 수량 | 시간 가중치 |
|----------|------------|--------|------|------------|
| 검색 | - | 1.0 | - | 0.3 ~ 3.0 |
| 장바구니 | ADD | 2 | 실제 수량 | 0.3 ~ 3.0 |
| 장바구니 | UPDATE | 1 | 실제 수량 | 0.3 ~ 3.0 |
| 장바구니 | REMOVE | 0 | - | - (제외) |
| 주문 | ORDER_CREATED | 3 | 실제 수량 | 0.3 ~ 3.0 |
| 주문 | ORDER_CANCELLED | 0 | - | - (제외) |

#### 시간 가중치 계산

```java
// 지수 감쇠 함수
timeWeight = max(0.3, 3.0 * exp(-hoursAgo / 168.0))

// 예시:
// 1시간 전: ~2.8배
// 7일 전: ~1.5배
// 30일 전: ~0.3배
```

#### 상품 임베딩 생성

상품명을 기반으로 임베딩 벡터를 생성합니다.

```java
ProductEmbeddingService.embedProduct(productName)
  → OpenAI Embedding API 호출
  → 1536차원 float[] 벡터 반환
  → ProductDocument에 저장
```

#### 관련 문서

- [임베딩 예시](EMBEDDING_EXAMPLE.md) - 실제 데이터 흐름 예시

---

## 👩‍🍳 레시피 추천 API

> ⚠️ **현재 미구현 상태**: 레시피 추천 기능은 아직 구현되지 않았습니다. `RecipeRecommendService`는 현재 빈 클래스입니다.

장바구니 상품을 기반으로 레시피를 추천하고 부족 재료를 제안하는 기능입니다.

### 예정된 API

#### POST /api/v1/recommendations/recipes/from-cart

장바구니 상품을 분석하여 레시피를 추천합니다.

#### 요청 본문
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### 예상 응답 형식
```json
{
  "success": true,
  "data": {
    "recipeName": "된장찌개",
    "ingredients": [
      {"name": "애호박", "available": true},
      {"name": "두부", "available": true},
      {"name": "된장", "available": false}
    ],
    "instructions": "1. 재료를 썰어 물에 넣고 끓인다...",
    "missingIngredients": [
      {
        "name": "된장",
        "recommendedProducts": [
          {
            "productId": "550e8400-e29b-41d4-a716-446655440001",
            "productName": "국산 된장 500g",
            "price": 8500
          }
        ]
      }
    ]
  }
}
```

#### 예정된 요청 방식별 엔드포인트

**장바구니 기반**: `POST /api/v1/recommendations/recipes/from-cart`
**직접 입력**: `POST /api/v1/recommendations/recipes/from-ingredients`

```json
{
  "ingredients": ["애호박", "두부", "계란"],
  "preferences": "매운 음식"
}
```

---

## 🔍 상품 검색 API

의미 기반 상품 검색 및 자동완성을 제공합니다.

### GET /api/v1/search/product

상품을 키워드로 검색합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search/product?q=사과&category=과일&page=0&size=20
```

#### 요청 파라미터
- `q`: 검색 키워드 (필수)
- `category`: 카테고리 필터 (선택)
- `minPrice`: 최소 가격 (선택)
- `maxPrice`: 최대 가격 (선택)
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지 크기 (기본값: 20)

#### 요청 헤더
- `X-User-Id` (선택): 사용자 ID. 존재하는 경우에만 검색 행동 로그를 남깁니다.

#### 응답 형식
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440001",
        "productName": "GAP 인증 사과 1kg",
        "category": "과일",
        "price": 12000
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "totalElements": 45
  }
}
```

### GET /api/v1/search/product/autocomplete

상품명 자동완성 제안 목록을 조회합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search/product/autocomplete?q=사과&size=5
```

#### 응답 형식
```json
{
  "success": true,
  "data": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "productName": "사과"
    },
    {
      "productId": "550e8400-e29b-41d4-a716-446655440002",
      "productName": "사과즙"
    }
  ]
}
```

---

## 🎪 체험 검색 API

체험 상품 검색 및 자동완성을 제공합니다.

### GET /api/v1/search/experience

체험 상품을 검색합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search/experience?q=농장체험&region=경기도&page=0&size=10
```

#### 요청 파라미터
- `q`: 검색 키워드 (필수)
- `region`: 지역 필터 (선택)
- `minPrice`: 최소 가격 (선택)
- `maxPrice`: 최대 가격 (선택)
- `startDate`: 시작 날짜 (선택)
- `endDate`: 종료 날짜 (선택)

#### 응답 형식
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "experienceId": "550e8400-e29b-41d4-a716-446655440003",
        "experienceName": "전통 농장 체험",
        "region": "경기도 가평",
        "pricePerPerson": 35000,
        "capacity": 20,
        "durationMinutes": 180
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 10
    },
    "totalElements": 5
  }
}
```

### GET /api/v1/search/experience/autocomplete

체험명 자동완성 제안 목록을 조회합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search/experience/autocomplete?q=농&size=5
```

#### 응답 형식
```json
{
  "success": true,
  "data": [
    {
      "experienceId": 3001,
      "experienceName": "전통 농장 체험"
    }
  ]
}
```

---

## 🔎 통합 검색 API

상품과 체험을 동시에 검색합니다.

### GET /api/v1/search

통합 검색 결과를 조회합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search?q=사과&page=0&size=10
```

#### 요청 헤더
- `X-User-Id` (선택): 사용자 ID. 존재하는 경우에만 검색 행동 로그를 남깁니다.

#### 응답 형식
```json
{
  "success": true,
  "data": {
    "products": {
      "content": [
        {
          "productId": 1001,
          "productName": "GAP 인증 사과 1kg",
          "category": "과일",
          "price": 12000
        }
      ],
      "totalElements": 25,
      "pageNumber": 0,
      "pageSize": 10
    },
    "experiences": {
      "content": [
        {
          "experienceId": 3001,
          "experienceName": "전통 농장 체험",
          "region": "경기도 가평",
          "pricePerPerson": 35000
        }
      ],
      "totalElements": 5,
      "pageNumber": 0,
      "pageSize": 10
    }
  }
}
```

### GET /api/v1/search/autocomplete

통합 자동완성 결과를 조회합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search/autocomplete?q=토마&pSize=5&eSize=5
```

#### 요청 파라미터
- `q`: 검색 키워드 (필수)
- `pSize`: 상품 자동완성 개수 (기본값: 5)
- `eSize`: 체험 자동완성 개수 (기본값: 5)

#### 응답 형식
```json
{
  "success": true,
  "data": {
    "products": [
      {
        "productId": 1001,
        "productName": "토마토"
      }
    ],
    "experiences": [
      {
        "experienceId": 3001,
        "experienceName": "토마토 수확 체험"
      }
    ]
  }
}
```

---

## 💬 챗봇 API

> ⚠️ **현재 미구현 상태**: 챗봇 기능은 아직 구현되지 않았습니다. `ChatbotController`는 현재 빈 클래스입니다.

서비스 정책 관련 질문을 답변하는 AI 챗봇 기능입니다.

### 예정된 API

#### POST /api/v1/chatbot/ask

질문을 입력받아 정책 기반 답변을 제공합니다.

#### 예상 요청 본문
```json
{
  "question": "환불은 언제까지 가능한가요?",
  "sessionId": "session-123" // 선택적
}
```

#### 예상 응답 형식
```json
{
  "success": true,
  "data": {
    "answer": "상품 수령 후 7일 이내에 환불 가능합니다...",
    "confidence": 0.92,
    "sources": [
      {
        "policy": "환불 정책",
        "relevance": 0.95
      }
    ]
  }
}
```

#### 예정된 지원 질문 유형

✅ **지원 가능**
- 상품 주문/배송/환불 정책
- 서비스 이용 방법
- 농산물 품질 보장

❌ **지원 불가**
- 상품 추천 문의
- 개인 계정 정보
- 기술 지원

---

## 🛠️ 데이터 생성 API

개발 및 테스트 편의를 위한 데이터 생성 도구입니다.

### POST /api/v1/datagen/auto-amplify-products

SQL 파일을 기반으로 LLM을 사용하여 상품 데이터를 자동 증폭하고 SQL 파일로 저장합니다.

#### 요청 파라미터
- `sqlFilePath`: SQL 파일 경로 (기본값: `scripts/generate-dummy/product_dummy_origin.sql`)

#### 응답 형식
```json
{
  "success": true,
  "data": {
    "originalCount": 10,
    "amplifiedCount": 500,
    "outputFilePath": "scripts/generate-dummy/amplified_product_data.csv"
  }
}
```

### POST /api/v1/datagen/dummy-logs

하드코딩된 테스트 사용자에 대한 더미 행동 로그(검색/장바구니/주문)를 생성합니다.

#### 설명
- Kafka 이벤트가 아직 머지되지 않아 실제 이벤트를 받을 수 없을 때 사용
- 각 타입별로 최대 5개씩 생성
- `UserProfileEmbeddingService`가 사용하는 형식에 맞춰 생성
- 검색 로그는 LLM으로 생성된 키워드 사용
- 장바구니/주문 로그는 Elasticsearch에서 랜덤으로 가져온 상품 사용
- 로그는 계속 쌓이며, `UserProfileEmbeddingService`는 최신순으로 각 타입별 최대 5개씩만 사용

#### 응답 형식
```text
더미 로그 생성 완료 - 하드코딩된 테스트 사용자에 대한 로그가 생성되었습니다.
⚠️ 중요: 추천 결과를 업데이트하려면 다음 API를 호출하세요:
POST /api/v1/datagen/user-profile-embedding
```

### POST /api/v1/datagen/user-profile-embedding

하드코딩된 테스트 사용자의 행동 로그를 기반으로 프로필 임베딩 벡터를 생성합니다.

#### 설명
- 최근 30일간의 로그(검색/장바구니/주문 각 최대 5개씩)를 사용하여 1536차원 벡터를 생성
- 테스트 사용자 ID: `550e8400-e29b-41d4-a716-446655440000`

#### 응답 형식
```text
사용자 프로필 임베딩 생성 완료 - User: 550e8400-e29b-41d4-a716-446655440000
```

---

## 🔄 공통 응답 형식

모든 API는 일관된 응답 형식을 따릅니다.

### 성공 응답
```json
{
  "success": true,
  "data": { ... },
  "message": "요청 처리 성공"
}
```

### 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력 값이 올바르지 않습니다",
    "details": { ... }
  }
}
```

### 공통 에러 코드
- `VALIDATION_ERROR`: 입력 값 검증 실패
- `USER_NOT_FOUND`: 사용자 정보 없음
- `SERVICE_UNAVAILABLE`: 외부 서비스 장애
- `RATE_LIMIT_EXCEEDED`: 요청 제한 초과

---

## 📊 API 성능 메트릭

### 응답 시간 SLA
- 개인화 추천: 200ms 이하 (캐시 히트 시)
- 검색: 300ms 이하
- 챗봇: 2초 이하

### 캐시 전략
- 개인화 추천: Redis TTL 1시간
- 검색 결과: Redis TTL 10분
- 자동완성: Redis TTL 1시간

### Rate Limiting
- IP당 분당 100회 요청
- 사용자당 분당 50회 추천 요청

---

*이 문서는 API 인터페이스 명세를 중심으로 작성되었습니다. 구현 세부사항은 [구현 가이드](IMPLEMENTATION.md)를 참고해주세요.*

### 기술 구현
- **벡터 생성**: 사용자 행동 패턴 → 임베딩 모델 → 취향 벡터
- **유사도 계산**: 코사인 유사도로 상품 벡터 매칭
- **랭킹**: 유사도 점수 기반 Top-K 추출
- **캐싱**: Redis로 실시간 응답 보장

---

## 👩‍🍳 2. **레시피 추천** (핵심 기능)

**관여 계층**: `domain`, `application`, `infrastructure`

### 기능 개요
사용자의 장바구니 상품을 분석하여 **가능한 레시피를 추천**하고, 부족한 재료를 찾아 상품으로 제안합니다.

### 추천 예시

```text
사용자 장바구니: 애호박, 두부, 계란

AI 분석 결과:
🎯 "된장찌개" 레시피 추천!

필요 재료:
✅ 애호박 (장바구니에 있음)
✅ 두부 (장바구니에 있음)
✅ 계란 (장바구니에 있음)
❌ 된장 (부족 - 상품 추천)

추천 상품: "국산 된장 500g" - 바로팜에서 구매 가능!
```

### 구현 로직

```java
public RecipeRecommendation suggestRecipe(List<CartItem> cartItems) {
    // 1. 장바구니 상품 목록 추출
    List<String> ingredients = cartItems.stream()
        .map(CartItem::getProductName)
        .collect(Collectors.toList());

    // 2. LLM으로 레시피 분석
    String prompt = buildRecipePrompt(ingredients);
    String llmResponse = chatModel.call(prompt);

    // 3. 부족 재료 추출
    List<String> missingIngredients = parseMissingIngredients(llmResponse);

    // 4. Elasticsearch로 상품 검색
    List<Product> recommendedProducts = missingIngredients.stream()
        .map(ingredient -> elasticsearchService.searchProducts(ingredient, 3))
        .flatMap(List::stream)
        .collect(Collectors.toList());

    return new RecipeRecommendation(llmResponse, recommendedProducts);
}
```

### 기술 구현
- **LLM 분석**: 상품 조합 → 레시피 추론
- **재료 매핑**: 한글 재료명 → 표준화된 검색어
- **상품 검색**: Elasticsearch로 재료 관련 상품 조회
- **실시간 재고**: 상품 추천 시 재고 상태 확인

---

## 💬 3. **서비스 챗봇** (보조 기능)

**관여 계층**: `domain`, `application`, `infrastructure`

### 기능 개요
서비스 정책과 관련된 질문을 **정확하게 답변**하는 AI 챗봇입니다.

### 지원 질문 유형

```text
✅ 지원 가능:
- "상품을 주문했는데 환불하고 싶어요. 언제까지 가능하나요?"
- "배송비는 얼마인가요? 무료 배송 조건이 있나요?"
- "농산물 신선도 보장은 어떻게 되나요?"

❌ 지원 불가:
- 상품 추천, 레시피 문의 (다른 기능에서 처리)
- 개인 계정 정보, 주문 상태 (권한 이슈)
```

### RAG 구현

```java
@Service
public class PolicyChatbotService {

    @Autowired
    private VectorStore vectorStore; // 정책 문서 벡터

    @Autowired
    private ChatModel chatModel; // GPT-4 with RAG

    public String answerQuestion(String question) {
        // 1. 질문과 유사한 정책 문서 검색
        List<Document> relevantPolicies = vectorStore
            .similaritySearch(question, 3);

        // 2. 검색된 정책을 컨텍스트로 답변 생성
        String context = relevantPolicies.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n"));

        String prompt = String.format("""
            다음은 바로팜 서비스 정책입니다:

            %s

            위 정책을 바탕으로 다음 질문에 답변해주세요:
            %s

            답변은 친절하고 정확해야 합니다.
            """, context, question);

        return chatModel.call(prompt);
    }
}
```

### 기술 구현
- **문서 임베딩**: 서비스 정책 문서 → 벡터 변환
- **유사도 검색**: 질문 → 관련 정책 문서 검색
- **컨텍스트 답변**: 검색된 정책 기반 정확한 답변 생성
- **안전성**: 정책 외 질문은 적절히 거부

---


