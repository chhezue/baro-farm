# 취향 벡터 파이프라인

행동 로그 수집 · 프로필 임베딩 · 개인화 추천을 다룬다. 사용자의 검색·장바구니·주문 행동을 로그로 모아 하나의 벡터(프로필 벡터)로 압축하고, 그 벡터와 가까운 상품을 추천하는 작업 단위의 스펙이다. 이벤트를 **발행하는 다른 모듈**(`baro-shopping`·`baro-order`)의 규칙까지 포함해, 이 기능이 실제로 어떤 조건에서 어떤 값을 받아 동작하는지를 코드 기준으로 적었다. 감사([`audits/taste-vector/`](../../audits/taste-vector/))·리팩터·포트폴리오가 이 문서를 계약값의 근거로 삼는다.

> 상태: Implemented (2026-09-04 코드 기준 역작성, 타 모듈 발행부 포함)
> MVP: In scope
> 관련 BR: N/A (비즈니스 규칙은 아래 표가 유일한 표현)

## 언제 이 문서를 보는가

- 개인화 추천이 "왜 이 상품을 추천했는지"를 로그·가중치 수준에서 설명해야 할 때
- cart/order 이벤트의 필드 의미(특히 `quantity`)와 발행 조건을 확인할 때
- 프로필 벡터 갱신 시점·비용을 바꾸는 감사·리팩터 전에 현재 계약을 확인할 때

## 목표

최근 30일의 행동 로그를 행동 종류·수량·경과 시간으로 가중해 사용자 취향을 벡터 하나로 표현하고, 매 요청마다 상품 벡터와 비교해 다양성 있는 Top-K 상품을 돌려준다.

## 배경

검색 로그는 baro-ai 자신이 검색 API에서 직접 저장하고, 장바구니·주문 로그는 다른 모듈이 `baro-common`의 `@TrackHistory` AOP로 발행한 Kafka 이벤트를 받아 저장한다. 로그 저장은 동기, 프로필 벡터 재계산은 비동기라는 최종 일관성 설계를 택했다. 상품 쪽 벡터와 벡터 검색 자체는 [`vector-similarity`](../vector-similarity/vector-similarity-search.md)가 담당하고, 이 문서는 사용자 쪽 벡터와 그것을 쓰는 개인화 추천만 다룬다.

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 행동 로그 | 검색어·장바구니 변경·주문 확정 1건을 ES 문서로 저장한 것 | [`glossary.md`](../../product/glossary.md) |
| 이벤트 봉투 | `baro-common`이 Kafka로 보내는 `{event, ts, userId, payload}` 구조 | `HistoryEnvelope` |
| 대표 텍스트 | 로그의 상품명·검색어를 가중치만큼 반복해 이어 붙인 문자열. 임베딩 모델의 입력 | `buildRepresentativeText` |
| 프로필 벡터(취향 벡터) | 대표 텍스트를 임베딩한 벡터(차원은 데이터 모델 표) | `UserProfileEmbeddingDocument.userProfileVector` |
| 갱신 스킵 게이트 | 조회 기간 안의 로그가 최소 개수에 못 미치면 프로필을 만들지 않는 조건(값은 아래 비즈니스 규칙) | `MIN_TOTAL_LOGS_FOR_EMBEDDING` |
| 경험 상품 | 최근 장바구니·주문 로그에 등장한 상품. 추천에서 제외 | `sourceProductIds` |

## 흐름

