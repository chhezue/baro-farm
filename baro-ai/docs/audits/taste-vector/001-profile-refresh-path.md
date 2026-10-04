# Taste-vector Architecture Audit 001 — 프로필 갱신 경로 (2026-09-05)

취향 벡터 작업 단위의 **프로필 갱신 경로** — 장바구니·주문 이벤트 소비(`CartEventConsumer`·`OrderEventConsumer`) → 행동 로그 저장(`LogWriteService`) → 프로필 벡터 재계산(`UserProfileEmbeddingService`) → 개인화 추천이 읽는 문서(`UserProfileEmbeddingDocument`) — 와 그 경로가 쓰는 공유 설정(`AsyncConfig`, `KafkaConsumerConfig`의 cart·order 팩토리)을 감사한 결과다. 반드시 고칠 A 항목 5건(장바구니 가중치가 문자열 불일치로 전부 1, `@Async`가 자기 호출이라 컨슈머에서 동기 실행, 이벤트마다 원격 호출 6회 전체 재계산, 가중치 0 이벤트가 문서와 달리 1회 반영, cart·order 컨슈머에 역직렬화 보호·에러 핸들러 없음)과 유지보수성 B 항목 4건이 나왔고, **2026-09-05 전부 승인 완료**(문서 끝 "승인 결과" 절). 메인 세션이 넘긴 가설 12개(H0~H11)는 각각 코드·설치된 jar 바이트코드로 독립 검증했고, 정책 판단이 필요한 5건은 등급을 매기지 않고 "판단이 서지 않는 항목"으로 넘겼다.

## 범위

- 패키지·파일: `event.consumer.CartEventConsumer`·`OrderEventConsumer`, `event.model.CartLogEvent`·`OrderLogEvent`, `log.*`(`LogWriteService`, 문서 3종, 저장소 3종), `embedding.application.UserProfileEmbeddingService`, `embedding.domain.UserProfileEmbeddingDocument`, `embedding.infrastructure.elasticsearch.UserProfileEmbeddingRepository`, `embedding.exception.EmbeddingErrorCode`, `recommend.application.PersonalizedRecommendService`, `config.AsyncConfig`, `config.KafkaConsumerConfig`의 cart·order 팩토리
- 호출부로서만 읽은 코드: `search.application.ProductIndexService.updateUserProfileAsync`, `search.application.ProductSearchService`의 검색 로그 블록(`:88-104`)
- 전제 확인용으로 읽기만 한 다른 모듈(수정 대상 아님): `baro-common` `log.history.**`(`HistoryEventType`·`HistoryEnvelope`·`HistoryAspect`·`HistoryLogWriter`), `baro-shopping` `cart.history.*`, `baro-order` `order.history.*`
- **범위 밖**: `review` 패키지와 `reviewEventConsumerFactory`(팀원 소유 — cart·order 팩토리와 같은 모양이지만 건드리지 않는다), 벡터 검색·다양화(`vector-similarity` 단위), 상품 색인(`keyword-search` 단위)
- 감사자: 메인 세션이 넘긴 가설 12개를 신선한 컨텍스트에서 코드로 독립 검증했다 — safe-refactor 1단계
- 기준: `audit-checklist.md` 1~16항목(16번 LLM(Large Language Model, 거대 언어 모델)·프롬프트는 이 단위에서 임베딩 호출 1곳뿐이라 입력 길이 문제만 해당), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- 저장 스키마(ES 매핑)는 동결 대상이 아니다 — `baro-ai-performance-refactor.md` "데이터 마이그레이션 방침". 단 로그 인덱스 삭제는 되돌릴 수 없으므로 사람이 직접 실행한다(STOP §3)
- 라이브러리 실물(로컬 Gradle 캐시 jar): `spring-kafka-3.3.11`, `kafka-clients-3.9.1`, `spring-data-elasticsearch-5.3.6`, `spring-context-6.2.14`(+ sources jar). 바이트코드는 `javap -c -p`로 읽었다
- 자동화 테스트는 사용자 결정(2026-09-04)으로 이번 라운드에서 다루지 않는다 — 모든 검증 방법은 로컬 수동 절차다. 가중치·대표 텍스트 함수는 순수 함수라 테스트를 들일 때 첫 대상이 된다(한 줄 메모, 항목 아님)

**이전·병행 감사가 이미 소유해서 여기서 다시 제안하지 않는 것**

| 문서 | 그쪽 소유 | 이 문서와의 접점 |
|------|-----------|------------------|
| [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) | B-4 `List<Double>`→`float[]` 전환, B-3 추천 파이프라인 공유화, B-1 `RecommendErrorCode` 정리, `ElasticsearchConfig` 정리, 카테고리 보너스 판단 | B-4가 `UserProfileEmbeddingService.embedText`(`:299-303`) 5줄을 바꾼다 — 이 문서 B-3과 같은 메서드라 구현 순서를 맞춘다 |
| [`keyword-search/001`](../keyword-search/001-query-path.md) | B-1 `ProductIndexService` 책임 분리 — "`updateUserProfileAsync`는 취향 벡터 감사가 전용 빈을 제안하면 그때 따라간다" | 이 문서 **A-2**가 그 전용 빈이다. A-2를 먼저 반영하면 keyword-search B-1은 자동완성 이동만 남는다 |
| [`search/001`](../search/001-index-path.md) | product 컨슈머 재시도·poll 건수(A-1), DLQ(Dead Letter Queue, 실패 메시지 보관 토픽) 안 함(D-1), `spring.kafka.consumer.*` 죽은 설정(C-1) | 이 문서 A-5는 같은 결정을 cart·order 팩토리에 적용하는 것이다. 팩토리 4중복 정리는 cross-cutting/002 후보(C-2) |
| [`cross-cutting/001`](../cross-cutting/001-llm-call-infra.md) | Gemini 재시도·타임아웃·actuator 지표 | 이 문서의 검증 방법이 그때 추가된 `/actuator/metrics`를 쓴다 |

**가설 판정 요약**

| 가설 | 판정 | 어디에 |
|------|------|--------|
| H0 장바구니 가중치 문자열 불일치 → 전부 1 | 사실. 선호 카테고리 계산도 같은 함수를 써 같이 왜곡 | **A-1** |
| H1 `@Async` 자기 호출 → 컨슈머에서 동기 실행 | 사실(`@EnableAsync` 기본 PROXY 모드, sources jar 주석으로 확인) | **A-2** |
| H2 이벤트마다 전체 재계산, 병합 없음 | 사실. 1회당 원격 호출 6회(ES 5 + Gemini 1), `lastUpdatedAt`은 쓰기만 하고 읽지 않음 | **A-3** (N은 `[미정]`) |
| H3 가중치 0 이벤트가 1회 반복·경험 상품에 포함 | 반복 부분은 문서 예시와 어긋나는 사실 → **A-4**. 경험 상품 포함 여부는 정책 | A-4 · 판단 항목 1 |
| H4 반복 횟수 상한 없음 | 사실. 임베딩 입력 한도는 확인 못 함 | 판단 항목 2 |
| H5 시간 감쇠 주석("7일에 0.5배")과 식(e⁻¹≈0.37) 불일치 | 사실. 어느 쪽이 의도인지는 정책 | 판단 항목 3 |
| H6 `source*LogIds` 3필드 쓰기만 | 사실(읽는 곳 0건) | **B-1** |
| H7 페이징 없는 저장소 메서드 3개·ErrorCode 2개 미사용 | 사실 | **B-2** |
| H8 cart·order 팩토리 에러 핸들러·역직렬화 보호 없음 | 사실. 기본값은 0ms 간격 10회 시도, 깨진 레코드는 파티션을 영구히 막음(바이트코드) | **A-5** |
| H9 `ORDER_CANCELLED` 분기 도달 불가 | 사실(발행부 0건) | 판단 항목 4 |
| H10 실행자 거절 정책·`initialize()` 중복 | 사실이나 현재 해가 없음 | **C-1** (A-2 설계에 반영) |
| H11 `quantity` 의미별 같은 가중치 식 | 사실. 정책 | 판단 항목 5 |

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 장바구니 행동 가중치가 문자열 불일치로 전부 1 — 담기 2배·삭제 0이 아니라 셋 다 같은 비중

