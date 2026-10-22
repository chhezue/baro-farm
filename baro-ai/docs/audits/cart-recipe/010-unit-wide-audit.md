# Cart-recipe Architecture Audit 010 — 단위 전체 재감사 (2026-09-09)

장바구니 레시피 작업 단위(장바구니 조회 → 색인 시점 재료명 → LLM 레시피 1개 → 서버 검증 → 부족 재료 상품)를 코드 전체 기준으로 다시 감사한 기록이다. 앞선 감사 001~009와 `refactor-log.md`가 닫은 항목은 다시 올리지 않았고, 닫혔다고 적힌 것도 코드로 재확인했다. 결과는 **A 1건 · B 4건 · C 6건 · D 9건**이고, 지금은 **승인 대기** 상태다. A-1·B-1~B-4는 2026-09-09 사용자 승인 후 같은 날 반영했다 — 내역과 검증은 [`refactor-log.md`](refactor-log.md) 2026-09-09(010) 절, 판단 항목 1에는 같은 날 실측을 붙였다.

A-1은 2026-09-08에 넣은 "요청 경로 3초 상한·재시도 없음"이 SDK 기본값 때문에 실제로는 **최대 5회 재시도·최악 약 33초**로 동작한다는 것이다 — 감사 문서만으로는 알 수 없고 `google-genai 1.37.0` 바이트코드를 읽어야 보인다. 나머지 B는 지표 태그 하나가 죽어 있는 것, 이제는 싸게 쓸 수 있게 된 단위 테스트, 2026-09-09 삭제분을 반영하지 못한 현행 문구, 도메인이 인프라를 거꾸로 참조하는 한 줄이다.

## 범위

- 패키지·파일
  - `com.barofarm.ai.recommend` 중 레시피 경로: `application.RecipeRecommendService`, `application.RecommendationMetrics`(레시피 부분), `application.dto.{RecipeRecommendResponse, RecipeRecommendReason, IngredientRecommendResponse}`, `domain.{CandidateRecipePlan, IngredientProcessingUtil}`, `exception.RecommendErrorCode`, `presentation.RecommendationController`(레시피 엔드포인트), `infrastructure.llm.{RecipeCandidateGenerator, RecipeCandidateDraft, RecipeCandidateDrafts}`, `infrastructure.client.{CartClient, dto.CartInfo, dto.CartItemInfo}`
  - `com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer`(색인 시점 재료명) · `search.application.ProductSearchService.searchByKeywords`·`findByIds` · `search.infrastructure.elasticsearch.ProductQueryFactory.buildIngredientQuery`
  - `com.barofarm.ai.common.llm.{LlmGateway, LlmCallKind}` · `config.LlmClientConfig` · `config.KafkaConsumerConfig`(product 팩토리, 재료명 호출 시간 산정 부분만)
  - `src/main/resources/application.yml`의 LLM·Feign·ES 타임아웃 키
  - 테스트: `src/test/java/com/barofarm/ai/recommend/**`, `common/llm/LlmGatewayTest`, `config/LlmClientConfigTest`
  - 대조 문서(수정하지 않음): `docs/specs/cart-recipe/*.md` 3편, `docs/audits/cart-recipe/001~009`, `refactor-log.md`, `open-items.md`, `scripts/goldenset/*`
  - 외부 실물: `~/.gradle` 캐시의 `google-genai-1.37.0.jar`·`spring-ai-google-genai-1.1.8.jar`를 `javap`로 읽었다
- 제외: `com.barofarm.ai.review`(팀원 소유 — 읽지 않았다)
- 감사자: 서브에이전트(`Agent` 툴, 읽기 전용). 코드는 한 줄도 고치지 않았다
- 기준: `audit-checklist.md` 1~16항목(7번 JPA는 이 모듈에 해당 없음), `core-guardrails.md` ⛔ STOP §1·§2·§4
- `baro-ai-performance-refactor.md`가 활성화된 동안이라 A 항목의 "근거"·"검증 방법"은 생략하지 않았다. 검증은 전부 로컬에서 재현 가능한 것만 적었다 — `./gradlew :baro-ai:test`, 골든셋 스크립트, 로컬 스텁
- 오늘(2026-09-09) 확정된 결정과 충돌하는 제안(판매 재료 목록·예외 목록·프롬프트 조이기·캐싱)은 A/B에 넣지 않고 D에 이유와 함께 뒀다

## 점검 항목별 결과

단위 전체를 한 번에 훑는 재감사라, 점검표 16항목 각각이 어느 A/B/C/D 항목으로 이어졌는지(또는 해당 없음인지)를 먼저 한 표로 둔다. 좁은 범위 감사(001~007)에는 없는 절이다.

| # | 항목 | 결과 |
|---|------|------|
| 1 | 중복 코드 | C-6 (사소) |
| 2 | Dead Code | B-1 (`llm.call{outcome=failed}` 도달 불가) |
| 3 | Legacy | B-3 (2026-09-09 삭제분을 반영하지 못한 현행 문구) |
| 4 | Spring Best Practice | C-3 (스테레오타입 불일치) — 생성자 주입·Optional·DTO 변환 위치는 이상 없음 |
| 5 | AOP | D-6 (적용 가치 없음) |
| 6 | 성능 | **A-1** (요청 경로 LLM 호출이 SDK 기본 재시도 5회를 탄다) · D-1·D-2·D-4 |
| 7 | JPA | 해당 없음 — 이 모듈에 JPA 엔티티가 없다 |
| 8 | Exception | 이상 없음 — `RecommendErrorCode` 2개 상수 모두 throw 지점 확인(`VectorRecommendPipeline.java:82`, `RecommendationController.java:140`) |
| 9 | 패키지 구조 | B-4 (domain → infrastructure 역참조) |
| 10 | 보일러플레이트 | 이상 없음 (2026-09-09 필드 복사 반복문 삭제로 해소됨) |
| 11 | Naming | C-3 |
| 12 | 의존성 | 이상 없음 — `build.gradle`의 Feign·caffeine·actuator·testcontainers 전부 사용처 확인 |
| 13 | Security | C-4 (간접 프롬프트 인젝션) · `X-User-Id` 미확인은 `open-items` B-2로 이미 열려 있어 재론하지 않음 |
| 14 | 테스트 가능성 | B-2 (생성기·정규화기 테스트 — 005 C-1의 전제가 사라짐) |
| 15 | 아키텍처 제안 | 아래 절 — Now 0건 |
| 16 | LLM·프롬프트 | A-1·B-1·D-1·D-2·D-3·D-5 · 판단 항목 1·3 |

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 요청 경로 Gemini 클라이언트가 "재시도 없음"이 아니라 SDK 기본값 5회 재시도를 탄다 — 최악 약 33초

