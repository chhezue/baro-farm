# Search Architecture Audit 001 — 상품 색인 경로 (2026-09-04)

상품 이벤트가 Kafka로 들어와 Elasticsearch(ES) 문서가 되기까지의 경로(`ProductEventConsumer` → `ProductIndexService` → `ProductIngredientNormalizer`·ES 저장)를 감사한 결과다. 반드시 고칠 A 항목 1건(Kafka 컨슈머 설정), 유지보수성 B 항목 2건이 나왔고, 세 건 모두 2026-09-04 사용자 승인 후 **반영 완료**했다(결과는 [`refactor-log.md`](refactor-log.md)). 이 문서는 감사 시점의 근거·분류를 남기는 스냅샷이다. B-2는 처음에 "폴백 출처 필드 추가"였으나 2026-09-04 사용자 결정으로 "정규식 폴백 제거, LLM만 + 실패 시 빈 값"으로 바꿨다. `search` 도메인의 첫 감사 문서(001)이며, 검색·자동완성 조회 경로는 이번 범위에 넣지 않았다(별도 라운드). 이전 라운드(자동완성 캐시 활성화)의 반영 이력은 [`refactor-log.md`](refactor-log.md)에 있다.

## 범위

- 패키지: `com.barofarm.ai.search.infrastructure.event`(`ProductEventConsumer`), `search.application.ProductIndexService.indexProduct()`, `search.domain.ProductDocument`, `search.infrastructure.llm.ProductIngredientNormalizer`(폴백 동작만 — LLM 호출 방식 자체는 [`cross-cutting/001-llm-call-infra.md`](../cross-cutting/001-llm-call-infra.md)에서 다룬다)
- 설정 파일 중 `config/KafkaConsumerConfig.java`의 **product 컨슈머 팩토리 2개**만 — cart·order·review 팩토리는 `event-log`·`review` 도메인 소관이라 손대지 않는다
- 감사자: 사용자가 가져온 조사 결과(코드 조사 에이전트 결과 포함)를 이 세션에서 코드로 재확인했다. `safe-refactor`가 정한 "신선한 서브에이전트 감사" 대신 조사 결과 검증으로 대체한 점을 명시한다
- 기준: `audit-checklist.md` 1~16항목, `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- 저장 스키마(ES 매핑)는 동결 대상이 아니다 — `baro-ai-performance-refactor.md` "데이터 마이그레이션 방침"

## ✅ A. 반드시 수정해야 하는 사항

### A-1. product 컨슈머가 재시도 0회로 레코드를 버리고, 주석은 반대로 적혀 있음

- **Priority**: High
- **Category**: Architecture (Resilience)
- **문제**: `productEventListenerContainerFactory`의 에러 핸들러가 `FixedBackOff(0L, 0L)`(재시도 0회)이라, 색인 중 어떤 예외가 나도 그 레코드를 즉시 건너뛰고 오프셋을 넘긴다. ES가 잠시 내려가 있으면 그 사이 들어온 상품은 검색·추천에서 영구히 빠진다. 반면 리스너의 `throw e` 주석은 "Kafka가 재시도하도록"이라고 적혀 있어 실제 동작과 반대다. `max.poll.records`·`max.poll.interval.ms`도 설정하지 않아 Kafka 기본값(500건 / 5분)이 그대로 쓰인다
- **근거**:
  - `KafkaConsumerConfig.java:76-80` — `new DefaultErrorHandler(new FixedBackOff(0L, 0L))`, 주석 "재시도 없이 즉시 건너뛰기"
  - `ProductEventConsumer.java:63` — `throw e; // 예외를 다시 던져서 Kafka가 재시도하도록 함` → 위 핸들러로 들어가 재시도 없이 스킵된다
  - `grep -rn 'max.poll\|max-poll' baro-ai/src/main/resources` 0건, `KafkaConsumerConfig.java`에도 없음
  - 레코드 1건 처리에 원격 호출 2회(`ProductIndexService.java:48` 임베딩, `:57` 재료명)가 각각 30초 상한(`LlmClientConfig.java:25`)이므로 최악 60초/건. 기본값대로 한 번의 poll에 500건이 쌓이면 5분 안에 다 처리할 수 없어 컨슈머가 그룹에서 쫓겨나고(rebalance) 같은 배치를 다시 받는 반복이 생긴다 — 계산상 5건만 최악 경로를 타도 5분을 넘긴다