- **Priority**: Critical
- **Category**: Architecture (계산 결함)
- **문제**: 컨슈머는 로그에 `"ADD"`·`"REMOVE"`·`"UPDATE"`를 저장하는데, 가중치 함수는 `"CART_ITEM_ADDED"`·`"CART_QUANTITY_UPDATED"`·`"CART_ITEM_REMOVED"`와 비교한다. 어떤 장바구니 로그도 `case`에 걸리지 않아 항상 `default -> 1`이다. 같은 함수를 선호 카테고리 계산도 쓰므로, "담은 상품 카테고리 2점·뺀 상품 카테고리 0점"이어야 할 합산도 전부 1점으로 평탄해진다. 주문 쪽은 저장값과 비교값이 같아 의도대로 동작한다(H0 검증 — 사실)
- **근거**:
  - `CartEventConsumer.java:46,63,80` — `saveCartEventLog(..., "ADD"/"REMOVE"/"UPDATE", ...)`
  - `UserProfileEmbeddingService.java:208-215` — `switch (eventType) { case "CART_ITEM_ADDED" -> 2; case "CART_QUANTITY_UPDATED" -> 1; case "CART_ITEM_REMOVED" -> 0; default -> 1; }`
  - `UserProfileEmbeddingService.java:256` — `calculatePreferredCategoryCode`도 `calculateCartEventWeight`를 호출
  - `OrderEventConsumer.java:67,94` vs `UserProfileEmbeddingService.java:218-224` — 주문은 `"ORDER_CONFIRMED"`·`"ORDER_CANCELLED"`로 일치
  - 의도한 값의 SSOT: [`taste-vector-pipeline.md`](../../specs/taste-vector/taste-vector-pipeline.md) 비즈니스 규칙 "행동 가중치 (의도)" 행, [`glossary.md`](../../product/glossary.md) "행동 가중치", [`EMBEDDING_EXAMPLE.md`](../../EMBEDDING_EXAMPLE.md) 2단계 표
  - 발행 측 enum `HistoryEventType`(`baro-common/.../HistoryEventType.java:3-9`)은 이미 `CartLogEvent.event()`로 baro-ai에 들어와 있다(`CartLogEvent.java:9`) — 컨슈머가 그 이름을 버리고 별도 문자열로 바꿔 저장하는 것이 불일치의 출발점이다
  - `OrderLogDocument.java:36` 주석은 `ORDER_CREATED`라고 적혀 있으나 실제 저장값은 `ORDER_CONFIRMED` — 같은 뿌리(값을 문자열로 손으로 옮김)의 stale 주석
- **왜 문제인가**: 삭제한 상품이 담은 상품과 같은 비중으로 취향 벡터와 선호 카테고리에 들어간다. 스펙·용어집·예시 문서 세 곳이 전부 "담기 2·삭제 0"을 현행으로 적고 있어 STOP §1(문서·구현 정합) 위반이기도 하다
- **개선 방법**: 값을 손으로 옮기지 않고 발행 측 enum 하나를 끝까지 쓴다
  1. `LogWriteService.saveCartEventLog`·`saveOrderEventLog`의 `String eventType`을 `HistoryEventType`으로 바꾸고, 컨슈머는 `event.event()`를 그대로 넘긴다. 그러면 `CartEventConsumer`의 세 `case`가 저장 문자열만 다른 복사본이므로 `switch`가 사라지고 도메인 검사(`event.event().getDomain() == CART`) + 저장 1회로 줄어든다. `convertToInstant` 헬퍼(두 컨슈머에 같은 2줄)와 시그니처의 완전수식 타입명(`java.util.UUID`)도 같이 정리한다
  2. `CartLogDocument.eventType`·`OrderLogDocument.eventType`을 `HistoryEventType` 타입으로 바꾼다(`@Field(Keyword)` 유지). Spring Data Elasticsearch 5.3.6은 enum을 이름 문자열로 쓴다(`MappingElasticsearchConverter` 바이트코드에 `java/lang/Enum` 특수 처리가 있음 — 구현 시 `GET cart_event_logs/_search`로 저장 JSON을 한 번 확인). 확인이 안 되면 대안은 `String` 유지 + `event.event().name()` 저장이다
  3. `calculateCartEventWeight`·`calculateOrderEventWeight`를 enum `switch`로 바꾼다. 값(2·1·0 / 3·0)은 그대로 — 정책 변경이 아니다. `default` 분기는 enum이라 필요 없어지고, 새 이벤트 종류가 baro-common에 추가되면 컴파일러가 알려준다
  4. 기존 `cart_event_logs`·`order_event_logs` 문서는 `"ADD"` 같은 옛 값이라 enum 변환에 실패한다. dev 모드이므로 두 인덱스를 지우고 다시 만든다 — 삭제는 사용자가 직접 실행(STOP §3). 지우지 않으려면 2번 대안(`String` 유지)을 택하면 옛 값은 지금처럼 `default -> 1`로 남는다
  5. 대표 텍스트를 DEBUG 한 줄로 남긴다(`log.debug("대표 텍스트({}자): {}", ...)`) — 지금은 길이만 찍어(`:289`) 가중치가 실제로 어떻게 반영됐는지 볼 방법이 없다
  6. 문서 amend: 스펙 "행동 가중치 (실제)" 행 삭제, 용어집 "행동 가중치"의 결함 서술 삭제, `OrderLogDocument:36`·`CartLogDocument:36` 주석
- **검증 방법** (로컬 수동): 같은 사용자로 검색 1회 + `CART_ITEM_ADDED`(수량 1) 1건 + `CART_ITEM_REMOVED`(수량 1) 1건을 흘려 게이트(로그 3개)를 넘긴 뒤 위 5번 DEBUG 줄을 본다. 1시간 안의 이벤트는 시간 가중치 3.0이므로 — Before: 담은 상품 3회(1×1×3.0), 뺀 상품 3회. After: 담은 상품 **6회**(2×1×3.0), 뺀 상품 1회(A-4까지 반영하면 0회). `GET user_profile_embeddings/_doc/{userId}`의 `preferredCategoryCode`가 담은 상품 카테고리인지도 확인(Before는 두 카테고리가 동점이라 `HashMap` 순서에 따라 아무거나 나온다)
- **API 영향**: No Impact (추천 API 응답 형식 불변 — 결과 순서는 바뀐다)
- **예상 변경 파일**: `event/consumer/CartEventConsumer.java`, `event/consumer/OrderEventConsumer.java`, `log/application/LogWriteService.java`, `log/domain/CartLogDocument.java`, `log/domain/OrderLogDocument.java`, `embedding/application/UserProfileEmbeddingService.java`, `docs/specs/taste-vector/taste-vector-pipeline.md`, `docs/product/glossary.md`, `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`(§3 로그 인덱스 eventType 값)
- **예상 변경 라인 수**: 코드 −60 / +25 (컨슈머 `switch` 삭제가 대부분), 문서 ±10
- **위험도**: Low — 값은 그대로이고 비교 방식만 바뀐다. 로그 인덱스 재생성이 필요하다(4번)
- **테스트 영향도**: 자동화 테스트 없음 — 위 수동 절차로 대체
- **예상 효과**: 문서대로 동작 · 컨슈머 코드 1/3 · 이벤트 종류 추가 시 컴파일 시점 검출

### A-2. `@Async`가 자기 호출이라 컨슈머 두 곳은 리스너 스레드에서 동기로 임베딩을 돈다

- **Priority**: High
- **Category**: Spring / Performance
- **문제**: `CartEventConsumer`·`OrderEventConsumer`는 `@Async("profileUpdateExecutor")`가 붙은 `updateUserProfileAsync`를 **같은 클래스 안에서** 호출한다. Spring의 `@Async`는 프록시 모드에서 프록시를 거친 호출만 가로채므로, `this.updateUserProfileAsync(...)`는 그냥 메서드 호출이고 Gemini 임베딩(호출당 최대 30초 + 재시도)이 Kafka 리스너 스레드에서 끝날 때까지 다음 레코드를 처리하지 못한다. 검색 경로만 다른 빈(`ProductIndexService`)을 거쳐 실제로 비동기다(H1 검증 — 사실)
- **근거**:
  - `CartEventConsumer.java:53,70,87` · `OrderEventConsumer.java:78,105` — 같은 클래스의 `updateUserProfileAsync(...)` 직접 호출; `@Async`는 `:108-109` / `:116-117`
  - `AsyncConfig.java:14` — `@EnableAsync`에 `mode` 지정 없음 → 기본 `AdviceMode.PROXY`. 설치된 `spring-context-6.2.14-sources.jar`의 `EnableAsync.java:197-199`: "The default is AdviceMode.PROXY. Please note that proxy mode allows for interception of calls through the proxy only. Local calls within the same class cannot get intercepted that way"
  - `ProductSearchService.java:99` — `productIndexService.updateUserProfileAsync(userId)`는 다른 빈 호출이라 프록시를 탄다(검색 경로는 비동기)
  - 스펙 "수동 확인" 항목이 이미 "현재 컨슈머 경로는 리스너 스레드에서 돈다"고 관찰을 적어 두었다(`taste-vector-pipeline.md:126`)
  - 위험을 키우는 설정: `KafkaConsumerConfig.java:106,130` `auto-offset-reset=earliest` + `max.poll.records` 미지정(기본 500). 새 컨슈머 그룹이 토픽을 처음부터 재생하면 한 poll에 최대 500건이 오고, 건마다 임베딩 30초를 동기로 기다리면 `max.poll.interval.ms`(기본 5분)를 넘겨 그룹에서 쫓겨나고 같은 묶음을 다시 받는 반복에 빠진다 — [`search/001`](../search/001-index-path.md) A-1이 product 컨슈머에서 같은 계산으로 `max.poll.records=1`을 정한 이유와 같다
