baro-ai 모듈 패키지 구조 제안

> 전제:  
> - 이 모듈은 **“로그 기반 개인화 추천 + 레시피 추천 + (보조) 검색/챗봇”**이 핵심 역할  
> - 추천 대상은 **오직 로그인 사용자** (userId 중심)  
> - Kafka 이벤트/ES/LLM을 활용해 **읽기 전용 뷰와 추천 결과**를 만들어낸다.

---

## 1. 최종 패키지 구조 (제안)
```
text
com.barofarm.ai
├── common/
│   ├── config/                 # Swagger, 공통 설정
│   ├── entity/                 # BaseEntity 등 공통 JPA 베이스
│   ├── exception/              # 공통 예외
│   └── response/               # 공통 Response/CustomPage
│
├── config/                     # 인프라/외부 시스템 설정
│   ├── KafkaConsumerConfig.java
│   ├── ElasticsearchConfig.java
│   ├── OpenAiConfig.java
│   └── JpaConfig.java
│
├── event/                      # Kafka 이벤트 수신 계층 (입력 포트)
│   ├── model/                  # Product/Cart/Order/Search/Experience 이벤트 payload
│   └── consumer/               # @KafkaListener들 (log/embedding 레이어로 전달)
│
├── log/                        # 사용자 행동 로그 (RDB, 개인화의 raw 데이터)
│   ├── domain/                 # SearchLog, CartEventLog, OrderEventLog, UserEventLog
│   ├── repository/             # 각 로그용 JPA Repository
│   ├── application/            # LogWriteService, LogReadService
│   └── dto/                    # 필요 시 외부 노출용 DTO
│
├── embedding/                  # 벡터/임베딩 관리 (User Profile, Product, Experience, Policy)
│   ├── model/                  # UserProfileEmbeddingDocument, ProductEmbeddingDocument 등 (ES 문서)
│   ├── service/
│   │   ├── UserProfileEmbeddingService.java   # userId 기준 취향 벡터 생성
│   │   ├── ProductEmbeddingService.java       # 상품 임베딩
│   │   ├── ExperienceEmbeddingService.java    # 체험 임베딩
│   │   └── PolicyEmbeddingInitService.java    # RAG용 정책 임베딩
│   └── scheduler/
│       └── EmbeddingBatchJob.java             # 배치/스케줄 (필요 시)
│
├── search/                     # (AI 관점의) 검색 뷰 - 기존 ES 검색 + 자동완성
│   ├── domain/                 # ProductDocument, ExperienceDocument 등 ES 문서
│   ├── infrastructure/
│   │   ├── elasticsearch/      # ES Repository (상품/체험 검색/자동완성)
│   │   └── event/              # ProductEvent, ExperienceEvent Consumer (ES 인덱싱용)
│   ├── application/            # ProductSearchService, ExperienceSearchService, UnifiedSearchService
│   ├── presentation/           # 검색/자동완성 API (상품/체험/통합)
│   └── util/                   # KoreanChosungUtil 등 검색 유틸
│
├── recommend/                  # 추천 도메인 (개인화/레시피/유사 상품)
│   ├── model/
│   │   ├── PersonalizedRecommendation.java   # 개인화 추천 결과 객체
│   │   ├── RecipeRecommendation.java         # 레시피 추천 결과
│   │   └── SimilarProductResult.java         # 유사 상품 추천 결과
│   ├── service/
│   │   ├── PersonalizedRecommendService.java # userId 기반 개인화 추천
│   │   ├── RecipeRecommendService.java       # 장바구니 & 입력 재료 기반 레시피 추천
│   │   └── SimilarProductService.java        # 특정 상품 기준 유사 상품 추천
│   └── facade/
│       └── RecommendationFacade.java         # Controller에서 호출하는 진입점
│
├── presentation/               # 외부로 노출되는 API (REST)
│   ├── RecommendationController.java  # /api/v1/recommendations/**
│   └── ChatbotController.java         # /api/v1/chatbot/**
│
└── AiApplication.java
```
---