- **왜 문제인가**: 평시(상품 등록이 드문드문)에는 드러나지 않지만, 아래 "개선 제안"의 재색인 경로를 만드는 순간 정확히 이 조건(짧은 시간에 수백 건)이 된다. 지금 고치지 않으면 재색인 기능이 첫 실행에서 rebalance 루프에 빠진다. 또 주석-동작 불일치는 STOP §1(문서·구현 정합) 위반이다
- **개선 방법**:
  1. `ProductEventConsumer.java:63` 주석을 실제 동작에 맞게 고친다(재시도 정책은 컨테이너 팩토리가 정한다는 한 줄) — 정책을 바꾸지 않더라도 이 줄은 필수
  2. `DefaultErrorHandler`의 백오프를 "짧게 몇 번 재시도 후 스킵"으로 바꾼다(예: `FixedBackOff(1000L, 2L)` — 값은 [미정], 아래 판단 항목 2번). 역직렬화 실패는 이미 `setFailedDeserializationFunction(record -> null)`로 핸들러에 도달하지 않으므로 재시도 대상에서 자연히 빠진다
  3. `max.poll.records`를 처리 시간 상한에 맞춰 낮춘다(`300초 ÷ 60초 = 5`가 계산상 안전선, 값은 [미정]). `max.poll.interval.ms`를 늘리는 대안은 장애 감지가 느려지므로 권장하지 않는다
  4. `KafkaConsumerConfig.java:27`의 `TODO: 재시도 로직 or DLQ 설정`(DLQ는 실패 메시지 보관 토픽, D-1 참고)은 이 변경으로 재시도 부분이 해소되므로 같은 변경에서 지우고, DLQ는 D-1로 결론을 남긴다(STOP §4 — "나중에 지울" TODO 방치 금지)
- **검증 방법** (수동): 로컬 `docker compose`로 Kafka만 띄우고 ES를 내린 상태에서 상품 이벤트 1건을 발행해, 컨슈머 로그에 같은 레코드의 실패가 설정한 횟수만큼 반복된 뒤 스킵되는지 본다. Before에서는 실패 로그가 1회만 찍히고 바로 다음 레코드로 넘어간다. `max.poll.records`는 기동 로그의 `ConsumerConfig values` 블록에서 값이 바뀌었는지 확인한다
- **API 영향**: No Impact
- **예상 변경 파일**: `config/KafkaConsumerConfig.java`, `search/infrastructure/event/ProductEventConsumer.java`
- **예상 변경 라인 수**: 코드 10줄 내외
- **위험도**: Low — 재시도가 늘어나면 잘못된 레코드 1건이 컨슈머를 `횟수 × 지연`만큼 붙잡는다. 값을 작게 잡는 이유다
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 성능(일시 장애 내성) · 문서-구현 정합 · 재색인 경로의 전제 조건

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `indexProduct()`의 재료명 `catch` 블록이 도달하지 않는 죽은 코드이고 주석이 사실과 다름

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `ProductIndexService.java:55-61`은 `normalize()`를 `try/catch`로 감싸고 실패 시 `""`을 두겠다고 주석에 적었지만, `normalize()`는 어떤 예외도 밖으로 던지지 않는다. 현재(B-2 전) 실패 시 값은 빈 문자열이 아니라 정규식 폴백 결과다
- **근거**: `ProductIngredientNormalizer.java:34-63` — 메서드 전체가 `try/catch (Exception)`이고 catch에서 `applyBasicNormalization()`을 반환한다. Javadoc(32줄)도 "이 메서드는 예외를 던지지 않는다"고 명시한다. 호출부 주석 54줄 "실패해도 색인은 계속되어야 하므로 빈 값으로 둔다"는 현재 동작과 다르다
- **왜 문제인가**: 다음 사람이 호출부만 읽으면 "실패하면 빈 값"이라고 믿는다. B-2를 다룰 때 이 오해가 설계를 틀리게 만든다. STOP §1 문서·구현 정합 대상
- **개선 방법**: `try/catch` 제거. 주석은 B-2 반영 후 동작에 맞춰 "`ProductIngredientNormalizer`는 예외를 던지지 않고 실패 시 빈 값을 돌려준다 — 추천이 빈 값을 건너뛰고 재색인 대상으로 센다"로 교체. 임베딩 쪽 `try/catch`(46-51줄)는 `embedProduct()`가 실제로 던질 수 있으므로 유지. B-2와 같은 변경에서 처리한다
- **API 영향**: No Impact
- **예상 변경 파일**: `search/application/ProductIndexService.java`
- **예상 변경 라인 수**: -5줄
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 코드 감소 · 가독성