- **왜 문제인가**: 컨슈머 지연(lag)이 Gemini 응답 시간에 그대로 묶인다. 포트폴리오 서술("비동기 처리로 Consumer Lag 해소")과 반대로 동작하고 있어 STOP §1 대상이다. `@Async` 메서드가 세 클래스에 복사돼 있는 것(`CartEventConsumer:108-119`, `OrderEventConsumer:116-128`, `ProductIndexService:108-119` — 로그 접두사만 다름)도 이 결함의 원인이다
- **개선 방법**: 비동기 진입점을 한 곳으로 모은다
  1. `embedding.application`에 `UserProfileRefreshTrigger`(이름은 구현 시 결정) 빈 하나를 두고 `@Async("profileUpdateExecutor") public void refresh(UUID userId)` 메서드 하나만 갖게 한다. 본문은 지금 세 곳에 복사된 try/catch-WARN 그대로. 세 호출부(`CartEventConsumer`, `OrderEventConsumer`, `ProductSearchService`)는 이 빈을 주입받아 호출하고, 각자의 `updateUserProfileAsync`는 삭제한다(STOP §4 — `ProductIndexService`의 것도 같이 지운다. keyword-search B-1이 이걸 기다리고 있다)
  2. **거절 처리**: `profileUpdateExecutor`는 기본 `AbortPolicy`라(C-1) 큐 100이 차면 호출자 스레드에 `TaskRejectedException`이 **동기로** 던져진다. 컨슈머의 호출은 `onMessage`의 `try` 안에 있고 그 `catch`는 예외를 다시 던지므로(`CartEventConsumer:93-97`), 거절이 이벤트 실패로 번져 이미 저장한 로그를 재시도마다 다시 저장하게 된다(A-5 "재시도 = 중복 로그"). 호출을 `try` 밖으로 빼거나 트리거 빈 호출부에서 `TaskRejectedException`만 잡아 WARN으로 낮춘다 — 프로필 갱신 실패는 이벤트 처리 결과에 영향이 없다는 현행 정책(`taste-vector-pipeline.md:108`)과 같다
  3. 인터페이스·추상 클래스는 만들지 않는다(구현체 1개, `spring-boot-java.md`)
- **검증 방법** (로컬 수동): 장바구니 이벤트 1건 발행 후 `UserProfileEmbeddingService.java:143`의 INFO("프로필 벡터를 성공적으로 업데이트")가 찍힌 스레드 이름을 본다. Before: `KafkaListenerEndpointContainer#…-C-1`. After: `profile-update-1`. 같은 로그의 타임스탬프와 컨슈머의 마지막 INFO 사이 간격이 Before는 임베딩 시간(수 초)만큼 벌어지고 After는 거의 0이다. actuator가 이미 들어와 있으므로(`build.gradle:80`, cross-cutting/001 B-1) `curl localhost:8092/actuator/metrics/executor.completed?tag=name:profileUpdateExecutor`의 값이 이벤트 수만큼 늘어나는지도 본다 — Boot의 `TaskExecutorMetricsAutoConfiguration`이 `ThreadPoolTaskExecutor` 빈을 자동 계측한다(구현 시 지표 이름 확인)
- **API 영향**: No Impact
- **예상 변경 파일**: `embedding/application/UserProfileRefreshTrigger.java`(신규), `event/consumer/CartEventConsumer.java`, `event/consumer/OrderEventConsumer.java`, `search/application/ProductIndexService.java`, `search/application/ProductSearchService.java`, `docs/specs/taste-vector/taste-vector-pipeline.md`(흐름 3·4 행, "비동기 실행" 규칙 행)
- **예상 변경 라인 수**: 코드 +25 / −40, 문서 ±5
- **위험도**: Low — 검색 경로가 이미 같은 방식으로 돌고 있다. 컨슈머 실패 시 재시도 대상에서 프로필 갱신이 빠지는 것은 현행과 같다(지금도 안에서 삼킨다)
- **테스트 영향도**: 자동화 테스트 없음 — 위 수동 절차로 대체
- **예상 효과**: 컨슈머 처리 시간에서 임베딩 시간(최대 30초 × 재시도) 제거 · `@Async` 복사본 3 → 1 · keyword-search B-1 선행 조건 해소

### A-3. 검색 1회·이벤트 1건마다 프로필을 통째로 다시 계산한다 — 원격 호출 6회, 합치거나 미루는 장치 없음

> **2026-09-05 결정**: 아래 "개선 방법" (가)·(나)는 채택하지 않는다. 문제 서술·근거는 유효하지만, 사용자가 "더 나은 프로필 계산 방식이 있으면 방향을 바꿔도 된다"고 열어 두어 **프로필 계산 자체를 재설계**하기로 했다 — 문서 끝 "승인 결과" 절 참고. 재설계 후에는 갱신 1회에 Gemini 호출이 0회가 되어 N분 스킵(판단 항목 6)이 필요 없어진다.

- **Priority**: High
- **Category**: Performance
- **문제**: 트리거 3곳(검색·장바구니·주문) 어디서 오든 `updateUserProfileEmbedding`은 매번 ES 조회 3회 + Gemini 임베딩 1회 + ES 저장 1회(+ 저장 뒤 자동 refresh 1회)를 수행한다. 사용자가 연속으로 5번 검색하면 임베딩 5회, 장바구니에서 수량을 세 번 바꾸면 임베딩 3회다. 결과는 매번 "최근 로그 5개씩"으로 거의 같은 벡터를 덮어쓴다. 문서에 `lastUpdatedAt`이 있지만 읽는 코드가 없어 "방금 갱신했으면 건너뛰기"조차 없다(H2 검증 — 사실)
- **근거**:
  - 호출 횟수(코드): `UserProfileEmbeddingService.java:63-68` ES 검색 3회 → `:117`(`embedText`, `:292` `embeddingModel.embed`) Gemini 1회 → `:141` `save`. Spring Data ES 5.3.6 `SimpleElasticsearchRepository.save`는 `executeAndRefresh` → `doRefresh` → 템플릿에 refresh 정책이 없으면 `IndexOperations.refresh()`를 추가로 호출한다(바이트코드 `SimpleElasticsearchRepository.doRefresh`). 즉 저장 1회 = ES 요청 2회. 로그 저장(`LogWriteService:48,73,90`)도 같은 저장소라 이벤트 1건 = 로그 저장 2회 + 재계산 6회 = **원격 8회**(ES 7 + Gemini 1)
  - 트리거: `ProductSearchService.java:99`(검색마다), `CartEventConsumer.java:53,70,87`(이벤트마다), `OrderEventConsumer.java:78,105`(주문마다). 어디에도 조건이 없다
  - `grep -rn 'getLastUpdatedAt\|lastUpdatedAt' baro-ai/src/main/java` — 쓰는 곳 `UserProfileEmbeddingService.java:130`·문서 `:27-28` 뿐, 읽는 곳 0건
  - 재현 가능한 관찰(스펙 "수동 확인" 항목 그대로): 같은 사용자로 검색 5회 → `org.springframework.ai` DEBUG에 임베딩 요청 5회
  - A-2를 반영해 컨슈머 경로가 비동기가 되면 **같은 사용자의 재계산 2개가 동시에** 돌 수 있다. 먼저 시작한 쪽이 Gemini 지연으로 늦게 끝나면 나중 로그가 빠진 벡터로 덮어쓴다(lost update). 지금은 컨슈머가 동기라 파티션 순서(같은 userId = 같은 파티션, `HistoryLogWriter.java:78`)가 이걸 막아 주고 있었다 — A-2와 A-3을 같이 반영해야 하는 이유
