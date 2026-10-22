# baro-ai 감사 ③ 목표 구조와 실행 계획

> 3부작 중 3부. [1부 — 복잡도·과잉 설계 진단](001-complexity-and-overengineering.md) ·
> [2부 — 부족한 백엔드 설계 판단](002-missing-backend-decisions.md)에서 이어진다.

> **반영 상태 (2026-09-09):** 이 감사에서 나온 **P0~P3을 review 패키지 몫과 아래 예외를 빼고 전부 반영했다**
> (P0·P1은 2026-09-08, P2·P3은 2026-09-09). 무엇을 어떻게 고쳤는지는 각 작업 단위의 반영 이력에 있다 —
> [`cross-cutting`](../cross-cutting/refactor-log.md) · [`keyword-search`](../keyword-search/refactor-log.md) ·
> [`taste-vector`](../taste-vector/refactor-log.md) · [`vector-similarity`](../vector-similarity/refactor-log.md).
>
> **반영하지 않은 것:** review 패키지 소유 항목(P0-1·3·4·5·6·8, P2-3·4, P3-5)은 팀원 몫이라 손대지 않았다.
> 측정이 필요했던 셋(P2-9·P2-10·P2-11)은 2026-09-09에 실제로 재고 [`004`](004-measured-open-questions.md)에
> 판정을 남겼다 — **P2-9·P2-11은 기각**(P2-11은 감사 쪽 지적이 틀린 것으로 확인됐다), P2-10은 실사용자
> 데이터가 없어 측정 불가.
> P3-2·P3-3은 `java-comments.md`가 "되돌리지 않는다"고 명시한 항목이라 **감사 쪽이 틀렸다**. P3-4도 springdoc 제약으로 불가능하다.
>
> **아래 본문은 반영 전 시점의 진단이므로 현행 코드와 다르게 읽히는 곳이 있다** — 현행 동작의 SSOT는
> `docs/specs/`와 위 반영 이력이다.

---

## 1. Recommended Target Architecture

### 모듈 구조

```
com.barofarm.ai
├── search/          상품 색인 + 키워드 검색 + 자동완성   (인덱스 1개)
├── recommend/       벡터 추천 + 레시피 추천
├── behavior/        행동 로그 수집                        (인덱스 1개)
└── shared/
    ├── llm/         LlmGateway  ← 신설: 모든 LLM 호출의 단일 관문
    └── config/
```

※ `review/`는 팀원 소유이므로 별도 결정 대상.

### ES 인덱스 8 → 4

| 지금 | 이후 |
|---|---|
| `product` + `product-autocomplete` | **`product`** (productName 멀티필드: nori / standard / edge_ngram) |
| `cart_event_logs` + `order_event_logs` | **`user_behavior_logs`** (+ `eventType`) |
| `user_profile_embeddings` | **삭제** (읽기 시점 계산) |
| `review_event` | 유지 |
| `product_best_review_index` | **삭제** (소비처 없음) |
| `product_review_summary` | 유지 (Kafka 발행 원본) |

### 핵심 컴포넌트: `LlmGateway` (신설)

지금 LLM 호출은 3곳에 흩어져 있고, 타임아웃·재시도·관측·주입 방어가 제각각입니다. 하나로 모읍니다.

```
LlmGateway
 ├─ callInteractive(prompt, schema)   예산 3s, 재시도 0, 격벽 5, 서킷브레이커
 │                                     ← 레시피 추천 (요청 경로)
 └─ callBatch(prompt, schema)          예산 30s, 재시도 2(429 제외), rate limit
                                       ← 재료명 정규화, 리뷰 요약 (배치 경로)

 공통: responseSchema 강제 / 사용자 입력 구분자 격리 / 지연·실패·토큰 메트릭
```

**이 클래스 하나가 M1·M6·M7·M10을 동시에 해결하고, 면접에서 설명할 것도 하나로 모입니다.**

### 데이터 흐름 및 동기·비동기 경계