발행 모듈부터 응답까지 한 줄에 한 단계씩 적었다. 코드 위치는 이 저장소 기준이다.

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 장바구니 이벤트 발행 | `baro-shopping` `CartService.addItem`·`updateQuantity`·`removeItem` | `@TrackHistory`가 붙은 세 메서드만 `HistoryAspect`를 거쳐 `cart-events`로 발행. 값은 이벤트 봉투 JSON **문자열**, 키는 `userId` | `baro-shopping/.../cart/application/CartService.java:57,78,111`, `baro-common/.../HistoryLogWriter.java:78` |
| 2. 주문 이벤트 발행 | `baro-order` `InventoryConfirmedConsumer.handle` | 재고 확정 이벤트(`inventory-confirmed`)를 받아 주문을 `CONFIRMED`로 바꾸는 메서드에만 `@TrackHistory(ORDER_CONFIRMED)`가 있다. `userId`는 HTTP 헤더가 아니라 주문 엔티티에서 매퍼가 조회 | `baro-order/.../InventoryConfirmedConsumer.java:47`, `OrderConfirmedHistoryPayloadMapper.java:41-47` |
| 3. 검색 로그 저장 | baro-ai `ProductSearchService.search` | `X-User-Id`와 검색어가 둘 다 있을 때만 `search_logs`에 저장하고, 다른 빈(`ProductIndexService`)의 `@Async` 메서드로 프로필 갱신을 넘긴다 | [`ProductSearchService.java:88-101`](../../../src/main/java/com/barofarm/ai/search/application/ProductSearchService.java) |
| 4. 이벤트 소비 | `CartEventConsumer`·`OrderEventConsumer` | 그룹 `ai-service-cart`·`ai-service-order`, `auto-offset-reset=earliest`. 이벤트 종류별로 `cart_event_logs`·`order_event_logs`에 저장하고 같은 클래스의 `updateUserProfileAsync`를 호출 | [`CartEventConsumer.java`](../../../src/main/java/com/barofarm/ai/event/consumer/CartEventConsumer.java), [`OrderEventConsumer.java`](../../../src/main/java/com/barofarm/ai/event/consumer/OrderEventConsumer.java) |
| 5. 프로필 벡터 갱신 | `UserProfileEmbeddingService.updateUserProfileEmbedding` | 30일 내 로그를 종류별 최신 5개씩 조회 → 게이트 → 대표 텍스트 → Gemini 임베딩 → 선호 카테고리 → `user_profile_embeddings` 저장(문서 ID = userId, 덮어쓰기) | [`UserProfileEmbeddingService.java`](../../../src/main/java/com/barofarm/ai/embedding/application/UserProfileEmbeddingService.java) |
| 6. 개인화 추천 | `PersonalizedRecommendService.recommendProducts` | 프로필 벡터 조회 → 경험 상품 제외·선호 카테고리 보너스로 후보 `min(topK×3, 100)`개 벡터 검색 → 다양화 → 응답 | [`PersonalizedRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/PersonalizedRecommendService.java) |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/personalized/{userId}` | 없음(경로의 userId를 신뢰) | `topK` 쿼리, 기본 5. 프로필이 없으면 빈 배열 |

응답은 `ProductRecommendResponse[]`(productId·productName·productCategoryName·price)를 **envelope 없이** 그대로 돌려준다. 검색 API가 `ResponseDto`로 감싸는 것과 다르다.

성공:

```json
[{ "productId": "…", "productName": "햇사과 1kg", "productCategoryName": "제철 과일", "price": 23000 }]
```

## 데이터 모델

매핑 SSOT는 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)다. 이 단위가 쓰는 인덱스만 요약한다.

| 인덱스 | 문서 | 핵심 필드 | 쓰는 곳 |
|--------|------|-----------|---------|
| `search_logs` | `SearchLogDocument` | userId, searchQuery, searchedAt | 검색 API가 쓰고 5단계가 읽음 |
| `cart_event_logs` | `CartLogDocument` | userId, productId, productName, categoryCode, eventType(`ADD`/`REMOVE`/`UPDATE`), quantity, occurredAt | 4단계가 쓰고 5단계가 읽음 |
| `order_event_logs` | `OrderLogDocument` | 같은 구조, eventType(`ORDER_CONFIRMED`/`ORDER_CANCELLED`) | 동일 |
| `user_profile_embeddings` | `UserProfileEmbeddingDocument` | userId(ID), userProfileVector(1536), lastUpdatedAt, sourceSearchLogIds·sourceCartLogIds·sourceOrderLogIds, sourceProductIds, preferredCategoryCode | 5단계가 쓰고 6단계가 읽음 |

`source*LogIds` 세 필드는 저장만 되고 읽는 코드가 없다(2026-09-04 grep 0건).

## 비즈니스 규칙

### 발행 측 (다른 모듈 — baro-ai가 바꿀 수 없는 전제)

