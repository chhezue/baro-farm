# Taste-vector Refactor Log

취향 벡터 작업 단위에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다. 감사 결과 원본은 같은 폴더의 `001-*.md`에 있다.

## 2026-09-05 — 프로필 갱신 경로 전면 반영 (감사 001 A-1·A-2·A-3·A-4·A-5·B-1~B-4)

**반영 항목**: [`001-profile-refresh-path.md`](001-profile-refresh-path.md)의 A 5건·B 4건 전부, 그리고 문서 끝 "승인 결과 (2026-09-05)" 절의 A-3 재설계. C-1(실행자 거절 정책)은 A-2에 흡수했고, C-2·C-3·D 항목은 그대로 두었다.

### 왜 문제였나 (쉬운 말로)

**담은 상품과 뺀 상품이 똑같이 취급됐다.** 장바구니 이벤트를 받는 쪽은 로그에 `"ADD"`·`"REMOVE"`라고 적었는데, 취향을 계산하는 쪽은 `"CART_ITEM_ADDED"`·`"CART_ITEM_REMOVED"`라는 다른 글자와 비교하고 있었습니다. 어느 쪽도 서로 맞지 않아 전부 "그 밖의 경우"로 떨어졌고, 결과적으로 담은 상품(2배로 볼 예정)과 뺀 상품(아예 빼기로 한 것)이 같은 비중으로 취향에 들어갔습니다. 같은 값을 사람이 두 곳에 손으로 적어 둔 것이 원인이라, 이번에는 발행하는 쪽이 쓰는 값 하나를 끝까지 그대로 쓰게 바꿨습니다. 이제 종류가 새로 생기면 코드를 다시 빌드할 때 바로 에러가 나서, 배포 전에 알아차립니다.

**"비동기 처리"라고 적어 놓고 실제로는 기다리고 있었다.** 장바구니·주문 이벤트를 받으면 취향 벡터를 다시 만드는데, 이 작업을 "백그라운드로 넘긴다"고 표시해 두었지만 실제로는 넘어가지 않았습니다. 스프링이 이 표시를 알아채려면 다른 클래스를 거쳐 호출해야 하는데, 같은 클래스 안에서 부르고 있었기 때문입니다. 그래서 이벤트를 받는 스레드가 외부 AI 서버 응답(한 번에 최대 30초)을 그대로 기다렸고, 그 사이 다음 이벤트는 줄을 섰습니다. 검색 경로만 우연히 다른 클래스를 거쳐 제대로 동작하고 있었습니다. 이번에 진입점을 별도 클래스 하나로 모아 세 경로가 전부 실제로 백그라운드에서 돌게 했습니다.

**깨진 메시지 한 건이 로그 수집을 통째로 멈출 수 있었다.** 다른 팀이 이벤트 종류를 하나 추가하고 우리 쪽을 배포하기 전에 그 이벤트가 오면, 우리 서비스는 그 메시지를 읽지 못합니다. 이때 메시지를 건너뛰는 장치가 없어서 같은 자리를 무한히 다시 읽게 되고, 그 뒤에 온 정상 이벤트는 영영 처리되지 않습니다. 그 줄에 속한 사용자 전원의 취향이 조용히 멈추는 셈입니다. 또 저장이 잠깐 실패했을 때는 쉬는 시간 없이 열 번을 곧바로 두드리고 포기했는데, 이러면 재시도가 있으나 마나입니다. 게다가 재시도가 같은 로그를 한 번 더 저장했습니다. 셋 다 고쳤습니다 — 읽지 못하는 메시지는 한 번 기록하고 건너뛰고, 실패는 1초 간격으로 두 번 더 시도하며, 로그에 메시지의 위치로 만든 고유 번호를 붙여 다시 처리해도 덮어쓰기만 되게 했습니다.