- **Priority**: High
- **Category**: Performance / LLM-Prompt
- **문제**: `LlmClientConfig.buildClient`는 `attempts > 1`일 때만 `HttpRetryOptions`를 넣는다([`LlmClientConfig.java:69-75`](../../../src/main/java/com/barofarm/ai/config/LlmClientConfig.java)). 요청 경로 클라이언트는 `attempts = 1`로 만들어(`:100-103`) `retryOptions`가 비어 있는데, google-genai SDK는 `retryOptions`가 비어 있으면 **기본 `HttpRetryOptions`로 `RetryInterceptor`를 항상 설치**한다. 그 기본값이 5회 시도·지수 백오프다. 그래서 코드·Javadoc·스펙이 말하는 "3초에 끊고 재시도하지 않는다"(`LlmClientConfig.java:31,99`, `LlmCallKind.java:12`, 스펙 흐름 4단계)는 실제 동작이 아니다.
- **근거** (전부 바이트코드·코드로 확인한 사실):
  1. `com.google.genai.ApiClient.createHttpClient` (google-genai 1.37.0, `~/.gradle/caches/.../google-genai-1.37.0.jar`, `javap -c`): OkHttp `connectTimeout`·`readTimeout`·`writeTimeout`을 전부 0(무제한)으로 두고, `HttpOptions.timeout`은 **`callTimeout`**(호출 전체)에만 건다. 이어서 `HttpOptions.retryOptions().orElse(HttpRetryOptions.builder().build())`로 **항상** `RetryInterceptor`를 `addInterceptor`한다 — 옵션을 안 넣어도 인터셉터는 들어간다
  2. `com.google.genai.RetryInterceptor.intercept`: 시도 횟수 `attempts().orElse(5)`, 재시도 상태 코드 기본 `408·429·500·502·503·504`. **`proceed()`가 `IOException`을 던져도 마지막 시도가 아니면 잠들었다가 다시 시도한다**(예외 표: `101-136 → 154`, `i == attempts`일 때만 rethrow). 마지막 시도가 재시도 대상 상태 코드로 끝나도 한 번 더 잠든 뒤 응답을 돌려준다(`139 → 151 → 166`)
  3. `RetryInterceptor.calculateDelay`: `initialDelay` 기본 1.0초, `expBase` 2.0, `maxDelay` 60초, `jitter` 1.0 → i번째 시도 앞의 대기는 `[0, 2·2^(i-1)]`초(평균 `2^(i-1)`초)
  4. 따라서 요청 경로에서 Gemini가 3초 안에 답하지 않으면: 3초에 `callTimeout`이 호출을 취소 → `IOException` → 인터셉터가 평균 1·2·4·8초를 잠들며 4번 더 `proceed()`하지만 취소된 호출이라 즉시 실패 → **평균 약 18초, 최악(지터 상한) 약 3+2+4+8+16 = 33초** 뒤에야 예외가 `LlmGateway.invoke`에 도착한다. 429(무료 티어 한도 — `open-items` C-12에서 실제로 관측된 실패)로 빠르게 실패하는 경우도 첫 두 시도 뒤 같은 꼬리를 탄다
  5. 그 시간 동안 Tomcat 요청 스레드와 격벽 permit(`LlmGateway.java:112-121`)이 잡혀 있다. 격벽 상한 5개는 여전히 지켜지므로 검색·자동완성으로 번지지는 않지만, 레시피 API 자체는 "3초 안에 `GENERATION_FAILED`로 200"이 아니라 게이트웨이 상한(5초)에 먼저 끊긴다 — 사용자가 보는 것은 친절한 빈 응답이 아니라 게이트웨이 오류다
  6. 같은 이유로 배치 경로 산정도 틀렸다(안전한 방향으로). `callTimeout`이 재시도까지 통째로 감싸므로 배치 호출 1회의 최악은 "30초 × 3회 + 백오프 = 93초"(`LlmClientConfig.java:26`, `KafkaConsumerConfig.java:63-65`의 180초/상품)가 아니라 **30초 + 취소 뒤 대기 약 2+4초 ≈ 36초**다. `PRODUCT_MAX_POLL_INTERVAL_MS`(15분)는 그대로 안전하다
- **왜 문제인가**: 2026-09-08 라운드가 P0으로 넣은 "요청 스레드를 3초 이상 잡지 않는다"가 실제 장애 상황(느린 응답·429)에서 성립하지 않는다. 지표 `llm.call`도 이 꼬리를 "empty"로만 남겨(B-1) 아무도 알아채지 못한다. 포트폴리오·스펙이 3초를 사실로 적고 있다 — STOP §1 문서·구현 정합 위반
- **개선 방법**:
  1. `buildClient`에서 `attempts == 1`일 때도 `HttpRetryOptions`를 **명시**한다 — `HttpRetryOptions.builder().attempts(1).initialDelay(0.0)`. `attempts(1)`만으로는 429 응답 뒤 한 번 잠드는 경로(`139 → 166`)가 남으므로 `initialDelay(0.0)`(또는 `httpStatusCodes(List.of())`)까지 넣어 대기를 0으로 만든다
  2. 배치 경로는 3회 그대로 두되, 잘못된 산정 문구를 고친다: `LlmClientConfig.java:26,31,99`, `LlmCallKind.java:12`, `KafkaConsumerConfig.java:63-65`(180초 → 상품당 재료명 약 36초 + 묶음당 임베딩 약 36초), 스펙 흐름 4단계·리스크 표(B-3과 함께)
  3. `refactor-log.md`(cart-recipe·cross-cutting)에 Before/After — 5인 공용 저장소라 "3초"를 믿고 있는 사람이 놀라지 않게
