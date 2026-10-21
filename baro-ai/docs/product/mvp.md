# MVP 범위 — baro-ai

baro-ai가 지금 무엇을 제공하고, 무엇을 아직 제공하지 않는지의 SSOT. `harness-map.md`의 `{{제품 범위}}` 슬롯이 가리키는 문서다. 새 기능을 시작하기 전에 이미 In Scope인지, 아직 손대지 않은 영역인지 여기서 먼저 확인한다. 범위는 2026-09-04부터 작업 단위 4개로 나눠 적는다.

## 언제 이 문서를 보는가

- 새 기능이 baro-ai MVP 범위에 들어가는지 판단할 때
- "이거 이미 되는 거 아니야?"를 코드 확인 전에 먼저 걸러낼 때

## In Scope (구현 완료)

| 작업 단위 | 제공하는 것 | 스펙 |
|-----------|-------------|------|
| 취향 벡터 | 장바구니·주문 로그 수집, 프로필 벡터 생성, 개인화 추천 API | [`taste-vector-pipeline.md`](../specs/taste-vector/taste-vector-pipeline.md) |
| 장바구니 레시피 | 장바구니 기반 레시피 1개 + 부족 재료 상품 추천 API | [`recipe-recommendation.md`](../specs/cart-recipe/recipe-recommendation.md) |
| 임베딩 유사도 검색 | 상품 임베딩, 벡터 유사도 검색, 다양화, 유사 상품 추천 API | [`vector-similarity-search.md`](../specs/vector-similarity/vector-similarity-search.md) |
| 키워드 검색 | 상품 이벤트 색인, 키워드 검색 API, 자동완성 API, **전체 재색인 관리 API**(2026-09-09) | [`product-search-and-autocomplete.md`](../specs/keyword-search/product-search-and-autocomplete.md) |
| review (팀원 소유) | 리뷰 감정분석·베스트 리뷰·요약 | [`REVIEW_ANALYSIS_PHASES.md`](../REVIEW_ANALYSIS_PHASES.md)(Phase 0·1 완료, 스펙은 아직 `docs/specs/`에 없음) |

## Out of Scope / 미정

| 기능 | 상태 | 비고 |
|------|------|------|
| 챗봇 | 미착수(TBD) | [`chatbot.md`](../specs/chat/chatbot.md) — 목적조차 미정 |
| 검색 결과 정렬 옵션(가격순 등) | Out of scope | 현재 관련도순 고정 |
| 추천·검색 결과 캐싱(Redis 등) | Out of scope | 자동완성만 로컬 캐시. 근거: [`decisions/001`](../decisions/001-autocomplete-caching.md). Redis는 baro-ai에 없다 |
| 주문 취소 이벤트 반영 | 미정(팀 결정) | 발행부가 없어 컨슈머 분기가 도달 불가 — [`taste-vector` 리스크](../specs/taste-vector/taste-vector-pipeline.md) |
| 리뷰 Aspect별 분석 | Out of scope | REVIEW_ANALYSIS_PHASES.md Phase 3 |

## 이 문서에 없는 것

우선순위(Milestone) 관리는 별도 문서가 없다 — `{{우선순위 SSOT}}` 슬롯은 `harness-map.md`에 `(없음)`으로 명시돼 있다. GitHub 이슈/라벨로 판단한다.

## 관련 문서

- [`docs/features/`](../features/README.md) — 작업 단위별 기능·기술 설명
- [`docs/specs/README.md`](../specs/README.md) — 작업 단위 ↔ 코드 ↔ 외부 모듈 매핑

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 코드베이스 기준으로 In/Out 정리 |
| 2026-09-04 | 작업 단위 4개 기준으로 재편, Redis 캐싱·재색인·주문 취소 항목 추가 |