**행동 한 번마다 외부 AI 서버를 불렀다.** 예전 방식은 "청송사과"라는 상품명을 관심도만큼 반복해 붙인 긴 문장(예: 34번 반복)을 만들어 Gemini에 보내고, 그 답으로 취향을 표현했습니다. 이 방식에는 문제가 셋 있었습니다. 첫째, 검색·담기·주문을 할 때마다 매번 불러서 가장 비싼 호출이 사용자 행동 횟수에 그대로 비례했습니다. 둘째, 수량에 제한이 없어 "20개 주문"이면 상품명이 180번 반복되는데, 이 모델이 한 번에 받을 수 있는 글자 양(단어 조각 2,048개 분량)을 넘으면 어떻게 되는지 — 뒤를 잘라내는지 오류를 내는지 — 공식 문서에 적혀 있지 않습니다. 셋째, "많이 반복하면 더 중요하게 본다"는 건 우리 기대일 뿐 모델이 보장한 동작이 아닙니다.

지금은 상품마다 이미 만들어 둔 벡터가 있으니, 그걸 관심도만큼 섞어 쓰기로 했습니다. 외부 호출이 0번이 되고, 관심도가 수식에 그대로 곱해지며, 같은 로그면 항상 같은 결과가 나옵니다. 검색은 검색어 대신 그때 상위에 나온 상품(최대 3개)으로 대신했습니다.

**뺀 상품이 계속 따라다녔다.** 관심도가 0이어도 최소 1번은 반영하는 계산이 있어서, 장바구니에서 뺀 상품이 취향에 남았고 "이미 경험한 상품" 목록에도 들어가 추천에서 영구히 빠졌습니다. 즉 "관심 없어졌다"는 신호가 정반대로 두 번 작용한 셈입니다. 이제 관심도 0인 행동은 취향에도, 제외 목록에도 들어가지 않습니다.

**선호 카테고리 보너스를 없앴다.** 사용자가 가장 많이 본 카테고리의 상품에 점수를 얹어 주고 있었는데, 이 보너스(0.3)가 상위권 상품끼리의 실제 점수 차(0.05 안팎)보다 훨씬 커서 후보가 한 카테고리로 쏠렸습니다. 그 뒤에 "다양하게 골라주는" 단계가 있지만 이미 쏠린 후보 안에서만 고르므로 효과가 없었습니다. 게다가 새 방식의 취향 벡터는 이미 자주 보는 카테고리 쪽으로 기울어 있어 같은 정보를 두 번 주는 셈입니다. 상품 상세의 "비슷한 상품" 추천은 보너스를 그대로 둡니다 — 그쪽은 기준 상품이 분명해 성격이 다릅니다.

### 무엇을 바꿨나

- **이벤트 종류를 옮겨 적지 않습니다.** 보내는 쪽이 쓰는 값 하나를 로그 저장과 취향 계산까지 그대로 씁니다. 담기는 2배, 수량 변경은 1배, 삭제는 0으로 의도대로 동작합니다.
- **취향 계산을 백그라운드로 확실히 넘깁니다.** 세 경로(검색·담기·주문)가 전용 클래스 하나를 거치게 해서, 이벤트를 받는 스레드가 계산이 끝나기를 기다리지 않습니다. 밀려서 대기열이 꽉 차면 이번 갱신은 기록만 남기고 버립니다 — 이벤트 처리는 그대로 성공합니다.
- **읽지 못하는 메시지가 뒤를 막지 않습니다.** 한 번 기록하고 건너뜁니다. 저장이 잠깐 실패하면 1초 간격으로 두 번 더 시도하고, 로그마다 메시지 위치로 만든 고유 번호를 붙여 다시 처리해도 중복이 쌓이지 않습니다.
- **취향 벡터를 외부 AI 서버 없이 만듭니다.** 상품마다 이미 만들어 둔 벡터를 관심도만큼 섞습니다. 행동 한 번에 외부 호출 1번씩 나가던 것이 0번이 됐고, 같은 로그면 항상 같은 결과가 나옵니다. 검색은 검색어 대신 그때 상위에 나온 상품(최대 3개)으로 반영합니다.
- **뺀 상품은 이제 완전히 빠집니다.** 취향에도 안 들어가고, "이미 경험해서 추천에서 제외"하는 목록에도 안 들어갑니다. 관심이 없어졌으니 나중에 다시 추천돼도 됩니다.
- **수량과 시간 규칙을 다듬었습니다.** 수량은 5개까지만 반영해 대량 주문 하나가 취향을 독차지하지 못하게 했고, 시간이 지나 관심이 옅어지는 속도를 주석에 적혀 있던 대로(7일에 절반) 맞췄습니다.