- **왜 문제인가**: 이 단위에서 가장 비싼 호출(Gemini, 호출당 최대 30초·무료 티어 분당 한도)이 사용자 행동 횟수에 1:1로 묶여 있다. 벡터 품질은 그대로인데 비용·지연·429 확률만 늘어난다. 캐싱·비동기가 이 이니셔티브의 중심축(`baro-ai-performance-refactor.md`)이고, 이 항목이 그 대상이다
- **개선 방법** (가장 단순한 것부터 — 구현은 (가) 하나로 시작하고, (나)는 A-2 반영 후 실제로 동시 실행이 관찰될 때만):
  - (가) **최근 갱신 스킵**: 재계산 시작 시 `userProfileEmbeddingRepository.findById(userId)`(ES get 1회, 가장 싼 호출)로 `lastUpdatedAt`을 읽고 N분 안이면 건너뛴다. `lastUpdatedAt` 필드가 이미 있고 저장도 되고 있으므로 매핑 변경이 없다. **N은 `[미정]`**(판단 항목 6) — 값은 사용자 결정이며, 이 스킵은 "N분 동안은 새 행동이 벡터에 반영되지 않는다"는 신선도 정책 변경이라 승인 대상이다
  - (나) **사용자별 단일 실행**: 트리거 빈(A-2)에 `ConcurrentHashMap.newKeySet()`으로 "지금 계산 중인 userId"를 두고, 이미 있으면 제출하지 않는다. 계산이 끝나면 지운다. Redis·외부 큐는 쓰지 않는다(D-1)
  - 두 방식 다 재계산 함수 자체(`updateUserProfileEmbedding`)는 건드리지 않는다 — 계산식 불변
- **검증 방법** (로컬 수동): 같은 사용자로 검색 5회를 N분 안에 연속 호출. Before: `curl localhost:8092/actuator/metrics/gen_ai.client.operation` 또는 `org.springframework.ai` DEBUG의 임베딩 요청 5회. After: 1회 + "최근 갱신이라 건너뜀" INFO 4줄. N분 뒤 검색 1회 → 다시 1회 계산되는지 확인
- **API 영향**: No Impact
- **예상 변경 파일**: `embedding/application/UserProfileEmbeddingService.java`(또는 A-2의 트리거 빈), `docs/specs/taste-vector/taste-vector-pipeline.md`("갱신 트리거" 규칙 행 amend + N 값)
- **예상 변경 라인 수**: (가) +12, (나) +10
- **위험도**: Medium — 신선도 정책이 바뀐다. N이 크면 "방금 담은 상품이 추천에서 제외되지 않는" 창이 생긴다(경험 상품 목록도 같은 문서에 있으므로)
- **테스트 영향도**: 자동화 테스트 없음 — 위 수동 절차로 대체
- **예상 효과**: 연속 행동 시 Gemini 호출 횟수를 행동 수가 아니라 N분당 1회로 상한 · A-2 이후의 동시 갱신 경쟁 제거

### A-4. 가중치 0인 이벤트(삭제·취소)가 `max(1, …)` 때문에 대표 텍스트에 1회 들어간다 — 예시 문서는 "0번(제외)"

- **Priority**: Medium
- **Category**: Architecture (계산 결함)
- **문제**: 반복 횟수 식이 `Math.max(1L, round(행동 × 수량 × 시간))`이라 행동 가중치가 0이어도 최소 1회 반복된다. 삭제한 상품명·취소한 주문 상품명이 취향 벡터에 들어간다. A-1 이전에는 장바구니 삭제가 애초에 가중치 1이라 이 문제가 가려져 있었고, A-1을 반영하면 이 항목이 남는 차이가 된다(H3 검증 — 반복 부분은 사실)
- **근거**:
  - `UserProfileEmbeddingService.java:189,201` — `Math.max(1L, Math.round((double) eventWeight * quantityWeight * timeWeight))`; `eventWeight`가 0이면 곱이 0 → 1
  - 의도의 SSOT: [`EMBEDDING_EXAMPLE.md:110`](../../EMBEDDING_EXAMPLE.md) — `CART_ITEM_REMOVED` 행 "0 × 1 × 1.2 = 0 | 0번 (제외)"; [`glossary.md`](../../product/glossary.md) "행동 가중치: … 취소·삭제는 0"
  - 반면 선호 카테고리 합산(`:260,275`)에는 `max(1, …)`가 없어 0이 0으로 들어간다 — 같은 가중치가 두 계산에서 다르게 작용한다
  - 검색 로그와 양의 가중치에는 `max(1, …)`가 필요하다: 16일 넘은 로그는 시간 가중치 0.3이라 `round(1×1×0.3)=0`이 되어 로그가 통째로 사라지기 때문(`:232-237` — 0.3 하한은 `hoursAgo ≥ 168·ln10 ≈ 387시간`부터). 따라서 `max`를 없애는 게 아니라 **행동 가중치 0을 먼저 걸러야** 한다
- **왜 문제인가**: 사용자가 장바구니에서 뺀 상품이 벡터에 남아 비슷한 상품이 계속 추천된다. 문서 세 곳이 "제외"를 현행으로 적고 있다(STOP §1)
- **개선 방법**: `expandCartProductWithFullWeight`·`expandOrderProductWithFullWeight` 첫 줄에 `if (eventWeight == 0) return Stream.empty();`를 넣는다(2줄). `max(1, …)`는 그대로 둔다. `sourceProductIds`(경험 상품)에서도 뺄지는 정책이라 여기 넣지 않는다(판단 항목 1)
- **검증 방법** (로컬 수동): A-1 검증 절차의 DEBUG 대표 텍스트에서 뺀 상품명 — Before(A-1 반영 후): 1회, After: 0회. 주문 취소는 발행부가 없어(판단 항목 4) 재현 불가 — 코드 경로만 같다
- **API 영향**: No Impact
- **예상 변경 파일**: `embedding/application/UserProfileEmbeddingService.java`, `docs/specs/taste-vector/taste-vector-pipeline.md`("반복 횟수" 규칙 행, 검증 시나리오 미체크 항목)
- **예상 변경 라인 수**: 코드 +4, 문서 ±3
- **위험도**: Low — 가중치 0인 경우만 달라진다. 단 삭제 로그만 있는 사용자는 대표 텍스트가 비어 `:109-112`에서 "충분한 텍스트 없음"으로 저장을 건너뛰게 된다 — 스펙 검증 시나리오의 미체크 항목("장바구니 삭제 이벤트만 있는 사용자")이 이 동작이다
- **테스트 영향도**: 자동화 테스트 없음
- **예상 효과**: 문서대로 동작 · 반복 횟수와 카테고리 합산이 같은 규칙을 따름

### A-5. cart·order 컨슈머에 역직렬화 보호·에러 핸들러가 없다 — 깨진 레코드 1건이 파티션을 영구히 막고, 일시 장애는 0ms 간격 10회 재시도, 재시도는 중복 로그를 만든다

> **SSOT 분담**: 이 항목이 컨슈머 동작(재시도 간격·중복 로그·결정적 문서 ID)의 SSOT다. 역직렬화 래핑과 팩토리 코드 자체의 변경은 [`cross-cutting/002`](../cross-cutting/002-dependencies-config-and-dead-code.md) A-2·B-6(공통 팩토리)에서 한 번에 구현하며, 그때 이 항목의 백오프·ID 결정을 함께 반영한다.

