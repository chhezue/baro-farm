# baro-ai 감사 ② 백엔드 설계 심화

> 3부작 중 2부. [1부 — 복잡도·과잉 설계 진단](001-complexity-and-overengineering.md)에서 이어지고,
> [3부 — 목표 구조와 우선순위](003-target-architecture-and-priority.md)로 이어진다.

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

## 1. Missing Backend Engineering Decisions

### M1. 요청 스레드 예산과 격벽 — 가장 시급

| 항목 | 내용 |
|---|---|
| **문제** | `GET /recommendations/recipes/{userId}` 한 건이 Tomcat 요청 스레드를 최대 ~93초 점유 가능 (Feign 3s + LLM 30s×3 + 백오프). 스레드 수는 Spring Boot 기본 200, `server.tomcat` 설정 없음 |
| **왜 중요** | Gemini가 느려지면 **레시피와 무관한 검색·자동완성·추천까지 전부 죽습니다.** k8s 파드는 cpu 500m / mem 512Mi, replicas 1(HPA max 3). Gemini 지연 30초 상황에서 동시 200요청이면 그 뒤 요청은 큐잉 → 헬스체크 실패 → 파드 재시작 → 되풀이 |
| **현재 구현** | 방어 없음. `LlmClientConfig` 주석은 "무기한 블로킹 차단"이라 적혀 있지만, 30초×3은 **차단이 아니라 상한 설정**입니다 |
| **부족한 것** | ① 호출 경로별 타임아웃 예산 ② 격벽(LLM 동시 호출 수 제한) ③ 서킷브레이커 ④ 게이트웨이 상한과의 정합 |
| **설계 대안** | (a) 요청 경로 LLM 예산을 3~5초·재시도 0회로 낮추고 초과 시 `GENERATION_FAILED` 반환<br>(b) resilience4j `Bulkhead`(동시 5) + `CircuitBreaker`<br>(c) 202 Accepted + 폴링/SSE로 비동기화<br>(d) 장바구니 변경 시 미리 계산해 캐시 |
| **trade-off** | (a) 즉시 적용 가능, 성공률 하락<br>(b) 의존성 1개 추가, 대기 요청을 빠르게 거절<br>(c) 프론트 변경 필요, 사용자 경험은 오히려 개선(로딩 표시)<br>(d) 캐시 무효화 문제 발생 |
| **포트폴리오 설명** | *"외부 LLM 호출이 요청 스레드를 최대 90초 점유할 수 있었고, 스레드풀 고갈 시 무관한 검색 API까지 함께 죽는 구조였습니다. 타임아웃 예산을 요청 경로 3초·배치 경로 30초로 분리하고, LLM 호출에 동시 5개 격벽을 둬서 장애를 그 기능 안에 가뒀습니다."*<br>→ **이 프로젝트에서 만들 수 있는 가장 강한 백엔드 서사입니다.** |

---

### M2. 실패한 메시지의 행방 (DLT 부재)

- **현재**: `DefaultErrorHandler(FixedBackOff(1s, 2))` → 3회 실패 시 **로그 한 줄 남기고 폐기**.
- **결과**: 상품 하나가 영영 색인되지 않아도 아무도 모릅니다. 검색에 안 뜨는 상품이 생기는데 원인 추적이 불가능합니다.
- **부족한 것**: DLT(`product-events.DLT`) + `DeadLetterPublishingRecoverer`, 폐기 건수 메트릭, 재처리 수단.
- **trade-off**: DLT는 토픽 1개와 컨슈머 운영 부담을 늘립니다. 최소안은 **폐기 시 카운터 메트릭 + 로그 레벨 ERROR + 원문 저장**입니다.
- **포트폴리오**: *"재시도 소진 후 조용히 버리던 걸 DLT로 보내고, `kafka.dlt.count` 메트릭을 붙여 색인 누락을 관측 가능하게 만들었습니다."*

---

### M3. 두 인덱스 이중 쓰기의 원자성 (ES에는 트랜잭션이 없다)