> 아래는 개발자 참조용이라 코드 식별자를 그대로 쓴다. 쉬운 설명은 위 절에 있다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| 로그의 `eventType` | `String`(`"ADD"`·`"ORDER_CONFIRMED"` 등 손으로 옮겨 적음) | `HistoryEventType` enum 그대로 저장 |
| 장바구니 행동 가중치 | 문자열 불일치로 전부 `default -> 1` | ADD 2 · UPDATE 1 · REMOVE 0 (의도대로) |
| 가중치 함수 | `calculateCartEventWeight` + `calculateOrderEventWeight`, 둘 다 `default` 분기 | `calculateEventWeight` 1개, `default` 없는 완전 분기 |
| 컨슈머 `switch` | cart 3분기(본문 동일)·order 2분기(본문 동일) | 도메인 확인 1줄 + 저장 1회 |
| `@Async` 진입점 | 3개 클래스에 복사(`CartEventConsumer`·`OrderEventConsumer`·`ProductIndexService`), 앞의 둘은 자기 호출이라 무효 | `UserProfileRefreshTrigger` 1개 |
| 컨슈머 실행 스레드 | Kafka 리스너 스레드(동기) | `profile-update-*`(실제 비동기) |
| 실행자 거절 정책 | `AbortPolicy` — 큐가 차면 이벤트 처리가 실패로 끝남 | WARN 후 버림 — 이벤트 처리에 영향 없음 |
| `executor.initialize()` | 명시 호출 + `afterPropertiesSet()` = 풀 2회 생성 | 명시 호출 삭제 |
| 실행자 빈 타입 | `Executor` | `ThreadPoolTaskExecutor`(Boot 자동 지표 수집 대상) |
| cart·order 역직렬화 | `JsonDeserializer` 직접 — 깨진 레코드가 파티션 영구 정지 | `ErrorHandlingDeserializer` 래핑 — 1회 기록 후 건너뜀 |
| cart·order 재시도 | 기본값 `FixedBackOff(0ms, 9)` = 즉시 10회 | `FixedBackOff(1000ms, 2)` = 1초 간격 3회 (product와 동일) |
| 로그 문서 ID | ES 자동 생성 — 재시도가 중복 로그 생성 | `{파티션}-{오프셋}`(주문은 `-{항목 순번}`) — 재처리가 덮어쓰기 |
| 프로필 벡터 계산 | 대표 텍스트(최대 9q회 반복) → Gemini 임베딩 | 로그 상품 `ProductDocument.vector`의 가중 평균 + L2 정규화 |
| 갱신 1회의 원격 호출 | ES 5 + Gemini 1 = 6회 | ES 5회, **Gemini 0회** |
| 검색 반영 | 검색어 문자열을 대표 텍스트에 포함 | `SearchLogDocument.topProductIds`(최대 3개)의 벡터 |
| 수량 가중치 | `max(1, quantity)` — 상한 없음 | `min(max(1, quantity), 5)` |
| 시간 가중치 | `3.0 × e^(−h/168)` — 7일에 0.37배(주석과 불일치) | `3.0 × 2^(−h/168)` — 7일에 정확히 0.5배 |
| 가중치 0 행동 | `max(1, …)` 때문에 1회 반영 + 경험 상품 포함 | 평균·경험 상품 모두에서 제외 |
| 프로필 문서 필드 | 7개(`source*LogIds` 3종·`preferredCategoryCode` 포함) | 4개(`userId`·벡터·`lastUpdatedAt`·`sourceProductIds`) |
| `sourceProductIds` 타입 | `List<String>`(UUID→String→UUID 왕복 변환) | `List<UUID>` |
| 개인화 카테고리 보너스 | `preferredCategoryCode` + 0.3 | 없음(유사 상품 경로는 유지) |
| 죽은 코드 | 페이징 없는 저장소 메서드 3개, 미사용 `EmbeddingErrorCode` 2개 | 삭제 |
| 이벤트 1건당 INFO 로그 | 장바구니 4줄 | 1줄 (나머지는 DEBUG) |
| 개인화 추천 API 응답 계약 | — | **변경 없음** |