- **검증 방법** (Gemini 키 불필요 — 로컬 스텁):
  - `LlmClientConfigTest`에 추가: `com.sun.net.httpserver.HttpServer`로 429를 즉시 돌려주는(또는 10초 잠드는) 스텁을 띄우고, `HttpOptions.builder().baseUrl("http://localhost:{port}").timeout(3000)`으로 `Client`를 만들어 `client.models.generateContent(...)`를 1회 부른다. `HttpOptions.Builder`에 `baseUrl(String)`이 있음을 확인했다(`javap`). `buildClient`가 `baseUrl`을 받지 않으므로 테스트가 옵션을 같은 모양으로 조립하거나, `buildClient`를 `HttpOptions.Builder`를 받는 패키지 전용 오버로드로 한 줄 열어 준다
  - **Before**: 스텁이 받은 요청 수 ≥ 2(두 번째 시도 앞 대기가 최대 2초라 3초 안에 반드시 온다), 호출이 예외로 끝나기까지 3초를 넘는다. **After**: 요청 수 정확히 1, 3초 근처에서 끝난다
  - `./gradlew :baro-ai:test` 전건 통과
- **API 영향**: No Impact (응답 계약 불변. 실패 시 200 + `GENERATION_FAILED`가 3초 근처에 나가게 된다)
- **예상 변경 파일**: `config/LlmClientConfig.java`, `config/KafkaConsumerConfig.java`(주석), `common/llm/LlmCallKind.java`(주석), `src/test/.../LlmClientConfigTest.java`, `docs/specs/cart-recipe/recipe-recommendation.md`, `docs/audits/cart-recipe/refactor-log.md`, `docs/audits/cross-cutting/refactor-log.md`
- **예상 변경 라인 수**: 코드 약 10줄, 테스트 약 40줄, 문서 약 15줄
- **위험도**: Low — 옵션을 명시할 뿐이고, 배치 경로 값은 그대로다
- **테스트 영향도**: 기존 3건 그대로. 새 테스트가 "SDK 기본 재시도가 켜져 있지 않다"를 고정한다 — SDK 버전을 올릴 때 이 계약이 깨지면 여기서 잡힌다
- **예상 효과**: 성능(장애 시 요청 경로 점유 평균 18초 → 3초) / 관측 정확성(B-1과 함께) / 문서 정합

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `llm.call{outcome=failed}`가 도달 불가 — 타임아웃·429·파싱 실패가 전부 `empty`로 찍힌다

- **Priority**: Medium
- **Category**: Dead Code / Cleanup (관측)
- **문제**: `LlmGateway.invoke`가 `catch (Exception)`으로 모든 실패를 `Optional.empty()`로 바꾼다([`LlmGateway.java:137-148`](../../../src/main/java/com/barofarm/ai/common/llm/LlmGateway.java)). 그 바깥의 `timed`는 `RuntimeException`을 잡아 `failed`를 기록하지만(`:157-168`), `invoke`가 예외를 밖으로 내보내지 않으므로 그 분기는 죽어 있다. `try` 앞의 `converterFor`·옵션 빌더는 결정적이라 예외를 내지 않는다. 결과: Gemini가 3초를 넘긴 것(A-1의 상황), 429, 스키마에 맞지 않는 응답이 전부 `outcome=empty`로만 남고, Javadoc이 약속한 "호출 수·지연·결과를 남긴다"(`:34`)의 "결과"가 `success`/`empty`/`rejected` 셋으로 줄어든다
- **근거**: 코드로 확인 — `invoke`의 `catch (Exception e)`(`:145`)와 `timed`의 `catch (RuntimeException e)`(`:162`). `record(kind, "failed", …)`(`:163`)를 부르는 경로가 없다
- **왜 문제인가**: `RecommendationMetrics`는 "빈 응답이면 Gemini 쪽을 봐야 한다"고 적었는데(`RecommendationMetrics.java:55-57`), 정작 Gemini가 시간을 넘긴 건지 답을 못 만든 건지 지표로 나눌 수 없다. A-1을 고친 뒤 "3초 안에 얼마나 실패하나"를 보려면 이 태그가 살아 있어야 한다
- **개선 방법**: `invoke`의 `catch`를 없애고 예외를 `timed`까지 올린 뒤, `timed`가 `failed`를 기록하고 **rethrow 대신 `Optional.empty()`를 돌려준다**(호출자 계약 "실패는 빈 결과"는 그대로). 로그 한 줄도 그쪽으로 옮긴다
- **검증 방법**: `LlmGatewayTest`에 `ChatClient` mock의 `prompt()`가 `RuntimeException`을 던지는 케이스 추가 → `SimpleMeterRegistry`에서 `llm.call{kind=interactive,outcome=failed}` count 1, 반환 `Optional.empty()`. 현재 코드에서는 같은 테스트가 `outcome=empty`로 찍혀 실패한다
- **API 영향**: No Impact
- **예상 변경 파일**: `common/llm/LlmGateway.java`, `src/test/.../LlmGatewayTest.java`
- **예상 변경 라인 수**: 약 15줄 + 테스트 약 25줄
- **위험도**: Low
- **테스트 영향도**: 기존 2건 그대로. 격벽 거절(`rejected`) 경로도 같은 테스트 클래스에서 `Semaphore`를 가득 채운 상태로 고정할 수 있으나 5개의 동시 호출을 만들어야 해 이번엔 필수로 두지 않는다
- **예상 효과**: 관측 정확성 / 죽은 분기 제거

