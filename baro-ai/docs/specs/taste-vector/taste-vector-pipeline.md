# 취향 벡터 파이프라인

행동 로그 수집 · 프로필 임베딩 · 개인화 추천을 다룬다. 사용자의 검색·장바구니·주문 행동을 로그로 모아 하나의 벡터(프로필 벡터)로 압축하고, 그 벡터와 가까운 상품을 추천하는 작업 단위의 스펙이다. 이벤트를 **발행하는 다른 모듈**(`baro-shopping`·`baro-order`)의 규칙까지 포함해, 이 기능이 실제로 어떤 조건에서 어떤 값을 받아 동작하는지를 코드 기준으로 적었다. 감사([`audits/taste-vector/`](../../audits/taste-vector/))·리팩터·포트폴리오가 이 문서를 계약값의 근거로 삼는다.

> 상태: Implemented (2026-09-05 프로필 계산 재설계 반영, 타 모듈 발행부 포함)
> MVP: In scope
> 관련 BR: N/A (비즈니스 규칙은 아래 표가 유일한 표현)

## 언제 이 문서를 보는가

- 개인화 추천이 "왜 이 상품을 추천했는지"를 로그·가중치 수준에서 설명해야 할 때
- cart/order 이벤트의 필드 의미(특히 `quantity`)와 발행 조건을 확인할 때
- 프로필 벡터 갱신 시점·비용을 바꾸는 감사·리팩터 전에 현재 계약을 확인할 때

## 목표

최근 30일의 행동 로그를 행동 종류·수량·경과 시간으로 가중해 사용자 취향을 벡터 하나로 표현하고, 매 요청마다 상품 벡터와 비교해 다양성 있는 Top-K 상품을 돌려준다.

## 배경

검색 로그는 baro-ai 자신이 검색 API에서 직접 저장하고, 장바구니·주문 로그는 다른 모듈이 `baro-common`의 `@TrackHistory` AOP로 발행한 Kafka 이벤트를 받아 저장한다. 로그 저장은 동기, 프로필 벡터 재계산은 비동기라는 최종 일관성 설계를 택했다. 프로필 벡터는 외부 임베딩 API를 부르지 않고 상품이 이미 가진 벡터를 섞어 만든다(2026-09-05 재설계). 상품 쪽 벡터와 벡터 검색 자체는 [`vector-similarity`](../vector-similarity/vector-similarity-search.md)가 담당하고, 이 문서는 사용자 쪽 벡터와 그것을 쓰는 개인화 추천만 다룬다.

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 행동 로그 | 검색어·장바구니 변경·주문 확정 1건을 ES 문서로 저장한 것 | [`glossary.md`](../../product/glossary.md) |
| 이벤트 봉투 | `baro-common`이 Kafka로 보내는 `{event, ts, userId, payload}` 구조 | `HistoryEnvelope` |
| 상위 결과 상품 | 검색 1건에서 상위에 나온 상품 ID(최대 3개). 검색어 대신 이 상품들의 벡터로 취향을 읽는다 | `SearchLogDocument.topProductIds` |
| 행동 비중 | 로그 1건이 취향에 기여하는 정도. `행동 종류 × 수량 × 경과 시간`(값은 아래 비즈니스 규칙) | `behaviorWeight` |
| 프로필 벡터(취향 벡터) | 로그에 등장한 상품들의 벡터를 행동 비중대로 섞어 길이를 1로 맞춘 벡터(차원은 데이터 모델 표) | `UserProfileEmbeddingDocument.userProfileVector` |
| 갱신 스킵 게이트 | 조회 기간 안의 로그가 최소 개수에 못 미치면 프로필을 만들지 않는 조건(값은 아래 비즈니스 규칙) | `MIN_TOTAL_LOGS_FOR_EMBEDDING` |
| 경험 상품 | 최근 장바구니·주문 로그 중 **비중이 0이 아닌** 로그의 상품. 추천에서 제외 | `sourceProductIds` |

## 흐름