- **Priority**: High
- **Category**: Architecture (Resilience)
- **문제**: product 팩토리는 `ErrorHandlingDeserializer` + `DefaultErrorHandler(FixedBackOff(1초, 2회))`를 명시하는데(`search/001` A-1), cart·order 팩토리는 둘 다 없다. 그래서 (1) 값 JSON을 `CartLogEvent`·`OrderLogEvent`로 못 바꾸는 레코드가 오면 `poll()` 자체가 예외를 던지고, 컨테이너는 그것을 로그로만 남긴 채 같은 offset을 다시 poll한다 — 그 파티션의 모든 이후 이벤트가 멈춘다. (2) 리스너 예외(ES 다운 등)는 기본값인 0ms 간격 10회 시도 뒤 건너뛴다 — 재시도 사이 대기가 없어 일시 장애를 넘길 수 없다. (3) 재시도는 같은 레코드를 다시 처리하는데 로그 문서 ID가 자동 생성이라 이미 저장된 로그가 한 번 더 저장된다 — 주문 이벤트는 상품마다 저장하므로 3번째 상품에서 실패하면 1·2번째가 중복된다(H8 검증 — 사실)
- **근거**:
  - `KafkaConsumerConfig.java:109-114,133-138` — `JsonDeserializer`를 바로 `DefaultKafkaConsumerFactory`에 넘김(`ErrorHandlingDeserializer` 없음); `:117-123,141-147` — `setCommonErrorHandler` 호출 없음. 대조: `:77-78,91-94`(product)
  - 기본 핸들러(설치된 `spring-kafka-3.3.11.jar` 바이트코드): `KafkaMessageListenerContainer$ListenerConsumer`는 핸들러가 없으면 `new DefaultErrorHandler()`를 만들고, 그 생성자는 `SeekUtils.DEFAULT_BACK_OFF` = `new FixedBackOff(0L, 9L)`(0ms 간격, 재시도 9 = 총 10회)를 쓴다
  - 깨진 레코드(바이트코드): `JsonDeserializer.deserialize`는 실패 시 `org.apache.kafka.common.errors.SerializationException`을 던지고, `kafka-clients-3.9.1`은 이를 `RecordDeserializationException`(같은 계열)으로 `poll()`에서 던진다. `ListenerConsumer.handleConsumerException`은 `CommonErrorHandler.handleOtherException`을 부르는데 `DefaultErrorHandler.handleOtherException`은 `instanceof SerializationException`이면 `IllegalStateException`("ErrorHandlingDeserializer를 쓰라"는 안내)을 던지고, 컨테이너는 그것을 "Consumer exception" ERROR로 로그한 뒤 루프를 계속한다. offset이 전진하지 않으므로 같은 레코드를 무한 반복한다. `DeserializationException`은 `ExceptionClassifier` 기본 치명(재시도 없음) 목록에 있어, 래퍼만 씌우면 1회 로그 후 건너뛴다
  - 현실적인 발생 경로: `CartLogEvent.event`는 `HistoryEventType` enum이다(`CartLogEvent.java:9`). 5인 저장소에서 발행 모듈이 baro-common enum에 값을 추가하고 baro-ai를 재배포하기 전에 그 이벤트가 오면 Jackson이 알 수 없는 enum 값으로 실패한다 — 코드 변경 없이도 생길 수 있는 poison pill
  - 중복 로그: `CartLogDocument.java:18-19`·`OrderLogDocument.java:18-19` `@Id private String id`를 어디서도 채우지 않는다(ES 자동 ID). `OrderEventConsumer.java:60-72`는 상품별로 `save`를 반복하고 `:46-51`의 `catch`가 예외를 다시 던진다
- **왜 문제인가**: 장바구니·주문 로그는 취향 벡터의 입력 전부다. 파티션 하나가 막히면 그 파티션의 사용자 전원의 프로필이 조용히 멈추고, 로그에는 같은 ERROR만 반복돼 원인을 찾기 어렵다. 0ms 재시도는 ES가 잠깐 흔들릴 때 10번을 즉시 두드리고 포기하므로 재시도가 있으나 마나다
- **개선 방법**:
  1. cart·order 두 `ConsumerFactory`의 `JsonDeserializer`를 `ErrorHandlingDeserializer`로 감싼다. product처럼 `null`로 바꾸지 않고 그대로 두면 `DeserializationException`이 리스너 대신 에러 핸들러로 가서 치명 예외로 분류돼 **재시도 없이 1회 로그 후 건너뛴다** — 리스너에 `null`이 들어오는 경우를 만들지 않아 더 단순하다
  2. 두 `ListenerContainerFactory`에 `DefaultErrorHandler(new FixedBackOff(1_000L, 2L))`를 명시한다. 값은 product와 같다 — 근거가 같고(ES·Gemini 일시 장애) DLQ를 두지 않는 결정도 같다(`search/001` D-1). 상수 `PRODUCT_RETRY_*`는 세 팩토리가 공유하므로 이름에서 `PRODUCT_`를 뗀다
  3. 로그 문서 ID를 레코드에서 결정적으로 만든다: 리스너에 `@Header(KafkaHeaders.RECEIVED_PARTITION)`·`@Header(KafkaHeaders.OFFSET)`를 받아 `"{partition}-{offset}"`(주문은 `"-{productId}"` 추가)을 `id`로 넣는다. 같은 레코드 재처리는 같은 문서를 덮어쓰므로 중복이 없다. 도메인 규칙은 바뀌지 않는다
  4. 팩토리 4개(product·cart·order·review)가 같은 모양이라 공통 헬퍼로 묶고 싶어지지만, review 팩토리는 팀원 소유라 **여기서 하지 않는다** — cross-cutting/002 후보(C-2). 이번 변경은 cart·order 팩토리에 값을 명시하는 것까지
  5. 문서 amend: 스펙 "컨슈머 실패 처리" 행
- **검증 방법** (로컬 수동):
  - 깨진 레코드: `kafka-console-producer --topic cart-events`로 `{not json` 1줄을 넣고 곧이어 정상 이벤트 1건. Before: "Consumer exception" ERROR가 계속 반복되고 정상 이벤트의 `[CART_CONSUMER] Received` 로그가 안 찍힌다. After: `DeserializationException` ERROR 1회 후 정상 이벤트가 처리된다
  - 재시도 간격: ES 컨테이너를 잠시 멈추고 정상 이벤트 1건. Before: `[CART_CONSUMER] Failed to process` ERROR 10줄이 같은 초에 찍힘. After: 1초 간격 3줄
  - 중복: 위 절차 뒤 ES를 켜고 같은 offset이 재처리되지 않았는지(재시도 소진 후 건너뛰므로 없음) 확인하고, 주문 이벤트 상품 3개 중 2번째 저장만 실패하도록 할 방법이 없으므로 중복 방지는 `GET cart_event_logs/_search`에서 `_id`가 `{partition}-{offset}` 형식인지로 대신 확인한다
- **API 영향**: No Impact
- **예상 변경 파일**: `config/KafkaConsumerConfig.java`, `event/consumer/CartEventConsumer.java`, `event/consumer/OrderEventConsumer.java`, `log/application/LogWriteService.java`(id 파라미터), `docs/specs/taste-vector/taste-vector-pipeline.md`
- **예상 변경 라인 수**: 코드 +30, 문서 ±3
- **위험도**: Low — product 팩토리에서 이미 같은 설정이 돌고 있다. ID 형식 변경은 기존 로그 문서와 섞여도 문제없다(자동 ID와 충돌하지 않음)
- **테스트 영향도**: 자동화 테스트 없음 — 위 수동 절차로 대체
- **예상 효과**: 깨진 레코드 1건이 로그 수집 전체를 멈추는 경로 제거 · 일시 장애 내성 · 재시도가 안전해짐(멱등)

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `UserProfileEmbeddingDocument`의 `source*LogIds` 3필드는 쓰기만 하고, `sourceProductIds`는 UUID를 문자열로 바꿨다가 다시 UUID로 되돌린다

- **Priority**: Medium
- **Category**: Dead Code
- **문제**: `sourceSearchLogIds`·`sourceCartLogIds`·`sourceOrderLogIds`는 "동일성/재현성 확보"라는 주석과 함께 저장되지만 읽는 코드가 없다. 문서마다 최대 15개 keyword를 매 갱신에 쓰는 셈이다. `sourceProductIds`는 `UUID → String`으로 바꿔 저장하고(`:85-86`) 추천에서 `String → UUID`로 되돌린다(`PersonalizedRecommendService.java:69-71`) — `CartLogDocument.userId`처럼 `UUID`를 `@Field(Keyword)`로 그대로 쓰는 전례가 같은 단위에 있다(H6 검증 — 사실)
- **근거**: `grep -rn 'sourceSearchLogIds\|sourceCartLogIds\|sourceOrderLogIds' baro-ai/src/main/java` — 쓰는 곳 `UserProfileEmbeddingService.java:71-81,132-134`·`UserProfileEmbeddingDocument.java:30-38`뿐, getter 호출 0건. `sourceProductIds` 변환 두 곳 `UserProfileEmbeddingService.java:84-89`, `PersonalizedRecommendService.java:69-71`. 문서: `ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md:98-100`, 스펙 데이터 모델 표(`:70,72`)
- **왜 문제인가**: 읽는 사람이 "재현성 기능이 있다"고 오해한다(문서도 그렇게 적혀 있다). 변환 코드는 형식만 바꾸는 코드라 지울수록 좋다
- **개선 방법**: 세 필드와 `:71-81`의 수집 코드를 지운다. `sourceProductIds`를 `List<UUID>`로 바꾸고 변환 두 곳을 지운다. `PersonalizedRecommendService.java:30`과 `:46`의 벡터 null 검사가 겹치므로(`:30`이 이미 null을 걸렀는데 `:46`이 null·empty를 다시 검사) 하나로 합친다. 매핑이 바뀌므로 `user_profile_embeddings` 인덱스를 지우고 다시 만든다(dev 모드, 사용자 실행). 문서 두 곳 amend. **순서**: vector-similarity B-3·B-4가 같은 두 파일을 만지므로 그쪽 반영 전후 어느 한쪽으로 몰아서 한다
- **검증 방법**: 컴파일 + 이벤트 1건 후 `GET user_profile_embeddings/_doc/{userId}`에 세 필드가 없고 `sourceProductIds`가 UUID 문자열 배열인지, 추천 API가 그 상품을 제외하는지 확인
- **API 영향**: No Impact
- **예상 변경 파일**: `embedding/domain/UserProfileEmbeddingDocument.java`, `embedding/application/UserProfileEmbeddingService.java`, `recommend/application/PersonalizedRecommendService.java`, `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`, `docs/specs/taste-vector/taste-vector-pipeline.md`
- **예상 변경 라인 수**: 코드 −30, 문서 −5
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 문서당 저장 필드 3개 감소 · 변환 코드 제거 · 문서-구현 정합