### B-2. `RecipeCandidateGenerator`·`ProductIngredientNormalizer` 단위 테스트 — 005 C-1이 "비싸다"고 본 전제가 사라졌다

- **Priority**: Medium
- **Category**: Cleanup (테스트 가능성)
- **문제**: 005 C-1은 "`ChatClient` 플루언트 체인 4단계를 전부 stub해야 해서" 생성기 테스트를 미뤘다. 2026-09-08 이후 두 클래스는 `ChatClient`가 아니라 **`LlmGateway` 하나만** 주입받는다([`RecipeCandidateGenerator.java:31,46`](../../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGenerator.java), [`ProductIngredientNormalizer.java:28,51-55`](../../../src/main/java/com/barofarm/ai/search/infrastructure/llm/ProductIngredientNormalizer.java)). `LlmGateway.call`은 public 비final 메서드라 Mockito로 한 줄에 stub된다. 그런데 두 클래스의 분기 — 후보 배열이 `null`, 이름이 빈 후보 건너뛰기, 외부 값의 중괄호 제거가 **호출 지점에서 실제로 적용되는지**, 카테고리 없음 대체 문구, 빈 답을 빈 문자열로 — 를 고정하는 테스트가 없다. `LlmGatewayTest`는 `asData` 정적 메서드만 본다
- **근거**: `src/test/java/com/barofarm/ai/recommend/**`에 `RecipeCandidateGenerator` 테스트 없음(`ls` 확인). `ProductIndexServiceTest`는 정규화기를 mock으로 대체한다(`:45-48`)
- **왜 문제인가**: 프롬프트 인젝션 방어(`asData`)는 정적 메서드 테스트로는 "호출 지점이 그걸 거치는지"를 보장하지 못한다. 2026-09-07에 감사 006이 원인 하나를 놓친 것처럼, 후보 선택·빈 값 처리는 프롬프트가 아니라 코드 분기라 골든셋이 잡아 주지 않는다
- **개선 방법**: 두 테스트 클래스 신설. 생성기: (1) 관문이 empty → empty, (2) `candidates == null` → empty, (3) 첫 후보 이름이 공백이면 두 번째를 고른다, (4) 보유 재료 `"사과 {2kg}"`가 프롬프트에 `- 사과 2kg`로 들어간다(`ArgumentCaptor`), (5) 프롬프트에 보유 재료 줄이 전부 있다. 정규화기: (1) 상품명 공백이면 관문을 부르지 않고 `""`, (2) 카테고리 `null` → 프롬프트에 `(카테고리 없음)`, (3) 관문 empty → `""`, (4) 답이 공백만이면 `""`, (5) 온도 0.0·`BATCH`로 부른다. 프롬프트 **문구 자체**는 단언하지 않는다 — 문구는 골든셋 담당이고, 테스트가 문장에 붙으면 프롬프트를 고칠 때마다 깨진다
- **검증 방법**: `./gradlew :baro-ai:test` — 신규 약 10건 통과
- **API 영향**: No Impact
- **예상 변경 파일**: `src/test/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGeneratorTest.java`(신규), `src/test/java/com/barofarm/ai/search/infrastructure/llm/ProductIngredientNormalizerTest.java`(신규)
- **예상 변경 라인 수**: 약 120줄 (프로덕션 코드 0줄)
- **위험도**: Low
- **테스트 영향도**: 커버리지 공백 2클래스 해소
- **예상 효과**: 테스트성 / 인젝션 방어의 호출 지점 고정

### B-3. 2026-09-09 삭제·변경분을 반영하지 못한 "현행" 문구 — 스펙 4곳·코드 주석 3곳·골든셋 README 1곳

- **Priority**: Low
- **Category**: Cleanup (문서·주석 정합 — STOP §1·§4)
- **문제**: 제외 카테고리 필터를 통째로 지운 변경(cross-cutting `refactor-log` 2026-09-09)과 A-1이 드러낸 재시도 사실이 아래 문구에 반영되지 않았다. 스펙은 변경 이력 행(`:172`)에는 삭제를 적었는데 본문이 옛 상태를 "현행"으로 말한다 — 2026-09-07 회고가 지적한 "같은 화면 안에서 앞뒤 모순" 유형이다
  | 어디 | 무엇이 현행이 아닌가 |
  |---|---|
  | [`recipe-recommendation.md:28`](../../specs/cart-recipe/recipe-recommendation.md) 용어 표 "보유 재료" | "제외 카테고리를 걸러낸 뒤 — 목록이 비어 지금은 전부 통과" — 걸러내는 코드 자체가 없다 |
  | 같은 문서 `:78` 데이터 모델 "Feign 수신 DTO" | `CartItemInfo(productId·productName·productCategoryCode)` — 카테고리 코드 필드는 삭제됐다(`CartItemInfo.java:19-22`) |
  | 같은 문서 `:149` 리스크 표 | "게이트웨이 5초 vs LLM 최악 약 93초 — 확정" — 요청 경로는 2026-09-08부터 3초 상한이고, A-1 반영 뒤에는 정말 3초가 된다 |
  | 같은 문서 `:154` 리스크 표 | "`RecommendErrorCode` … `INVALID_TOP_K` 1건" — `USER_MISMATCH`가 2026-09-08에 추가돼 2건이고, 레시피 컨트롤러가 던진다(`RecommendationController.java:140`) |
  | [`RecipeRecommendReason.java:22`](../../../src/main/java/com/barofarm/ai/recommend/application/dto/RecipeRecommendReason.java) | "제외 카테고리 목록이 비어 있는 지금은" — 목록이 없다 |
  | [`ProductDocument.java:84`](../../../src/main/java/com/barofarm/ai/search/domain/ProductDocument.java) | "레시피 추천의 제외 카테고리 판정에도 쓸 수 있다(그 목록은 현재 비어 있다)" — 판정 코드가 없다 |
  | [`RecipeCandidateDrafts.java:9`](../../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateDrafts.java) | "복수의 레시피 후보를 감싸는" — 프롬프트는 1개를 요구하고 168회 측정 전부 1개였다(D-1). 배열을 유지하는 이유(빈 이름 방어)를 적는 편이 맞다 |
  | [`scripts/goldenset/README.md:104`](../../../scripts/goldenset/README.md) | "호출 타임아웃 30초 · 재시도 총 3회"가 운영과 같다고 적혀 있으나 운영 요청 경로는 3초·0회다. 실행기 `CALL_TIMEOUT_SEC = 30`(`run_goldenset.py:44`)이라 **골든셋은 운영이 겪을 타임아웃을 한 번도 관측하지 못한다**(판단 항목 1·3) |