- **현재**: `indexProducts`가 autocomplete → main 순서로 각각 `saveAll`. 중간 실패 시 부분 반영. `deleteProduct`도 동일.
- **왜 중요**: RDB 트랜잭션이 없는 저장소에서 **"두 곳을 함께 바꿔야 하는" 문제를 어떻게 푸는가**는 정확히 백엔드 질문입니다.
- **대안**:
  1. **인덱스를 하나로 합쳐 문제 자체를 없앤다** ([1부](001-complexity-and-overengineering.md) O2 — 권장)
  2. ES `_bulk` 한 번에 두 인덱스를 담아 부분 실패를 응답에서 확인
  3. outbox 패턴 (이 규모에는 과함)
- **포트폴리오**: *"ES에는 트랜잭션이 없어 두 인덱스 동기화가 보장되지 않았습니다. 별칭·2PC 같은 걸 붙이는 대신 멀티필드로 인덱스를 통합해 이중 쓰기 자체를 제거했습니다."*
  → **"복잡한 문제를 없애서 푼다"가 가장 좋은 답입니다.**

---

### M4. 낙관적 동시성 제어 부재 (lost update)

- **현재**: `userProfileEmbeddingRepository.save(...)` — 버전 확인 없는 덮어쓰기. core 2 스레드가 같은 사용자를 동시에 처리 가능.
- **왜 중요**: Lost Update가 **실제로 존재하는 지점**입니다. 여기서 "왜 락이 필요한가 / 어떤 락인가"를 논할 수 있습니다.
- **대안**:
  1. 사용자 단위 직렬화 (Kafka는 이미 `userId` 키로 파티셔닝 가능 — 상류 협의 필요)
  2. ES `seq_no`/`primary_term` 기반 낙관적 갱신 + 충돌 시 재계산
  3. **읽기 시점 계산으로 전환하면 소멸** ([1부](001-complexity-and-overengineering.md) O1 — 권장)
- **포트폴리오**: *"동시 갱신에서 lost update가 가능했는데, 비관적 락도 낙관적 락도 붙이지 않고 상태를 없애는 쪽(읽기 시점 계산)을 골랐습니다. 계산 비용이 ES 왕복 대비 무시할 수준이었기 때문입니다."*

---

### M5. 재색인 경로 부재

- **현재**: 매핑을 바꾸면 **상류가 상품 이벤트를 전부 재발행해야만** 재색인됩니다. `docs/audits/open-items.md` A-3이 이를 인정하고 있습니다.
- **왜 중요**: 임베딩 모델·차원을 바꿀 수 있다는 게 이 프로젝트의 전제인데, 그때마다 다른 팀에 부탁해야 하는 구조입니다.
- **대안**: `POST /internal/reindex` 관리 엔드포인트 + baro-shopping 상품 목록 페이징 조회 + 배치 색인. 또는 ES alias 기반 blue/green 재색인.
- **포트폴리오**: 검색 서비스의 필수 운영 기능이라 있으면 확실한 가점입니다.

---

### M6. LLM 호출량 제어와 비용 예산

- **현재**: rate limiter 없음. 실측으로 **1건씩 임베딩하면 13회 후 429**가 확인됐는데(무료 티어), 코드 대응은 "재시도 3회"뿐입니다.
  **429에 재시도하는 건 한도를 더 태우는 행동입니다.**
- 그리고 `POST /reviews/batch/refresh-all`은 **인증 없이 전 상품 LLM 배치를 트리거**합니다 → 비용 DoS 벡터.
- **대안**:
  1. 429는 재시도 대상에서 제외하고 백오프만
  2. 색인 경로에 토큰 버킷 rate limiter
  3. 배치 엔드포인트에 인증 + 동시 실행 방지(`AtomicBoolean` 또는 ShedLock)