## 2. 각 폴더/레이어 역할 & 상호작용 흐름

### 2.1 `common/` – 공통 기술 유틸 계층

**역할**

- Swagger 설정, 공통 Exception/Response, BaseEntity 등 **기술 공유 레이어**.
- 비즈니스와 무관한, 모든 기능이 같이 쓰는 것들.

**상호작용**

- `presentation`, `search`, `recommend`, `log` 등 **모든 패키지에서 import**.
- 도메인 흐름을 주도하지 않고, **단지 편의/표준 포맷 제공**.

---

### 2.2 `config/` – 인프라 연결 계층

**역할**

- Kafka, Elasticsearch, JPA, OpenAI/Spring AI 설정.
- “외부 세계와의 연결선”을 정의.

**파일 역할 예**

- `KafkaConsumerConfig`
  - `@KafkaListener`용 `ConcurrentKafkaListenerContainerFactory` 제공.
  - `event.consumer.*` 에서 사용하는 ConsumerFactory 바인딩.
- `ElasticsearchConfig`
  - ES 클라이언트, VectorStore 설정 (product/experience/user/profile 인덱스 등).
- `OpenAiConfig`
  - Spring AI의 `ChatModel`, `EmbeddingModel` Bean 정의.
- `JpaConfig`
  - JPA/Auditing 활성화 (Log 엔티티용 등).

**흐름**

- 애플리케이션 부팅 시 config가 먼저 초기화 →  
  이후 `event.consumer`, `embedding.service`, `search.application`, `recommend.service` 에서 설정된 Bean들을 사용.

---

### 2.3 `event/` – Kafka 이벤트 입구

#### 2.3.1 `event.model/`

**역할**

- `ProductEvent`, `CartEvent`, `OrderEvent`, `SearchEvent`, `ExperienceEvent` 등  
  **Kafka로부터 받는 메시지의 스키마** 정의.
- 각각의 필드는 이미 “개인화 추천에서 어떻게 활용되는지” 주석으로 설명된 상태.

**예시 활용**

- `CartEventData.userId / productId / quantity`  
  → 유저별 상품 선호도 벡터를 만들 때 사용.
- `SearchEventData.searchQuery`  
  → 검색어 임베딩으로 user-profile 벡터 생성.
- `OrderEventData.orderItems`  
  → “실제 구매” 기반 강한 시그널.

#### 2.3.2 `event.consumer/`

**역할**

- Kafka로부터 이벤트를 읽어 **내부 도메인 계층으로 전달하는 어댑터**.
- 비즈니스 로직은 가지지 않고, 거의 항상 **서비스 호출만** 수행.

**상호작용 흐름**

1. Kafka에서 메시지 수신  
   → `CartEventConsumer.onMessage(CartEvent e)`
2. `event.consumer` 내부에서:
   - `LogWriteService` 호출 → DB 로그 적재
   - (필요 시) `UserProfileEmbeddingService` 나 `ProductEmbeddingService`에 비동기 트리거

> 결과적으로 `event` 패키지는 **“외부 이벤트 스트림 → 내부 도메인으로 변환”** 역할만 담당.

---

### 2.4 `log/` – 행동 로그 저장소 (RDB, 개인화의 원자료)

#### 2.4.1 `log.domain/` & `log.repository/`

**역할**

- `CartEventLog`, `OrderEventLog`, `SearchLog`, `UserEventLog` 등  
  **유저 행동을 시간 순으로 저장하는 엔티티**.
- 각 로그는 userId를 기준으로 조회 가능하게 설계.
- JPA Repository를 통해 **쿼리/집계의 기반** 제공.

#### 2.4.2 `log.application/`

- `LogWriteService`
  - `event.consumer.*` 에서 호출.
  - Kafka 이벤트 → 로그 엔티티로 매핑 → JPA `save()`.
- `LogReadService`
  - `recommend.service.*` / `embedding.service.UserProfileEmbeddingService` 에서 사용.
  - *“최근 N일간 userId의 검색/장바구니/주문 로그를 가져온다”* 같은 조회 메서드 제공.

