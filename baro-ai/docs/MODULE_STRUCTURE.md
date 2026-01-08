# baro-ai 모듈 구조

## 전체 구조

```
baro-ai/
├── build.gradle
├── docs/
│   ├── AI_AWS_OPENAI.md
│   ├── AI_SUBJECT.md
│   ├── MCP_TOOL_GUIDE.md
│   └── MODULE_STRUCTURE.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/barofarm/ai/
    │   │       ├── AiApplication.java
    │   │       ├── config/                    # 설정 클래스
    │   │       ├── event/                     # Kafka 이벤트 수신/공유 모델
    │   │       ├── log/                       # 로그 도메인 (검색/장바구니/주문 행동 로그)
    │   │       ├── embedding/                 # 임베딩 생성 + 벡터 스토어 연동
    │   │       ├── recommend/                 # 추천 서비스 (상품, 개인화, 레시피 등)
    │   │       └── presentation/               # REST API
    │   └── resources/
    │       ├── application.yml
    │       └── application-local.yml
    └── test/
        ├── java/
        │   └── com/barofarm/ai/
        │       └── AiApplicationTest.java
        └── resources/
            └── application-test.yml
```

## 상세 패키지 구조

### 1. config (설정)

```
com.barofarm.ai.config
├── KafkaConsumerConfig.java      # 공용 Kafka Consumer 설정
├── KafkaProducerConfig.java      # (필요시) AI 쪽에서 이벤트 발행할 때
├── ElasticsearchConfig.java      # ES 클라이언트 / 벡터 스토어 설정
├── OpenAiConfig.java             # Spring AI(OpenAI) 설정
└── JpaConfig.java                # JPA / Transaction 설정
```

### 2. event (이벤트 모델 & Consumer)

Kafka에서 받아서 내부로 전달할 공용 DTO/핸들러들입니다.

```
com.barofarm.ai.event
├── model/
│   ├── ProductEventPayload.java      # product-events
│   ├── ExperienceEventPayload.java   # experience-events
│   ├── SearchEventPayload.java       # user-search-events
│   ├── CartEventPayload.java         # user-cart-events
│   └── OrderEventPayload.java        # order-events
└── consumer/
    ├── ProductEventConsumer.java
    ├── ExperienceEventConsumer.java
    ├── SearchEventConsumer.java
    ├── CartEventConsumer.java
    └── OrderEventConsumer.java
```

**역할:**
- **Product/Experience** 이벤트 → `embedding` / `recommend` 쪽으로 전달 (필요하면 `log`에도 일부 기록)
- **Search/Cart/Order** 이벤트 → 주로 `log` 도메인으로 전달해 로그 테이블에 적재

### 3. log (행동 로그 도메인)

검색/장바구니/주문 등 **사용자 행동 로그를 RDB에 저장**하는 영역입니다.

```
com.barofarm.ai.log
├── domain/
│   ├── UserEventLog.java              # 통합 이벤트 로그 엔티티(혹은 타입별 엔티티)
│   ├── SearchLog.java                 # (선택) 검색 전용 로그 엔티티
│   ├── CartEventLog.java              # (선택) 장바구니 전용
│   └── OrderEventLog.java             # (선택) 주문 전용
├── repository/
│   ├── UserEventLogRepository.java
│   ├── SearchLogRepository.java
│   ├── CartEventLogRepository.java
│   └── OrderEventLogRepository.java
├── application/
│   ├── LogWriteService.java           # Consumer에서 호출하는 로그 쓰기 서비스
│   └── LogReadService.java           # 추천/통계에서 로그 조회할 때 사용
└── dto/
    └── UserEventLogDto.java           # API/다른 계층에 노출할 DTO가 필요하면
```

**패턴:**
- Consumer → `LogWriteService` 호출 → JPA로 로그 테이블에 insert

### 4. embedding (임베딩 & 벡터 스토어)

상품/체험/사용자 프로필/정책 문서 등에 대한 임베딩 생성과 ES 벡터 인덱스 관리.

```
com.barofarm.ai.embedding
├── model/
│   ├── ProductEmbeddingDocument.java      # ES에 저장되는 상품 벡터 문서(메타 포함)
│   ├── ExperienceEmbeddingDocument.java   # 체험 벡터
│   ├── UserProfileEmbeddingDocument.java  # 사용자 취향 벡터
│   └── PolicyEmbeddingDocument.java       # 정책/RAG용
├── service/
│   ├── ProductEmbeddingService.java       # ProductEvent 기반 임베딩 생성/업데이트
│   ├── ExperienceEmbeddingService.java
│   ├── UserProfileEmbeddingService.java   # 검색 로그 집계 → 임베딩
│   └── PolicyEmbeddingInitService.java    # 정책 문서 초기 임베딩
└── scheduler/
    └── EmbeddingBatchJob.java             # 배치/스케줄 잡 (신규/변경 데이터 임베딩)
```

### 5. recommend (추천 기능)

실제 API/내부에서 사용하는 추천 로직들.

```
com.barofarm.ai.recommend
├── model/
│   ├── SimilarProductResult.java         # 함께 보면 좋은 상품 리스트
│   ├── PersonalizedRecommendation.java   # 개인화 추천 결과
│   └── RecipeRecommendation.java        # 레시피 추천 결과
├── service/
│   ├── SimilarProductService.java        # 상품 기준 유사 상품
│   ├── PersonalizedRecommendService.java # 검색/행동 기반 개인화
│   └── RecipeRecommendService.java      # 장바구니 기반 레시피 추천
└── facade/
    └── RecommendationFacade.java         # 외부에서 "추천"을 하나의 진입점으로 쓰고 싶을 때
```

### 6. presentation (외부에 노출되는 API)

게이트웨이/다른 서비스에서 호출할 REST API.

```
com.barofarm.ai.presentation
├── RecommendationController.java     # 상품/개인화/레시피 추천 API
└── ChatbotController.java           # (필요 시) 챗봇 엔드포인트
```

## 파일 통계

- **config**: 5개 파일
- **event**: 10개 파일 (model 5개, consumer 5개)
- **log**: 11개 파일 (domain 4개, repository 4개, application 2개, dto 1개)
- **embedding**: 9개 파일 (model 4개, service 4개, scheduler 1개)
- **recommend**: 7개 파일 (model 3개, service 3개, facade 1개)
- **presentation**: 2개 파일

**총 Java 파일**: 44개 (+ 테스트 1개)

## 주요 기능

1. **이벤트 소비**: Kafka를 통해 다양한 이벤트 수신
2. **로그 적재**: 사용자 행동 로그를 RDB에 저장
3. **임베딩/추천**: Elasticsearch 벡터 스토어를 활용한 임베딩 생성 및 추천 서비스
4. **REST API**: 외부 서비스에서 호출 가능한 추천 및 챗봇 API 제공

## 기술 스택

- **Spring Boot 3.5.8**
- **Spring AI 1.0.0-M5** (OpenAI, Ollama)
- **Spring Kafka**
- **Spring Data Elasticsearch**
- **Spring Data JPA**
- **MySQL**
- **Eureka Client** (Service Discovery)