- **포트폴리오**: *"LLM은 지연이 아니라 한도가 병목이었습니다(실측: 1건씩 13회 후 429, 10건 묶음은 통과). 그래서 재시도가 아니라 배치 크기로 풀었습니다."*
  → 이미 절반은 하셨으니 나머지를 채우면 됩니다.

---

### M7. 관측 가능성 — 지표가 없다

- **현재**: actuator + prometheus + otel 트레이싱(baro-common)은 켜져 있으나, **도메인 지표가 하나도 없습니다.**
- **없는 것**: LLM 호출 실패율·지연 분포, 임베딩 실패 건수, 추천 빈 응답 비율, 색인 지연(이벤트 발행→색인 완료), Kafka 폐기 건수, 자동완성 캐시 히트율.
- **왜 중요**: `VectorProductSearchService`는 ES 예외를 잡아 **빈 리스트를 반환**합니다. ES가 죽어도 API는 200 + `[]`를 돌려줍니다. 지표가 없으면 **아무도 모릅니다.**
- **대안**: `MeterRegistry` 카운터 5~6개. 코드 20줄.

---

### M8. 통합 테스트 0건

- **현재**: 단위 테스트 10클래스 59건. `@SpringBootTest` **0개**, Testcontainers 없음.
- **왜 중요**: 이 모듈의 위험은 전부 경계(ES 쿼리 DSL, Kafka 묶음 실패 처리, LLM 파싱)에 있는데, 그 경계가 전혀 테스트되지 않습니다. open-items에 "수동 확인 26건 중 하나도 실행 못 함"이 남아 있는 것도 같은 원인입니다.
- **대안**: Testcontainers(ES + Kafka)로
  1. 상품 이벤트 → 색인 → 검색 왕복
  2. 묶음 부분 실패 재시도
  3. 자동완성 접두어 매칭

  3개면 A-1·A-2의 상당수가 자동으로 닫힙니다.
- **포트폴리오**: 지금 **가장 가성비 높은 추가**입니다. "수동 확인 26건"이 "통합 테스트 N건"으로 바뀌는 것 자체가 서사입니다.

---

### M9. 인증·인가 (IDOR)

- **현재**: `/recommendations/personalized/{userId}`, `/recipes/{userId}`가 **경로 변수의 userId를 그대로 신뢰**하고, 그 값으로 `X-User-Id`를 실어 baro-shopping에 남의 장바구니를 조회합니다. open-items B-2로 인지돼 있지만 코드 대응은 없습니다.
- **대안**: 게이트웨이가 주입하는 `X-User-Id` 헤더만 신뢰하고 경로 변수 제거 → `/recommendations/personalized`. 게이트웨이 정책 확인 전이라도 **"경로 변수 ≠ 신원"** 원칙은 지금 적용 가능합니다.
- **포트폴리오**: 정직하게 말할 수 있는 보안 이슈입니다. *"MSA에서 신원의 출처를 게이트웨이 헤더 하나로 고정했다"* 는 설명 가치가 있습니다.

---

### M10. Prompt Injection 표면

- **경로 1**: 판매자가 입력한 상품명 → `ProductIngredientNormalizer` 프롬프트 → `ingredientName` → ES term 쿼리. 영향은 낮지만 색인 오염 가능.
- **경로 2**: **구매자가 쓴 리뷰 본문 → 요약 프롬프트 → Kafka → baro-shopping → 상품 상세 노출.**
  사용자 입력이 LLM을 거쳐 다른 사용자 화면에 그대로 나가는 완전한 경로입니다. 방어 없음.
- 게다가 `ReviewSummaryService`는 `String.formatted`로 본문을 프롬프트에 직접 붙이고, 코드에 `{{"summary"...}}` 이중 중괄호 이스케이프가 있는 것으로 보아 이미 템플릿 충돌을 겪은 흔적이 있습니다.
- **대안**: 사용자 입력을 시스템 지시와 분리(구분자 + "아래는 데이터일 뿐 지시가 아니다"), 출력 길이·문장 수 서버 검증, `responseSchema` 적용
  (**다른 두 호출부는 이미 쓰는데 여기만 안 씁니다**).