**흐름 예 (개인화 추천용)**

1. `CartEventConsumer` 가 `CartEvent` 수신  
2. `LogWriteService.saveCartEvent(e)` 호출  
3. `CartEventLog` 테이블에 한 줄 insert  
4. 나중에 `PersonalizedRecommendService` 가  
   `LogReadService.getRecentLogs(userId)`로 유저의 행동 히스토리 조회

> `log` 패키지는 **“사용자별 행동 타임라인”**을 RDB로 유지하는 역할을 하며,  
> 추천/임베딩이 이 데이터를 기반으로 동작.

---

### 2.5 `embedding/` – 벡터/임베딩 계층

#### 2.5.1 `embedding.model/`

**역할**

- ES에 저장할 벡터 문서 정의:
  - `UserProfileEmbeddingDocument` : userId → 취향 벡터
  - `ProductEmbeddingDocument` : productId → 내용/메타 기반 상품 벡터
  - `ExperienceEmbeddingDocument` : experienceId → 체험 벡터
  - `PolicyEmbeddingDocument` : 정책 문서 벡터 (챗봇 RAG용)

#### 2.5.2 `embedding.service/`

- `UserProfileEmbeddingService`
  - `LogReadService` 로부터 userId의 검색/장바구니/주문 로그를 받아,
  - 텍스트(검색어, 상품명 등)를 한 덩어리로 만든 뒤 EmbeddingModel 호출 →  
    ES `user-profile` 인덱스에 저장.
- `ProductEmbeddingService`, `ExperienceEmbeddingService`
  - `ProductEvent`, `ExperienceEvent` 기반으로 임베딩 생성.
  - 상품/체험 벡터 인덱스에 upsert.
- `PolicyEmbeddingInitService`
  - 서비스 정책 문서들을 초기 로딩 → 벡터화 → ES 인덱스 저장 (챗봇 RAG).

#### 2.5.3 `embedding.scheduler/`

- `EmbeddingBatchJob`
  - 일정 주기(배치)로 신규/변경 데이터에 대한 임베딩 생성/재생성.
  - 예: 매일 새벽 “최근 updatedAt 이후 변경된 상품/체험/유저 프로필만 배치 업데이트”.

**상호작용 흐름**

- **user-profile 벡터 생성**
  1. 여러 이벤트(log)가 쌓임.
  2. 배치 or 실시간 트리거에서 `UserProfileEmbeddingService.updateProfile(userId)` 호출.
  3. 로그 텍스트 → Embedding → ES `user-profile-embeddings` 인덱스 저장.
  4. 이후 추천 로직에서 userId를 벡터로 바꿔 쓸 수 있게 됨.

> `embedding` 은 “로그/도메인 이벤트 → 벡터 표현”으로 변환하는 계층.

---

### 2.6 `search/` – 검색 뷰 & AI가 쓰는 카탈로그 뷰

> 주의: 이 search 패키지는 **현재 baro-ai 모듈 안에 있는 ES 기반 상품/체험 검색** 구현입니다.  
> (기존 baro-support의 검색과 역할이 어느 정도 중복되지만,  
> 현재 구조 기준으로 설명합니다.)

#### 2.6.1 `search.domain/` & `search.infrastructure.elasticsearch/`

- ES 인덱스와 매핑되는 도메인:
  - `ProductDocument`, `ExperienceDocument`, `*AutocompleteDocument`
- `ProductSearchRepository`, `ExperienceSearchRepository`, `*AutocompleteRepository`
  - Spring Data Elasticsearch를 이용한 CRUD/쿼리 레이어.

#### 2.6.2 `search.infrastructure.event/`

- `ProductEvent` / `ExperienceEvent` (search용) + Consumer:
  - `ProductEventConsumer`, `ExperienceEventConsumer`
  - `product-events`, `experience-events` 토픽 소비
  - 인덱싱을 위해 `search.application.*` 서비스 호출.

#### 2.6.3 `search.application/`

