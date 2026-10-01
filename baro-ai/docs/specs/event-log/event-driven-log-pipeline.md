# 이벤트 기반 로그 파이프라인

> 상태: Implemented
> MVP: In scope
> 관련 BR: N/A

## 목표

다른 모듈(주문·장바구니·상품)이 발행하는 Kafka 이벤트를 받아 행동 로그로 적재하고, 개인화 추천이 최신 상태를 유지하도록 프로필 임베딩 재계산을 연쇄적으로 트리거한다.

## 배경

baro-ai는 검색/장바구니/주문 로그의 **원본 저장소**다. 이 파이프라인이 멈추면 로그만 끊기는 게 아니라 `embedding`(프로필 갱신)·`search`(상품 인덱스)까지 연쇄로 stale해진다.

## 요구사항

### Must Have (구현 완료)

- [x] `CartEventConsumer` — 토픽 `cart-events`, 그룹 `ai-service-cart`: `CART_ITEM_ADDED`/`CART_ITEM_REMOVED`/`CART_QUANTITY_UPDATED` → 로그 저장 + 프로필 비동기 갱신
- [x] `OrderEventConsumer` — 토픽 `order-events`, 그룹 `ai-service-order`: `ORDER_CONFIRMED`/`ORDER_CANCELLED` → 상품별 로그 저장 + 프로필 비동기 갱신
- [x] `ProductEventConsumer` — 토픽 `product-events`, 그룹 `search-service`: `PRODUCT_CREATED`/`UPDATED`/`DELETED` → 인덱싱/삭제
- [x] `ReviewConsumer` — 토픽 `review-events`, 그룹 `ai-service-review`: 리뷰 CRUD → `review` 도메인으로 위임(이 스펙은 소비 지점만 다룸, 처리 로직은 review 도메인 소관)
- [x] `LogWriteService` — 검색/장바구니/주문 로그를 각각 ES에 저장
- [x] 처리 중 예외 발생 시 **rethrow** — Kafka consumer가 재시도하도록 함

### Out of Scope (이번 스펙에서 하지 않음)

- 재시도 상한·DLQ (아래 리스크 참고 — 실제로는 아직 없음)

## 데이터 모델

- `SearchLogDocument`, `CartLogDocument`, `OrderLogDocument` — 전부 ES, userId/productId/eventType/quantity/occurredAt 등

## 비즈니스 규칙

| 규칙 | 값 |
|------|-----|
| 프로필 갱신 트리거 | cart/order 이벤트 처리 성공 시마다 `@Async("profileUpdateExecutor")`로 비동기 실행 |
| 프로필 갱신 실패 | 이벤트 처리 자체는 실패로 취급하지 않음(로그만 warn) — 원본 이벤트 처리와 분리 |
| consumer 처리 실패 | 예외를 삼키지 않고 다시 던져 Kafka 재시도 유도 |

## 검증 시나리오

### 정상

- [x] cart/order/product 이벤트 각각 정상 처리 → 로그 저장 + (cart/order는) 프로필 재계산 트리거

### 엣지 · 실패

- [x] 알 수 없는 이벤트 타입 → warn 로그만 남기고 무시(재던지지 않음)
- [x] 로그 저장 자체가 실패 → 예외 rethrow → Kafka 재시도
- [x] 프로필 재계산 실패 → 이벤트 처리 결과에는 영향 없음(비동기·별도 try-catch)

## 완료 기준

- [x] 기능 구현 및 수동 확인(로그 메시지로 확인)
- [ ] `./gradlew :baro-ai:test` — **자동화 테스트 없음**

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| **DLQ/재시도 상한 없음** | 확정(기존 TODO) | [`KafkaConsumerConfig.java:27`](../../../src/main/java/com/barofarm/ai/config/KafkaConsumerConfig.java)에 이미 남은 TODO. 처리 실패 메시지가 무한 재시도되거나 consumer가 멈춰 뒤 메시지까지 밀릴 위험 |
| 자동화 테스트 0개 | 확정 | consumer 3개·`LogWriteService` 전부 수동 로그로만 검증 |
| consumer마다 실패 처리 방식이 다름 | 확정 | 검색 로그 저장 실패는 catch 후 무시, cart/order/product는 rethrow — 일관성 없음. 의도된 차이인지 확인 필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