- **근거**: 위 표의 각 줄을 코드와 대조했다. 스펙 파일은 감사 시점 working tree에서 편집 중이었다(`git diff --stat` 13+/10-, 흐름 표 2단계의 `isRecipeEligible` 행은 이미 고쳐져 있었다) — 반영할 때 편집 중인 내용과 합친다
- **왜 문제인가**: 스펙이 이 단위의 SSOT인데 존재하지 않는 필터·필드·시간을 사실로 말한다. 다음 감사자가 이 문구를 근거로 삼으면 006 때처럼 처방이 틀어진다
- **개선 방법**: 표의 문구를 현행으로 고친다. 스펙은 "제외 카테고리" 용어 행(`:34`)이 이미 "더 이상 없다"로 돼 있으니 `:28`은 그 행을 가리키게만 하면 된다. 골든셋 README는 "요청 경로는 3초·0회이고 실행기는 30초로 잰다"고 차이를 명시한다(맞추는 것은 판단 항목 1 결과 뒤)
- **API 영향**: No Impact
- **예상 변경 파일**: 위 표 8곳
- **예상 변경 라인 수**: 약 12줄
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 문서 정합

### B-4. `CandidateRecipePlan`(domain)이 `RecipeCandidateDraft`(infrastructure.llm)를 참조한다 — 레이어 방향이 거꾸로다

- **Priority**: Low
- **Category**: Architecture
- **문제**: 도메인 record의 정적 팩토리가 LLM 응답 스키마 타입을 파라미터로 받는다([`CandidateRecipePlan.java:3,40`](../../../src/main/java/com/barofarm/ai/recommend/domain/CandidateRecipePlan.java)). `RecipeCandidateDraft`는 `BeanOutputConverter`가 Gemini 스키마로 등록하는 인프라 계약이라(`RecipeCandidateDraft.java:9`) 도메인이 그것에 의존하면 스키마를 바꿀 때 도메인 테스트(`CandidateRecipePlanTest.java:5`)까지 같이 바뀐다. `spring-boot-java.md` 레이어 절은 domain을 "순수 계산 유틸·프레임워크 의존 최소"로 둔다
- **근거**: import 한 줄로 확인. `recommend.domain` 두 클래스 중 다른 하나(`IngredientProcessingUtil`)는 순수하다
- **왜 문제인가**: 유지보수 — 지금은 필드 3개라 눈에 띄지 않지만, 응답 스키마에 필드를 더하거나 이름을 바꾸는 순간 도메인이 따라 움직인다. baro-ai에는 ArchUnit이 없어 사람이 잡아야 한다
- **개선 방법**: `CandidateRecipePlan.from(String recipeName, List<String> core, String instructions, List<String> ownedIngredients)`로 바꾸고 `RecipeCandidateGenerator.generateRecipe`가 draft를 풀어서 넘긴다(`:58`). 테스트 헬퍼도 같은 모양으로. record 자체는 옮기지 않는다 — 스키마 record는 인프라에 있는 것이 맞다
- **검증 방법**: `./gradlew :baro-ai:test` — `CandidateRecipePlanTest` 5건 그대로 통과
- **API 영향**: No Impact
- **예상 변경 파일**: `recommend/domain/CandidateRecipePlan.java`, `recommend/infrastructure/llm/RecipeCandidateGenerator.java`, `src/test/.../CandidateRecipePlanTest.java`
- **예상 변경 라인 수**: 약 15줄
- **위험도**: Low
- **테스트 영향도**: 헬퍼 1개 수정
- **예상 효과**: 유지보수성 / 레이어 규칙 준수

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. `buildIngredientQuery`가 빈 키워드를 받으면 상태 필터만 남아 "아무 상품 2건"을 돌려준다

[`ProductQueryFactory.java:58-66`](../../../src/main/java/com/barofarm/ai/search/infrastructure/elasticsearch/ProductQueryFactory.java)은 키워드가 공백이면 `should` 절 없이 `applyStatusFilter`만 건다 — 일반 검색(`buildSearchQuery`)의 "키워드 없으면 전체 보기"를 그대로 물려받은 모양이다. 부족 재료 조회에서는 엉뚱한 카드가 붙는 결과가 된다. **왜 지금 안 하나**: 지금 도달 경로가 없다. `missingCore`는 `normalizeList`가 공백을 걸러낸 목록이고(`IngredientProcessingUtil.java:33-38`), `toSearchKeyword`는 `trim()` 뒤 마지막 어절을 자르므로 빈 문자열이 나올 수 없다(`:55-62`, `IngredientProcessingUtilTest` 2건이 고정). `searchByKeywords`에 두 번째 호출자가 생기면 그때 `ProductSearchService.searchByKeywords`에서 빈 키워드를 빈 결과로 매핑하는 한 줄을 넣는다.

### C-2. 보유 재료가 비교용 정규화 형태(소문자·공백 제거)로 프롬프트와 `GENERATION_FAILED` 응답에 나간다