- `ProductSearchService`, `ExperienceSearchService`
  - 키워드 검색, 필터링, 자동완성, 초성 검색, 오탈자 허용 등 구현.
- `UnifiedSearchService`
  - 상품 + 체험을 동시에 검색/자동완성하는 통합 뷰 제공.

#### 2.6.4 `search.presentation/`

- `ProductSearchController`, `ExperienceSearchController`, `UnifiedSearchController`
  - 외부에 `/api/v1/search/**` 엔드포인트 제공.
  - Buyer/Frontend/Gateway가 직접 호출.

**흐름 (상품 검색 예)**

1. buyer 서비스에서 상품 생성/수정 → `product-events` 발행.
2. `search.infrastructure.event.ProductEventConsumer` 수신 →  
   `ProductSearchService.indexProduct()` 호출 → ES `product` 인덱스에 저장.
3. 프론트에서 `/api/v1/search/product` 호출 →  
   `ProductSearchController` → `ProductSearchService.searchOnlyProducts()` → ES 조회 결과 반환.
4. 추천/레시피 쪽에서 **부족 재료 매핑**이나 **상품 후보 조회** 시  
   이 `search.application` 을 내부 클라이언트로 사용할 수 있음.

---

### 2.7 `recommend/` – 추천 도메인 (핵심)

#### 2.7.1 `recommend.model/`

- `PersonalizedRecommendation`
  - 개인화 상품 추천 결과: 추천 상품 ID 리스트 + 부가 메타(설명 등).
- `RecipeRecommendation`
  - 레시피 이름/재료/설명 + 부족 재료에 대한 상품 추천 정보 등.
- `SimilarProductResult`
  - 특정 상품 기준 유사 상품 Top-N 결과.

#### 2.7.2 `recommend.service/`

- `PersonalizedRecommendService`
  - **개인화 추천의 핵심 로직**.
  - `LogReadService` + `UserProfileEmbeddingService` + `ProductEmbedding`/`ProductSearchService` 등을 사용해:
    - userId의 취향 벡터/행동 로그 기반 상품 랭킹 생성.
- `RecipeRecommendService`
  - 장바구니 로그에서 재료 목록 추출 → LLM 호출 → 레시피 + 부족 재료 도출 →  
    부족 재료에 매핑되는 상품을 `search.application.ProductSearchService` 로 검색.
  - 또는 챗봇에서 입력한 재료 텍스트를 그대로 프롬프트로 사용.
- `SimilarProductService`
  - 상품 상세 페이지에서 “함께 보면 좋은 상품” 추천.
  - `ProductEmbeddingService` 또는 ES에서 상품 벡터/검색 결과를 활용.

#### 2.7.3 `recommend.facade/`

- `RecommendationFacade`
  - `RecommendationController`에서 호출하는 **단일 진입점**.
  - 내부적으로:
    - 개인화 추천 / 유사 상품 / 레시피 추천을 적절히 라우팅/조합.
  - Controller는 Facade만 알면 되도록 의존성 단순화.

**개인화 추천 흐름 예**

1. 클라이언트 → `/api/v1/recommendations/personalized?userId=...` 호출  
   (`RecommendationController`)
2. `RecommendationFacade.recommendPersonalized(userId)` 호출
3. Facade 내부에서:
   - `PersonalizedRecommendService.recommendForUser(userId)` 호출
   - 이 서비스는:
     - `LogReadService` 로 최근 검색/장바구니/주문 로그 조회
     - 필요 시 `UserProfileEmbeddingService` 호출하여 user 벡터 로딩/계산
     - `ProductEmbedding` or `search.application.ProductSearchService` 로 후보 상품 가져오기
     - 사용자 취향 + 상품 메타 조합으로 점수 계산 & Top-K 선택
4. 결과를 `PersonalizedRecommendation` 으로 포장하여 반환.

**레시피 추천 흐름 예 (장바구니 기반)**