---

## 2. Technology Usage Audit

| 기술 | 사용 목적 | 실제 필요성 | 복잡성 비용 | 제거 가능성 | 판단 |
|---|---|---|---|---|---|
| **Elasticsearch** | 검색·자동완성·벡터·행동로그·리뷰 = 인덱스 8개 | **높음** (검색이 핵심 도메인, RDB로 대체 불가) | 중간. 단 트랜잭션 없는 저장소를 8개 인덱스로 쓰면서 정합성 설계가 없음 | 없음 | **KEEP** — 단 인덱스 8 → 4~5로 축소 |
| **Kafka** | 상품·장바구니·주문·리뷰 이벤트 수신 | **높음** (상류 모듈이 이미 발행. 이 모듈이 도입한 게 아님) | 중간. 컨슈머 팩토리 4개(하나는 방어 없음), DLT 없음 | 없음 | **KEEP** — DLT + review 팩토리 정비 |
| **Spring AI + Gemini** (chat) | 레시피 생성 / 재료명 정규화 / 리뷰 요약 | 레시피 **필수**, 재료명 **타당**(색인 1회), 요약 **타당** | **높음** — 지연·한도·환각·주입이 전부 여기서 옴 | 없음 | **KEEP** — 호출 경로별 예산 분리 + rate limit + 요약에도 `responseSchema` |
| **Spring AI** (embedding) | 상품명 768차원 | **중간** — 상품명만 임베딩하는 게 최선인지 미검증 | 낮음 | 낮음 | **KEEP, 개선** — `productName + " " + categoryName` 결합 텍스트를 A/B |
| **Caffeine 캐시** | 자동완성 5분 / 1000건 | **타당** — 접두어는 재요청이 많음 | 매우 낮음 | 중간 | **KEEP** — 단 히트율 지표가 없어 효과 미검증 |
| **OpenFeign** | baro-shopping 장바구니 조회 | 중간 — 호출 지점 **1곳** | 낮음 (선언적이라 저렴) | 중간 (`RestClient`로 대체 가능) | **KEEP** — 팀 컨벤션. 단 fallback·서킷브레이커 없음 |
| **Actuator + Micrometer + OTel** | health/metrics/prometheus/loggers | 높음 | 낮음 (baro-common 제공) | 없음 | **KEEP** — 단 **도메인 지표 0개**. 미사용 자산 |
| **`@Async` + `ThreadPoolTaskExecutor`** | 프로필 갱신 | **낮음** ([1부](001-complexity-and-overengineering.md) O1) | 중간 (폐기 정책·경쟁조건·읽기지연) | **높음** | **REMOVE** |
| **`@Scheduled`** | 베스트 리뷰 배치 | — | — | — | **BROKEN** — `@EnableScheduling` 없어 미동작 |
| **Spotless + Checkstyle** | 코드 스타일 | 높음 | 낮음 | 없음 | **KEEP** |
| **springdoc** | Swagger | 높음 | 낮음 | 없음 | **KEEP** |
| **Redis** | — | 없음 | — | — | **잘 안 넣음** |
| **RDB / JPA** | — | 없음 (그런데 `baro-common`이 끌고 와서 `application.yml`에서 자동설정 2개를 꺼야 함) | 낮음 | — | **open-items B-8로 팀 논의 필요** |
| **resilience4j** | — | **필요한데 없음** | — | — | **ADD** (M1) |
| **Testcontainers** | — | **필요한데 없음** | — | — | **ADD** (M8) |

---

## 3. Concurrency / Transaction Audit

### 실행 주체 지도