001 C-3의 이월이다. `loadOwnedIngredientNames`가 `normalizeForCompare`를 거친 문자열을 모으고([`RecipeRecommendService.java:210-214`](../../../src/main/java/com/barofarm/ai/recommend/application/RecipeRecommendService.java)), 그것이 프롬프트 입력(`:124`)과 실패 응답의 `ownedIngredients`(`:129`)가 된다. 2026-09-06 이후 정상 응답의 `ownedIngredients`는 LLM 원문(`usedOwnedIngredients`)이라 그쪽은 해소됐다. **왜 지금 안 하나**: 색인 재료명이 한 단어 한글이라 눈에 보이는 차이가 없고(골든셋 168회 중 영문·공백 재료명 0건), 표시용과 비교용을 분리하면 `CandidateRecipePlan.from`의 이중 정규화(C-6)까지 함께 손대야 한다. 사용자에게 보이는 사례가 나오면 연다.

### C-3. 인프라 클래스의 스테레오타입이 갈린다 — `@Service`(생성기) vs `@Component`(정규화기)

[`RecipeCandidateGenerator.java:27`](../../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGenerator.java)은 `@Service`, [`ProductIngredientNormalizer.java:18`](../../../src/main/java/com/barofarm/ai/search/infrastructure/llm/ProductIngredientNormalizer.java)은 `@Component`, `LlmGateway`도 `@Component`다. 둘 다 유스케이스가 아니라 외부 호출 어댑터라 `@Component`가 맞다. **왜 지금 안 하나**: 동작 차이가 없고, 애너테이션 하나 바꾸는 커밋이 다른 변경에 섞이면 diff만 흐린다. B-4를 반영할 때 같은 파일을 만지므로 그때 한 줄 같이 고치면 된다.

### C-4. 색인 시점 재료명이 판매자 상품명에서 나오므로 레시피 프롬프트에 간접 인젝션 경로가 있다

상품명 → (`ProductIngredientNormalizer`, LLM) → `ingredientName` → 레시피 프롬프트의 `<보유 재료>`(`RecipeCandidateGenerator.java:97-100`). 관문의 `asData`는 중괄호만 지우고, `LlmGateway.java:87-88` Javadoc이 "지시문처럼 읽히는 문장은 막지 못한다"고 이미 인정한다. 정규화 프롬프트가 "한 단어 명사"를 요구해 지시문이 통과할 여지는 좁지만, 규칙을 어긴 답이 그대로 색인되는 것을 막는 검사는 없다. **왜 지금 안 하나**: 관측된 사례가 없고, 막으려면 (a) 정규화 결과의 길이·문자 종류 검사(코드) 또는 (b) 레시피 프롬프트 문구 변경인데, (b)는 골든셋 재측정을 부른다. 먼저 `scripts/localstub/seed_catalog.py`에 지시문처럼 생긴 상품명 하나를 넣어 색인 결과를 보는 것이 순서다. 결과가 나오면 (a)만으로 충분한지 판단한다.

### C-5. 골든셋 실행기가 지연을 기록하지 않고 30초 상한으로 잰다

`run_goldenset.py`가 남기는 키는 토큰·후보 수·선택 결과뿐이고(`raw-*.jsonl` 키 목록 확인) `elapsed`가 없다. 상한도 `CALL_TIMEOUT_SEC = 30`(`:44`)이라 운영 요청 경로(3초)가 겪을 타임아웃을 측정이 보지 못한다. **왜 지금 안 하나**: 스크립트 변경이라 이 라운드의 코드 범위 밖이고, 무엇을 잴지는 판단 항목 1의 결정에 달렸다. 판단 항목 1을 열기로 하면 `urlopen` 앞뒤에 `time.monotonic()` 두 줄을 더하는 것이 전부다.

### C-6. `CandidateRecipePlan.from`이 보유 재료 정규화 집합을 두 번 만든다

`:47-49`에서 `ownedNormalized`를 만들고, `:60-61`의 `subtractNormalized`가 같은 집합을 다시 만든다(`IngredientProcessingUtil.java:93-96`). 핵심 재료 5개·보유 재료 10개 안팎이라 비용은 없다. **왜 지금 안 하나**: 한 번의 분할(partition)로 합치면 `subtractNormalized`의 단독 테스트 3건이 소비처를 잃어 유틸 API를 함께 정리해야 한다 — 얻는 것에 비해 손대는 면이 넓다. C-2를 열 때 같이 본다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 응답 스키마의 `candidates` 배열을 단일 객체로 바꾸지 않는다

프롬프트는 1개를 요구하는데 스키마는 배열이라 "모델이 여러 개를 만들어 출력 토큰을 낭비하지 않나"를 확인했다. 골든셋 원본 4회분(`raw-after-20260907-175038`·`175938`·`20260909-040156`·`061606`, 각 42건) **168회 전부 `candidate_count = 1`**이다. 배열이 낭비를 만들지 않는다는 실측이고, 바꾸면 스키마가 달라져 골든셋을 다시 재야 한다. 빈 이름 방어(`RecipeCandidateGenerator.java:55-58`)도 배열이라 자연스럽다. 유지.

### D-2. 프롬프트를 줄이거나(572토큰) 캐싱하지 않는다

측정 009가 +147토큰(425 → 572)을 규칙 4의 값으로 확인했고(팔지 않는 종류 51% → 22%), 어느 문장이 죽어 있다는 증거가 없다. researcher 조사(📎)대로 Gemini implicit caching 최소 문턱(2,048~4,096토큰)과 Spring AI explicit caching 최소(32,768)에 한참 못 미쳐 캐싱 대상도 아니다. 문구를 한 글자라도 바꾸면 42건 × 2팔 재측정이 따라온다 — 사용자가 "더 조이지 않는다"고 정한 것과도 같은 방향. 유지.

### D-3. 카탈로그 그라운딩·enum 스키마·retrieval-then-generate를 도입하지 않는다

researcher가 찾은 유사 구현(📎)은 전부 "파는 재료 목록"을 프롬프트·스키마·검색 단계에 두는 방식이다. 오늘 결정은 정반대다 — 판매 상품 SSOT는 검색 인덱스 자체이고, 코드·프롬프트에 재료 목록·예외 목록을 두지 않는다(008 §3, 009). 기술적으로도 재료명은 닫힌 집합이 아니어서 enum 스키마는 구조적으로 맞지 않고, 개수 상한도 공식 문서에 없다(커뮤니티 약 100개). 결정과 충돌하므로 A/B가 아니라 여기 둔다. 이 결정을 다시 열려면 상품 시드가 들어온 뒤 인덱스 집계로 적중률을 재는 것이 먼저다.

