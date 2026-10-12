# Keyword-search Architecture Audit 002 — 색인 처리량·검색 로그 동기 쓰기·단위 테스트 (2026-09-06)

키워드 검색 작업 단위의 색인 경로(`ProductEventConsumer` → `ProductIndexService` → 임베딩·재료명·ES 저장)와 검색 응답 경로의 부가 작업(검색 로그 적재)을 2026-09-06 비판 평가에서 다시 훑은 결과다. 반드시 고칠 A 항목 2건(상품 1건씩 직렬 색인이라 전체 재색인이 반나절, 검색 로그 쓰기가 응답 스레드에서 동기 실행), 유지보수성 B 항목 1건(페이징 검증 단위 테스트)이 나왔고 **2026-09-06 전부 승인·반영 완료**다(M-3 측정 포함). A-1은 `open-items.md` A-3이 요구하는 **`product` 인덱스 재생성 + 상품 이벤트 전량 재발행**의 소요 시간을 직접 결정하므로 가정이 아니라 예정된 작업의 병목이다.

## 범위

- 패키지: `search.application.ProductIndexService`·`ProductSearchService`, `search.infrastructure.event.ProductEventConsumer`, `embedding.application.ProductEmbeddingService`, `config.KafkaConsumerConfig`(product 팩토리), `config.AsyncConfig`(실행자 재사용 여부)
- 감사자: 메인 세션(2026-09-06 비판 평가). 서브에이전트를 띄우지 않았고, 이 세션은 해당 코드를 수정한 적이 없다
- 기준: `audit-checklist.md` 1~16항목(16번 — 재료명 LLM 호출의 배치화 가능성 포함), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md` 근거 기준 적용
- 검색 쿼리 조립·자동완성·캐시는 `001`에서 끝났다 — 여기서는 다루지 않는다. 단 `cart-recipe/005` A-2(`multiSearch` 진입점)가 `ProductSearchService`에 메서드 하나를 추가한다 — 그쪽이 SSOT

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 상품 색인이 1건씩 직렬이라 전체 재색인이 상품 수 × (임베딩 + LLM) 시간이다

- **Priority**: High
- **Category**: Performance
- **문제**: 상품 이벤트 1건마다 (1) 임베딩 API 1회, (2) 재료명 LLM 1회, (3) ES 저장 2회(메인·자동완성)를 **순차·동기**로 수행하고, Kafka는 poll당 1건만 가져온다. 임베딩 API는 목록을 받는 인터페이스인데 항목 1개짜리 목록으로 부른다. 건당 1~2초면 상품 1만 건 재색인에 3~6시간이 걸리고, 그동안 다른 상품 이벤트도 같은 파티션에서 기다린다
- **근거**:
  - `ProductEmbeddingService.java:46` — `embeddingModel.embed(List.of(productName))`. Spring AI `EmbeddingModel.embed(List<String>)`은 배치 인터페이스다
  - `ProductIndexService.java:54·62·88·90` — 임베딩 → 재료명 → 자동완성 `save` → 메인 `save`, 전부 같은 스레드에서 순차
  - `KafkaConsumerConfig.java:54` — `PRODUCT_MAX_POLL_RECORDS = 1`, 이유는 "건당 처리 시간이 길어 `max.poll.interval.ms` 초과 리밸런싱 방지"(`:50-53` Javadoc). 즉 **느린 처리를 배치 축소로 보상**하는 구조라, 처리량은 건당 지연의 역수로 고정된다
  - `open-items.md` A-3 — `product` 인덱스 재생성 + 상품 이벤트 재발행이 필수라고 이미 적혀 있다. 이 병목은 그 작업의 소요 시간이다
- **왜 문제인가**: 재색인은 프롬프트를 고칠 때마다 반복된다(이번 주에 재료명 프롬프트를 두 번 고쳤다 — `cart-recipe/004`). 반나절짜리 작업이면 프롬프트 개선을 미루게 된다
- **개선 방법**:
  1. product 리스너를 **배치 리스너**로 바꾼다(`factory.setBatchListener(true)`, `List<ProductEvent>` 수신, `max.poll.records`를 1 → N). N은 M-3 측정으로 정한다
  2. `ProductIndexService.indexProducts(List<ProductIndexRequest>)` — 임베딩은 **한 번의 `embed(List)`**로, 재료명은 상품별 LLM 호출 유지(프롬프트를 여러 상품용으로 바꾸면 `004`의 17/17 측정이 무효가 된다 — 이번 라운드 밖), ES는 `saveAll` 2회(메인·자동완성)
  3. 실패 격리: 배치 임베딩이 실패하면 **상품별 개별 호출로 폴백**해 현재 동작("실패한 상품만 벡터 null")을 보존한다. 재료명 LLM은 이미 상품별 격리다
  4. 삭제 이벤트는 배치 안에서 순서대로 처리한다(같은 상품의 생성→삭제 순서 보존)
  5. `max.poll.interval.ms`(기본 5분) 안에 배치가 끝나야 한다 — N × 건당 최악 지연(임베딩 30초 타임아웃 + LLM 30초)이 5분을 넘지 않게 N ≤ 4로 시작하거나, 재시도 포함 최악을 감안해 `max.poll.interval.ms`를 명시한다. **이 트레이드오프가 이 항목의 핵심 판단**이며 M-3 수치로 정한다
- **검증 방법 (M-3 — 앱 없이 잴 수 있는 부분)**:
  - 임베딩: `scripts/vectorbench/embed_batch.py` — 상품명 50개를 1개씩 50회 vs 10개씩 5회 vs 50개 1회로 `gemini-embedding-001`에 보내 총 소요 시간·요청 수를 비교(Gemini 키 필요 — 있음). 배치 크기 상한은 API 문서로 확인한다(G1 — 단일 사실이라 인라인 `WebFetch`)
  - ES: 같은 폴더 `bulk_vs_single.py` — 문서 1,000건을 `_doc` 1건씩 vs `_bulk` 100건씩으로 로컬 ES에 넣어 소요 시간 비교
  - 통합(로컬 ES + Kafka가 갖춰진 뒤, `open-items.md` A절 묶음): 상품 이벤트 100건 재발행 시 색인 완료까지 시간 Before/After
- **API 영향**: No Impact (HTTP API 아님. Kafka 소비 방식만 바뀐다)
- **예상 변경 파일**: `config/KafkaConsumerConfig.java`, `search/infrastructure/event/ProductEventConsumer.java`, `search/application/ProductIndexService.java`, `embedding/application/ProductEmbeddingService.java`
- **예상 변경 라인 수**: 80줄 내외
- **위험도**: Medium — 소비 단위가 바뀌어 실패 시 재처리 범위가 배치 전체가 된다. 색인은 문서 ID로 덮어쓰는 멱등 연산이라 재처리가 안전하다는 점이 이를 상쇄한다
- **테스트 영향도**: `ProductIndexService.indexProducts`는 mock(`EmbeddingModel`·리포지토리)으로 "배치 실패 → 개별 폴백" 경로를 단위 테스트할 수 있다
- **예상 효과**: 임베딩 왕복 N → 1, ES 왕복 2N → 2. 재색인 시간은 M-3 결과로 적는다

### A-2. 검색 로그 적재가 응답 스레드에서 동기 실행된다

- **Priority**: Medium
- **Category**: Performance
- **문제**: 회원 검색마다 `search_logs` 인덱스에 문서 1건을 **응답을 돌려주기 전에** 쓴다. ES 쓰기 지연이 그대로 검색 응답 시간에 더해진다. 뒤이은 프로필 갱신만 비동기다
- **근거** (줄 번호는 감사 시점 커밋 `a1328313` 기준 — 같은 날 `cart-recipe/005` A-2가 이 파일을 먼저 고쳐 이후 줄이 밀렸다): `ProductSearchService.java:157-158` 주석이 이 사실을 명시한다("동기 호출이라 검색 응답 시간에 포함된다"), `:168-174` — `logWriteService.saveSearchLog(...)` 직후 `userProfileRefreshTrigger.refresh(userId)`. `AsyncConfig`에 이미 `profileUpdateExecutor`(core 2·max 5·큐 100·초과 시 로그 후 폐기)가 있다
- **왜 문제인가**: 검색은 가장 자주 불리는 API다. 로그는 취향 분석용 부가 데이터라 유실이 허용되는데(실행자의 폐기 정책이 이미 그렇게 정했다), 그 부가 데이터 쓰기가 주 응답을 지연시킨다
- **개선 방법**: 로그 저장과 프로필 갱신을 **하나의 비동기 작업**으로 묶는다 — `UserProfileRefreshTrigger`에 `recordSearchAndRefresh(userId, keyword, topProductIds, searchedAt)`를 추가해 `saveSearchLog` → `updateUserProfileEmbedding` 순서로 실행한다(`@Async("profileUpdateExecutor")`). 둘을 따로 비동기로 던지면 갱신이 저장보다 먼저 돌아 이번 검색이 다음 갱신까지 반영되지 않는다. `ProductSearchService`의 try-catch는 제출 실패만 잡게 된다. 실행자 큐가 차면 로그도 함께 버려진다 — 폐기 정책의 범위가 "갱신"에서 "로그+갱신"으로 넓어지는 것이 유일한 동작 변화이고, 이는 `taste-vector/002` C-1(1초 refresh 지연)과 같은 성격의 허용 가능한 손실이다. 이 변화는 스펙 리스크 표에 적는다
- **검증 방법**:
  - 단위: `ProductSearchService.search`에서 `ElasticsearchOperations` mock 뒤에 `LogWriteService`가 **호출되지 않고** 트리거만 1회 호출되는지
  - 로컬: 검색 응답 시간 Before/After(ES 쓰기 1회 ≈ 수 ms~수십 ms)와 `search_logs` 문서가 여전히 생기는지
- **API 영향**: No Impact
- **예상 변경 파일**: `search/application/ProductSearchService.java`, `embedding/application/UserProfileRefreshTrigger.java`
- **예상 변경 라인 수**: 25줄 내외
- **위험도**: Low
- **테스트 영향도**: B-1과 같은 테스트 클래스

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. 페이징·자동완성 검증 단위 테스트

- **Priority**: High
- **Category**: Architecture (테스트성)
- **문제**: `toPageable`(page < 0, size 1~100, `(page+1)×size ≤ 10000`)과 `autocomplete`의 size 검증(1~20)·2글자 미만 빈 목록은 `001` A-4에서 넣은 계약인데 테스트가 없다
- **근거**: `ProductSearchService.java:189-201`, `:171-183`(감사 시점 커밋 `a1328313` 기준 — 구현 후에는 `toPageable`이 `:250`, `autocomplete`가 `:231` 부근). 검증은 ES 호출 전에 일어나므로 `ElasticsearchOperations`·리포지토리를 mock으로 두면 ES 없이 검증만 테스트할 수 있다
- **왜 문제인가**: 이 경계가 깨지면 ES가 400을 돌려주거나(`from + size` 초과) 응답이 무제한으로 커진다. `001` A-4가 넣은 방어인데 회귀를 잡을 장치가 없다
- **개선 방법**: `ProductSearchServiceTest`(Mockito) — 경계 8개(page −1, size 0·1·100·101, page 99 size 101 → 10,100 초과, autocomplete size 0·21, 1글자 → 빈 목록·리포지토리 미호출). A-2의 "로그는 트리거로만" 확인도 같은 클래스
- **API 영향**: No Impact
- **예상 변경 파일**: `src/test/java/com/barofarm/ai/search/application/ProductSearchServiceTest.java`
- **예상 변경 라인 수**: 100줄 내외
- **위험도**: Low
- **테스트 영향도**: 신규
- **예상 효과**: 페이징·자동완성 계약이 회귀 테스트로 고정된다

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 재료명 LLM 호출 배치화(상품 N개를 한 프롬프트로)

임베딩과 달리 LLM은 프롬프트 형태가 바뀌어 `004`의 17/17 측정을 다시 해야 한다. 재색인 시간의 절반은 이 호출이 차지할 텐데(M-3에서 임베딩·LLM 각각의 건당 지연을 함께 적는다), 프롬프트 변경은 골든셋 검증이 붙는 별도 라운드가 맞다
### C-2. 상태·가격만 바뀐 수정 이벤트에도 임베딩·LLM을 다시 부르는 것

`001` C-1과 동일. 수정 이벤트 빈도 `[미정]`. A-1이 반영되면 절대 비용이 줄어 시급성이 더 낮아진다

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 색인용 스레드풀을 따로 만드는 것

배치 리스너 + 컨슈머 스레드 하나로 충분하다. 병렬화는 파티션 수를 늘려 컨슈머 `concurrency`로 하는 것이 Kafka의 방식이고, 앱 안에 스레드풀을 두면 오프셋 커밋과 처리 완료가 어긋난다
### D-2. 검색 로그 전용 실행자

`profileUpdateExecutor`는 이미 "취향 벡터 관련 부가 작업" 풀이고 로그는 그 입력이다. 풀을 나누면 큐·거부 정책이 두 배가 된다

## 판단이 서지 않는 항목

1. **배치 크기 N과 `max.poll.interval.ms`.** M-3의 건당 지연 수치가 나와야 정할 수 있다. 결과와 함께 제안한다(기본 후보: N=10, interval 명시 5분 유지 — 최악 10 × 60초 = 10분이면 초과라 N=4 또는 interval 15분 중 택일)

## 승인 결과 (2026-09-06)

사용자가 A·B 전 항목과 M-3 측정을 승인했다. 순서: B-1 → A-2 → M-3 → A-1(A-1은 M-3 수치로 N을 정한 뒤 구현). 판단 항목 1(N·`max.poll.interval.ms`)은 M-3 결과와 함께 정한다.

### 측정 결과 (M-3, 2026-09-06)

| 측정 | 결과 |
|------|------|
| ES 1건씩 vs bulk(100) — 문서 1,000건 | 4.95초 vs 1.16초, **4.3배** |
| Gemini 임베딩 1건씩 | **13회 성공 뒤 14번째 호출에서 429**(무료 티어) |
| Gemini 임베딩 10건 묶음 | 5회 전부 성공, 호출당 p50 1.45초 |
| Gemini 임베딩 25·50건 묶음 | 분당 한도 초과(429 quota) |

원본은 `vectorbench/` 폴더의 JSON이고, 해석은 [`refactor-log.md`](refactor-log.md) 2026-09-06 항목에 있다.

### 구현 결과 (2026-09-06)

A 2건·B 1건 반영. 판단 항목 1은 **N = 5, `max.poll.interval.ms` = 15분**으로 정했다 — 상품 1건 최악 지연 180초 × 5 = 900초로, 1건일 때 지키던 "최악에도 리밸런싱 없음"을 유지하는 최대값이다. 상세·검증은 refactor-log 같은 항목. 테스트 누적 60건 통과.