```
[HTTP 요청 스레드]  Tomcat 기본 200 (미설정)
   ├─ /search, /autocomplete           → ES 1왕복.  안전
   ├─ /recommendations/personalized    → ES 1(프로필) + 1(script_score).  안전
   ├─ /recommendations/similar         → ES 1 + 1.  안전
   ├─ /recommendations/recipes/{id}    → Feign(≤3s) + ES + [Gemini ≤93s] + ES   ← 위험
   └─ /reviews/batch/refresh-all       → 전 상품 루프 × (ES agg + Gemini ×2)    ← 무제한

[Kafka 컨슈머 스레드]  concurrency=1 × 4 그룹 (전부 기본값, 명시 없음)
   ├─ product-events  묶음 5건 → 상품마다 Gemini 재료명 + 묶음 임베딩 + ES bulk ×2
   │                  최악 900초 / max.poll.interval.ms 900_000 과 정확히 일치  ← 여유 0
   ├─ cart-events     → ES 1 + Async 제출
   ├─ order-events    → ES N + Async 제출
   └─ review-events   → ES 1  (ErrorHandlingDeserializer 없음, 에러 핸들러 없음) ← 파티션 정지 가능

[profileUpdateExecutor]  core 2 / max 5 / queue 100 / 초과 시 조용히 폐기
   └─ ES 2(로그) + 1(벡터) + 1(저장)

[스케줄러]  없음 — @EnableScheduling 미적용으로 @Scheduled 미동작
```

### 판정

| 항목 | 현재 | 타당한가 | 지적 |
|---|---|---|---|
| **왜 이 작업은 동기인가** | 검색·추천 조회는 동기 | 적절 | 짧은 ES 조회라 동기가 맞음 |
| **왜 레시피가 동기인가** | 답이 없음 | 문제 | LLM 지연이 요청 지연이 되는 유일한 경로인데 판단이 없음 |
| **왜 프로필 갱신이 비동기인가** | "Kafka 컨슈머 흐름 보호" | 애매 | 목적은 타당하나 **작업이 4 ES 왕복뿐**이라 컨슈머에서 동기로 해도 됐음. 비동기 대가(폐기·경쟁·지연)가 이득보다 큼 |
| **Thread Pool 크기 근거** | core 2 / max 5 / queue 100 | 문제 | 근거 없음. IO-bound 작업인데 CPU 기준도 아니고 큐 100의 의미도 없음 |
| **Connection Pool 관계** | **DB 커넥션 풀 없음** (RDB 미사용) | — | `Request Thread → Executor → DB Connection` 병목은 이 프로젝트에 **존재하지 않습니다.** 대신 `Request Thread → Gemini HTTP`가 그 자리를 차지합니다 |
| **ES 커넥션 풀** | 기본값 (Apache HttpClient, route당 10 / 전체 30) | 애매 | 미설정. 동시 200 요청 시 ES 커넥션이 먼저 마릅니다. 지금 규모에선 문제 없으나 **"안 정했다"는 사실은 알고 있어야 합니다** |
| **Transaction 범위** | **트랜잭션 없음** (`@Transactional` 0개, ES 미지원) | — | 대신 논의할 것: **이중 인덱스 쓰기의 부분 실패**(M3), **Kafka at-least-once + 문서 ID 멱등성**(잘 처리됨) |
| **Isolation Level** | 해당 없음 | — | ES는 문서 단위 원자성만 보장. 여러 문서에 걸친 읽기 일관성 없음 |
| **Lock** | 없음 | 애매 | 필요한 곳이 딱 하나 — 프로필 동시 갱신(M4). 그리고 정답은 락이 아니라 **상태 제거** |
| **Optimistic / Pessimistic** | 둘 다 없음 | 애매 | ES는 `seq_no`/`primary_term` 낙관적 제어를 제공하는데 미사용 |
| **`FOR UPDATE SKIP LOCKED`** | 해당 없음 | — | RDB가 없어 이 논의 자체가 성립하지 않습니다. **면접에서 이 주제를 말하려면 baro-order / baro-payment 쪽이 무대입니다** |
| **Race Condition** | ① 프로필 동시 갱신(lost update)<br>② 상품 이벤트 키 없음 → 파티션 분산으로 순서 역전(open-items B-6)<br>③ 갱신 태스크가 방금 쓴 로그를 못 봄(C-23, 재현 실패) | 애매 | ①②는 실재. ②는 상류 협의 필요 |
| **Deadlock** | 없음 | 적절 | 락이 없으니 데드락도 없음 |
| **Backpressure** | 없음 — 큐 초과 시 폐기 | 문제 | "밀리면 버린다"가 유일한 정책 |
| **Timeout** | ES 30s(socket) / Feign 1s+3s / Gemini 30s×3 | 애매 | 개별 값은 있으나 **경로 전체의 예산 합**이 계산된 적 없음. ES socket 30s는 검색 경로 기준으로 지나치게 김 |
| **Retry** | Kafka 2회, Gemini 3회, Kafka 프로듀서 3회 | 애매 | 429에 재시도하는 것은 역효과. 재시도 대상 분류가 없음 |
| **Circuit Breaker / Bulkhead** | 없음 | 문제 | 외부 의존 3개(Gemini·ES·baro-shopping)에 대해 전무 |
| **Idempotency** | Kafka 프로듀서 `enable.idempotence=true`, 컨슈머는 문서 ID 멱등 | 우수 | **이 프로젝트에서 가장 잘 된 부분** |

