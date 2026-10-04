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
| 색인 경로 | `product-events` 수신부터 `product`·`product-autocomplete` 저장까지 | `ProductEventConsumer` → `ProductIndexService.indexProduct` |
| 정확·일반·오탈자 매칭 | `match_phrase` · `match` OR · fuzzy 세 `should` 절(boost·조건은 아래 비즈니스 규칙) | `ProductSearchService` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED`만 노출 | 검색·자동완성·벡터 검색 공통 |
| 자동완성 접두어 | `edge_ngram`으로 색인한 상품명 조각(길이 범위는 아래 비즈니스 규칙) | `settings/autocomplete-analyzer.json` |

## 흐름

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 상품 이벤트 발행 | `baro-shopping` `ProductService.createProduct`·`updateProduct`·`deleteProduct` | 트랜잭션 **커밋 후**(`AFTER_COMMIT`) `product-events`에 발행. 키 없음, 값은 `ProductEvent{type, data}` JSON(타입 헤더 없음). `updatedAt`은 발행 시각 | `ProductTransactionEventListener.java:17-46`, `ProductEventPublisher.java:42-57`, `ProductEventProducer.java:26` |
| 2. 소비 | `ProductEventConsumer` | 그룹 `search-service`, poll당 1건, 실패 시 1초 간격 2회 재시도 후 건너뜀. 역직렬화 실패 레코드는 `null`로 받아 WARN 후 건너뜀 | [`KafkaConsumerConfig.java`](../../../src/main/java/com/barofarm/ai/config/KafkaConsumerConfig.java), [`ProductEventConsumer.java`](../../../src/main/java/com/barofarm/ai/search/infrastructure/event/ProductEventConsumer.java) |
| 3. 색인 | `ProductIndexService.indexProduct` | 임베딩(Gemini, 실패 시 `null`) → 요리 재료명(LLM(Large Language Model, 거대 언어 모델), 실패 시 `""`) → `product-autocomplete` 저장 → `product` 저장. CREATED·UPDATED 모두 전체 덮어쓰기 | [`ProductIndexService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductIndexService.java) |
| 4. 삭제 | `ProductIndexService.deleteProduct` | 두 인덱스에서 삭제 | 동일 |
| 5. 검색 | `GET /api/v1/search` | `bool` 쿼리(should 3 + filter) → `_score desc, updatedAt desc` → `CustomPage`. `X-User-Id`와 검색어가 있으면 검색 로그 저장 + 프로필 갱신([`taste-vector`](../taste-vector/taste-vector-pipeline.md)) | [`ProductSearchService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductSearchService.java) |
| 6. 자동완성 | `GET /api/v1/search/autocomplete` | 2글자 미만 빈 배열 → Caffeine 캐시(키 `query-size`, 1,000개·5분) → `match_phrase_prefix` + 상태 필터 | `ProductIndexService.autocomplete`, `ProductAutocompleteRepository` |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/search` | `X-User-Id` 선택 | `q`·`categories[]`·`priceMin`·`priceMax`·`page`(0)·`size`(10). `q`가 없으면 필터만으로 검색 |
| GET | `/api/v1/search/autocomplete` | 없음 | `q`(필수)·`size`(5) |

두 API 모두 `ResponseDto<…>`로 감싼다. 추천 API는 감싸지 않는다.

## 데이터 모델

매핑 SSOT는 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)다.

| 인덱스 | 문서 | 이 단위가 읽는 필드 | 비고 |
|--------|------|---------------------|------|
| `product` | `ProductDocument` | `productName`(nori) · `productName.raw`(keyword) · `productCategoryCode` · `price` · `status` · `updatedAt` | `vector`·`ingredientName`도 같은 문서에 있어 검색 응답 조회 시 함께 로드된다(`_source` 필터 없음) |
| `product-autocomplete` | `ProductAutocompleteDocument` | `productName`(edge_ngram / search: standard) · `status` | 상품당 1건 |

## 비즈니스 규칙

### 발행 측 (`baro-shopping` — baro-ai가 바꿀 수 없는 전제)

| 규칙 | 값 | 근거 |
|------|-----|------|
| 이벤트 종류 | `PRODUCT_CREATED`·`PRODUCT_UPDATED`·`PRODUCT_DELETED` | `ProductEvent.ProductEventType` |
| 발행 조건 | 판매자의 생성·수정·삭제 API. 수정은 필드 diff 없이 **항상** UPDATED 발행(상태만 바뀌어도, 아무것도 안 바뀌어도) | `Product.update` 전 필드 대입 |
| 재고 변경 | 재고 수량 변경·주문에 의한 차감은 **이벤트 없음** | `InventoryService`에 발행 코드 0건 |
| 상품 상태 | `ON_SALE`·`DISCOUNTED`·`SOLD_OUT`·`HIDDEN`·`DELETED`. 생성 시 `ON_SALE` 고정, 이후 판매자 수정으로만 변경. **재고 0 → SOLD_OUT 자동 전환 없음** | `ProductStatus`, `ProductService.java:90` |
| 카테고리 | 3단계 22개, 전부 농산물(`AGRI_*`). 리프 카테고리 코드가 `productCategoryCode`로 온다 | `scripts/init-db/02-init-categories.sql` |
| 페이로드 | productId·productName·productCategoryId·Code·Name·price·status(문자열)·updatedAt(발행 시각) | `ProductEventData` |
| 순서 | 키 없이 발행 → 같은 상품의 이벤트 순서 미보장 | `ProductEventProducer.java:26` |
| 토픽 생성 | 선언적 생성 없음(자동 생성 의존). 파티션 수 `[미정]` | `k8s/infra/kafka/`·`docker-compose.data.yml` |