### B-2. 페이징 없는 저장소 메서드 3개와 던지지 않는 `EmbeddingErrorCode` 2개

- **Priority**: Low
- **Category**: Dead Code
- **문제**: `findAllByUserIdAndOccurredAtAfter`(cart·order)·`findAllByUserIdAndSearchedAtAfter`(search)는 페이징 버전(`…OrderBy…Desc(…, Pageable)`)만 쓰이고 호출부가 없다. `EmbeddingErrorCode.INSUFFICIENT_LOGS`·`USER_PROFILE_NOT_FOUND`는 어디서도 던지지 않는다 — 게이트 미달은 `return`(`:95-101`), 프로필 없음은 추천이 빈 배열(`PersonalizedRecommendService:30-33`)로 처리한다. 같은 이름의 `RecommendErrorCode.USER_PROFILE_NOT_FOUND`도 있는데 그쪽은 vector-similarity B-1 소관(H7 검증 — 사실)
- **근거**: `grep -rn 'findAllByUserIdAndOccurredAtAfter(\|findAllByUserIdAndSearchedAtAfter(' baro-ai/src/main/java` — 선언 3건만; `grep -rn 'INSUFFICIENT_LOGS\|USER_PROFILE_NOT_FOUND' baro-ai/src/main/java` — enum 선언 외 `EmbeddingErrorCode` 참조 0건. `EMBEDDING_GENERATION_FAILED`는 `UserProfileEmbeddingService:312`·`ProductEmbeddingService:42`가 쓰므로 남긴다
- **왜 문제인가**: 페이징 없는 메서드는 사용자 로그가 쌓이면 30일치를 전부 가져오는 함정이고, ErrorCode는 "이 상황에서 400/404가 난다"는 잘못된 계약 신호다
- **개선 방법**: 메서드 3개·enum 값 2개 삭제. STOP §2 대상이지만 스펙 API 표에 에러 표가 없고(프로필 없음 = 빈 배열) Swagger에도 노출된 적이 없으므로 enum 삭제로 끝난다
- **검증 방법**: 컴파일
- **API 영향**: No Impact
- **예상 변경 파일**: `log/infrastructure/elasticsearch/CartLogRepository.java`·`OrderLogRepository.java`·`SearchLogRepository.java`, `embedding/exception/EmbeddingErrorCode.java`
- **예상 변경 라인 수**: −8
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 잘못된 계약 신호 제거

### B-3. `embedText`가 빈 결과를 `RuntimeException`으로 던졌다가 `CustomException`으로 다시 싸고, 실패를 두 번 ERROR로 남긴다

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `:294-297`은 빈 결과에 `RuntimeException`을 던지고 `:310-313`의 `catch (Exception)`이 그것을 잡아 ERROR 로그 후 `CustomException(EMBEDDING_GENERATION_FAILED)`으로 바꾼다. 호출부 `:118-120`이 그 예외를 다시 ERROR로 남기고 `return`한다 — 실패 1건에 스택 포함 ERROR 2줄. `CustomException`은 HTTP 응답용 enum을 담지만 이 경로는 HTTP가 아니라 잡혀서 끝난다. [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) B-5가 `ProductEmbeddingService`의 같은 모양을 지적하며 "이 메서드는 취향 벡터 단위라 여기서 고치지 않는다"고 넘긴 것
- **근거**: `UserProfileEmbeddingService.java:294-297,310-313,118-120`
- **왜 문제인가**: 같은 실패가 두 번 찍혀 로그를 읽기 어렵고, `RuntimeException → CustomException` 재포장은 정보를 더하지 않는다
- **개선 방법**: 빈 결과 분기를 지우고(`embeddings.get(0)`이 던지는 예외가 같은 `catch`로 간다), `embedText`의 `catch`는 지우거나 WARN 한 줄(스택 없이)로 줄인다 — 스택은 호출부 `:119`가 남긴다. vector-similarity B-4(`float[]` 전환)가 `:299-303`을 바꾸므로 같은 턴에 한다
- **검증 방법**: 잘못된 API 키로 기동해 이벤트 1건 → ERROR가 1줄만 찍히는지
- **API 영향**: No Impact
- **예상 변경 파일**: `embedding/application/UserProfileEmbeddingService.java`
- **예상 변경 라인 수**: −10
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 실패 로그 1/2 · vector-similarity B-5와 같은 규칙

### B-4. 컨슈머가 장바구니 이벤트 1건에 INFO 4줄, 주문 1건에 INFO 3+N줄을 남긴다

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `CartEventConsumer`는 "수신"(`:33`)·"처리 시작"(`:39,56,73`)·"저장 완료"(`:50,67,84`)를 찍고 `LogWriteService`가 "저장"(`:49`)을 또 찍는다 — 같은 상품명이 4번. 주문은 "수신"(`:34`)·"처리 시작"(`:55`)·상품마다 "저장"(`LogWriteService:74`)·"완료"(`:74`). [`keyword-search/001`](../keyword-search/001-query-path.md) B-4가 product 컨슈머에서 지적한 것과 같은 패턴이다. `application-prod.yml:66`이 `com.barofarm.ai: DEBUG`라 전부 출력된다
- **근거**: 위 줄 번호. A-1이 `switch`를 없애면 "처리 시작"·"저장 완료" 3쌍은 자연히 1쌍이 된다
- **왜 문제인가**: A-5 재시도가 걸리면 시도마다 반복되고, `earliest`로 토픽을 재생하면 수천 줄이 된다
- **개선 방법**: 컨슈머는 완료 시점 INFO 1줄(종류·사용자·상품)만 남기고 수신·시작 줄은 DEBUG로 내리거나 지운다. `LogWriteService`의 INFO 3줄은 DEBUG로. A-1과 같은 파일이라 같은 턴에 한다
- **검증 방법**: 이벤트 1건 후 `[CART_CONSUMER]`·`[LOG_WRITE]` INFO 줄 수 Before(4)/After(1)
- **API 영향**: No Impact
- **예상 변경 파일**: `event/consumer/CartEventConsumer.java`, `event/consumer/OrderEventConsumer.java`, `log/application/LogWriteService.java`
- **예상 변경 라인 수**: −12
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 이벤트당 로그 1/4

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. `profileUpdateExecutor`의 거절 정책·`initialize()` 중복 — 지금은 해가 없고 A-2가 거절 처리를 흡수한다

`ExecutorConfigurationSupport`의 기본 거절 정책은 `AbortPolicy`다(`spring-context-6.2.14` 바이트코드). 큐 100이 차면 호출자에게 `TaskRejectedException`이 동기로 던져지는데, 검색 경로는 `ProductSearchService.java:92-103`의 `try/catch`가 잡아 "Failed to save search log"라는 **오해를 부르는 메시지**로 WARN을 남긴다(로그는 이미 저장됐다). `AsyncConfig.java:29`의 명시적 `initialize()`는 `afterPropertiesSet()`이 다시 호출해 `ThreadPoolExecutor`를 두 번 만들지만, 첫 번째는 스레드를 미리 띄우지 않아 그냥 버려진다 — 해는 없고 지워도 된다(H10 검증). **왜 지금 안 하는가**: 거절 처리는 A-2가 트리거 빈으로 옮기면서 같이 정리하고(A-2 개선 방법 2번), 메시지 수정도 그 파일 변경에 포함된다. 별도 항목으로 둘 만큼의 변경이 아니다.

