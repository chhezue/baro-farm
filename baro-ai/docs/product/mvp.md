# MVP 범위 — baro-ai

baro-ai가 지금 무엇을 제공하고, 무엇을 아직 제공하지 않는지의 SSOT. `harness-map.md`의 `{{제품 범위}}` 슬롯이 가리키는 문서다. 새 기능을 시작하기 전에 이미 In Scope인지, 아직 손대지 않은 영역인지 여기서 먼저 확인한다.

## 언제 이 문서를 보는가

- 새 기능이 baro-ai MVP 범위에 들어가는지 판단할 때
- "이거 이미 되는 거 아니야?"를 코드 확인 전에 먼저 걸러낼 때

## In Scope (구현 완료)

| 기능 | 도메인 | 스펙 |
|------|--------|------|
| 상품 키워드 검색·자동완성 | search | [product-search-and-autocomplete.md](../specs/search/product-search-and-autocomplete.md) |
| 개인화 추천 · 유사 상품 추천 | recommend | [vector-recommendation.md](../specs/recommend/vector-recommendation.md) |
| 장바구니 기반 레시피 추천 | recommend | [recipe-recommendation.md](../specs/recommend/recipe-recommendation.md) |
| 상품·사용자 프로필 임베딩 | embedding | [profile-and-product-embedding.md](../specs/embedding/profile-and-product-embedding.md) |
| Kafka 이벤트 기반 로그 적재 | event-log | [event-driven-log-pipeline.md](../specs/event-log/event-driven-log-pipeline.md) |
| 리뷰 감정분석 · 베스트 리뷰 · 요약 | review | [REVIEW_ANALYSIS_PHASES.md](../REVIEW_ANALYSIS_PHASES.md)(Phase 0·1 완료 — 팀원 소유 도메인, 스펙 문서는 아직 `docs/specs/`로 옮기지 않음) |

## Out of Scope / 미정

| 기능 | 상태 | 비고 |
|------|------|------|
| 챗봇 | 미착수(TBD) | [chatbot.md](../specs/chat/chatbot.md) — 목적조차 미정 |
| 검색 결과 정렬 옵션(가격순 등) | Out of scope | 현재 관련도순 고정 |
| 리뷰 Aspect별(배송/품질/가격) 분석 | Out of scope | REVIEW_ANALYSIS_PHASES.md Phase 3 |

## 이 문서에 없는 것

우선순위(Milestone) 관리는 별도 문서가 없다 — `{{우선순위 SSOT}}` 슬롯은 `harness-map.md`에 `(없음)`으로 명시돼 있다. GitHub 이슈/라벨로 판단한다.

## 관련 문서

- [`docs/FEATURES.md`](../FEATURES.md) — 기능별 상세 설명(이 문서보다 옛날부터 있던 전체 기능 문서, In/Out 판단의 상세 근거)
- [`docs/specs/README.md`](../specs/README.md) — 도메인 폴더 ↔ 코드 패키지 매핑

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 코드베이스 기준으로 In/Out 정리 |