### 수신·색인·조회 측 (baro-ai)

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| poll당 건수 · 재시도 | 1건 · 1초 간격 2회 | `KafkaConsumerConfig` product 팩토리 ([`audits/search/001`](../../audits/search/001-index-path.md) A-1) |
| 색인 시 원격 호출 | 임베딩 1회 + 재료명 LLM 1회, 각 30초 상한·SDK 재시도 총 3회 | `LlmClientConfig` |
| 임베딩 실패 | 벡터 `null`로 색인 계속 | `indexProduct` |
| 재료명 실패 | `""`로 색인, "재색인 대상" WARN. 정규식 폴백 없음 | [`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md) |
| 정확 매칭 | `match_phrase`, `slop=1`, boost 2.0 | `applyExactMatch` |
| 일반 매칭 | `match`, `OR`, boost 1.0 | `applyNormalMatch` |
| 오탈자 매칭 | 검색어 3글자 이상일 때만, `productName.raw`(keyword) 대상, `fuzziness=AUTO`, `prefixLength=1`, boost 0.3 | `applyFuzzyMatch` |
| 정렬 | `_score desc` → `updatedAt desc` 고정 | `search` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED` | `applyStatusFilter` |
| 자동완성 최소 길이 | 2글자 | `autocomplete` |
| 자동완성 캐시 | Caffeine 최대 1,000개·TTL 5분·인스턴스별 | `CacheConfig`, [`decisions/001`](../../decisions/001-autocomplete-caching.md) |
| 자동완성 접두어 길이 | `edge_ngram` 1~20자, `token_chars` letter·digit | `settings/autocomplete-analyzer.json` |
| 자동완성 쿼리 | `match_phrase_prefix`(`max_expansions=10`) + 상태 필터 | `ProductAutocompleteRepository` |
| 검색 로그 | `X-User-Id` + 검색어 있을 때만. 저장 실패는 검색 응답에 영향 없음 | `search` 말미 |

### 배포 전제 (게이트웨이·인프라)

| 항목 | 값 | 근거 |
|------|-----|------|
| 진입 경로 | `baro-gateway` `/ai-service/**` → `http://baro-ai:8080` | `baro-gateway/.../application-prod.yml:127-132` |
| 인증 | **prod 라우트에 `AuthenticationFilter` 없음.** 토큰 없이 도달하고 `X-User-Id`는 클라이언트 값이 그대로 전달 | 동일 |
| 게이트웨이 타임아웃 | 응답 5초 | `application-prod.yml:29-30` |
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
- [x] 검색 로그 저장 실패 → 검색 응답 정상
- [ ] `"청송사고"`(오탈자) 검색 → 현재는 `productName.raw`가 상품명 전체를 한 토큰으로 비교하므로 다단어 상품명에서 거의 매칭되지 않는다

### 수동 확인 (로컬)

- [ ] 오탈자 1글자 검색어로 Before/After 결과 수 비교
- [ ] `GET product/_search?_source=false`와 현재 응답 크기 비교(벡터 1536개 float 포함 여부)

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — 자동화 테스트 없음

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 오탈자 매칭이 keyword 전체 문자열 대상 | 확정(결함 의심) | 감사 [`audits/keyword-search/001`](../../audits/keyword-search/) 대상 |
| 자동완성이 색인 `edge_ngram` + 조회 `match_phrase_prefix` 이중 접두어 | 확정 | 단순화 후보 |
| 검색 응답 조회가 벡터·재료명까지 `_source` 전체 | 확정 | `withSourceFilter` 0건 |
| prod 게이트웨이 무인증·`X-User-Id` 위조 가능 | 확정 | 게이트웨이 소유자 확인 `[미정]`. 검색 자체는 비로그인 허용이 의도일 수 있음 |
| 상태만 바뀐 수정에도 임베딩·LLM 재호출 | 확정 | 발행 측이 diff를 안 하므로 baro-ai가 상품명 변경 여부로 건너뛸지 `[미정]` |
| 로컬 ES에 nori 플러그인 없음 | 확정 | 로컬에서 `product` 인덱스 자동 생성이 실패할 수 있음 `[불확실]` |
| ES 서버 9.1.2 vs 클라이언트 8.13.3 | 확정 | 헬스 인디케이터가 꺼져 있는 이유. 인프라 소유자 확인 `[미정]` |
| `spring.kafka.consumer.*` yml 값이 커스텀 팩토리에 안 읽힘 | 확정 | [`audits/search/001`](../../audits/search/001-index-path.md) C-1 |
| 재색인·재정규화 경로 없음 | 확정 | Track A `specify` 대상 |
| `application-local.yml` 없음 | 확정 | 로컬 기동 시 Gemini 키·Kafka 주소를 env로만 줘야 함 |

## 관련 문서

- [`vector-similarity-search.md`](../vector-similarity/vector-similarity-search.md) — 색인 시 만드는 상품 벡터
- [`cart-recipe/recipe-recommendation.md`](../cart-recipe/recipe-recommendation.md) — 색인 시 만드는 요리 재료명
- [`taste-vector-pipeline.md`](../taste-vector/taste-vector-pipeline.md) — 검색 로그가 흘러가는 곳
- [`audits/search/001-index-path.md`](../../audits/search/001-index-path.md) · [`002-llm-prompt-tuning.md`](../../audits/search/002-llm-prompt-tuning.md) — 이전 라운드 감사
- [`decisions/001`](../../decisions/001-autocomplete-caching.md) · [`002`](../../decisions/002-ingredient-name-at-index-time.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
| 2026-09-04 | `search/`에서 작업 단위 `keyword-search/`로 이동. 발행 모듈(`baro-shopping`) 규칙·게이트웨이·인프라 전제·오탈자 매칭 리스크 추가 |
