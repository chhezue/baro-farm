# 키워드 검색·자동완성

상품 색인 경로 · 키워드 검색 · 자동완성을 다룬다. 상품 모듈이 발행하는 Kafka 이벤트로 Elasticsearch 상품 인덱스를 채우고, 키워드 검색과 자동완성으로 그 인덱스를 읽는 작업 단위의 스펙이다. 색인 경로는 [`vector-similarity`](../vector-similarity/vector-similarity-search.md)(상품 벡터)와 [`cart-recipe`](../cart-recipe/recipe-recommendation.md)(요리 재료명)도 같이 쓰므로, 색인 규칙의 SSOT는 이 문서다. 감사([`audits/keyword-search/`](../../audits/keyword-search/), 이전 라운드는 [`audits/search/`](../../audits/search/))가 이 문서를 근거로 삼는다.

> 상태: Implemented (2026-09-04 코드 기준 역작성, 발행 모듈 포함)
> MVP: In scope
> 관련 BR: N/A

## 언제 이 문서를 보는가

- 상품이 검색에 안 나올 때 색인 경로의 어느 단계가 원인인지 좁힐 때
- 검색 점수(정확·일반·오탈자)와 자동완성 규칙의 현재 값을 확인할 때
- 상품 이벤트가 언제 발행되고 무엇을 담는지 확인할 때

## 목표

상품 변경이 곧바로 검색에 반영되고, 키워드로 판매 중인 상품을 관련도 순으로 찾으며, 두 글자부터 자동완성을 보여준다.

## 배경