---

## 4. AI / Recommendation Architecture Audit

### LLM이 반드시 필요한 곳 / 아닌 곳

| 기능 | LLM 필요? | 판단 |
|---|---|---|
| **레시피 생성** | **필수** | 규칙 기반으로 대체 불가. 이 프로젝트가 AI를 쓰는 유일한 정당한 이유 |
| **상품명 → 재료 표준명** | **타당** | "횡성 한우 등심 300g 1등급" → "소고기"는 사전·정규식으로 못 함. **색인 시점 1회**로 옮긴 결정(`decisions/002`)은 훌륭 |
| **리뷰 요약** | **타당** | 추출 요약으로도 되지만 품질 차이가 큼 |
| **감정 분류** | **현재 방식이 최악** | 별점만으로 충분([1부](001-complexity-and-overengineering.md) O4). LLM을 쓸 거면 요약과 **한 번에** 처리 |
| **임베딩** | **타당** | 상품명 유사도에 필요 |

### Embedding / Vector Search 평가

| 항목 | 현재 | 평가 |
|---|---|---|
| 생성 시점 | 색인 시(Kafka 이벤트) | 옳음. 단 **변경 감지 없이 매번 재생성**([1부](001-complexity-and-overengineering.md) H7) |
| 임베딩 대상 텍스트 | **`productName` 하나** | 카테고리·가격대 정보가 벡터에 없습니다. 그래서 "이름이 비슷한 상품"을 찾는 것이지 "성격이 비슷한 상품"이 아닙니다. 역설적으로 다양화가 필요했던 이유(카테고리 쏠림 45%)는 임베딩이 이름을 통해 카테고리를 잘 잡고 있다는 뜻이기도 합니다 |
| 차원 | 768 (3072→1536→768) | 실측 근거 우수 |
| 저장 | ES `dense_vector` | 별도 벡터 DB 안 쓴 것 잘한 결정 |
| 유사도 | 코사인 (`script_score` + 1.0 오프셋) | 정규화된 벡터라 코사인이 맞음 |
| kNN vs 선형 스캔 | `script_score` 유지 (1만 건까지 p95 8ms 미만 실측) | **모범 사례** |
| 후보 수 | topK × 3, 상한 100 | 3의 근거는 "카테고리 쌍 비율"뿐 |
| Filtering | 상태 + `exists(vector)` + 제외 ID | 견고 |
| Re-ranking | Max-Min 다양화 후 유사도 재정렬 | 문제 ([1부](001-complexity-and-overengineering.md) O3) |
| Hybrid Search | 없음 (키워드 검색과 벡터 검색이 완전 분리) | 현재 규모에선 불필요. **넣지 않은 게 맞습니다** |
| 재생성 정책 | 없음 (매 이벤트마다 재생성) | 문제 |
| 품질 검증 | 레시피만 골든셋 있음. **유사 상품·개인화 추천은 검증 수단 없음** | 보완 필요 |