발행 모듈부터 응답까지 한 줄에 한 단계씩 적었다. 코드 위치는 이 저장소 기준이다.

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 장바구니 이벤트 발행 | `baro-shopping` `CartService.addItem`·`updateQuantity`·`removeItem` | `@TrackHistory`가 붙은 세 메서드만 `HistoryAspect`를 거쳐 `cart-events`로 발행. 값은 이벤트 봉투 JSON **문자열**, 키는 `userId` | `baro-shopping/.../cart/application/CartService.java:57,78,111`, `baro-common/.../HistoryLogWriter.java:78` |
| 2. 주문 이벤트 발행 | `baro-order` `InventoryConfirmedConsumer.handle` | 재고 확정 이벤트(`inventory-confirmed`)를 받아 주문을 `CONFIRMED`로 바꾸는 메서드에만 `@TrackHistory(ORDER_CONFIRMED)`가 있다. `userId`는 HTTP 헤더가 아니라 주문 엔티티에서 매퍼가 조회 | `baro-order/.../InventoryConfirmedConsumer.java:47`, `OrderConfirmedHistoryPayloadMapper.java:41-47` |
| 3. 검색 로그 저장 | baro-ai `ProductSearchService.search` | `X-User-Id`와 검색어가 둘 다 있을 때만 `search_logs`에 저장한다. 상위 결과 상품 ID 최대 3개(`topProductIds`)를 함께 남기고, `UserProfileRefreshTrigger`로 프로필 갱신을 넘긴다 | [`ProductSearchService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductSearchService.java) |
| 4. 이벤트 소비 | `CartEventConsumer`·`OrderEventConsumer` | 그룹 `ai-service-cart`·`ai-service-order`, `auto-offset-reset=earliest`. 발행 측 enum을 그대로 `cart_event_logs`·`order_event_logs`에 저장하고 `UserProfileRefreshTrigger`를 호출. 문서 ID는 `{파티션}-{오프셋}`(주문은 `-{항목 순번}` 추가) | [`CartEventConsumer.java`](../../../src/main/java/com/barofarm/ai/event/consumer/CartEventConsumer.java), [`OrderEventConsumer.java`](../../../src/main/java/com/barofarm/ai/event/consumer/OrderEventConsumer.java) |
| 5. 프로필 갱신 예약 | `UserProfileRefreshTrigger.refresh` | 세 경로가 모두 거치는 유일한 `@Async` 진입점. 실패·거절을 삼켜 호출한 쪽에 영향을 주지 않는다 | [`UserProfileRefreshTrigger.java`](../../../src/main/java/com/barofarm/ai/embedding/application/UserProfileRefreshTrigger.java) |
| 6. 프로필 벡터 갱신 | `UserProfileEmbeddingService.updateUserProfileEmbedding` | 30일 내 로그를 종류별 최신 5개씩 조회 → 게이트 → 로그별 비중 계산(0은 제외) → 상품 벡터 일괄 조회 → 가중 평균 + L2 정규화 → `user_profile_embeddings` 저장(문서 ID = userId, 덮어쓰기) | [`UserProfileEmbeddingService.java`](../../../src/main/java/com/barofarm/ai/embedding/application/UserProfileEmbeddingService.java) |
| 7. 개인화 추천 | `PersonalizedRecommendService.recommendProducts` | 프로필 벡터 조회 → 경험 상품 제외로 후보 `min(topK×3, 100)`개 벡터 검색 → 다양화 → 응답. 카테고리 보너스는 주지 않는다 | [`PersonalizedRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/PersonalizedRecommendService.java) |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/personalized/{userId}` | 없음(경로의 userId를 신뢰) | `topK` 쿼리, 기본 5. 프로필이 없으면 빈 배열 |

응답은 `ResponseDto<ProductRecommendResponse[]>`(data에 productId·productName·productCategoryName·price)다. 2026-09-05에 검색 API와 envelope를 통일해, 성공·실패 응답 모양이 같아졌다.

성공:

```json
[{ "productId": "…", "productName": "햇사과 1kg", "productCategoryName": "제철 과일", "price": 23000 }]
```

## 데이터 모델

매핑 SSOT는 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)다. 이 단위가 쓰는 인덱스만 요약한다.

| 인덱스 | 문서 | 핵심 필드 | 쓰는 곳 |
|--------|------|-----------|---------|
| `search_logs` | `SearchLogDocument` | userId, searchQuery, **topProductIds(최대 3)**, searchedAt | 검색 API가 쓰고 6단계가 읽음 |
| `cart_event_logs` | `CartLogDocument` | id(`{파티션}-{오프셋}`), userId, productId, productName, categoryCode, eventType(`HistoryEventType` enum), quantity, occurredAt | 4단계가 쓰고 6단계가 읽음 |
| `order_event_logs` | `OrderLogDocument` | 같은 구조, id는 `{파티션}-{오프셋}-{항목 순번}` | 동일 |
| `user_profile_embeddings` | `UserProfileEmbeddingDocument` | userId(ID), userProfileVector(1536), lastUpdatedAt, sourceProductIds | 6단계가 쓰고 7단계가 읽음 |
| `product` | `ProductDocument` | vector(1536) — 프로필 계산의 재료 | [`vector-similarity`](../vector-similarity/vector-similarity-search.md)가 쓰고 6단계가 읽음 |

`eventType`은 발행 측(`baro-common`)의 `HistoryEventType`을 그대로 저장한다. 문자열로 옮겨 적지 않으므로 계산 쪽 비교값과 어긋날 수 없고, 발행 측에 종류가 추가되면 컴파일이 실패해 알아차리게 된다.

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
| 조회 기간 | 최근 30일 | `LOOKBACK_DAYS` |
| 종류별 로그 상한 | 검색·장바구니·주문 각 최신 5개 | `RECENT_LOG_LIMIT` (`PageRequest.of(0, 5)`) |
| 갱신 스킵 게이트 | 세 종류 합계가 3개 미만이면 저장하지 않음 | `MIN_TOTAL_LOGS_FOR_EMBEDDING` |
| 행동 가중치 | 주문 CONFIRMED 3 · 장바구니 ADD 2 · UPDATE 1 · 검색 1 / 삭제 REMOVE·취소 CANCELLED 0 | `calculateEventWeight`, `SEARCH_EVENT_WEIGHT` |
| 수량 가중치 | `min(max(1, quantity), 5)` — 5를 넘는 수량은 5로 본다. 상한이 없으면 수량이 큰 로그 하나가 가중 평균을 거의 혼자 결정한다 | `quantityWeight`, `MAX_QUANTITY_WEIGHT` |
| 시간 가중치 | `clamp(3.0 × 2^(−경과시간/168), 0.3, 3.0)`. 7일(168시간)마다 절반. 경과 시간은 시간 단위로 세므로 1시간 안의 행동은 값이 같다 | `calculateTimeWeight` |
| 행동 비중 | 로그 1건이 취향에 기여하는 정도. `행동 가중치 × 수량 가중치 × 시간 가중치`(반올림 없음). 검색은 `1.0 × 시간 가중치` | `behaviorWeight` |
| 비중 0인 로그 | 가중 평균과 경험 상품 **양쪽 모두에서 제외**. 삭제·취소한 상품은 관심이 없어진 것이므로 다시 추천에 나와도 된다 | `WeightedProduct.isUsable` |
| 검색 반영 방식 | 검색어를 임베딩하지 않는다. 검색 시점의 상위 결과 상품 최대 3개에 각각 검색 비중을 준다. 결과 0건인 검색은 기여하지 않는다 | `SEARCH_LOG_TOP_PRODUCT_LIMIT`, `topProductIds` |
| 프로필 벡터 계산 | 로그 상품의 `ProductDocument.vector`를 비중대로 더한 뒤 **L2 정규화**. 비중 합으로 나누는 단계는 정규화가 흡수하므로 코드에 없다 | `blendIntoProfileVector` |
| 벡터 없는 상품 | 아직 색인되지 않았거나 임베딩이 실패한 상품은 조용히 건너뛴다. 쓸 수 있는 벡터가 하나도 없으면 프로필을 저장하지 않는다 | `loadProductVectors` |
| 외부 임베딩 호출 | **없음.** 프로필 갱신 1회에 Gemini 호출 0회, ES 조회만 남는다 | — |
| 경험 상품 | 비중이 0이 아닌 장바구니·주문 로그의 productId(중복 제거) | `sourceProductIds` |
| 갱신 트리거 | 검색 1회·장바구니 이벤트 1건·주문 이벤트 1건마다 재계산. 빈도 제한은 두지 않는다 — 가장 비쌌던 Gemini 호출이 빠져 제한할 이유가 없어졌다 | 3·4단계 |
| 비동기 실행 | `profileUpdateExecutor`(core 2 · max 5 · queue 100). 세 경로 모두 `UserProfileRefreshTrigger`라는 **다른 빈**을 거치므로 실제로 비동기다 | `AsyncConfig`, `UserProfileRefreshTrigger` |
| 대기열 초과 처리 | 큐가 가득 차면 그 갱신을 버리고 WARN만 남긴다. 호출한 쪽(이벤트 처리·검색)은 영향을 받지 않는다 | `AsyncConfig`의 거절 핸들러 |
| 추천 후보 규모 · 상태 필터 | [`vector-similarity`](../vector-similarity/vector-similarity-search.md) 비즈니스 규칙과 같다(후보 규모·상태 필터는 그쪽이 SSOT) | `PersonalizedRecommendService` |
| 카테고리 일치 보너스 | **없다.** 취향 벡터가 이미 선호 카테고리 쪽으로 기울어 있어 정보가 중복되고, 후보가 한 카테고리로 쏠리면 다양화가 그 안에서만 고르게 된다. 유사 상품 추천 경로의 보너스도 2026-09-05에 함께 삭제됐다 | `VectorRecommendPipeline` |
| 다양화 | max-min 다양화를 적용한다. 값·동작은 `vector-similarity` 비즈니스 규칙이 SSOT | `MaxMinDiversityService` |
| 컨슈머 실패 처리 | 로그 저장 예외는 다시 던지고, 1초 간격 2회 재시도 뒤 건너뛴다. 값을 이벤트로 못 바꾸는 레코드는 재시도 없이 한 번 기록하고 건너뛴다(`ErrorHandlingDeserializer`) | `KafkaConsumerConfig` cart·order 팩토리 |
| 재시도의 안전성 | 로그 문서 ID가 `{파티션}-{오프셋}`이라 같은 레코드를 다시 처리해도 같은 문서를 덮어쓴다. 재시도가 로그를 중복 저장하지 않는다 | `CartLogDocument.id`, `OrderLogDocument.id` |
| 프로필 갱신 실패 | 예외를 삼키고 WARN. 이벤트 처리·검색 결과에 영향 없음 | `UserProfileRefreshTrigger.refresh` |

## 검증 시나리오

### 정상

- [x] 검색 1회 + 담기 1회 + 주문 확정 1회 → 로그 3건 → 프로필 저장 → 추천 응답에 경험 상품이 없고 `topK`개 이하
- [x] 같은 사용자가 다시 검색 → 프로필이 최신 로그로 덮어쓰기

### 엣지 · 실패

- [x] 로그 2개 이하 → 프로필 없음 → 추천 빈 배열
- [x] 프로필은 있는데 후보 상품이 0개(전부 품절) → 빈 배열
- [ ] 장바구니 삭제 이벤트만 있는 사용자 → 비중이 전부 0이라 프로필을 저장하지 않는다(추천은 빈 배열)
- [ ] 로그의 상품이 모두 아직 색인되지 않아 벡터가 없는 경우 → 프로필을 저장하지 않는다
- [ ] 결과 0건인 검색만 있는 사용자 → 그 검색은 취향에 기여하지 않는다

### 수동 확인 (로컬 — ES·Kafka 기동 필요)

2026-09-05 재설계 시점에는 로컬 인프라를 띄우지 못해 **실행하지 못한 절차**다. 인프라가 준비되면 이 순서로 확인한다.

- [ ] 이벤트 1건 발행 후 프로필 갱신 INFO가 찍힌 스레드 이름이 `profile-update-*`인지(리스너 스레드 `KafkaListenerEndpointContainer#…`가 아니어야 한다)
- [ ] 검색 5회 연속 호출 시 `org.springframework.ai` DEBUG에 임베딩 요청이 **0회**인지(재설계 전에는 5회)
- [ ] `GET user_profile_embeddings/_doc/{userId}`의 `userProfileVector` 길이(제곱합의 제곱근)가 1.0 ± 1e-6인지
- [ ] 사과만 담은 사용자의 프로필과 `product` 인덱스의 사과 문서 벡터의 코사인 유사도가 0.99 이상인지
- [ ] `cart-events`에 `{not json` 1줄을 넣고 곧이어 정상 이벤트 1건 → `DeserializationException` ERROR 1회 뒤 정상 이벤트가 처리되는지
- [ ] ES를 잠시 멈추고 이벤트 1건 → 실패 ERROR가 1초 간격 3줄인지(재설계 전에는 같은 초에 10줄)
- [ ] `GET cart_event_logs/_search`의 `_id`가 `{파티션}-{오프셋}` 형식이고, `eventType`이 `CART_ITEM_ADDED` 같은 enum 이름 문자열로 저장되는지
- [ ] `topProductIds`·`sourceProductIds`가 UUID 문자열 배열로 저장되고 다시 읽을 때 `List<UUID>`로 변환되는지(둘 다 이번에 타입이 바뀐 필드다)

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — 자동화 테스트 없음(사용자 결정으로 코드 변경 완료 후 도입)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `ORDER_CANCELLED` 이벤트가 발행되지 않음 | 확정 | `OrderCancelledHistoryPayloadMapper`는 있으나 `@TrackHistory(ORDER_CANCELLED)`를 붙인 곳이 없다. 컨슈머·가중치 표의 분기는 유지하기로 했다(2026-09-05 결정). 붙일지 여부는 **팀 결정** `[미정]` |
| `quantity` 의미가 이벤트마다 다름 | 확정(완화) | ADD는 증분, UPDATE는 새 총량, REMOVE는 전량인데 같은 식을 쓴다. 2026-09-05에 상한 5를 도입해 "3개 더 담음"과 "총 10개로 바꿈"의 차이가 크게 벌어지지 않게 했다. 이벤트별로 식을 나눌지는 `[미정]` |
| 같은 사용자의 갱신이 동시에 돌 수 있음 | 확정(설계) | 컨슈머 경로가 실제 비동기가 되면서 같은 userId의 재계산 두 개가 겹칠 수 있다. 나중에 시작한 쪽이 먼저 끝나면 한 번 오래된 벡터가 남지만, 다음 행동에서 다시 계산돼 스스로 회복한다. 외부 API 호출이 없어져 갱신 자체가 짧아진 만큼 겹칠 확률도 낮다 |
| 검색 상위 결과가 취향을 정한다 | 확정(설계) | 검색어 자체가 아니라 그때 검색 결과 상위 3개가 취향에 들어간다. 검색 품질이 나쁘면 엉뚱한 상품이 취향에 섞인다. 반대로 결과가 0건이면 아무 영향이 없다 |
| 게스트 행동이 취향 벡터에 없음 | 확정(설계) | 로그인 후 병합 시에도 ADD 이벤트가 없어 이전 담기 이력은 영구 소실 |
| `X-User-Id`를 게이트웨이가 만들지 않음(prod) | 확정 | prod 게이트웨이의 `/ai-service/**` 라우트에 `AuthenticationFilter`가 없어 클라이언트가 보낸 헤더가 그대로 전달된다. 타인의 userId로 추천 조회·검색 로그 오염 가능. 게이트웨이 소유자 확인 `[미정]` |
| 발행이 `@Transactional` 커밋 전인지 후인지 | 불확실 | `HistoryAspect`와 `@Transactional`의 어드바이저 순서가 지정돼 있지 않다. baro-order 소유자 확인 `[미정]` |
| 자동화 테스트 0개 | 확정 | 비중 계산(`behaviorWeight`·`calculateTimeWeight`)과 가중 평균(`blendIntoProfileVector`)은 외부 호출이 없는 순수 계산이라, 테스트를 들이면 첫 대상이 된다 |

