# 상품 · 사용자 프로필 임베딩

> 상태: Implemented
> MVP: In scope
> 관련 BR: N/A

## 목표

검색·추천의 기반이 되는 벡터를 만든다 — 상품명은 그대로, 사용자는 최근 행동 로그를 가중치로 합성한 대표 텍스트로 임베딩한다.

## 배경

`search`·`recommend` 도메인이 이 벡터에 의존한다. 특히 사용자 프로필 임베딩은 단순 평균이 아니라 **이벤트 타입·수량·경과 시간**을 모두 반영한 가중치 텍스트 생성이 핵심 로직이다.

## 요구사항

### Must Have (구현 완료)

- [x] 상품 임베딩 — 상품명으로 Gemini `gemini-embedding-001` 임베딩 생성(출력 차원 1536 고정)
- [x] 사용자 프로필 임베딩 — 최근 30일, 로그 타입별(검색/장바구니/주문) 최대 5개
- [x] 최소 로그 3개 미만이면 임베딩 생성 스킵
- [x] 이벤트 가중치 — 장바구니: 추가 2 / 수량변경 1 / 삭제 0, 주문: 확정 3 / 취소 0
- [x] 시간 가중치 — 지수감쇠(168시간=7일 기준 반감), 0.3~3.0배로 clamp
- [x] 수량 가중치 — `max(1, quantity)`
- [x] 선호 카테고리 코드 계산 — 카테고리별 가중치 합산 후 최댓값
- [x] 재현성 — 임베딩 생성에 쓰인 로그 ID들(`sourceSearchLogIds` 등)과 `sourceProductIds`(제외 목록용)를 함께 저장

### Out of Scope (이번 스펙에서 하지 않음)

- 임베딩 모델 버전 관리/재생성 트리거
- 리뷰 텍스트 기반 임베딩 (review 도메인 소관)

## 데이터 모델

- ES 인덱스 `user_profile_embeddings` — `UserProfileEmbeddingDocument`(userId, userProfileVector, lastUpdatedAt, sourceSearchLogIds, sourceCartLogIds, sourceOrderLogIds, sourceProductIds, preferredCategoryCode)
- 상세 매핑: [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md) · 예시: [`EMBEDDING_EXAMPLE.md`](../../EMBEDDING_EXAMPLE.md)

## 비즈니스 규칙

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| 조회 기간 | 최근 30일 | `UserProfileEmbeddingService.updateUserProfileEmbedding` |
| 타입별 최대 로그 수 | 5개 | 동일 |
| 최소 임베딩 조건 | 총 로그 3개 이상 | `MIN_TOTAL_LOGS_FOR_EMBEDDING` |
| 시간 가중치 | `max(0.3, min(3.0, 3.0 * e^(-hoursAgo/168)))` | `calculateTimeWeight` |
| 트리거 지점 | 검색 API 호출 시, cart/order Kafka 이벤트 수신 시 — 전부 `@Async("profileUpdateExecutor")`로 비동기 | `ProductIndexService`·`CartEventConsumer`·`OrderEventConsumer` |

## 검증 시나리오

### 정상

- [x] 로그 3개 이상 → 벡터·선호 카테고리 갱신

### 엣지 · 실패

- [x] 로그 3개 미만 → 스킵(로그만 남기고 저장 안 함)
- [x] 임베딩 API 실패 → 예외를 삼키고 스킵(호출부는 이벤트 처리 실패로 취급하지 않음)
- [x] 대표 텍스트가 빈 문자열 → 스킵

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — **자동화 테스트 없음**

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 자동화 테스트 0개 | 확정 | 가중치 계산(`calculateTimeWeight` 등)은 순수 함수라 단위 테스트 우선순위가 높음 |
| 임베딩 실패가 조용히 스킵됨 | 확정 | 사용자·운영자에게 실패 신호가 남지 않음 — 모니터링/알림 없음 |
| 임베딩 모델 변경 시 벡터 차원 불일치 방어 없음 | [미정] | 현재는 `dimensions: 1536` 설정으로 맞춘다 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