```
동기 경계 ───────────────────────────────────────────
 GET /search                       → ES 1왕복                        p99 목표 50ms
 GET /recommendations/similar      → ES 2왕복                        p99 목표 100ms
 GET /recommendations/personalized → ES 3왕복 (로그+벡터+검색)        p99 목표 150ms
 GET /recommendations/recipes      → Feign 3s + LLM 3s + ES          p99 목표 5s  ← 예산 명시
                                     실패 시 200 + reason

비동기 경계 ─────────────────────────────────────────
 product-events    → [변경 감지] → 임베딩(묶음) + 재료명(변경 시만) → ES bulk
                     실패 → 재시도 2 → DLT + 메트릭
 cart/order-events → user_behavior_logs 저장 (그게 전부)
                     ※ 프로필 갱신 없음 — 읽기 시점 계산
```

### 트랜잭션 / 정합성 경계

- **인덱스 통합으로 이중 쓰기 제거** → "여러 문서를 함께 바꿔야 하는" 경우가 남지 않음
- Kafka at-least-once + 결정론적 문서 ID = 멱등 재처리 (현행 유지)
- 프로필 상태 제거 → lost update 소멸

### 장애 처리

| 의존 | 실패 시 | 사용자에게 |
|---|---|---|
| Gemini (레시피) | 3초 초과 → 즉시 중단 | 200 + `GENERATION_FAILED` |
| Gemini (색인) | 재시도 후 DLT | 재료명 빈 값으로 색인 진행 (현행 유지) |
| Elasticsearch | **예외를 삼키지 말고 5xx** + 메트릭 | 503 |
| baro-shopping | 3초 → `UPSTREAM_ERROR` | 200 + reason (현행 유지) |

> 현행 `VectorProductSearchService`가 ES 예외를 삼키고 빈 배열을 주는 건 바꿔야 합니다.
> **"추천이 없음"과 "검색 엔진이 죽음"이 같은 응답이면 운영이 불가능합니다.**

### 순증감

**사라지는 것**
`AsyncConfig`, `UserProfileRefreshTrigger`, `UserProfileEmbeddingDocument/Repository`,
`ProductAutocompleteDocument/Repository`, `MaxMinDiversityService`,
`CartLogDocument`/`OrderLogDocument` 중 1개 + 레포 1개,
`RecommendProperties` + `recipe-recommend.yml`, `NegativeReviewProperties` + yml,
`ProductSearchResponse`/`ProductRecommendResponse` 중 1개
→ **약 15개 클래스 / 700~900줄**

**생기는 것**
`LlmGateway`(~120줄), DLT 설정(~20줄), 도메인 메트릭(~30줄),
재색인 엔드포인트(~60줄), Testcontainers 통합 테스트 3건(~200줄)
→ **약 430줄**

**순감 400~500줄이면서, 설명 가능한 백엔드 판단은 0개 → 6개가 됩니다.**

---

## 2. Refactoring Priority

### P0 — 반드시 제거 / 수정 (잘못된 설계이거나 동작하지 않음)

| # | 항목 | 근거 | 규모 |
|---|---|---|---|
| P0-1 | **`@EnableScheduling` 누락으로 베스트 리뷰 배치 미동작** | 다른 5개 모듈에는 전부 있음. 이 모듈만 없음 | 1줄 (또는 기능 삭제) |
| P0-2 | **레시피 추천의 LLM 타임아웃 예산 분리** (요청 경로 3s · 재시도 0) | 요청 스레드 최대 93초 점유 | 소 |
| P0-3 | **`POST /reviews/batch/refresh-all` 보호** (인증 + 중복 실행 방지) | 무인증 전 상품 LLM 배치 = 비용 DoS | 소 |
| P0-4 | **`review-events` 컨슈머에 `ErrorHandlingDeserializer` + 에러 핸들러** | 깨진 메시지 1건이 파티션 영구 정지 | 소 · **팀원 확인 필요** |
| P0-5 | **감정 키워드 substring 충돌 제거** (`냄새없`⊃`냄새` 등 3쌍) | 설정 파일에 실재 | 소 · **팀원 확인 필요** |
| P0-6 | **`NegativeReviewProperties` + yml 90줄 삭제** | 읽는 코드 없음 | 소 |
| P0-7 | **`VectorProductSearchService`의 예외 삼킴 제거** | ES 장애가 "추천 0건"으로 위장 | 소 |
| P0-8 | **`ReviewService.deleteReview` 불필요 조회 / `save`·`update` 중복 제거** | 명백한 낭비·중복 | 소 · **팀원 확인 필요** |