### B-2. 정규식 폴백을 없애고 "LLM만 + 실패 시 빈 값"으로 바꿈

- **Priority**: Medium
- **Category**: Architecture (데이터 품질)
- **문제**: LLM이 실패하면 `applyBasicNormalization()`이 브랜드·수량·단위를 지운 **나머지 글자**를 돌려주고, 그 값이 `ingredientName`에 영구 저장된다. 예: `"곰곰 무항생제 신선한 대란, 30구, 1개"` → `"대란"`. 이 값은 재료명처럼 보이지만 LLM이 냈을 `"계란"`과 달라 레시피 추천의 재료 매칭에서 빠진다. 폴백은 매핑이 아니라 "지우고 남은 것"이라 그럴듯한 오답을 만든다
- **근거**:
  - `ProductIngredientNormalizer.java:65-77` — 폴백은 정규식 치환 7개로 단어를 지우기만 한다. 동의어 사전·카테고리 매핑이 없다
  - 유일한 읽기 지점 `RecipeRecommendService.java:172-198`은 **빈 값을 이미 처리한다** — 재료명이 비면 건너뛰고 "재색인 필요" WARN을 남기며 건수를 센다. 폴백이 값을 채워 넣으면 이 신호가 사라진다
  - 상품명은 판매자 자유 입력이다 — `baro-shopping/.../ProductCreateRequest.java`의 검증은 `@NotBlank`뿐이고 `Product.productName`은 `length = 50`. 형식 제약이 없어 규칙 기반으로 재료명을 안정적으로 뽑기 어렵다(D-3)
  - 카테고리는 2단계까지만 있고 "국·찌개용 채소"·"뿌리채소"·"육류"처럼 넓어(`scripts/init-db/02-init-categories.sql`) 카테고리→재료명 매핑도 대안이 못 된다. "계란" 카테고리만 예외
  - [`decisions/002-ingredient-name-at-index-time.md`](../../decisions/002-ingredient-name-at-index-time.md) "고려한 대안"은 "정규식만 쓰기"를 기각하며 정규식을 폴백으로 남겼지만, 폴백 값의 품질은 검토하지 않았다
- **왜 문제인가**: 틀린 재료명이 정상 데이터처럼 저장되고, 어느 문서가 그런지 찾을 수 없다. 빈 값이면 그 상품이 잠시 보유 재료로 안 잡힐 뿐 틀린 재료가 들어가진 않고, 추천 코드가 이미 그 상황을 세고 있다. 재시도(`cross-cutting/001-llm-call-infra.md` A-1)가 걸리면 실패 자체가 드물어진다
- **개선 방법**:
  1. `applyBasicNormalization()`과 그 호출 2곳(54-55줄, 60-61줄)을 삭제한다. 실패·빈 응답 시 `""`을 돌려주고 WARN에 실패 종류를 남긴다(`cross-cutting/001-llm-call-infra.md` B-1과 같은 분류). "예외를 던지지 않는다"는 계약은 유지한다
  2. `ProductIndexService.java:53-54` 주석을 "실패 시 빈 값 — 추천이 건너뛰고 재색인 대상으로 센다"로 고친다(B-1과 같은 변경)
  3. `ProductIngredientNormalizer` Javadoc(32줄)의 "정규식 폴백" 문구 삭제
  4. `002-ingredient-name-at-index-time.md`를 같은 변경에서 amend한다 — "폴백"을 "실패 시 빈 값 + 재시도 + 재처리 경로(개선 제안 1)"로. STOP §1 문서·구현 정합
  5. 출처 필드(`ingredientNameSource`)는 **추가하지 않는다** — 빈 값 자체가 "다시 계산 필요" 표시라 필드가 필요 없다(이전 안을 이 안으로 교체, 2026-09-04 사용자 결정)