### C-2. cart·order 팩토리의 `group-id` 이중 지정과 팩토리 4중복 — cross-cutting/002 후보

`@KafkaListener(groupId = "ai-service-cart")`(`CartEventConsumer:27`)가 팩토리의 `groupId + "-cart"`(`KafkaConsumerConfig:105`)를 덮어쓴다. 지금은 `spring.kafka.consumer.group-id=ai-service`(`application-prod.yml:30`)라 두 값이 같아 문제가 없지만 한쪽만 바꾸면 조용히 어긋난다. 팩토리 4개(product·cart·order·review)가 역직렬화기·신뢰 패키지·오프셋 정책만 다른 복사본인 것도 같은 파일 문제다. **왜 지금 안 하는가**: review 팩토리가 팀원 소유라 공유 설정 개편은 사용자 확인 후 cross-cutting 라운드에서 한다(`baro-ai-performance-refactor.md` 범위 표). 이 문서는 A-5로 cart·order에 값을 명시하는 데까지만 간다.

### C-3. 자동화 테스트 부재 — 2026-09-04 사용자 결정으로 제외

가중치 함수(`calculateCartEventWeight`·`calculateTimeWeight`)와 대표 텍스트 조립은 입출력이 고정된 순수 함수라, 테스트를 들이기로 하면 A-1·A-4의 Before/After가 그대로 첫 테스트가 된다. 이번 라운드의 검증은 전부 수동 절차로 적었다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 디바운스를 Redis·외부 큐로 구현하지 않음

A-3의 (가)는 이미 있는 문서 필드 하나를 읽는 것이고, (나)는 인스턴스 안의 집합 하나다. baro-ai 인스턴스가 여럿이면 (나)는 인스턴스별로 따로 동작하지만, 같은 userId는 같은 Kafka 파티션 → 같은 인스턴스로 가므로(`HistoryLogWriter.java:78` 키 = userId) 컨슈머 경로에서는 그것으로 충분하다. 검색 경로는 게이트웨이 라우팅에 따라 다른 인스턴스로 갈 수 있으나, 그 경우에도 (가)가 ES 문서 기준으로 동작한다. 분산 락·Redis를 들일 근거(실측된 중복 계산량)가 없다.

### D-2. `CartEventConsumer`·`OrderEventConsumer`를 하나로 합치지 않음

토픽·컨슈머 그룹·페이로드 구조(단건 vs 상품 목록)가 다르고 각각 100줄 안팎이다. A-1로 장바구니 쪽 `switch`가 사라지면 두 클래스는 "받아서 저장하고 트리거"만 남아 더 짧아진다. 제네릭 베이스 클래스는 두 파일을 읽는 것보다 이해 비용이 크다.

### D-3. 로그 저장까지 비동기로 돌리지 않음

로그 저장은 이벤트 처리의 본체이고 실패하면 Kafka 재시도가 다시 해야 한다. 비동기로 보내면 실패가 컨슈머 바깥에서 조용히 사라진다. 비싼 것은 임베딩이지 로그 저장(ES 2회)이 아니므로 A-2로 임베딩만 떼어내면 충분하다.

### D-4. 실패 이벤트 보관 토픽(DLQ) — [`search/001`](../search/001-index-path.md) D-1과 같은 이유로 두지 않음

재처리 절차와 보존할 운영 데이터가 아직 없다. A-5가 "짧은 재시도 뒤 로그로 남기고 건너뜀"으로 product와 같은 결정을 적용한다.

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

- **Async Processing (`@Async` 실제 적용 + 재계산 병합)** — **Now**. A-2·A-3과 동일하며 근거도 거기 있다. 장점: 컨슈머 지연이 Gemini와 분리되고 임베딩 호출이 행동 수에 비례하지 않게 된다. 단점: A-3의 신선도 창(N분)이 생긴다. 난이도 낮음(빈 1개 + 조건 1개)
- **Resilience (컨슈머 역직렬화 보호·재시도 간격·멱등 ID)** — **Now**. A-5와 동일. product 팩토리가 이미 같은 설정으로 돌고 있어 도입 비용이 거의 없다
- **Monitoring** — 추가 제안 없음. cross-cutting/001 B-1로 actuator가 이미 들어왔고(`build.gradle:80`), 이 단위가 필요한 지표(임베딩 호출 수·실행자 큐 길이)는 그 자동 계측이 이미 낸다. A-2·A-3 검증 방법이 그것을 쓴다
- **Event Architecture (발행 측 Outbox)** — **Never**(이 단위에서는). 발행 유실(`HistoryLogWriter.java:45-53`이 실패를 WARN으로 삼킴)은 사실이지만 발행부는 다른 모듈이고, 취향 벡터는 로그 몇 건이 빠져도 다음 행동에서 다시 계산되는 통계적 신호라 정확히 한 번 전달이 필요하지 않다

## 판단이 서지 않는 항목

1. **삭제·취소한 상품을 경험 상품(`sourceProductIds`)에서도 뺄지** (H3 후반). 지금은 최신 장바구니 5건 + 주문 5건의 productId를 이벤트 종류와 무관하게 넣는다(`UserProfileEmbeddingService.java:84-89`). "뺀 상품은 관심이 없으니 추천하지 않는다"와 "뺐으니 다시 추천해도 된다" 둘 다 말이 된다. 용어집은 "장바구니·주문 로그의 상품 ID"라고만 적어 결정을 담고 있지 않다. 사용자 결정 후 A-4에 2줄 추가하거나 용어집을 "종류 무관"으로 명시한다
2. **반복 횟수 상한과 임베딩 입력 한도** (H4). 상한이 없어(`:189,201`) 주문 3 × 수량 q × 시간 3.0 = 9q회 반복이고, 주문 5건이면 최대 45q회다(수량 20이면 900회 상품명 반복). `gemini-embedding-001`(`application-prod.yml:54`)의 입력 토큰 한도와 초과 시 동작(잘림/오류)은 공식 문서로 확인하지 못했다 — `researcher`(G1) 대상. 한도를 알아도 "반복 180회와 20회가 벡터를 유의미하게 다르게 만드는가"는 실측 없이는 모른다. 상한 도입은 정책 변경이라 사용자 결정
3. **시간 감쇠: 주석과 식 중 어느 쪽이 의도인가** (H5). `:231` 주석은 "7일에 0.5배", `:232` 식은 `e^(−h/168)`이라 7일에 약 0.37배(3.0 → 1.10)다. 반감기 7일이 의도면 식을 `exp(−h · ln2 / 168)`로 바꾸고(정책 변경), 식이 의도면 주석만 고친다(정책 불변 — 승인 시 A-1과 같은 턴에 반영 가능). 감사자가 정하지 않는다
4. **`ORDER_CANCELLED` 컨슈머 분기 유지/삭제** (H9). 발행부가 저장소 전체에 없다(`grep -rn '@TrackHistory' --include='*.java'` 4건 — cart 3, `ORDER_CONFIRMED` 1). `OrderEventConsumer.java:41,81-106`과 `calculateOrderEventWeight`의 `case "ORDER_CANCELLED"`는 도달 불가다. 그러나 baro-common enum과 baro-order의 `OrderCancelledHistoryPayloadMapper`가 있어 "붙일 예정"으로 보인다 — 붙이는 것은 다른 모듈(팀원) 작업이고, 지우는 것은 STOP §4(도달 불가 분기)에 해당한다. 팀 결정 `[미정]`. A-1이 enum `switch`로 바꾸면 이 case는 컴파일러가 요구하는 자리라 지우기보다 두는 편이 자연스럽다
5. **`quantity` 의미별 가중치** (H11). ADD는 증분, UPDATE는 새 총량, REMOVE는 전량인데(발행 측 매퍼 `CartAddHistoryPayloadMapper:49`, `CartQuantityUpdateHistoryPayloadMapper:38`, `CartRemoveHistoryPayloadMapper:64`) 같은 `max(1, quantity)`를 곱한다(`:186,198,257,272`). "수량 10으로 바꿈"이 "10개 담음"과 같은 무게가 된다. UPDATE에 수량을 곱하지 않거나 직전 수량과의 차를 쓰는 등 선택지가 있으나 전부 정책이다. 사용자 결정
6. **A-3의 N(분)과 신선도 정책**. 값이 없으면 A-3 (가)를 구현할 수 없다. 참고 수치: 시간 가중치는 시간 단위(`ChronoUnit.HOURS`, `:228`)라 1시간 안의 이벤트는 가중치가 같다 — N을 60분 이하로 잡으면 "같은 시간 가중치 안에서 로그 5개 창만 바뀌는" 재계산을 줄이는 셈이다. 트래픽 규모(사용자당 시간당 행동 수) `[미정]`
7. **`X-User-Id`·추천 API 무인증** (체크리스트 13). 스펙 리스크 표(`taste-vector-pipeline.md:146`)가 이미 게이트웨이 소유자 확인 `[미정]`으로 적어 두었다. 이 단위 코드로는 고칠 수 없어 여기서 다시 다루지 않는다