### P1 — 포트폴리오를 위해 반드시 개선 (여기가 실제 가치)

| # | 항목 | 얻는 것 | 규모 |
|---|---|---|---|
| P1-1 | **`LlmGateway` 도입** — 예산·격벽·서킷브레이커·rate limit·메트릭·주입 격리 통합 | M1·M6·M7·M10 동시 해결. **면접 서사 1순위** | 중 |
| P1-2 | **취향 벡터를 읽기 시점 계산으로 전환** | 클래스 4개 + lost update + 폐기 정책 + 읽기 지연 소멸 | 중 |
| P1-3 | **`product-autocomplete` 인덱스 통합** | 이중 쓰기 원자성 문제 + 상수 이원화 소멸. 어차피 재색인 대기 중 | 중 |
| P1-4 | **Kafka DLT + 폐기 메트릭** | "조용한 데이터 유실" 제거 | 소~중 |
| P1-5 | **Testcontainers 통합 테스트 3건** (색인→검색 왕복, 묶음 부분 실패, 자동완성) | open-items 수동 확인 26건의 상당수 자동 해소 | 중 |
| P1-6 | **색인 변경 감지** (`productName` 해시) | 가격 변경마다 LLM+임베딩 재호출 제거 → 한도 문제 완화 | 소 |
| P1-7 | **경로 변수 userId 제거, `X-User-Id` 헤더만 신뢰** | IDOR 차단 | 소 |
| P1-8 | **도메인 메트릭 5~6개** (LLM 실패율·지연, 추천 빈 응답률, 색인 지연, 캐시 히트율) | 이미 있는 Prometheus·OTel 자산 활용 | 소 |

### P2 — 있으면 좋은 개선

| # | 항목 |
|---|---|
| P2-1 | `MaxMinDiversityService` → 카테고리 상한 2개로 대체 (클래스 삭제, 후보 배수·topK 33 제약 동반 소멸) |
| P2-2 | `cart/order_event_logs` → `user_behavior_logs` 통합 (정렬 왜곡 해소) |
| P2-3 | 키워드 감정 분류기 → 별점 기준으로 축소 (**팀원 확인**) |
| P2-4 | `product_best_review_index` 소비처 확인 후 삭제 (**팀원 확인**) |
| P2-5 | `exclude-recipe-category-codes` 죽은 분기 삭제 (`RecommendProperties`·yml·`NO_ELIGIBLE_ITEMS` 포함) |
| P2-6 | `ProductSearchService` 406줄 → 3클래스 분리 |
| P2-7 | `ProductSearchResponse` / `ProductRecommendResponse` DTO 통합 |
| P2-8 | 재색인 관리 엔드포인트 |
| ~~P2-9~~ | ~~임베딩 텍스트에 카테고리명 결합 후 A/B~~ — **측정 후 기각**([`004`](004-measured-open-questions.md)) |
| P2-10 | `experiencedProductIds` 제외 정책 재검토 (식료품 재구매) — **측정 불가, 제품 결정 대기**([`004`](004-measured-open-questions.md)) |
| ~~P2-11~~ | ~~프롬프트 "부족 재료 최소 1개" 규칙 완화 검토~~ — **측정 후 규칙 유지. 이 항목의 전제가 틀렸다**([`004`](004-measured-open-questions.md)) |
| P2-12 | `MAX_TOP_K = 33` 공개 계약에서 분리 |
| P2-13 | ES `socket-timeout: 30s` → 검색 경로 기준으로 하향 |

### P3 — 지금은 건드리지 않아도 됨

| # | 항목 | 이유 |
|---|---|---|
| P3-1 | 로그의 이모지(✅❌🔄📨) | 스타일. 다만 포트폴리오 공개 전엔 정리 권장 |
| P3-2 | `[1단계]`·`[2단계]` 단계 주석 | 코드를 다시 말하는 주석이라 프로젝트 자체 규칙(`java-comments.md`)과 충돌하지만 기능 영향 없음 |
| P3-3 | Javadoc에 감사 문서 경로가 박혀 있는 것 | 추적성은 좋지만 문서가 옮겨지면 stale. 유지해도 무방 |
| P3-4 | `SwaggerConfig` (yml로 대체 가능) | 이득 미미 |
| P3-5 | `SentimentClassifier` 인터페이스 (구현 1개) | LLM 구현 추가 여지가 있으면 유지 정당 |
| P3-6 | `ProductEvent`만 record가 아닌 것 | 상류 스키마 대응 흔적. 일관성만의 문제 |
| P3-7 | `application.yml`의 `spring.kafka.producer.*` (커스텀 팩토리가 있어 미사용) | 혼란 요인이지만 무해 |
| P3-8 | ES 커넥션 풀 미설정 | 현 규모에서 문제 없음. 다만 "안 정했다"는 인지는 필요 |