baro-ai에는 RDB가 없다. 상품의 원본은 `baro-shopping`(MySQL)이고, baro-ai는 `product-events`를 받아 만든 ES 문서만 갖는다. 그래서 "상품이 검색에 없다"의 원인은 발행 누락, 컨슈머 실패, 색인 실패 셋 중 하나다. 한국어 상품명은 nori 형태소 분석기로 색인한다.

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 색인 경로 | `product-events` 수신부터 `product` 저장까지 | `ProductEventConsumer` → `ProductIndexService.indexProducts` |
| 정확·일반·오탈자 매칭 | `match_phrase` · `match` OR · fuzzy 세 `should` 절(boost·조건은 아래 비즈니스 규칙) | `ProductSearchService` |
| 단어 서브필드 | 상품명을 띄어쓰기 단위로 토큰화한 `productName.word`. 오탈자 매칭 전용이며 형태소 분석기 결과에 좌우되지 않는다 | `ProductDocument` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED`만 노출 | 검색·자동완성·벡터 검색 공통 |
| 자동완성 접두어 | `edge_ngram`으로 색인한 상품명 조각(길이 범위는 아래 비즈니스 규칙). 조회는 확장 없이 입력한 말 그대로 찾는다 | `settings/autocomplete-analyzer.json` |

## 흐름

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 상품 이벤트 발행 | `baro-shopping` `ProductService.createProduct`·`updateProduct`·`deleteProduct` | 트랜잭션 **커밋 후**(`AFTER_COMMIT`) `product-events`에 발행. 키 없음, 값은 `ProductEvent{type, data}` JSON(타입 헤더 없음). `updatedAt`은 발행 시각 | `ProductTransactionEventListener.java:17-46`, `ProductEventPublisher.java:42-57`, `ProductEventProducer.java:26` |
| 2. 소비 | `ProductEventConsumer` | 그룹 `ai-service`(팩토리가 정한다 — 리스너 어노테이션에는 적지 않는다), **묶음 리스너 — poll당 최대 5건, 묶음 처리 허용 15분**(2026-09-06), 실패 시 실패 레코드 위치를 담아 던져(`BatchListenerFailedException`) 앞 레코드는 커밋, 실패 레코드부터 1초 간격 2회 재시도 후 그 레코드만 `product-events.DLT` 토픽으로 옮기고 다음으로 넘어감(2026-09-08 [`KafkaDeadLetterConfig.java`](../../../src/main/java/com/barofarm/ai/config/KafkaDeadLetterConfig.java)). 생성·수정은 모아서 한 번에 색인하고, 삭제를 만나면 모인 색인을 먼저 내보낸 뒤 삭제한다(같은 상품의 생성→삭제 순서 보존). 역직렬화 실패 레코드는 리스너에 닿기 전에 `DefaultErrorHandler`가 재시도 없이 건너뜀 | [`KafkaConsumerConfig.java`](../../../src/main/java/com/barofarm/ai/config/KafkaConsumerConfig.java), [`ProductEventConsumer.java`](../../../src/main/java/com/barofarm/ai/search/infrastructure/event/ProductEventConsumer.java) |
| 3. 색인 | `ProductIndexService.indexProducts` | **기존 문서 일괄 조회 → 상품명이 그대로면 임베딩·재료명을 그대로 옮겨 쓴다**(2026-09-08). 바뀐 상품만 묶음 임베딩(Gemini `batchEmbedContents` 1회, 실패 시 상품별 폴백 → 실패한 상품만 `null`) → 요리 재료명(상품별 1회)(LLM(Large Language Model, 거대 언어 모델), 실패 시 `""`) → `product` `saveAll`(bulk 1회). `updatedAt`은 이벤트 값(발행 시각)을 그대로 쓰고 없을 때만 현재 시각. CREATED·UPDATED 모두 전체 덮어쓰기 | [`ProductIndexService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductIndexService.java) |
| 4. 삭제 | `ProductIndexService.deleteProduct` | `product` 인덱스에서 삭제(2026-09-08 자동완성 인덱스 통합 후 한 곳이다) | 동일 |
| 5. 검색 | `GET /api/v1/search` | 페이징 파라미터 검증 → `bool` 쿼리(should 3 + filter) → `_score desc, updatedAt desc` → 벡터를 뺀 `_source`만 받아 `CustomPage`. **조회만 하고 아무것도 기록하지 않는다** — 2026-09-07까지는 검색 로그를 남겼으나 검색이 취향 신호에서 빠지며 제거됐다 | [`ProductSearchService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductSearchService.java) |
| 6. 자동완성 | `GET /api/v1/search/autocomplete` (`ProductAutocompleteService`) | `size` 검증 → 2글자 미만 빈 배열 → Caffeine 캐시(키 `query-size`, 1,000개·5분) → `product` 인덱스의 `productName.ac`에 `match`(`operator=and`) + 상태 필터 | `ProductSearchService.autocomplete` |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/search` | 없음(사용자 식별 안 함) | `q`·`categories[]`·`priceMin`·`priceMax`·`page`(0)·`size`(10). `q`가 없으면 필터만으로 검색 |
| GET | `/api/v1/search/autocomplete` | 없음 | `q`(필수)·`size`(5) |
| POST | `/api/v1/search/admin/reindex` | `X-Admin-Token` 헤더(`BARO_AI_ADMIN_TOKEN`) | 전체 재색인 시작(2026-09-09 신설, [`ProductReindexController.java`](../../../src/main/java/com/barofarm/ai/search/presentation/ProductReindexController.java)). 이미 진행 중이면 409 |

두 API 모두 `ResponseDto<…>`로 감싼다. 2026-09-05부터 추천 API도 같은 envelope를 쓴다.

### 에러

범위를 벗어난 페이징 파라미터는 400으로 끊는다. 그전에는 `size=0`이 Spring 영문 메시지로, `page=1000&size=10`이 500으로 나갔다.

| 코드 | HTTP | 조건 |
|------|------|------|
| `INVALID_PAGE` | 400 | `page < 0` |
| `INVALID_PAGE_SIZE` | 400 | `size < 1` 또는 `size > 100` |
| `PAGE_RANGE_EXCEEDED` | 400 | `(page + 1) × size > 10,000` (ES `index.max_result_window`) |
| `INVALID_AUTOCOMPLETE_SIZE` | 400 | 자동완성 `size < 1` 또는 `size > 20` |
| `ADMIN_TOKEN_INVALID` | 403 | 재색인 요청의 `X-Admin-Token`이 없거나 다름 |
| `REINDEX_ALREADY_RUNNING` | 409 | 재색인이 이미 진행 중 |

SSOT: `search/exception/SearchErrorCode.java`

## 데이터 모델

매핑 SSOT는 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)다.