## 승인 결과 (2026-09-05)

사용자가 A·B 전 항목을 승인했고, 판단 항목에 아래와 같이 답했다. 구현은 다음 세션에서 이 절과 [`taste-vector-pipeline.md`](../../specs/taste-vector/taste-vector-pipeline.md) "승인된 재설계" 절을 기준으로 진행한다.

### 판단 항목 답

| 번호 | 결정 | 반영 위치 |
|------|------|-----------|
| 1 | **가중치 0인 행동(삭제·취소)의 상품은 경험 상품에서도 뺀다.** "관심이 없어진 상품이므로 다시 추천해도 된다" | 재설계의 경험 상품 규칙 |
| 2 | **해당 없음** — 재설계로 반복 텍스트 자체가 사라진다. 참고로 `researcher` 조사 결과 `gemini-embedding-001` 입력 한도는 2,048 토큰(Google AI 공식 문서, 2026-09-04 확인)이고 초과 시 동작은 이 모델에 대해 명시돼 있지 않았다 | — |
| 3 | 구현 세션에서 결정 — 재설계 시 가중치 식을 옮기면서 주석·식 중 하나로 맞춘다. 기본 제안: **식이 의도**(주석만 수정, 정책 불변) | 구현 시 확인 |
| 4 | **`ORDER_CANCELLED` 분기 유지.** 발행부는 팀 `[미정]`으로 두고 스펙 리스크 표에 남긴다 | 스펙 리스크 표 |
| 5 | 구현 세션에서 결정 — 재설계에서 `quantity`를 그대로 곱하는 것을 기본으로 하고, UPDATE 의미 차이는 스펙 `[미정]` 유지 | 스펙 |
| 6 | **해당 없음** — Gemini 호출이 0회가 되어 N분 스킵을 두지 않는다 | — |
| 7 | 팀 전달 항목(게이트웨이 소유자) — 이 단위 코드 변경 없음 | 스펙 리스크 표 |

### A-3 재설계 — 상품 벡터의 가중 평균

**결정**: 상품명을 가중치 횟수만큼 반복한 텍스트를 Gemini로 임베딩하는 현재 방식을 버리고, **행동 로그의 상품이 이미 갖고 있는 벡터(`ProductDocument.vector`)를 같은 가중치로 평균**해 프로필 벡터로 쓴다.

- **왜 바꾸는가** (코드·문서 근거):
  - 반복 텍스트는 이벤트 3 × 수량(상한 없음) × 시간 3.0 = 최대 9q회 반복이라(판단 항목 2) 입력 한도 2,048 토큰에 걸릴 수 있고, 걸렸을 때의 동작이 공식 문서에 없다
  - "반복 횟수 = 가중치"라는 가정은 임베딩 모델의 계약이 아니다. 가중 평균은 가중치가 수식 그대로 곱해진다
  - 프로필 갱신에서 가장 비싼 호출(Gemini)이 사라진다. ES 조회만 남으므로 A-3의 "행동 1건 = 원격 6회" 문제가 스킵 장치 없이 해소된다
  - 같은 로그면 같은 벡터가 나오는 결정적 계산이라 나중에 단위 테스트를 붙일 때 외부 API를 흉내 낼 필요가 없다
  - 참고(근거 아님): 아이템 임베딩의 가중 평균으로 사용자 임베딩을 만드는 방식은 추천 시스템에서 일반적으로 쓰인다(`researcher` 2026-09-05 조사 — Spotify 사례를 인용한 arXiv:2308.12767 등)
- **계산 규칙** (값은 전부 현행 유지 — 정책 변경은 판단 항목 1·6뿐):
  1. 30일 내 로그를 종류별 최신 5개씩 조회(현행)
  2. 로그별 가중치 `w = 행동 가중치 × max(1, quantity) × 시간 가중치`(현행 식, 반올림 없음). `w = 0`(삭제·취소)인 로그는 평균과 경험 상품 둘 다에서 제외(판단 항목 1)
  3. 장바구니·주문 로그의 `productId`로 `products` 인덱스에서 벡터만 일괄 조회(`mget` + `_source` 필터 — Elasticsearch 8.15 공식 문서 확인). 벡터가 없는 상품(색인 실패)은 건너뛴다
  4. 검색 로그는 Gemini 없이 처리한다: 검색 시점에 **상위 결과 상품 ID 최대 3개**를 `SearchLogDocument`에 함께 저장하고(새 필드 `topProductIds`), 그 상품 벡터에 검색 가중치(현행 1.0 × 시간 가중치)를 준다. 결과가 0건인 검색은 벡터에 기여하지 않는다
  5. `profile = Σ(w_i · v_i) / Σ w_i` 후 **L2 정규화**. Google 공식 문서가 1536차원(3072 미만) 출력은 수동 정규화가 필요하다고 명시하므로 상품 벡터 쪽 편차를 평균 뒤 한 번에 정리한다. 기여 벡터가 0개면 저장하지 않는다(현행 "로그 3개 미만 스킵" 게이트는 유지)
  6. 저장 문서는 `userId`·`userProfileVector`·`lastUpdatedAt`·`sourceProductIds`만 남긴다 — `source*LogIds` 3필드(B-1)와 `preferredCategoryCode`(아래)는 삭제. 매핑이 바뀌므로 `user_profile_embeddings` 인덱스는 재생성한다(dev 모드 — `baro-ai-performance-refactor.md` 데이터 마이그레이션 방침)
- **선호 카테고리·카테고리 보너스 삭제** (2026-09-05 결정, [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) 판단 항목 1의 답): 개인화 추천 경로에서 `calculatePreferredCategoryCode`·`preferredCategoryCode`·`PersonalizedRecommendService`의 보너스 인자를 없앤다. 이유 — (1) 보너스는 후보 선정에만 작용하고 최종 정렬에서 사라진다(사실), (2) 코사인 상위권 점수 차(약 0.05 안팎)에 비해 0.3은 커서 후보가 선호 카테고리로 쏠리고 그 뒤 다양화가 같은 카테고리 안에서만 고르게 된다, (3) 가중 평균 벡터가 이미 선호 카테고리 쪽으로 기울어 있어 정보가 중복된다. 유사 상품 추천 경로의 보너스는 `vector-similarity` 구현 라운드에서 따로 결정한다
- **검증 방법** (로컬 재현): 같은 사용자로 검색 5회 + 장바구니 담기 3회 → Before: `org.springframework.ai` DEBUG 임베딩 요청 8회. After: 0회, `user_profile_embeddings` 문서의 벡터 norm이 1.0(±1e-6). 사과만 담은 사용자의 프로필과 `products`의 사과 문서 벡터 코사인이 0.99 이상. 삭제 이벤트만 있는 사용자는 문서가 저장되지 않음
- **A/B 항목과의 관계**: A-4(가중치 0 반복)와 B-1(`source*LogIds`)은 재설계에 흡수된다. A-1(enum 통일)·A-2(`@Async`)·A-5(역직렬화 보호)·B-2~B-4는 그대로 진행한다
- **예상 변경 파일**: `UserProfileEmbeddingService`(계산부 전체), `UserProfileEmbeddingDocument`, `SearchLogDocument`·`LogWriteService.saveSearchLog`·`ProductSearchService`(상위 ID 저장), `PersonalizedRecommendService`·`VectorProductSearchService`(보너스 인자 제거 — 유사 상품 경로는 유지), `EMBEDDING_EXAMPLE.md`, 스펙·용어집
- **API 영향**: No Impact — 개인화 추천 응답 계약은 그대로

### 구현 순서

A-1 → A-2 → A-5 → **A-3 재설계(A-4·B-1 포함)** → B-2·B-3 → B-4. A-2·C-1은 한 턴. C/D는 이번 라운드에서 수정하지 않는다.