| 규칙 | 값 | 근거 |
|------|-----|------|
| 이벤트가 나가는 장바구니 조작 | 담기(`CART_ITEM_ADDED`)·수량 변경(`CART_QUANTITY_UPDATED`)·항목 삭제(`CART_ITEM_REMOVED`) 3가지뿐. **비우기·게스트 병합·옵션 변경 병합은 이벤트 없음** | `CartService.java:94-153` |
| `quantity`의 의미 | ADD = 이번에 담은 **증분**, UPDATE = 변경 후 **새 총량**, REMOVE = 삭제 직전 **전량** | `CartAddHistoryPayloadMapper.java:49`, `CartQuantityUpdateHistoryPayloadMapper.java:38`, `CartRemoveHistoryPayloadMapper.java:64` |
| 게스트 장바구니 | `userId`가 없으면 Kafka 발행을 건너뛴다(파일 로그만 남음). 게스트 행동은 취향 벡터에 들어오지 않는다 | `HistoryLogWriter.java:66-70` |
| 주문 확정 시점 | 결제 성공 직후가 아니라 `baro-shopping`이 예약 재고를 확정해 `inventory-confirmed`를 보낸 뒤 `baro-order`가 그것을 받은 시점. 상태 `AWAITING_PAYMENT → CONFIRMED` | `InventoryConfirmedConsumer.java:53-57` |
| 주문 취소 | `OrderCancelledHistoryPayloadMapper`는 있으나 `@TrackHistory(ORDER_CANCELLED)`를 붙인 곳이 저장소 전체에 **없다**. 취소 이벤트는 발행되지 않는다 | 저장소 전체 grep 2026-09-04 |
| 주문 항목의 상품명·카테고리 | 주문 생성 요청에 담긴 값의 스냅샷. 상품 서비스에서 다시 조회하지 않는다 | `OrderItem.java:33-37`, `OrderService.java:54-64` |
| 발행 신뢰성 | 아웃박스 없이 `KafkaTemplate.send` 직접 호출. 실패는 WARN 로그로 삼켜지므로 유실 가능 | `HistoryLogWriter.java:45-53` |
| 파티션 키 | `userId` 문자열 → 같은 사용자 이벤트는 같은 파티션에서 순서 보장 | `HistoryLogWriter.java:78` |

### 수신·계산 측 (baro-ai)

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| 조회 기간 | 최근 30일 | `updateUserProfileEmbedding` |
| 종류별 로그 상한 | 검색·장바구니·주문 각 최신 5개 | 동일 (`PageRequest.of(0, 5)`) |
| 갱신 스킵 게이트 | 세 종류 합계가 3개 미만이면 저장하지 않음 | `MIN_TOTAL_LOGS_FOR_EMBEDDING` |
| 행동 가중치 (의도) | 장바구니 ADD 2 · UPDATE 1 · REMOVE 0 / 주문 CONFIRMED 3 · CANCELLED 0 / 검색 1 | `calculateCartEventWeight`, `calculateOrderEventWeight` |
| 행동 가중치 (실제) | **장바구니는 전부 1.** 컨슈머가 로그에 저장하는 값은 `ADD`·`REMOVE`·`UPDATE`인데 가중치 함수는 `CART_ITEM_ADDED`·`CART_QUANTITY_UPDATED`·`CART_ITEM_REMOVED`와 비교해 항상 `default -> 1`로 떨어진다. 주문은 저장값과 비교값이 같아 의도대로 동작 | [`CartEventConsumer.java:46,63,80`](../../../src/main/java/com/barofarm/ai/event/consumer/CartEventConsumer.java) vs [`UserProfileEmbeddingService.java:210-212`](../../../src/main/java/com/barofarm/ai/embedding/application/UserProfileEmbeddingService.java) |
| 수량 가중치 | `max(1, quantity)` — 이벤트마다 `quantity` 의미가 다른데(위 표) 같은 식을 적용한다 | `expandCart/OrderProductWithFullWeight` |
| 시간 가중치 | `clamp(3.0 × e^(−hoursAgo/168), 0.3, 3.0)`. 168시간(7일)에 약 0.37배 | `calculateTimeWeight` |
| 반복 횟수 | `max(1, round(행동 × 수량 × 시간))`회 텍스트에 반복. **상한 없음.** 가중치 0인 REMOVE·CANCELLED도 `max(1, …)` 때문에 1회 들어간다 | `Math.max(1L, …)` |
| 경험 상품 | 최신 장바구니 5건 + 주문 5건의 productId(중복 제거). REMOVE·CANCELLED도 포함 | `sourceProductIds` |
| 선호 카테고리 | 장바구니·주문 로그를 행동×수량×시간 가중치로 카테고리별 합산해 최댓값 1개 | `calculatePreferredCategoryCode` |
| 갱신 트리거 | 검색 1회·장바구니 이벤트 1건·주문 이벤트 1건마다 매번 재계산. 합치거나 미루는 장치 없음 | 3·4단계 |
| 비동기 실행 | `profileUpdateExecutor`(core 2 · max 5 · queue 100, 초과 시 거절). 검색 경로만 다른 빈을 거쳐 실제로 비동기이고, **컨슈머 두 곳은 같은 클래스 메서드를 직접 호출해 프록시를 타지 않으므로 동기 실행** | `AsyncConfig`, `CartEventConsumer.java:106`, `OrderEventConsumer.java:252` |
| 추천 후보 규모 · 상태 필터 | [`vector-similarity`](../vector-similarity/vector-similarity-search.md) 비즈니스 규칙과 같다(후보 규모·상태 필터는 그쪽이 SSOT) | `PersonalizedRecommendService` |
| 카테고리 일치 보너스 · 다양화 | 선호 카테고리가 있을 때 보너스를 주고 max-min 다양화를 적용한다. 값·동작은 `vector-similarity` 비즈니스 규칙이 SSOT | `VectorProductSearchService`, `MedoidDiversityService` |
| 컨슈머 실패 처리 | 로그 저장 예외는 다시 던진다. cart·order 팩토리에는 에러 핸들러가 없어 Spring Kafka 기본값(즉시 재시도 후 건너뜀)이 적용된다 | `KafkaConsumerConfig` cart·order 팩토리 |
| 프로필 갱신 실패 | 예외를 삼키고 WARN. 이벤트 처리 결과에 영향 없음 | `updateUserProfileAsync` |

