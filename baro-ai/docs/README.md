# 바로팜 AI 모듈 (baro-ai)

> **로그 기반 개인화 추천 엔진** - 사용자의 행동 패턴을 학습하여 맞춤 상품과 레시피를 추천하는 AI 서비스

## 📋 목차

- [시스템 개요](#-시스템-개요)
- [핵심 기능](#-핵심-기능)
- [아키텍처 설계](#-아키텍처-설계)
- [폴더 구조 가이드라인](#-폴더-구조-가이드라인)
- [기술 스택](#-기술-스택)
- [개발 시작하기](#-개발-시작하기)
- [문서 맵](#-문서-맵)

---

## 🎯 시스템 개요

`baro-ai` 모듈은 **사용자 행동 기반 개인화 추천 시스템**으로, 다음과 같은 특징을 가집니다:

### 📈 핵심 가치

- **데이터 중심**: 실시간으로 수집되는 사용자 행동 로그를 기반으로 한 추천
- **벡터 기반**: Elasticsearch를 활용한 벡터 유사도 계산 (개인화 추천 등)
- **이벤트 드리븐**: Kafka를 통한 실시간 데이터 처리
- **확장성**: 헥사고날 아키텍처 기반의 모듈화된 설계

### 🎪 작업 단위 (2026-09-04부터 이 4개가 감사·문서·설명의 기본 단위 — 매핑 SSOT: [`specs/README.md`](specs/README.md))

1. **취향 벡터** — 검색/장바구니/주문 로그 → 프로필 벡터 → 개인화 추천 ([스펙](specs/taste-vector/taste-vector-pipeline.md))
2. **장바구니 레시피** — 장바구니 → 보유 재료 → LLM 레시피 후보 → 서버 검증 → 부족 재료 상품 ([스펙](specs/cart-recipe/recipe-recommendation.md))
3. **임베딩 유사도 검색** — 상품 임베딩 → 벡터 검색 → 다양화 → 유사 상품 추천 ([스펙](specs/vector-similarity/vector-similarity-search.md))
4. **키워드 검색** — 상품 이벤트 색인 → 키워드 검색 → 자동완성 ([스펙](specs/keyword-search/product-search-and-autocomplete.md))
5. 서비스 챗봇 — 미구현 스텁. 리뷰 감정분석·요약은 팀원 소유 도메인

### 🔄 데이터 플로우

```mermaid
graph LR
    A[사용자 행동] --> B[Kafka 이벤트]
    B --> C[baro-ai 수신]
    C --> D[로그 저장 + 벡터화]
    D --> E[추천 알고리즘]
    E --> F[개인화 결과]
    F --> G[추천 응답]
```

---

## 🚀 핵심 기능

### 1. **로그 기반 개인화 추천** ⭐⭐⭐

**사용자 행동 로그 → 행동 비중 → 상품 벡터 가중 평균 → 상품 매칭** 방식의 추천 엔진

- **입력**: 검색/장바구니/주문 로그 (각 타입별 최대 5개씩, 총 최대 15개)
- **처리**:
  - 로그마다 이벤트 종류 × 수량 가중치 계산 (삭제·취소는 0이라 제외. 시간 감쇠 축은 2026-09-06에 삭제 — 효과가 없었다)
  - 로그에 등장한 상품이 이미 가진 벡터를 비중대로 가중 평균한 뒤 L2 정규화
  - 1536차원 벡터로 사용자 취향 표현 (외부 임베딩 API 호출 없음)
- **출력**: Top-K 개인화 상품 리스트 (상품 ID, 이름, 카테고리, 가격 포함)
- **품질 보장**: 최소 3개 이상 로그 필요
- **API**: 
  - `GET /api/v1/recommendations/personalized/{userId}` - 추천 상품 목록 (`topK` 쿼리 파라미터, 기본값 5)

### 2. **레시피 추천** ✅ 구현 완료

**장바구니 상품 → 보유 재료 확인 → 레시피 + 부족 재료 추천**

- **입력**: 장바구니 상품 목록 (`productId` 기준)
- **처리**:
  - `RecipeRecommendService`: 장바구니 상품의 `ProductDocument.ingredientName`을 ES에서 한 번에 조회 (LLM 아님)
  - `RecipeCandidateGenerator`: 보유 재료로 레시피 생성 (LLM — 요청 경로의 유일한 호출)
  - `IngredientProcessingUtil`: 재료 정규화 및 비교
- **재료명 계산 시점**: 상품 색인 시점 1회 (`ProductIngredientNormalizer`) — 근거: [`decisions/002-ingredient-name-at-index-time.md`](decisions/002-ingredient-name-at-index-time.md)
- **출력**: 레시피 정보 + 부족 재료 + 추천 상품
- **API**: `GET /api/v1/recommendations/recipes/{userId}`

### 3. **비슷한 상품 추천** ✅ 구현 완료

**상품 상세 페이지에서 유사한 상품 추천 (벡터 유사도 기반)**

- **입력**: 기준 상품 ID
- **처리**:
  - 기준 상품의 임베딩 벡터 조회
  - `VectorProductSearchService`: 코사인 유사도를 이용한 벡터 검색
  - 자기 자신 제외 및 판매 중인 상품만 필터링
- **출력**: 유사도가 높은 상품 목록 (Top-K)
- **API**: `GET /api/v1/recommendations/similar/{productId}?topK=3`


### 4. **서비스 챗봇** ⚠️ 미구현

> 현재 미구현 상태입니다. 자리만 잡고 있던 빈 클래스 4개는 2026-09-05에 삭제했고, 목적이 정해지면 [`specs/chat/chatbot.md`](specs/chat/chatbot.md)를 근거로 새로 만듭니다.

**정책 문서 기반 RAG**로 정확한 답변 제공

- **RAG 구현**: 정책 문서 벡터화 + 유사도 검색
- **안전성**: 정책 외 질문 거부
- **실시간성**: 최신 정책 반영

### 5. **리뷰 감정 분석/요약** (이벤트 + 배치)

- Kafka: `review-events` 구독 → 별점/키워드 기반 감정 분류
- Elasticsearch: `review_event`에 리뷰/감정 저장

---

## 🏛️ 아키텍처 설계

### DDD 계층형 아키텍처 (DDD Layered Architecture)

`baro-ai` 모듈은 도메인 주도 설계(DDD)의 핵심 사상을 반영한 **계층형 아키텍처**를 지향합니다. 이 구조는 소프트웨어를 논리적인 세 개의 계층으로 분리하여 각 계층이 명확한 책임을 갖도록 합니다.

```text
┌─────────────────────────────────────────┐
│        Infrastructure Layer             │  layered-architecture
│ (Web, Persistence, Messaging, External APIs) │
└───────────────────┬─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│          Application Layer              │
│ (UseCases, DTOs, Transaction Control)   │
└───────────────────┬─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│             Domain Layer                  │
│   (Entities, Value Objects, Repositories)   │
└─────────────────────────────────────────┘
```

#### 핵심 계층과 원칙

1.  **Domain Layer (도메인 계층)**
    *   **역할**: 애플리케이션의 핵심 비즈니스 로직과 규칙을 포함합니다. `Entity`, `Value Object`, `Repository Interface` 등이 여기에 속합니다.
    *   **원칙**: 가장 안쪽에 위치하며, 다른 어떤 계층에도 의존하지 않는 순수한 영역입니다.

2.  **Application Layer (애플리케이션 계층)**
    *   **역할**: 도메인 계층의 객체들을 사용하여 실제 유스케이스(Use Case)를 구현합니다. 트랜잭션 처리, DTO 변환 등 응용 로직을 담당합니다.
    *   **원칙**: 도메인 계층에만 의존합니다. 인프라 계층의 구체적인 기술을 알지 못합니다.

3.  **Infrastructure Layer (인프라스트럭처 계층)**
    *   **역할**: 외부 세계와의 통신과 기술적인 부분을 처리합니다. REST 컨트롤러, 데이터베이스 연동(Repository 구현체), 메시징(Kafka), 외부 API 호출 등이 여기에 속합니다.
    *   **원칙**: 애플리케이션 계층을 호출하여 유스케이스를 실행하거나, 도메인 계층의 인터페이스를 구현합니다.

#### 의존성 규칙
오직 바깥쪽 계층에서 안쪽 계층으로만 의존성이 흐릅니다. (`Infrastructure` → `Application` → `Domain`) 이를 통해 비즈니스 로직(Domain)이 외부 기술 변화로부터 완벽하게 보호됩니다.

---

## 📁 폴더 구조 가이드라인

### DDD 계층형 아키텍처 기반 구조

`recommend`, `search` 등 혼재된 도메인 관심사를 명확히 분리하고, 계층별 역할을 명확히 하기 위해 다음과 같은 구조를 제안합니다.

> 아래 트리는 **아직 채택하지 않은 제안**이다. 현재 구조는 아래 "현재 구조 (As-Is)" 절이 SSOT다.
> 트리에 보이는 `chatbot`은 코드가 없다 — 빈 클래스 4개를 2026-09-05에 삭제했다([`specs/chat/chatbot.md`](specs/chat/chatbot.md)).

```text
src/main/java/com/barofarm/ai/
├── 📁 domain/               # 🏛️ 도메인 계층: 순수한 비즈니스 로직과 데이터
│   ├── model/              # 도메인 모델 (엔티티, VO)
│   │   ├── recommendation/
│   │   ├── search/
│   │   └── chatbot/
│   ├── repository/         # 리포지토리 인터페이스 (영속성 추상화)
│   │   ├── RecommendationRepository.java
│   │   └── SearchLogRepository.java
│   └── service/            # 도메인 서비스 (선택적)
│
├── 📁 application/          # 🚀 애플리케이션 계층: 유스케이스 구현
│   ├── recommendation/
│   │   ├── RecommendationService.java
│   │   └── dto/
│   ├── search/
│   │   ├── SearchService.java
│   │   └── dto/
│   └── chatbot/
│       ├── ChatbotService.java
│       └── dto/
│
├── 📁 infrastructure/       # 🔧 인프라스트럭처 계층: 외부 기술 연동
│   ├── web/                # REST 컨트롤러
│   │   ├── RecommendationController.java
│   │   └── SearchController.java
│   ├── persistence/        # 데이터 영속성 구현체
│   │   ├── jpa/
│   │   └── elasticsearch/
│   ├── messaging/          # 메시징 관련 (Kafka Consumers/Producers)
│   │   └── consumer/
│   └── config/             # Spring, Kafka 등 각종 설정
│
└── 📁 common/                # 🛠️ 공통 유틸리티 (계층에 속하지 않음)
    ├── exception/
    └── response/
```

### 구조 설계 원칙

1.  **계층 분리 원칙**: 각 클래스는 `domain`, `application`, `infrastructure` 중 하나의 계층에만 속해야 합니다.
2.  **명확한 책임**: `domain`은 비즈니스 규칙, `application`은 유스케이스 흐름, `infrastructure`는 기술 구현을 책임집니다.
3.  **단방향 의존성**: `infrastructure`는 `application`을, `application`은 `domain`을 의존할 수 있지만, 그 반대는 절대 불가합니다.

### 현재 구조 (As-Is)

현재 baro-ai 모듈은 다음과 같은 구조를 가지고 있습니다:

```text
src/main/java/com/barofarm/ai/
├── search/
│   ├── presentation/         # 검색 관련 컨트롤러
│   ├── application/          # 검색 서비스 로직
│   ├── domain/               # 검색 도메인 모델
│   ├── infrastructure/       # Elasticsearch 리포지토리·Kafka 컨슈머·LLM
│   └── exception/            # 검색 에러 코드 (SearchErrorCode)
├── recommend/
│   ├── application/           # 추천 서비스 (PersonalizedRecommendService, RecipeRecommendService)
│   │   ├── config/           # 추천 설정 (RecommendProperties)
│   │   └── dto/              # 추천 응답 DTO
│   ├── domain/               # 도메인 모델 (CandidateRecipePlan, IngredientProcessingUtil)
│   ├── infrastructure/       # 외부 연동
│   │   ├── client/           # Cart API 클라이언트
│   │   └── llm/              # LLM 서비스 (RecipeCandidateGenerator)
│   └── presentation/          # 추천 컨트롤러 (RecommendationController)
├── embedding/
│   ├── application/           # 임베딩 서비스
│   ├── domain/               # 임베딩 도메인 모델
│   ├── infrastructure/        # Elasticsearch 리포지토리
│   │   └── elasticsearch/
│   └── exception/            # 임베딩 에러 코드
├── log/
│   ├── domain/               # 로그 도메인 모델
│   ├── infrastructure/       # Elasticsearch 리포지토리
│   │   └── elasticsearch/
│   └── application/          # 로그 서비스
├── event/
│   ├── consumer/              # Kafka 이벤트 컨슈머
│   └── model/                # 이벤트 모델
├── config/                   # Spring 설정
└── common/                   # 공통 유틸리티
```

### 목표 구조 (To-Be)

향후 DDD 계층형 아키텍처로 리팩토링할 계획입니다:

| 현재 구조 (As-Is) | 목표 구조 (To-Be) | 설명 |
| :--- | :--- | :--- |
| `recommend/presentation` | `recommend/presentation` | REST 컨트롤러 (완료) |
| `recommend/application` | `recommend/application` | 서비스 로직 (완료) |
| `recommend/domain` | `recommend/domain` | 도메인 모델 (완료) |
| `recommend/infrastructure` | `recommend/infrastructure` | 외부 연동 (완료) |
| `recommend/application/dto` | `recommend/application/dto` | DTO (완료) |
| `search/presentation` | `search/presentation` | REST 컨트롤러 (유지) |
| `search/application` | `search/application` | 유스케이스 서비스 (유지) |
| `search/domain` | `search/domain` | 도메인 모델 (유지) |
| `search/infrastructure` | `search/infrastructure` | 리포지토리 구현체 (유지) |
| `search/exception` | `search/exception` | 에러 코드 — `SearchErrorCode` (2026-09-05 신설) |
| `log/domain` | `log/domain` | 도메인 모델 (유지) |
| `log/infrastructure/elasticsearch` | `log/infrastructure/elasticsearch` | 리포지토리 구현체 (완료) |
| `log/application` | `log/application` | 로그 서비스 (유지) |
| `event` | `event` | Kafka 관련 로직 (공통 유지) |
| `embedding/application` | `embedding/application` | 임베딩 서비스 (완료) |
| `embedding/domain` | `embedding/domain` | 도메인 모델 (유지) |
| `embedding/infrastructure/elasticsearch` | `embedding/infrastructure/elasticsearch` | 리포지토리 구현체 (완료) |
| `embedding/exception` | `embedding/exception` | 에러 코드 (완료) |
| `config` | `config` | Spring, 모듈 설정 (유지) |
| `common` | `common` | 공통 유틸리티 (유지) |

> **참고**: DDD 계층형 아키텍처 기반 리팩토링이 완료되었습니다. 각 도메인은 `application`, `domain`, `infrastructure`, `presentation`, `exception` 계층으로 구성되어 있습니다.



---

## 🛠️ 기술 스택

### AI/ML
- **Google Gemini (chat model)**: 텍스트 생성 (레시피/요약 등). 기본값은 `gemini-2.5-flash-lite` (`SPRING_AI_GEMINI_MODEL`)
- **Google Gemini Embedding**: 벡터 생성 (`gemini-embedding-001`, 출력 차원을 1536으로 고정)
- **Spring AI**: AI 통합 프레임워크
- **LangChain**: RAG 구현 (향후)

### 데이터/검색
- **Elasticsearch**: 벡터 검색 및 로그 저장
- **Cache**: 자동완성만 Caffeine 로컬 캐시 적용(최대 1,000개·TTL 5분). 개인화·추천 결과는 캐싱하지 않음 — 근거: [`decisions/001-autocomplete-caching.md`](decisions/001-autocomplete-caching.md)
- **Spring Data Elasticsearch**: 문서 저장·검색 (baro-ai에는 RDB·JPA 엔티티가 없다)

### 메시징/이벤트
- **Apache Kafka**: 이벤트 스트림 처리
- **Spring Kafka**: 메시징 통합

### 인프라
- **Spring Boot 3**: 마이크로서비스 프레임워크
- **Spring Cloud**: 클라우드 네이티브 기능
- **Docker/K8s**: 컨테이너화 및 오케스트레이션

### 모니터링
- **Spring Actuator**: 애플리케이션 메트릭
- **Micrometer**: 메트릭 수집
- **ELK Stack**: 로그 수집 및 분석

---

## 🚀 개발 시작하기

### 환경 요구사항

```text
- Java 21+
- Gradle 7.6+
- Elasticsearch 8.x
- Kafka 3.x
- Docker & Docker Compose
```

### 로컬 개발 환경 설정

```bash
# 1. 의존성 설치
./gradlew build

# 2. 인프라 서비스 실행
docker-compose -f docker-compose.data.yml up -d

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. API 테스트
curl "http://localhost:8092/api/v1/recommendations/personalized/550e8400-e29b-41d4-a716-446655440000?topK=5"
```

### 개발 워크플로우

1. **도메인 모델링**: 도메인 계층부터 시작
2. **유스케이스 정의**: 애플리케이션 서비스 작성
3. **포트 설계**: 인터페이스 기반 설계
4. **어댑터 구현**: 외부 연동 구현
5. **테스트 작성**: TDD 방식 권장

### 코드 품질

- **테스트 커버리지**: 80% 이상 목표
- **코드 리뷰**: 모든 PR에 필수
- **아키텍처 준수**: 헥사고날 패턴 준수 검증
- **성능 모니터링**: 메트릭 기반 최적화

---

## 📚 문서 맵

`baro-ai/docs/`는 이 모듈의 **모든 마크다운 문서가 모이는 단일 저장소**입니다. 어떤 문서를 어디에 어떤 형식으로 쓰는지의 SSOT는 [**산출물 등록부**](templates/README.md)이고, 문장·구조 작성 기준은 [`.claude/rules/doc-writing.md`](../../.claude/rules/doc-writing.md)입니다. 새 문서를 만들기 전에 등록부에서 유형을 먼저 확인하세요.

### 폴더

| 경로 | 무엇이 있나 |
|------|-------------|
| [`features/`](features/README.md) | **기능·기술 문서** — 주제 4개(검색·임베딩 검색·장바구니 레시피·개인별 추천)마다 목표·흐름·설계 결정·실측·한계를 한 편에. 처음 보는 사람이 스펙 전에 읽는 문서 |
| [`portfolio/`](portfolio/README.md) | **포트폴리오 케이스 스터디** — 같은 4개 주제를 문제·분석·대안·해결·자평·한계·회고로 |
| [`specs/`](specs/README.md) | 기능 스펙 — **작업 단위 4개**(취향 벡터·장바구니 레시피·임베딩 유사도 검색·키워드 검색) 폴더, 각 스펙에 발행 모듈 규칙·리스크 포함. `Draft → Approved → Implemented` |
| [`decisions/`](decisions/README.md) | 아키텍처 결정 기록(ADR) — 되돌리기 비싼 선택의 근거와 트레이드오프 |
| [`audits/`](audits/README.md) | 아키텍처 감사·리팩터 반영 이력·하네스 회고 백로그 |
| [`product/`](product/README.md) | MVP 범위·용어 사전·클라이언트 전제 |
| [`harness/`](harness/README.md) | AI 에이전트 하네스의 레이어별 설계 배경 |
| [`templates/`](templates/README.md) | 산출물 등록부 + 유형별 문서 템플릿 4종 |

### 단일 문서

- [**기능 명세**](FEATURES.md) — 각 기능의 상세 설명(작업 단위 재편 이전 문서 — 최신 설명은 [`features/`](features/README.md))
- [**구현 가이드**](IMPLEMENTATION.md) — 코드 예시 및 설정
- [**검색·임베딩 아키텍처**](ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md) — Elasticsearch 인덱스 매핑 SSOT
- [**임베딩 예시**](EMBEDDING_EXAMPLE.md) — 사용자 프로필 임베딩 생성 예시
- [**검토 분석 확장**](REVIEW_ANALYSIS_PHASES.md) — 리뷰 분석·요약 고도화 로드맵
- [**하네스 설계 배경**](harness-engineering.md) — AI를 어떻게 활용했는지에 대한 총정리

---

## 🤝 기여하기

1. **이슈 생성**: 개선사항이나 버그 리포트
2. **브랜치 생성**: `feature/` 또는 `fix/` 접두사 사용
3. **PR 제출**: 코드 리뷰 요청
4. **테스트 확인**: CI/CD 파이프라인 통과 확인

### 코드 리뷰 체크리스트

- [ ] 헥사고날 아키텍처 패턴 준수
- [ ] 도메인 로직이 domain 패키지에 위치
- [ ] 인터페이스(포트)를 통한 의존성 역전
- [ ] 단위 테스트 및 통합 테스트 작성
- [ ] 코드 커버리지 80% 이상 유지

---

*이 문서는 지속적으로 업데이트됩니다. 최신 정보는 팀 문서를 참고해주세요.*