### 근거 없는 scoring / weighting 목록

실제 데이터로 검증되지 않은 숫자 전부입니다.

| 값 | 위치 | 근거 |
|---|---|---|
| 상품명 매칭 boost 2.0 / 1.0 / 0.3 | `ProductSearchService` | 없음 |
| `ingredientName` term boost 4.0 | `buildIngredientQuery` | "상품명 2.0보다 높게" — 상대 순서만, 값의 근거 없음 (C-24로 인지됨) |
| `slop(1)`, `fuzziness(1)`, `prefixLength(1)` | 동 | 없음 |
| 행동 가중치 3 / 2 / 1 / 0 | `UserProfileEmbeddingService` | 직관 |
| `MIN_TOTAL_LOGS_FOR_EMBEDDING = 3` | 동 | 없음 |
| `RECENT_LOG_LIMIT = 5` (유형별) | 동 | 없음. 게다가 정렬 왜곡([1부](001-complexity-and-overengineering.md) H9) |
| `LOOKBACK_DAYS = 30` | 동 | 없음 |
| `CANDIDATE_MULTIPLIER = 3` | `VectorRecommendPipeline` | **부분 근거 있음**(카테고리 쌍 %). 단 그 지표 자체의 정당성이 미검증 |
| `MAX_CANDIDATE_SIZE = 100` | 동 | 없음 |
| `MISSING_PRODUCTS_LIMIT_PER_INGREDIENT = 2` | `RecipeRecommendService` | 없음 |
| `tolerance 2 / strongDiff 3 / mixedMin 4` | `SentimentProperties` | 없음 |
| `CANDIDATE_SIZE = 10` (겸 최소 컷오프) | `BestReviewListService` | 없음 |
| `REPLACE_THRESHOLD = 0.5` | 동 | 없음 |
| 자동완성 캐시 1000건 / 5분 | `CacheConfig` | 없음 (히트율 미측정) |
| `temperature = 0.7` / `0.0` | `application.yml` / `ProductIngredientNormalizer` | 0.0은 **근거 명확**(결정적 추출) / 0.7은 기본값 |

**총 15개 이상.** 이 중 근거가 문서화된 것은 `CANDIDATE_MULTIPLIER`와 `temperature=0.0` 둘뿐입니다.

> **중요한 판단**: 데이터가 없으면 **숫자를 줄이는 것**이 정답입니다.
> 근거 없는 상수 15개는 "튜닝을 많이 했다"가 아니라 "왜 그 값인지 아무도 모른다"로 읽힙니다.
> 검증 가능한 것(boost, 다양화 배수)만 남기고 나머지는 제거하거나 단순화하세요.

### AI vs 결정론적 로직의 책임 경계

| 판단 | 주체 | 평가 |
|---|---|---|
| 요리 이름·핵심 재료·조리법 | LLM | 필연 |
| **사용한 보유 재료 / 부족한 재료** | **서버 집합 연산** | **이 프로젝트 최고의 설계 결정** |
| 재료 표준명 | LLM (색인 시 1회) | 시점 이동이 핵심 |
| 검색 키워드 추출 | 서버 (`toSearchKeyword` — 마지막 어절) | 휴리스틱. C-25로 인지됨 |
| 조리법 본문 | **LLM 그대로 노출, 검증 0** | 잘못된 조리법이 나가도 감지 수단 없음. 최소한 길이·단계 수 검증 |
| 리뷰 요약 본문 | **LLM 그대로 Kafka 발행 → 상품 상세 노출** | 문장 수·길이 검증 없음, `responseSchema` 미적용 |

---

> 이어서 [**3부 — 목표 구조와 우선순위**](003-target-architecture-and-priority.md).