| 인덱스 | 문서 | 이 단위가 읽는 필드 | 비고 |
|--------|------|---------------------|------|
| `product` | `ProductDocument` | `productName`(nori) · `productName.word`(standard) · `productCategoryCode` · `price` · `status` · `updatedAt` | `vector`·`ingredientName`도 같은 문서에 있지만 검색 응답 조회는 `_source`에서 `vector`를 제외한다 |

## 비즈니스 규칙

### 발행 측 (`baro-shopping` — baro-ai가 바꿀 수 없는 전제)

| 규칙 | 값 | 근거 |
|------|-----|------|
| 이벤트 종류 | `PRODUCT_CREATED`·`PRODUCT_UPDATED`·`PRODUCT_DELETED` | `ProductEvent.ProductEventType` |
| 발행 조건 | 판매자의 생성·수정·삭제 API. 수정은 필드 diff 없이 **항상** UPDATED 발행(상태만 바뀌어도, 아무것도 안 바뀌어도) | `Product.update` 전 필드 대입 |
| 재고 변경 | 재고 수량 변경·주문에 의한 차감은 **이벤트 없음** | `InventoryService`에 발행 코드 0건 |
| 상품 상태 | `ON_SALE`·`DISCOUNTED`·`SOLD_OUT`·`HIDDEN`·`DELETED`. 생성 시 `ON_SALE` 고정, 이후 판매자 수정으로만 변경. **재고 0 → SOLD_OUT 자동 전환 없음** | `ProductStatus`, `ProductService.java:90` |
| 카테고리 | 루트 1 + Level 1 7개 + Level 2 14개 = 22개(루트를 빼면 21개), 전부 농산물·축산물 계열(`AGRI_*`). 양념·가공식품 칸은 없다. 리프 카테고리 코드가 `productCategoryCode`로 온다 | `scripts/init-db/02-init-categories.sql` |
| 페이로드 | productId·productName·productCategoryId·Code·Name·price·status(문자열)·updatedAt(발행 시각) | `ProductEventData` |
| 순서 | 키 없이 발행 → 같은 상품의 이벤트 순서 미보장 | `ProductEventProducer.java:26` |
| 토픽 생성 | 선언적 생성 없음(자동 생성 의존). 파티션 수 `[미정]` | `k8s/infra/kafka/`·`docker-compose.data.yml` |