### D-4. 같은 장바구니에 같은 답을 주는 캐싱을 넣지 않는다

008 §5-4가 "지금 해도 되는 유일한 항목"으로 적었으나 2026-09-09 사용자가 제외했다 — 포트폴리오에서 설명할 수 없는 변경은 넣지 않는다. 결정 사항이라 재론하지 않는다.

### D-5. `seed`로 결정성을 만들지 않는다

Spring AI 1.1.8 `GoogleGenAiChatOptions`에 `seed`가 없다(researcher jar 디컴파일, 008 §2-1도 확인). SDK `GenerateContentConfig.seed()`를 쓰려면 Spring AI를 우회해야 하는데, 그러면 `LlmGateway`가 모은 관문 구조가 깨진다. 불가능에 가깝고 얻는 것(같은 장바구니 같은 답)은 D-4로 이미 제외됐다.

### D-6. `verifyOwner`를 AOP로 빼지 않는다

`RecommendationController.java:73,95` 두 곳에서 같은 private 메서드를 부른다. 컨트롤러 하나 안의 한 줄 검사이고 `harness-map.md`가 AOP 옵션을 끈 상태다. 헤더 필수화(`open-items` B-2)가 끝나면 경로 변수 자체가 사라져 이 메서드도 없어진다.

### D-7. 장바구니 Feign 호출에 서킷브레이커·fallback을 붙이지 않는다

호출 지점이 하나(`CartClient.getCart`)이고, 연결 1초·읽기 3초 상한(`application.yml:50-51`)과 최상위 catch → `UPSTREAM_ERROR`(`RecipeRecommendService.java:72-79`)가 이미 실패를 격리한다. 실패율 데이터가 없고, 브레이커가 열려도 결과는 같은 빈 응답이다 — 아래 아키텍처 절 Later.

### D-8. 레시피 생성을 비동기·큐로 돌리지 않는다

사용자가 화면 앞에서 결과를 기다리는 호출이라 비동기로 바꿔도 기다림은 그대로이고, 폴링·상태 저장이 붙는다. 동기 + 3초 상한 + 격벽 5(`LlmGateway.java:54`)가 이 규모의 정답이다. A-1을 고쳐야 그 설계가 말대로 동작한다.

### D-9. 요청 경로에 재시도 1회를 "의도적으로" 넣지 않는다

A-1은 우연히 켜진 재시도를 끄자는 것이지, 재시도 정책을 새로 정하자는 것이 아니다. 3초 안에 두 번 시도할 여유가 있는지는 판단 항목 1(실제 지연 분포)이 나와야 안다. 그전에는 `LlmClientConfig.java:31`의 원래 판단("재시도는 사용자를 더 기다리게 할 뿐")을 그대로 둔다.

## 백엔드 아키텍처 개선 제안

이 단위에 실제로 적용 가치가 있는지 본 기술 후보다. 각 행의 근거는 코드로 확인한 현재 동작이고, Now/Later/Never 판정에 이유를 붙였다.

| 카테고리 | 제안 | 근거(현재 동작) | 장단점 · 난이도 | 판정 |
|---|---|---|---|---|
| Resilience | 요청 경로 Gemini 호출에 서킷브레이커 | 격벽(`LlmGateway.java:54,112`)이 동시 5건을 막아 번짐은 이미 없다. 다만 Gemini가 완전히 죽으면 5건이 각각 3초(A-1 반영 뒤)씩 실패를 반복한다 | 실패율이 높을 때 즉시 `GENERATION_FAILED`로 돌려 3초도 아낀다 / 임계값을 정할 실패율 데이터가 없고(B-1 반영 전에는 `failed` 태그 자체가 없다) resilience4j 의존성이 붙는다. 난이도 중 | **Later** — A-1·B-1 반영 뒤 `llm.call{outcome=failed}` 비율을 보고 정한다 |
| Monitoring | 추가 없음 | `llm.call`(경로·결과)·`recommendation.results.reason`·`product.index.enrichment`가 이미 있다 | — | **Now 해당 없음** — B-1로 기존 지표를 살리는 것이 먼저 |
| Redis | 응답 캐시 | — | — | **Never** — D-4 사용자 결정 |
| Async | 레시피 생성 큐잉 | — | — | **Never** — D-8 |
| Security | 레이트 리밋 | 격벽이 동시성은 막지만 초당 호출 수는 안 막는다. 무료 티어 429는 C-12에서 관측됐다 | 사용자별 한도는 게이트웨이 소관(`platform.md` — 외부 클라이언트 없음) | **Later** — 트래픽이 생기고 게이트웨이에 없는 것이 확인되면 |

## 📎 참고: Best Practice·유사 구현 사례 (`researcher` 조사 결과 — A/B 판정 근거 아님)

메인 세션이 `researcher`로 조사한 결과를 그대로 옮긴다. 이 절은 사용자가 판단할 때 참고하는 자리이고, 위 A/B의 "근거"를 대체하지 않는다.