### 변경 파일

**신규**

- `embedding/application/UserProfileRefreshTrigger.java` — 비동기 진입점 단일화

**코드**

- `embedding/application/UserProfileEmbeddingService.java` — 계산부 전면 재작성(가중 평균·L2 정규화), `EmbeddingModel` 의존 제거, `embedText`·`buildRepresentativeText`·`calculatePreferredCategoryCode` 삭제
- `embedding/domain/UserProfileEmbeddingDocument.java` — 필드 7 → 4, `sourceProductIds`를 `List<UUID>`로
- `embedding/exception/EmbeddingErrorCode.java` — 미사용 상수 2개 삭제
- `event/consumer/CartEventConsumer.java`·`OrderEventConsumer.java` — `switch` 제거, enum 전달, 파티션·오프셋 헤더 수신, 트리거 빈 호출, 로그 정리
- `config/AsyncConfig.java` — 거절 핸들러 추가, 중복 `initialize()` 제거, 반환 타입 변경
- `config/KafkaConsumerConfig.java` — cart·order에 `ErrorHandlingDeserializer`·`DefaultErrorHandler`, 재시도 상수 이름에서 `PRODUCT_` 제거
- `log/application/LogWriteService.java` — 시그니처에 문서 ID·enum·`topProductIds` 추가, INFO를 DEBUG로
- `log/domain/CartLogDocument.java`·`OrderLogDocument.java` — `eventType`을 enum으로, `id`를 빌더로 받음
- `log/domain/SearchLogDocument.java` — `topProductIds` 추가
- `log/infrastructure/elasticsearch/{Cart,Order,Search}LogRepository.java` — 페이징 없는 메서드 삭제
- `recommend/application/PersonalizedRecommendService.java` — 카테고리 보너스 제거, UUID 변환 제거, 중복 null 검사 통합
- `search/application/ProductIndexService.java` — `@Async` 메서드와 `UserProfileEmbeddingService` 의존 제거
- `search/application/ProductSearchService.java` — 상위 결과 상품 ID 저장, 트리거 빈 호출

**문서**

- `docs/specs/taste-vector/taste-vector-pipeline.md` — 흐름·데이터 모델·비즈니스 규칙·검증 시나리오·리스크 표를 새 동작으로 재작성, "승인된 재설계"를 "재설계 반영"으로 교체
- `docs/EMBEDDING_EXAMPLE.md` — 새 계산 방식으로 전면 재작성(같은 사례를 새 숫자로)
- `docs/product/glossary.md` — 취향 벡터 용어 8개 갱신, 폐지된 용어 3개(대표 텍스트·반복 횟수·선호 카테고리 코드) 삭제
- `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` — §3.3 필드 목록, §8 파이프라인 단계
- `docs/README.md`·`docs/IMPLEMENTATION.md` — 개인화 추천 서술·코드 발췌

### 검증

- `./gradlew :baro-ai:build` 통과 (컴파일 + `spotlessCheck` + `checkstyleMain`)
- `./gradlew :baro-ai:test` — 통과하지만 **테스트가 0개**라 의미 있는 신호가 아니다(`NO-SOURCE`). 자동화 테스트 도입은 사용자 결정으로 이번 라운드 범위 밖
- `spring-reviewer`·`code-review` 서브에이전트 리뷰 수행. 지적 3건을 반영했다:
  1. 섞은 벡터의 길이가 **정확히 0**일 때만 저장을 건너뛰면, 서로 거의 상쇄돼 길이가 극히 짧아진 경우 그 길이로 나누며 반올림 오차만 부풀린 벡터가 저장될 수 있다 → 하한 `1e-6`으로 바꿈
  2. 이벤트 종류가 비어 있는 레코드에서 도메인 검사가 `try` 밖이라 `NullPointerException`이 문맥 로그 없이 컨테이너로 빠져나갔다 → null을 명시적으로 걸러 WARN 후 건너뛰도록 바꿈(두 컨슈머)
  3. 주문 로그 문서 ID에 상품 ID를 쓰면 **한 주문에 같은 상품이 두 줄로 담길 때** 뒤 항목이 앞 항목을 덮어써 로그 1건이 사라진다 → 감사가 제안한 `-{productId}` 대신 `-{항목 순번}`을 쓴다. 재시도 중복 방지 효과는 같다