## 재설계 반영 (2026-09-05)

위 흐름·데이터 모델·비즈니스 규칙은 아래 변경이 **반영된 뒤**의 코드 기준이다. 감사 [`audits/taste-vector/001`](../../audits/taste-vector/001-profile-refresh-path.md) "승인 결과" 절에서 확정한 내용이며, 실제 반영 이력과 검증 결과는 [`audits/taste-vector/refactor-log.md`](../../audits/taste-vector/refactor-log.md)에 있다.

| 항목 | 이전 | 지금 |
|------|------|------|
| 프로필 벡터 계산 | 대표 텍스트(상품명 반복)를 Gemini로 임베딩 | 로그 상품의 `ProductDocument.vector`를 행동×수량×시간 가중치로 가중 평균 후 L2 정규화. Gemini 호출 없음 |
| 검색어 반영 | 검색어 텍스트를 대표 텍스트에 포함 | 검색 시점의 상위 결과 상품 최대 3개(`topProductIds`)의 벡터를 검색 비중으로 반영 |
| 비중 0 행동(삭제·취소) | 반복 1회로 포함, 경험 상품에도 포함 | 가중 평균·경험 상품 모두에서 제외 |
| 수량 가중치 | `max(1, quantity)` — 상한 없음 | `min(max(1, quantity), 5)` — 수량이 큰 로그 하나가 평균을 독차지하지 못하게 |
| 시간 가중치 | `3.0 × e^(−h/168)` — 주석("7일에 0.5배")과 달리 7일에 0.37배 | `3.0 × 2^(−h/168)` — 주석대로 7일에 정확히 0.5배 |
| 이벤트 종류 저장 | 컨슈머가 `"ADD"` 등 별도 문자열로 옮겨 적음 → 계산 쪽 비교값과 불일치 | 발행 측 `HistoryEventType` enum을 그대로 저장 |
| 선호 카테고리·카테고리 보너스 | 계산·저장하고 개인화 후보 검색에 +0.3 | 개인화 경로에서 삭제(유사 상품 경로도 2026-09-05 `vector-similarity` 라운드에서 삭제) |
| 프로필 문서 필드 | userId·벡터·lastUpdatedAt·source*LogIds 3종·sourceProductIds·preferredCategoryCode | userId·벡터·lastUpdatedAt·sourceProductIds |
| 비동기 실행 | 컨슈머 두 곳은 자기 클래스 호출이라 동기 실행 | 세 경로 모두 `UserProfileRefreshTrigger`를 거쳐 실제 비동기 |
| 컨슈머 내구성 | 역직렬화 보호·재시도 간격 없음, 재시도가 로그 중복 생성 | `ErrorHandlingDeserializer` + 1초 간격 2회 + 결정적 문서 ID |
| 갱신 빈도 제한 | 없음 | 두지 않음(Gemini가 빠져 필요 없어짐) |
| 값(행동 가중치·30일·5개·최소 로그 3개) | — | 전부 이전과 같다 |