- **seed**: Spring AI 1.1.8 `GoogleGenAiChatOptions`에 `seed`가 없다(jar 디컴파일 확인). SDK `GenerateContentConfig`에는 `seed()`가 있으나 Spring AI가 매핑하지 않는다. `temperature`·`topP`·`topK`는 있다. → D-5
- **구조화 출력**: `responseSchema`(문자열)는 SDK `responseJsonSchema`로 매핑된다. 이 감사에서 `spring-ai-google-genai-1.1.8.jar`의 `GoogleGenAiChatModel`을 `javap`로 열어 `GoogleGenAiChatOptions.getResponseSchema()` → `GenerateContentConfig.Builder.responseJsonSchema(Object)` 호출을 직접 확인했다. enum·items·maxItems·nullable·required를 지원하고, enum은 구조적으로 강제되나 값의 의미 정확성은 보장하지 않는다. enum 개수 상한은 공식 문서에 없고 커뮤니티(포럼, python-genai #660·#950)가 약 100개에서 "too many states" 오류를 보고했다. 재료명은 닫힌 집합이 아니라 enum 전환에 구조적 제약이 있다. → D-3
- **캐싱**: implicit caching 최소 2,048~4,096토큰(flash-lite는 표에 없음), Spring AI explicit auto-caching 최소 32,768. 572토큰 프롬프트는 대상이 아니다. → D-2
- **유사 구현**: retrieval-then-generate(카탈로그 grounding — 예: GitHub `ArturGR3/food_search_RAG`, 벡터 검색 + 정확 필터 + LLM judge) vs generate-then-filter(현재 baro-ai). 학술 arXiv 2607.23273은 재료 어휘를 표준화한 뒤 벡터화한다. → D-3 (오늘 결정과 충돌)
- **문서 함정**: docs.spring.io Spring AI 문서를 버전 없이 열면 2.0.1로 리다이렉트된다 — `/1.1/` 경로로 고정해야 한다
- **확인하지 못한 것**: flash-lite implicit caching 문턱 · `responseJsonSchema` vs `responseSchema` 경로의 enum 강제 강도 차이. 572토큰이 실측인지는 확인됐다 — `run_goldenset.py`가 응답의 `usageMetadata.promptTokenCount`로 센 값이다(`:236`)

## 판단이 서지 않는 항목

1. **요청 경로 3초 상한이 정상 응답을 자르고 있지 않은가.** 3초는 실측이 아니라 게이트웨이 상한(5초)에서 끌어낸 값이고(cross-cutting `refactor-log` 2026-09-08 "남긴 것"), `gemini-3.5-flash-lite`의 응답 시간 분포를 잰 자료가 저장소에 없다 — 골든셋은 지연을 기록하지 않고 30초로 기다린다(C-5). p95가 3초를 넘는다면 A-1을 고친 뒤에도 정상 요청이 `GENERATION_FAILED`로 나간다. **확인 방법**: `run_goldenset.py`에 `elapsed_ms`를 더해 `--arm after` 1회(42호출, 키 필요) → p50·p95·최대를 3초와 비교. 3초를 넘는 비율이 의미 있으면 다음 라운드 A(상한 조정 또는 모델 재검토). 이 측정은 Gemini 키와 무료 티어 한도를 쓰므로 사용자가 실행 여부를 정한다.
   > **2026-09-09 실측 (승인 후 같은 날).** 실행기에 `elapsed_ms`를 넣고 현행 프롬프트로 42회를 두 번 돌렸다(`raw-after-20260909-103121.jsonl`·`raw-after-20260909-103917.jsonl`, 실행기 자체 상한 30초).
   >
   > | 실행 | p50 | p90 | p95 | 최대 | 3초 초과 |
   > |---|---|---|---|---|---|
   > | 1차 10:31 | 1,630ms | 8,501ms | 8,710ms | 9,296ms | 16/42 (38%) |
   > | 2차 10:39 | 1,480ms | 1,825ms | 3,045ms | 4,341ms | 3/42 (7%) |
   >
   > 1차의 느린 호출은 실행 초반 13건(S1~S4)에 몰려 있고 토큰 수·시나리오·생각 토큰(0)과 무관하다 — 8분 뒤 같은 조건에서 사라졌으므로 API 쪽 일시 현상으로 본다. 평상시 분포는 2차다: **p95가 3초 상한에 걸쳐 있어 정상 상태에서도 42회 중 3회(7%)가 `GENERATION_FAILED`로 나가고, 1차 같은 구간에서는 3분의 1 이상이 그렇게 된다.** 상한을 4초로 올리면 2차 기준 초과가 1/42(2%)로 줄고 게이트웨이 5초 안에 남는다 — 값 변경은 스펙 수치라 사용자 결정 대기.
2. **경로 전체 예산 합이 게이트웨이 5초를 넘는다.** 코드 값을 더하면 장바구니(연결 1초 + 읽기 3초, `application.yml:50-51`) + ES 문서 조회(소켓 3초, `:35`) + LLM(3초, `LlmClientConfig.java:57`, A-1 반영 후) + ES 부족 재료 검색(3초) = 최악 13초다. 정상 경로는 이 합에 한참 못 미치겠지만(ES p95 8ms 미만, decisions/004), 어느 단계가 늦어져도 사용자는 5초에 끊긴다. `open-items` B-3(게이트웨이 담당자)로 이미 열려 있어 새 항목으로 세우지 않고, 합산 값만 여기 남긴다.
3. **골든셋이 운영과 같은 조건인가.** 스키마: 운영은 `BeanOutputConverter`가 만든 JSON Schema를 `responseJsonSchema`로 보내고(`LlmGateway.java:130-132`), 실행기는 손으로 적은 OpenAPI 계열 스키마를 REST `responseSchema`로 보낸다(`prompts.py:_schema`). 필드 셋은 같지만 바이트가 같지 않고, 007 M-4의 앱 경유 실행 1회 외에는 운영 스키마로 잰 골든셋이 없다. 상한: 운영 3초 vs 실행기 30초(C-5). **확인 방법**: `LlmGatewayTest`에서 `new BeanOutputConverter<>(RecipeCandidateDrafts.class).getJsonSchema()`를 출력해 `prompts.py`의 `AFTER_SCHEMA`와 나란히 놓고 차이를 적는다(키 불필요). 차이가 `required`·`additionalProperties` 수준이면 README에 "같은 조건"의 범위를 명시하는 것으로 끝내고, 그 이상이면 실행기가 Java 스키마를 파일로 읽게 바꾼다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현한다 — A-1(High) → B-1·B-2(Medium) → B-3·B-4(Low). C/D는 이번 라운드에서 수정하지 않는다. 판단 항목 1은 사용자가 측정 실행 여부를 정한다.