- **수동 확인 절차는 실행하지 못했다** — 로컬 Elasticsearch·Kafka를 띄우지 못했다. 절차는 스펙의 "수동 확인 (로컬)" 항목에 체크되지 않은 상태로 남겨 두었다

### 인덱스 재생성이 필요하다 (사람이 직접 실행)

매핑이 바뀌어 **기존 문서를 읽지 못하거나 필드가 맞지 않는다.** 운영 데이터가 없는 dev 전제이므로 지우고 다시 만든다. 되돌릴 수 없는 삭제라 에이전트가 실행하지 않는다.

| 인덱스 | 왜 |
|--------|-----|
| `cart_event_logs` | `eventType`이 enum이라 옛 `"ADD"`·`"REMOVE"`·`"UPDATE"` 값을 변환하지 못한다 |
| `order_event_logs` | 값은 그대로지만 문서 ID 체계가 바뀐다 |
| `search_logs` | `topProductIds`가 추가됐다. 옛 문서는 이 값이 없어 취향에 기여하지 않는다 |
| `user_profile_embeddings` | 필드 구성이 바뀌었다. 옛 문서의 벡터는 계산 방식이 달라 새 벡터와 섞이면 안 된다 |

### 남긴 것 · 알려진 제약

- **같은 사용자의 갱신이 동시에 돌 수 있다.** 컨슈머 경로가 실제 비동기가 되면서 생긴 창이다. 감사 A-3의 (나)안(사용자별 단일 실행)은 실제 동시 실행이 관찰될 때만 넣기로 했고, 외부 API 호출이 빠져 갱신 자체가 짧아진 만큼 겹칠 확률도 낮아졌다. 겹쳐도 다음 행동에서 다시 계산돼 스스로 회복한다
- **검색 품질이 취향 품질을 좌우하게 됐다.** 검색 결과 상위 3개가 그대로 취향에 들어가므로, 검색이 엉뚱한 상품을 올리면 취향도 그쪽으로 기운다. 이전에는 검색어 문자열이라 이 결합이 없었다 — `keyword-search` 단위의 개선이 이 단위에도 영향을 준다
- **감사 A-1의 "대표 텍스트 DEBUG 한 줄 추가"는 하지 않았다.** 같은 라운드의 A-3이 대표 텍스트 자체를 없애기 때문에, 두 커밋 뒤 지워질 코드를 넣는 셈이었다
- **상품 벡터 조회는 `findAllById`를 썼다.** 감사는 `mget` + `_source` 필터를 적었지만, 필터로 걸러낼 나머지 필드가 상품명·가격 등 작은 값뿐이라 이득이 거의 없고 코드만 늘어난다. 한 번에 조회하는 것(mget)은 동일하다
- **`ORDER_CANCELLED` 발행부는 여전히 없다.** 분기는 유지하기로 했다(감사 판단 항목 4) — 스펙 리스크 표에 `[미정]`으로 남아 있다
- **`quantity` 의미 차이(ADD 증분 vs UPDATE 새 총량)는 완전히 해소되지 않았다.** 상한 5로 격차를 줄이는 선에서 타협했고, 이벤트별로 식을 나눌지는 스펙 리스크 표에 `[미정]`으로 남겼다
- **팩토리 4중복 정리**(product·cart·order·review)는 review 팩토리가 팀원 소유라 `cross-cutting` 라운드로 미뤘다(감사 C-2)
