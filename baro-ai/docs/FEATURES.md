# 🎯 기능 명세 및 API 설계

> baro-ai 모듈이 제공하는 기능들의 상세 명세와 API 인터페이스

## 📋 목차

- [개인화 추천 API](#-개인화-추천-api)
- [비슷한 상품 추천 API](#-비슷한-상품-추천-api)
- [임베딩 서비스](#-임베딩-서비스)
- [레시피 추천 API](#-레시피-추천-api)
- [상품 검색 API](#-상품-검색-api)
- [챗봇 API](#-챗봇-api)
---

## 🛒 개인화 추천 API

사용자의 행동 로그를 기반으로 개인화된 상품을 추천합니다.

### GET /api/v1/recommendations/personalized/{userId}

사용자의 개인화 추천 상품 목록을 조회합니다.

#### 요청 파라미터
```json
{
  "userId": "UUID (필수) - 사용자 ID",
  "topK": 5
}
```

#### 응답 형식
```json
{
  "status": 200,
  "data": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "productName": "청송사과 프리미엄 1kg",
      "productCategoryName": "과일",
      "price": 15000
    },
    {
      "productId": "550e8400-e29b-41d4-a716-446655440002",
      "productName": "바나나 1송이",
      "productCategoryName": "과일",
      "price": 5000
    }
  ],
  "message": null
}
```

#### 응답 필드 설명
- `data`는 추천 상품 배열입니다.
  - `productId`: 상품 ID (UUID)
  - `productName`: 상품명
  - `productCategoryName`: 상품 카테고리 이름
  - `price`: 가격
- 이미 주문하거나 장바구니에 담은 상품은 제외됨

> 참고: `GET /api/v1/recommendations/personalized/{userId}/with-score` 엔드포인트는 현재 제공되지 않습니다.

---

## 🤖 임베딩 서비스

### 사용자 프로필 임베딩 생성

사용자의 행동 로그를 기반으로 가중치를 적용하여 임베딩 벡터를 생성합니다.

#### 처리 흐름

1. **로그 수집** (각 타입별 최대 5개씩)
   ```java
   List<CartLogDocument> cartLogs;      // 최대 5개
   List<OrderLogDocument> orderLogs;    // 최대 5개
   ```
   검색 로그는 2026-09-07에 신호에서 뺐다 — 사용자가 고른 상품이 아니라 검색 결과 상위에 뜬
   상품을 취향으로 삼는 방식이었다(`docs/audits/taste-vector/005-signal-and-weight-simplification.md`).

2. **품질 검증**
   - 총 로그 개수가 3개 미만이면 임베딩 생성 건너뜀

3. **로그별 비중 계산** — 이벤트 타입 하나로 정해진다(곱하는 축이 없다)
   - 장바구니: 담기 2 · 수량 변경 1 · 삭제 0
   - 주문: 확정 3 · 취소 0

   수량 축은 2026-09-07에 뺐다. 수량은 선호의 세기가 아니라 소비량이고, `quantity`의 의미가
   이벤트마다 달라(담기는 증분, 수량 변경은 새 총량) 같은 식에 넣을 수 없는 값이었다.

4. **프로필 벡터 계산**
   - 로그 상품이 이미 가진 `ProductDocument.vector`를 비중대로 가중 평균 후 L2 정규화
   - 외부 임베딩 API 호출 없음 (2026-09-05 재설계)

5. **Elasticsearch 저장**
   - 인덱스: `user_profile_embeddings`
   - 문서 ID: `userId`

#### 가중치 계산 규칙

| 로그 타입 | 이벤트 타입 | 비중 |
|----------|------------|------|
| 장바구니 | CART_ITEM_ADDED | 2 |
| 장바구니 | CART_QUANTITY_UPDATED | 1 |
| 장바구니 | CART_ITEM_REMOVED | 0 (제외) |
| 주문 | ORDER_CONFIRMED | 3 |
| 주문 | ORDER_CANCELLED | 0 (제외) |

이 값이 곧 로그 1건의 최종 비중이다 — 곱하는 축이 더 없어서 범위가 0 또는 1~3이다.

한때 여기에 두 축을 더 곱했으나 둘 다 뺐다. **시간 감쇠**(7일 반감기, 2026-09-06 삭제)는 유형별 최근 5건만 읽는 구조에서 한 번의 쇼핑 로그가 전부 같은 시간대라 감쇠값이 같고, L2 정규화가 그 배수를 소거해 결과에 영향이 없었다(`docs/audits/taste-vector/002-weight-axes-and-tests.md`). **수량**(2026-09-07 삭제)은 선호의 세기가 아니라 소비량이었다(`docs/audits/taste-vector/005-signal-and-weight-simplification.md`).

#### 상품 임베딩 생성

상품명을 기반으로 임베딩 벡터를 생성합니다.

```java
ProductEmbeddingService.embedProduct(productName)
  → Gemini Embedding API 호출
  → 1536차원 float[] 벡터 반환
  → ProductDocument에 저장
```

#### 관련 문서

- [임베딩 예시](EMBEDDING_EXAMPLE.md) - 실제 데이터 흐름 예시

---

## 🎯 비슷한 상품 추천 API

특정 상품과 유사한 상품을 벡터 유사도 기반으로 추천하는 기능입니다.

### GET /api/v1/recommendations/similar/{productId}

상품 상세 페이지 등에서 사용할 수 있는 유사 상품 추천 API입니다.

#### 요청 파라미터
- `productId`: 기준 상품 ID (UUID, 필수)
- `topK`: 추천할 상품 개수 (기본값: 3)

#### 응답 형식
```json
{
  "status": 200,
  "data": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440002",
      "productName": "청송사과 프리미엄 2kg",
      "productCategoryName": "과일",
      "price": 28000
    },
    {
      "productId": "550e8400-e29b-41d4-a716-446655440003",
      "productName": "부사 사과 1kg",
      "productCategoryName": "과일",
      "price": 12000
    }
  ],
  "message": null
}
```

#### 응답 필드 설명
- `data`는 유사 상품 목록(배열)입니다. (기본값: 3개)
  - `productId`: 상품 ID (UUID)
  - `productName`: 상품명
  - `productCategoryName`: 상품 카테고리 이름
  - `price`: 가격
- 추천 순서는 벡터 유사도 점수 기반 내림차순
- 기준 상품 자신은 제외됨
- 판매 중인 상품만 추천됨 (ON_SALE, DISCOUNTED 상태)

#### 동작 원리
1. **기준 상품 조회**: Elasticsearch에서 기준 상품의 임베딩 벡터를 조회합니다.
2. **벡터 유사도 검색**: `VectorProductSearchService`를 사용하여 코사인 유사도 기반으로 유사한 상품을 검색합니다.
3. **필터링**: 
   - 자기 자신 제외
   - 판매 중인 상품만 필터링 (ON_SALE, DISCOUNTED)
4. **결과 반환**: 유사도가 높은 순서대로 Top-K 개의 상품을 반환합니다.

#### 에러 응답
- 현재 구현에서는 기준 상품을 찾지 못하거나(상품/벡터 없음) 해당 조건을 만족하지 않으면 빈 리스트를 반환합니다.

#### 벡터가 없는 경우
- 기준 상품의 임베딩 벡터가 없는 경우 빈 리스트를 반환합니다.
- 이 경우 상품 인덱싱 시 임베딩 생성이 필요합니다.

---

## 👩‍🍳 레시피 추천 API

장바구니 상품을 기반으로 레시피를 추천하고 부족 재료를 제안하는 기능입니다.

### GET /api/v1/recommendations/recipes/{userId}

사용자의 실제 장바구니를 `baro-shopping`에서 Feign으로 가져와 레시피를 추천합니다.

#### 응답 형식
```json
{
  "status": 200,
  "data": {
    "recipeName": "계란찜",
    "ownedIngredients": ["계란"],
    "missingCoreIngredients": ["대파", "소금"],
    "missingRecommendations": [
      {
        "ingredientName": "대파",
        "products": [
          {
            "productId": "550e8400-e29b-41d4-a716-446655440001",
            "productName": "대파 1단",
            "productCategoryName": "채소",
            "price": 2500
          }
        ]
      }
    ],
    "instructions": "1. 계란을 풀어 소금을 넣고 섞는다...\n2. 대파를 썰어 넣는다..."
  },
  "message": null
}
```

#### 응답 필드 설명
- `recipeName`: 추천된 레시피 이름
- `ownedIngredients`: 사용자가 보유한 재료 목록
- `missingCoreIngredients`: 부족한 핵심 재료 목록
- `missingRecommendations`: 부족한 재료별 추천 상품 목록(각 항목에 `products` 포함)
- `instructions`: 조리법 (줄바꿈으로 구분)

---

## 🔍 검색 API

상품 검색 및 자동완성을 제공합니다. 검색어(q)만 필수이고, 카테고리/가격 필터·페이지는 선택입니다.

### GET /api/v1/search

상품을 키워드로 검색합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search?q=사과&categories=AGRI_FRUIT_SEASONAL&priceMin=1000&priceMax=50000&page=0&size=10
```

#### 요청 파라미터
- `q`: 검색 키워드 (선택; 값이 비어있으면 키워드 검색 조건 없이 상태 필터만 적용)
- `categories`: 카테고리 코드(복수 가능, 선택)
- `priceMin`: 최소 가격 (선택)
- `priceMax`: 최대 가격 (선택)
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지 크기 (기본값: 10)

#### 요청 헤더
- `X-User-Id`: 검색 API는 2026-09-07부터 이 헤더를 읽지 않습니다 — 검색이 취향 신호에서 빠지며 로그 적재 경로가 사라졌습니다.

#### 응답 형식
```json
{
  "status": 200,
  "data": {
    "content": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440001",
        "productName": "GAP 인증 사과 1kg",
        "productCategoryName": "과일",
        "price": 12000
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 45,
    "totalPages": 5,
    "first": true,
    "last": false,
    "hasNext": true,
    "hasPrevious": false
  },
  "message": null
}
```

### GET /api/v1/search/autocomplete

상품명 자동완성 제안 목록을 조회합니다.

#### 쿼리 파라미터
```text
GET /api/v1/search/autocomplete?q=사과&size=5
```

#### 요청 파라미터
- `q`: 검색 키워드 (필수)
- `size`: 자동완성 개수 (기본값: 5)

#### 응답 형식
```json
{
  "status": 200,
  "data": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "productName": "사과"
    },
    {
      "productId": "550e8400-e29b-41d4-a716-446655440002",
      "productName": "사과즙"
    }
  ],
  "message": null
}
```

---

## 💬 챗봇 API

> ⚠️ 현재 상태: 코드가 없습니다. 엔드포인트가 하나도 없던 빈 클래스 4개를 2026-09-05에 삭제했고, `/api/v1/chatbot/*`로 호출 가능한 API도 없습니다.

---

## 🔄 공통 응답 형식

검색·자동완성·추천·레시피 **모든 엔드포인트**가 성공 응답을 `ResponseDto.ok(...)`로 감싸 반환합니다(2026-09-05 통일).
전역 예외 처리(`BaseExceptionHandler`)도 같은 `ResponseDto` 형태로 에러 응답을 반환하므로, 호출하는 쪽은 성공과 실패에서 같은 봉투를 받습니다.
`review` 도메인의 배치 API는 팀원 소유라 이 통일 대상에서 빠져 있습니다.

### 성공 응답
```json
{
  "status": 200,
  "data": { ... },
  "message": null
}
```

### 에러 응답
```json
{
  "status": 400,
  "data": null,
  "message": "입력 값이 올바르지 않습니다"
}
```

---

## 📊 API 성능 메트릭
현재 문서에는 기능별 SLA/Rate Limit 수치가 고정되어 있지 않습니다.

### 캐시 전략
- `autocomplete`: Caffeine 로컬 캐시 적용(최대 1,000개·TTL 5분) — `CacheConfig.java`
- 개인화 추천·유사 상품 추천·레시피 추천: 캐싱하지 않음(의도적 결정)
- 근거·트레이드오프·기각한 대안: [`decisions/001-autocomplete-caching.md`](decisions/001-autocomplete-caching.md)

### Rate Limiting
- 현재 문서 기준으로 rate limiting 구현/정책을 특정하지 않았습니다.

---

*이 문서는 API 인터페이스 명세를 중심으로 작성되었습니다. 구현 세부사항은 [구현 가이드](IMPLEMENTATION.md)를 참고해주세요.*

### 기술 구현 (개인화 추천)
- **벡터 생성**: 사용자 행동 패턴 → 임베딩 모델 → 취향 벡터
- **유사도 계산**: 코사인 유사도로 상품 벡터 매칭
- **랭킹**: 유사도 점수 기반 Top-K 추출
- **캐싱**: 적용하지 않음 — 사용자별로 결과가 갈려 캐시 효율이 낮고 최신성이 더 중요하다고 판단(근거: [`decisions/001-autocomplete-caching.md`](decisions/001-autocomplete-caching.md))

### 기술 구현 (비슷한 상품 추천)
- **벡터 조회**: 기준 상품의 임베딩 벡터 조회
- **유사도 계산**: Elasticsearch script_score를 사용한 코사인 유사도 계산
- **필터링**: 자기 자신 제외 및 판매 중인 상품만 필터링
- **랭킹**: 유사도 점수 기반 Top-K 추출

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
    private ChatModel chatModel; // Gemini with RAG

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