- **API 영향**: No Impact. ES 매핑 변경 없음
- **예상 변경 파일**: `search/infrastructure/llm/ProductIngredientNormalizer.java`, `search/application/ProductIndexService.java`, `docs/decisions/002-ingredient-name-at-index-time.md`
- **예상 변경 라인 수**: 코드 -15줄 내외, 결정문 5줄
- **위험도**: Low — 실패한 상품이 재처리 전까지 보유 재료로 안 잡힌다. 이는 이미 이 변경 이전에 색인된(필드 없는) 문서와 같은 상태이고, 추천 코드가 그 상태를 처리한다
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정). 수동 확인: 잘못된 Gemini 키로 기동해 상품 이벤트 1건을 발행한 뒤 `curl localhost:9200/product/_doc/{productId}`로 `ingredientName`이 빈 문자열인지, 로그에 실패 종류가 찍히는지 본다. Before에서는 정규식 결과가 들어간다
- **예상 효과**: 데이터 품질(오답 저장 차단) · 코드 감소 · 재처리 대상이 빈 값으로 자연히 식별됨

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. `spring.kafka.consumer.*` yml 값이 커스텀 팩토리에는 적용되지 않음

`application-prod.yml:29-33`의 `key-deserializer`·`value-deserializer`·`auto-offset-reset`은 Spring Boot 자동설정 컨슈머 팩토리용인데, baro-ai의 리스너 4개는 전부 `KafkaConsumerConfig`가 직접 만든 팩토리를 쓰고 그 팩토리는 `bootstrap-servers`·`group-id`만 `@Value`로 읽는다(`KafkaConsumerConfig.java:33-37, 43-46`). 동작에는 영향이 없지만 `cross-cutting/001-llm-call-infra.md` A-1(`spring.ai.retry`)과 같은 "설정이 있는데 안 읽히는" 유형이다. 지금 안 고치는 이유는 product 팩토리 밖(cart·order·review)까지 같이 봐야 정리가 끝나서다 — `event-log` 도메인 라운드에서 config 전반과 함께 다룬다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. DLQ(Dead Letter Queue, 실패 메시지 보관 토픽) 도입 안 함

`KafkaConsumerConfig.java:27`의 TODO가 DLQ를 언급하지만, DLQ는 "실패한 메시지를 누가 언제 다시 처리하는가"라는 운영 절차가 있어야 의미가 있다. 지금은 그 절차도, 재처리 도구도, 보존할 운영 데이터도 없다(dev 모드). 실패 레코드는 A-1의 재시도 후 WARN 로그로 남기고, 재색인 경로(개선 제안 1)로 덮는 편이 이 규모에 맞다. 운영 데이터가 생기면 재검토한다.

### D-2. 색인 시 임베딩·재료명 호출 병렬화 안 함

레코드당 원격 호출 2회를 병렬로 돌리면 최악 60초가 30초로 줄지만, 상품 등록 이벤트의 실제 유입량 추정치가 없어([미정] — 판단 항목 2번과 같은 값, 사용자 확인 필요) 체감 이득을 수치로 뒷받침할 수 없고, 컨슈머 스레드 안에서 executor를 다루는 복잡도가 붙는다. A-1의 `max.poll.records` 조정이 같은 문제(poll 예산 초과)를 더 단순하게 푼다. 대량 재색인이 실제로 느리다는 측정이 나오면 그때 본다.

### D-3. 사전(동의어 매핑) 기반으로 LLM을 대체하지 않음

카테고리가 넓고(B-2 근거) 상품명이 자유 입력이라, 사전만으로 하려면 "한우 등심→소고기", "대란·특란→계란" 같은 동의어 목록을 사람이 만들고 유지해야 한다. 그 비용이 LLM보다 싼지는 상품 수와 상품명 다양성에 달려 있는데, 서버가 내려가 있어 실데이터가 없고 상품 수 추정치도 없다([미정] — 사용자 확인 결과 "잘 모름"). 근거 없이 바꾸지 않는다. 크롤링은 대안이 아니다 — 필요한 건 "우리 판매자의 표현→표준 재료명"이라 외부 사이트에 없고, 농산물 표준 품목명은 공개 데이터 다운로드 대상이다. LLM 결과의 일관성 문제("소고기" vs "한우")가 실데이터에서 확인되면 그때 **LLM 뒤에** 작은 동의어 표를 붙이는 것을 검토한다.

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