### 수신·색인·조회 측 (baro-ai)

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| poll당 건수 · 처리 허용 · 재시도 | 최대 5건(묶음) · 15분 · 실패 레코드부터 1초 간격 2회 | `KafkaConsumerConfig` product 팩토리. 5는 상품 1건 최악 지연 180초(임베딩·재료명 각 30초×3회) × 5 = 15분에서 나왔다 — 1건일 때 지키던 "최악에도 리밸런싱 없음"을 유지하는 최대값([`audits/keyword-search/002`](../../audits/keyword-search/002-index-throughput-and-search-log.md) A-1) |
| 색인 시 원격 호출 | **상품명이 바뀐 상품에 대해서만** 묶음당 임베딩 1회 + 상품당 재료명 LLM 1회, 각 30초 상한·SDK 재시도 총 3회(배치 경로 클라이언트). 이름이 그대로면 0회 | `LlmClientConfig`, `ProductIndexService` |
| 임베딩 실패 | 묶음이 실패하면 상품별로 다시 시도, 그래도 실패한 상품만 벡터 `null`로 색인 계속 | `ProductEmbeddingService.embedProducts` |
| 재료명 실패 | `""`로 색인, "재색인 대상" WARN. 정규식 폴백 없음 | [`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md) |
| 정확 매칭 | `match_phrase`, `slop=1`, boost 2.0 | `applyExactMatch` |
| 일반 매칭 | `match`, `OR`, boost 1.0 | `applyNormalMatch` |
| 오탈자 매칭 | 검색어 2글자 이상일 때만, `productName.word`(standard text) 대상, `fuzziness=1`(단어마다 한 글자), `prefixLength=1`, boost 0.3 | `applyFuzzyMatch` |
| 정렬 | `_score desc` → `updatedAt desc` 고정. `updatedAt`은 이벤트 **발행 시각**(발행 측이 찍어 보낸 값)이지 상품이 바뀐 시각이 아니다 | `search` |
| 검색 응답 조회 | `_source`에서 `vector` 제외 | `search` |
| 페이징 상한 | `page ≥ 0`, `size` 1~100, `(page + 1) × size ≤ 10,000` | `toPageable` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED` | `applyStatusFilter` |
| 자동완성 최소 길이 | 2글자 | `autocomplete` |
| 자동완성 개수 상한 | `size` 1~20 | `autocomplete` |
| 자동완성 캐시 | Caffeine 최대 1,000개·TTL 5분·인스턴스별 | `CacheConfig`, [`decisions/001`](../../decisions/001-autocomplete-caching.md) |
| 자동완성 접두어 길이 | `edge_ngram` 1~20자, `token_chars` letter·digit | `settings/autocomplete-analyzer.json` |
| 자동완성 쿼리 | `productName.ac`에 `match`(`operator=and`) + 상태 필터. 색인 쪽 `edge_ngram`이 이미 접두어를 갖고 있어 조회에서 다시 확장하지 않는다. **2026-09-08부터 별도 인덱스가 아니라 상품 문서의 서브필드다** — 한 상품을 두 인덱스에 나눠 쓰면 한쪽만 성공할 수 있고 구매 가능 상태 목록도 두 곳에 갈라졌기 때문이다 | `ProductSearchService.autocomplete` |
| 검색 로그 | **없다.** 검색 API는 부작용이 없는 조회다. `X-User-Id` 헤더도 더 이상 읽지 않는다(2026-09-07, [`audits/taste-vector/005`](../../audits/taste-vector/005-signal-and-weight-simplification.md) A-1) | — |

### 배포 전제 (게이트웨이·인프라)

| 항목 | 값 | 근거 |
|------|-----|------|
| 진입 경로 | `baro-gateway` `/ai-service/**` → `http://baro-ai:8080` | `baro-gateway/.../application-prod.yml:127-132` |
| 인증 | **prod 라우트에 `AuthenticationFilter` 없음.** 토큰 없이 도달하고 `X-User-Id`는 클라이언트 값이 그대로 전달 | 동일 |
| 게이트웨이 타임아웃 | 응답 5초 | `baro-gateway/.../application-prod.yml:29-30` |
| 레이트 리밋 | 전 라우트 공통 50/초, 버스트 100, 키는 `X-User-Id` 또는 IP | `TrafficControlConfig` |
| 인스턴스 수 | 1~3(HPA, CPU 70%) → 자동완성 캐시는 인스턴스마다 따로 | `k8s/apps/baro-ai/hpa.yml` |
| ES | 로컬 8.12.0(nori 없음) / 운영 9.1.2 커스텀 이미지(nori 설치) / 클라이언트 8.13.3 | `docker-compose.data.yml`, `docker/elasticsearch/Dockerfile` |

## 검증 시나리오

### 정상

- [x] 키워드 + 카테고리 + 가격 조합 검색
- [x] 키워드 없이 필터만 검색
- [x] 자동완성 2글자 이상

### 엣지 · 실패

- [x] 자동완성 1글자 → 빈 배열
- [x] 임베딩·재료명 실패해도 상품 색인 유지
- [x] 검색에 부가 작업이 없다 — 회원 검색 963회에서 비동기 제출 0건(2026-09-07 로컬 확인)
- [ ] `"청송사고"`(오탈자) 검색 → `productName.word` 전환 후 `"청송사과 프리미엄 1kg"`이 나와야 한다 (2026-09-05 리팩터, 로컬 ES 미기동으로 미확인)

### 수동 확인 (로컬)

2026-09-05 리팩터 시점에 로컬 ES를 띄우지 못해 아래는 모두 **미확인**이다.

- [ ] 오탈자 1글자 검색어로 Before/After 결과 수 비교
- [ ] `"청송 사과"`(두 단어) 자동완성 Before(빈 배열)/After(1건) 비교. `"토마"`·`"청송사"`는 Before/After 같은 목록·순서
- [ ] `logging.level.tracer=TRACE`로 검색 1회 호출해 ES 응답 본문 크기 Before/After 비교
- [ ] `page=1000&size=10` Before(500)/After(400 한글 메시지), `size=0` Before(400 영문)/After(400 한글)
- [ ] 과거 시각 `updatedAt`을 실은 이벤트 1건을 발행해 색인된 문서의 `updatedAt`이 이벤트 값과 같은지
- [ ] 자동완성 2회 호출 시 두 번째가 ES 요청 없이 응답하는지(캐시 프록시 유지 확인)

## 완료 기준

- [x] 기능 구현
- [ ] 위 "수동 확인 (로컬)" 절차 — 로컬 ES 미기동으로 2026-09-05 리팩터에서는 수행하지 못했다
- [x] `./gradlew :baro-ai:test` — 이 단위 39건 통과 (`ProductSearchServiceTest` 8 · `ProductIndexServiceTest` 10 · `ProductEventConsumerTest` 5 · `ProductAutocompleteServiceTest` 2 · `ProductReindexServiceTest` 6 · `ProductReindexControllerTest` 4 · `ProductIndexAndSearchIntegrationTest` 4). 저장소 전체는 17클래스 93건(2026-09-09 감사 010 반영 뒤 `./gradlew :baro-ai:test` 실행 기준)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 오탈자 매칭이 keyword 전체 문자열 대상 | 해소 | 2026-09-05 `productName.word` 전환 |
| 자동완성이 색인 `edge_ngram` + 조회 `match_phrase_prefix` 이중 접두어 | 해소 | 2026-09-05 `match`(`operator=and`)로 단순화 |
| 검색 응답 조회가 벡터·재료명까지 `_source` 전체 | 해소 | 2026-09-05 `vector` 제외. `ingredientName`은 수십 바이트라 그대로 둔다 |
| prod 게이트웨이 무인증·`X-User-Id` 위조 가능 | 확정 | 게이트웨이 소유자 확인 `[미정]`. 검색 자체는 비로그인 허용이 의도일 수 있음 |
| 상태만 바뀐 수정에도 임베딩·LLM 재호출 | 확정 | 발행 측이 diff를 안 하므로 baro-ai가 상품명 변경 여부로 건너뛸지 `[미정]` |
| 로컬 ES에 nori 플러그인 없음 | 확정 | 로컬에서 `product` 인덱스 자동 생성이 실패할 수 있음 `[불확실]`. 오탈자 매칭은 nori와 무관한 `productName.word`를 쓰므로 이 리스크의 영향을 받지 않는다 |
| ES 서버 9.1.2 vs 클라이언트 8.13.3 | 확정 | 헬스 인디케이터가 꺼져 있는 이유. 인프라 소유자 확인 `[미정]` |
| `spring.kafka.consumer.*` yml 값이 커스텀 팩토리에 안 읽힘 | 확정 | [`audits/search/001`](../../audits/search/001-index-path.md) C-1 |
| `updatedAt`이 "상품이 바뀐 시각"이 아님 | 확정 | 발행 측이 `Instant.now()`를 찍어 보낸다(`ProductEventPublisher.java:53`). 색인 시각을 쓰던 흔들림은 2026-09-05에 없앴지만, 재발행으로 전체를 다시 색인하면 최신순이 무의미해진다. 발행 모듈 담당자 확인 `[미정]` — [`open-items.md`](../../audits/open-items.md) B-11 |
| 재색인·재정규화 경로 없음 | 확정 | Track A `specify` 대상 |
| ~~검색 로그 유실 가능~~ | **해소(2026-09-07)** | 실측에서 유실률이 37~65%로 확인됐고([`audits/taste-vector/004`](../../audits/taste-vector/004-executor-fix-measurement.md)), 그 뒤 검색 로그 자체를 없애 리스크가 사라졌다 |
| Gemini 무료 티어 한도 | 확정 | 2026-09-06 실측: 임베딩을 1건씩 보내면 13번째부터 429, 10건 묶음은 연속 성공. 재색인 규모가 크면 유료 티어 또는 묶음 크기 조정이 필요 `[미정]` |
| `application-local.yml` 없음 | 확정 | 로컬 기동 시 Gemini 키·Kafka 주소를 env로만 줘야 함 |

## 관련 문서

- [`vector-similarity-search.md`](../vector-similarity/vector-similarity-search.md) — 색인 시 만드는 상품 벡터
- [`cart-recipe/recipe-recommendation.md`](../cart-recipe/recipe-recommendation.md) — 색인 시 만드는 요리 재료명
- [`taste-vector-pipeline.md`](../taste-vector/taste-vector-pipeline.md) — 개인화 추천. 2026-09-07부터 검색과 코드 의존이 없다
- [`audits/search/001-index-path.md`](../../audits/search/001-index-path.md) · [`002-llm-prompt-tuning.md`](../../audits/search/002-llm-prompt-tuning.md) — 이전 라운드 감사
- [`decisions/001`](../../decisions/001-autocomplete-caching.md) · [`002`](../../decisions/002-ingredient-name-at-index-time.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-09 | `ProductSearchService`(426줄)를 질의 조립(`ProductQueryFactory`)·검색·자동완성(`ProductAutocompleteService`) 셋으로 분리. **전체 재색인 관리 API**(`POST /api/v1/search/admin/reindex`, 관리 토큰 필요) 신설 — 인덱스를 새로 만든 뒤 상류에 재발행을 부탁하지 않고 직접 채운다. Elasticsearch 응답 상한 30초 → 3초 |
| 2026-09-08 | 자동완성을 `product-autocomplete` 인덱스에서 `product.productName.ac` 서브필드로 통합(이중 쓰기·상태 목록 이원화 제거). 색인에 변경 감지 도입 — 상품명이 그대로면 임베딩·재료명을 재사용. Testcontainers 통합 테스트 3건 추가 |
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
| 2026-09-04 | `search/`에서 작업 단위 `keyword-search/`로 이동. 발행 모듈(`baro-shopping`) 규칙·게이트웨이·인프라 전제·오탈자 매칭 리스크 추가 |
| 2026-09-05 | 감사 001 A·B 전 항목 반영 — 오탈자 매칭 필드·허용 범위·최소 길이 변경, 자동완성 쿼리 단순화, `_source`에서 벡터 제외, 페이징 검증·`SearchErrorCode` 신설, 자동완성 조회를 `ProductSearchService`로 이동, `updatedAt`을 이벤트 값으로 |
| 2026-09-05 | `updatedAt` 서술 정정 — 이벤트 값은 "상품이 바뀐 시각"이 아니라 **발행 시각**이다(발행 측 `Instant.now()`). 비즈니스 규칙·흐름 행을 고치고 남은 한계를 리스크 표에 추가 |
| 2026-09-06 | 감사 002 반영 — 상품 이벤트 묶음 소비(poll당 5건·15분)와 묶음 색인(임베딩 1회·인덱스당 bulk 1회, 실패 시 상품별 폴백), 검색 로그 적재를 비동기 작업으로 이동, 페이징·자동완성·색인 단위 테스트 도입. 측정 M-3 근거 |
| 2026-09-07 | 검색 로그 적재를 **제거**. 검색이 취향 신호에서 빠지면서 소비자가 없어졌다(감사 [`taste-vector/005`](../../audits/taste-vector/005-signal-and-weight-simplification.md) A-1). 검색 API는 이제 부작용 없는 조회이고 `X-User-Id`를 읽지 않는다 |