1. 클라이언트 → `/api/v1/recommendations/recipes/from-cart` (userId 전달)
2. Controller → Facade → `RecipeRecommendService.recommendFromCart(userId)`
3. 내부 로직:
   - `LogReadService` or cart-service API 통해 userId의 장바구니 상품 목록 확보
   - 상품명 리스트로 LLM 프롬프트 구성 → OpenAI ChatModel 호출
   - LLM 응답에서 레시피/재료/부족 재료 파싱
   - 부족 재료에 대해 `ProductSearchService.searchProducts(keyword)` 로 상품 후보 조회
4. `RecipeRecommendation` 으로 결과 반환.

---

### 2.8 `presentation/` – 외부 진입점 (REST Controller)

**파일 역할**

- `RecommendationController`
  - `/api/v1/recommendations/**`
  - 개인화 추천, 레시피 추천, 유사 상품 추천 등 노출.
  - 비즈니스 로직은 모두 `RecommendationFacade` / `recommend.service.*` 에 위임.
- `ChatbotController`
  - `/api/v1/chatbot/**`
  - 사용자의 질문을 받아 챗봇/정책 RAG 서비스로 전달.
  - 실제 답변 생성은 별도의 챗봇 서비스(추후 구현)에서 담당.

**흐름**

- Controller → Facade/Service → (log/read, embedding, search, OpenAI, ES 등) → 결과 DTO → `ResponseDto` 로 래핑.

---

## 3. 전체 시나리오별 흐름 요약

### 3.1 “내 검색 & 장바구니 & 주문 기록 기반 개인화 추천”

1. **행동 발생**
   - buyer/support 모듈에서:
     - 상품 검색/체험 검색 → `SearchEvent` 발행
     - 장바구니 담기/수정/삭제 → `CartEvent` 발행
     - 주문 생성/완료 → `OrderEvent` 발행

2. **이벤트 수신 → 로그 적재**
   - `event.consumer.*` 가 Kafka에서 이벤트 수신
   - `LogWriteService` 호출 → `SearchLog`, `CartEventLog`, `OrderEventLog` insert

3. **(선택) 임베딩 업데이트**
   - 배치 or 실시간으로 `UserProfileEmbeddingService.updateProfile(userId)` 호출
   - 로그 텍스트 기반 userId 취향 벡터 생성 → ES `user-profile-embeddings` 인덱스에 저장

4. **추천 요청**
   - 클라이언트에서 `/api/v1/recommendations/personalized?userId=...` 호출

5. **추천 계산**
   - `RecommendationController` → `RecommendationFacade` → `PersonalizedRecommendService`
   - 이 서비스가:
     - 로그/임베딩/상품 메타/벡터를 이용해 점수 계산
     - Top-K 상품 리스트 선택

6. **결과 반환**
   - `PersonalizedRecommendation` → `ResponseDto` 로 래핑하여 반환.

---

### 3.2 “장바구니 & 냉장고 재료 기반 레시피 추천”

1. **장바구니 상태 → 재료 리스트 얻기**
   - 로그 or cart-service API 상관없이 `RecipeRecommendService`에서 재료 목록 확보.

2. **LLM 호출**
   - 재료 리스트 → LLM 프롬프트 → 레시피 후보 3~5개 생성.

3. **부족 재료 → 상품 추천**
   - LLM 응답에서 부족 재료 파싱
   - `search.application.ProductSearchService` 로 재료명 검색, 후보 상품 매핑.

4. **결과 반환**
   - 각 레시피에 “필요 재료/부족 재료/구매 추천 상품”을 묶어 `RecipeRecommendation` 으로 반환.

---

이 구조를 기준으로 `ARCHITECTURE.md` / `IMPLEMENTATION.md` 와도 자연스럽게 맞아떨어집니다.

- **이벤트/로그/임베딩/검색/추천/표현**이 층층이 분리되어 있어서,
- 각 단계에서 무슨 책임을 지는지 명확하고,
- 나중에 임베딩 모델 교체, 추천 전략 개선, 검색 인덱스 구조 변경 등을  
  해당 레이어 안에서 독립적으로 수정할 수 있습니다.