---

## 3. 권장 진행 순서

기존 하네스의 작업 단위(취향 벡터 → 임베딩 유사도 → 키워드 검색 → 장바구니 레시피)와 겹치지 않게, **위험도 순**으로 다시 묶었습니다.

| 라운드 | 묶음 | 왜 이 순서 |
|---|---|---|
| **R1** | P0 전체 (8건) | 대부분 소규모이고, 동작하지 않거나 명백히 틀린 것들. review 소유 항목 4건은 팀원 확인 후 |
| **R2** | P1-1 `LlmGateway` + P1-8 메트릭 | 가장 큰 위험(M1)을 닫으면서 관측을 같이 붙임. 이후 모든 판단의 근거가 여기서 나옴 |
| **R3** | P1-5 Testcontainers 3건 | R4 이후의 구조 변경을 안전하게 만들어 주는 안전망. 먼저 깔아야 함 |
| **R4** | P1-3 인덱스 통합 + P1-6 변경 감지 + P2-8 재색인 | 전부 `product` 인덱스 재생성을 동반하므로 **한 번에** 묶어야 함 |
| **R5** | P1-2 취향 벡터 읽기 시점 계산 + P2-2 로그 인덱스 통합 | 같은 도메인. 인덱스 재생성 2건이 겹침 |
| **R6** | P1-4 DLT + P1-7 IDOR | 독립적, 아무 때나 가능 |
| **R7** | P2-1 다양화 대체 | R4의 재색인이 끝난 뒤 실제 상품으로 재측정하고 결정 |

---

## 4. 최종 정리

### 버릴 것

`user_profile_embeddings` + 비동기 프로필 갱신 서브시스템 · `product-autocomplete` 인덱스 ·
`product_best_review_index` · `NegativeReviewProperties`+yml · 제외 카테고리 필터 ·
`MIXED` 감정 · `MaxMinDiversityService` · 중복 DTO 1개 · `deleteReview`의 조회

### 단순화할 것

행동 로그 인덱스 2 → 1 · 감정 분류 (204줄+110줄 → 별점 매핑) ·
`ProductSearchService` 406줄 분리 · 근거 없는 상수 15개 → 3~4개 · 베스트 리뷰 교체 정책

### 새로 고민할 것 (여기가 진짜 포트폴리오)

1. **외부 LLM 호출의 타임아웃 예산·격벽·서킷브레이커** — 이 프로젝트에서 유일하게 실제로 터질 장애
2. **실패한 Kafka 메시지의 행방(DLT)** — 지금은 조용히 사라짐
3. **트랜잭션이 없는 저장소에서의 정합성** — 이중 쓰기를 없애서 푸는 쪽
4. **동시 갱신의 lost update** — 락 대신 상태 제거로 푸는 판단
5. **LLM은 지연이 아니라 한도가 병목** — 이미 실측했으니 rate limiter로 마무리
6. **통합 테스트로 "수동 확인 26건"을 자동화** — 남은 부채를 가장 빠르게 갚는 길

---

### 마지막으로

이 프로젝트의 강점은 이미 갖춘 **측정 문화**입니다 — 골든셋 A/B, 차원 축소 실측, knn 벤치.
그런데 그 측정이 전부 **AI 품질**에만 걸려 있고, **백엔드 지표**(지연 분포, 스레드 점유, 실패율, 처리량)에는 하나도 없습니다.

위의 P1을 하면서 같은 방식으로 측정만 붙이면, 포트폴리오의 성격이
**"AI 기능을 만든 사람"에서 "AI를 다루는 백엔드 엔지니어"로 바뀝니다.**

그게 이 감사의 결론입니다.