## 검증 시나리오

### 정상

- [x] 검색 1회 + 담기 1회 + 주문 확정 1회 → 로그 3건 → 프로필 저장 → 추천 응답에 경험 상품이 없고 `topK`개 이하
- [x] 같은 사용자가 다시 검색 → 프로필이 최신 로그로 덮어쓰기

### 엣지 · 실패

- [x] 로그 2개 이하 → 프로필 없음 → 추천 빈 배열
- [x] 임베딩 API 실패 → 프로필 저장 안 함, 이벤트 처리는 성공으로 커밋
- [x] 프로필은 있는데 후보 상품이 0개(전부 품절) → 빈 배열
- [ ] 장바구니 삭제 이벤트만 있는 사용자 → 현재는 삭제한 상품명이 대표 텍스트에 1회 포함된다(규칙 표 "반복 횟수")

### 수동 확인 (로컬)

- [ ] 컨슈머 로그의 스레드 이름으로 프로필 갱신이 어느 스레드에서 도는지 확인 — 현재 컨슈머 경로는 리스너 스레드에서 돈다
- [ ] 검색 5회 연속 호출 시 Gemini 임베딩 호출이 5회 찍히는지 `org.springframework.ai` DEBUG 로그로 확인

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — 자동화 테스트 없음(사용자 결정으로 코드 변경 완료 후 도입)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| **장바구니 행동 가중치가 문자열 불일치로 전부 1** | 확정(결함) | 담기 2배·삭제 0이 아니라 셋 다 1. 삭제한 상품이 담은 상품과 같은 비중으로 취향에 반영된다. 감사 [`audits/taste-vector/001`](../../audits/taste-vector/) 대상 |
| `@Async`가 컨슈머에서 동작하지 않음(자기 호출) | 확정(결함) | 포트폴리오의 "Consumer Lag 해소"와 반대. 감사 [`audits/taste-vector/001`](../../audits/taste-vector/) 대상 |
| 이벤트마다 임베딩 재계산, 디바운스 없음 | 확정 | 검색 N회 = 임베딩 N회. 감사 대상 |
| REMOVE·CANCELLED가 대표 텍스트·경험 상품에 남음 | 확정(결함) | 문서·포트폴리오의 "즉시 완전 제외"와 불일치 |
| 반복 횟수 상한 없음 | 확정 | 주문 3 × 수량 20 × 시간 3 = 180회 반복. Gemini 임베딩 입력 한도 초과 시 잘림 가능 — 한도 수치 `[미정]`(공식 문서 확인 필요) |
| `ORDER_CANCELLED` 컨슈머 분기가 도달 불가 | 확정 | 발행부 부재. 붙일지 지울지는 **팀 결정** `[미정]` |
| `quantity` 의미가 이벤트마다 다른데 같은 가중치 식 | 확정 | UPDATE의 "새 총량"을 증분처럼 곱한다. 정책 판단 필요 `[미정]` |
| 게스트 행동이 취향 벡터에 없음 | 확정(설계) | 로그인 후 병합 시에도 ADD 이벤트가 없어 이전 담기 이력은 영구 소실 |
| `X-User-Id`를 게이트웨이가 만들지 않음(prod) | 확정 | prod 게이트웨이의 `/ai-service/**` 라우트에 `AuthenticationFilter`가 없어 클라이언트가 보낸 헤더가 그대로 전달된다. 타인의 userId로 추천 조회·검색 로그 오염 가능. 게이트웨이 소유자 확인 `[미정]` |
| `source*LogIds` 3필드 미사용 | 확정 | 삭제 후보(감사) |
| cart·order 컨슈머 에러 핸들러 미명시 | 확정 | product 팩토리처럼 값을 명시할지 감사에서 결정 |
| `calculateTimeWeight` 주석("7일에 0.5배")과 식 불일치 | 확정 | 실제 약 0.37배 |
| 발행이 `@Transactional` 커밋 전인지 후인지 | 불확실 | `HistoryAspect`와 `@Transactional`의 어드바이저 순서가 지정돼 있지 않다. baro-order 소유자 확인 `[미정]` |
| 자동화 테스트 0개 | 확정 | 가중치·대표 텍스트는 순수 함수라 도입 시 첫 대상 |