**인덱스 재생성이 필요하다.** `user_profile_embeddings`(필드 구성 변경)·`cart_event_logs`·`order_event_logs`(`eventType`이 enum이라 옛 `"ADD"` 값은 읽지 못함)·`search_logs`(`topProductIds` 추가)를 지우고 다시 만든다. 운영 데이터가 없는 dev 전제이며, 삭제는 사람이 직접 실행한다.

## 관련 문서

- [`vector-similarity-search.md`](../vector-similarity/vector-similarity-search.md) — 이 단위가 쓰는 벡터 검색·다양화 규칙
- [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) — 검색 로그가 저장되는 검색 API
- [`decisions/001-autocomplete-caching.md`](../../decisions/001-autocomplete-caching.md) — 추천 결과를 캐싱하지 않는 결정
- [`decisions/003-gemini-migration.md`](../../decisions/003-gemini-migration.md) — 임베딩 모델·1536차원 고정
- [`EMBEDDING_EXAMPLE.md`](../../EMBEDDING_EXAMPLE.md) — 비중 계산·벡터 생성을 실제 숫자로 따라간 예시
- [`product/glossary.md`](../../product/glossary.md) — 용어

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | `embedding/profile-and-product-embedding.md`·`event-log/event-driven-log-pipeline.md`를 작업 단위 "취향 벡터"로 통합. 상품 임베딩은 `vector-similarity`로 이동 |
| 2026-09-04 | 타 모듈 발행부(`baro-shopping` 장바구니·`baro-order` 주문 확정)와 게이트웨이 전제를 전수 조사해 발행 측 규칙·리스크 추가 |
| 2026-09-05 | "승인된 재설계" 절 추가 — 프로필 벡터를 상품 벡터 가중 평균으로 계산, 선호 카테고리·보너스 삭제 (감사 001 승인 결과) |
| 2026-09-05 | 재설계 구현 완료. 흐름·데이터 모델·비즈니스 규칙·리스크 표를 새 동작으로 재작성하고, 해소된 결함 4건(가중치 문자열 불일치·`@Async` 자기 호출·디바운스 없음·삭제 상품 잔존)을 리스크 표에서 제거 |