### 1. 재색인·재정규화 경로 — **Later** (단, 성격이 리팩터가 아니라 기능이라 Track A `specify` 대상)

- **왜 필요한지**: baro-ai는 DB 없이 Kafka 이벤트와 ES 문서만 갖는다. 매핑을 바꾸거나(임베딩 차원 교체) B-2 이후 빈 값으로 남은 재료명을 다시 계산하려면 상품을 다시 색인해야 하는데, 그 경로가 없다. 이전 라운드 로그가 이미 "재색인 필요, dev 모드라 전체 재색인으로 해소"라고 적었지만 해소할 수단이 실제로는 없는 상태다
- **근거**: `grep -rn -iE 'reindex|republish|backfill' baro-ai/src/main/java` 0건. `ProductIndexService`에 `indexProduct`·`deleteProduct`만 있고 목록·전체 처리 메서드 없음
- **선택지**: (가) product 모듈에 "전체 상품 이벤트 재발행" API를 요청 — 다른 모듈 변경이라 STOP §0(사용자 확인) 대상이고 팀 결정이 필요. (나) baro-ai 안에 "`ingredientName`이 빈 문서만 재정규화" 배치 — 상품 원본 없이 ES 문서의 `productName`으로 다시 계산하므로 baro-ai 혼자 할 수 있지만, 임베딩 차원 교체 같은 전체 재색인은 못 덮는다
- **장단점·난이도**: (가)는 범용이지만 팀 협의 필요, (나)는 baro-ai 안에서 끝나지만 부분 해결. 난이도는 둘 다 낮음
- **Later인 이유**: A-1이 먼저 고쳐져야 재색인이 rebalance 루프에 빠지지 않는다. 그리고 어느 선택지든 새 API·배치가 생기므로 `safe-refactor`가 아니라 `specify`로 스펙을 쓰고 승인받아야 한다

## 판단이 서지 않는 항목

1. **역직렬화 실패로 `null` 값이 리스너에 들어올 때의 동작** — `setFailedDeserializationFunction(record -> null)`이 `null`을 돌려주면 `ProductEventConsumer.onMessage(ProductEvent event)`의 `event.getData()`가 NPE를 내는지, Spring Kafka 3.3.11이 `null` 페이로드를 리스너 호출 전에 걸러내는지 확인하지 못했다. NPE라면 A-1의 재시도 대상에 들어가 "재시도해도 안 되는 레코드"가 된다. 확인 방법: `spring-kafka-3.3.11` 공식 문서의 "Null Payloads" 절, 또는 단위 테스트로 `null` 레코드를 흘려보기
2. **A-1의 재시도 횟수·지연·`max.poll.records` 값 [미정]** — 계산상 안전선(5건)만 있고 실제 이벤트 유입량 추정치가 없다. 사용자 확인 필요
3. **재색인 경로의 선택지 (가)/(나)** — 팀 결정. (가)를 택하면 product 모듈 담당자와 협의가 먼저다. B-2로 폴백을 없애면 이 경로가 "빈 값인 문서 다시 계산"의 유일한 수단이 되므로 우선순위가 올라간다
4. **상품 수·상품명 다양성 [미정]** — D-3(사전 대체 여부)의 판단 근거. 사용자도 모른다고 확인(2026-09-04). 서버가 다시 뜨면 ES `product` 인덱스 문서 수와 `productName` 샘플로 확인한다

## 승인 대기

2026-09-04 사용자 승인으로 A-1 → B-2 + B-1 순서로 구현했고(B-2는 `cross-cutting/001-llm-call-infra.md` A-1 재시도 반영 뒤에 진행), 반영 내역·검증·잠정값은 [`refactor-log.md`](refactor-log.md)에 있다. C/D는 이번 라운드에서 수정하지 않았다. 검증은 전부 로컬 수동 절차다(테스트 코드는 사용자 결정으로 제외).