## 승인된 재설계 (2026-09-05, 구현 전)

위 흐름·데이터 모델·비즈니스 규칙은 **현행 코드** 기준이다. 감사 [`audits/taste-vector/001`](../../audits/taste-vector/001-profile-refresh-path.md) "승인 결과" 절에서 다음이 확정됐고, 구현이 끝나면 이 문서의 해당 절을 그 내용으로 amend한다.

| 항목 | 현행 | 승인된 변경 |
|------|------|-------------|
| 프로필 벡터 계산 | 대표 텍스트(상품명 반복)를 Gemini로 임베딩 | 로그 상품의 `ProductDocument.vector`를 행동×수량×시간 가중치로 **가중 평균** 후 L2 정규화. Gemini 호출 없음 |
| 검색어 반영 | 검색어 텍스트를 대표 텍스트에 포함 | 검색 시점의 상위 결과 상품 ID 최대 3개를 검색 로그에 저장(`topProductIds`)하고 그 상품 벡터를 검색 가중치로 반영 |
| 가중치 0 행동(삭제·취소) | 반복 1회로 포함, 경험 상품에도 포함 | 평균·경험 상품 모두에서 제외 |
| 선호 카테고리·카테고리 일치 보너스 | 계산·저장하고 개인화 후보 검색에 +0.3 | **삭제**(개인화 경로). 유사 상품 경로는 `vector-similarity` 라운드에서 결정 |
| 프로필 문서 필드 | userId·벡터·lastUpdatedAt·source*LogIds 3종·sourceProductIds·preferredCategoryCode | userId·벡터·lastUpdatedAt·sourceProductIds. 인덱스 재생성 |
| 갱신 빈도 제한 | 없음 | 두지 않음(Gemini가 빠져 필요 없어짐) |
| 값(가중치·30일·5개·최소 로그 3개) | — | 전부 현행 유지 |

## 관련 문서

- [`vector-similarity-search.md`](../vector-similarity/vector-similarity-search.md) — 이 단위가 쓰는 벡터 검색·다양화 규칙
- [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) — 검색 로그가 저장되는 검색 API
- [`decisions/001-autocomplete-caching.md`](../../decisions/001-autocomplete-caching.md) — 추천 결과를 캐싱하지 않는 결정
- [`decisions/003-gemini-migration.md`](../../decisions/003-gemini-migration.md) — 임베딩 모델·1536차원 고정
- [`EMBEDDING_EXAMPLE.md`](../../EMBEDDING_EXAMPLE.md) — 대표 텍스트·벡터 생성 예시
- [`product/glossary.md`](../../product/glossary.md) — 용어

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | `embedding/profile-and-product-embedding.md`·`event-log/event-driven-log-pipeline.md`를 작업 단위 "취향 벡터"로 통합. 상품 임베딩은 `vector-similarity`로 이동 |
| 2026-09-04 | 타 모듈 발행부(`baro-shopping` 장바구니·`baro-order` 주문 확정)와 게이트웨이 전제를 전수 조사해 발행 측 규칙·리스크 추가 |
| 2026-09-05 | "승인된 재설계" 절 추가 — 프로필 벡터를 상품 벡터 가중 평균으로 계산, 선호 카테고리·보너스 삭제 (감사 001 승인 결과) |
