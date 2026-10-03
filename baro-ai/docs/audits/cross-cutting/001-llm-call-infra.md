# Cross-cutting Architecture Audit 001 — LLM 호출 인프라 (2026-09-04)

baro-ai가 Gemini를 호출하는 공통 인프라(`LlmClientConfig`·`application-prod.yml`의 `spring.ai.*`)와, 그 인프라를 공유하는 호출부 2곳(`RecipeCandidateGenerator`·`ProductIngredientNormalizer`)을 감사한 결과다. 반드시 고칠 A 항목 2건, 유지보수성 B 항목 1건이 나왔고, 세 건 모두 2026-09-04 사용자 승인 후 **반영 완료**했다(결과는 [`refactor-log.md`](refactor-log.md)). 이 문서는 감사 시점의 근거·분류를 남기는 스냅샷이다. 테스트 관련 2건은 2026-09-04 사용자 결정("테스트 코드는 일단 없어도 됨")으로 C로 옮겼고, 그에 따라 모든 검증 방법은 로컬에서 수동으로 재현하는 절차로 적었다. 지적 사항의 근거 조사는 [`ssoss-llm-comparison.md`](../ssoss-llm-comparison.md)에 있고, 이 문서는 그 조사를 코드·jar 바이트코드로 재확인한 뒤 A/B/C/D로 분류한 것이다.

## 범위

- 패키지: `com.barofarm.ai.config`(`LlmClientConfig`, `application-prod.yml`의 `spring.ai.*`) + 이 인프라를 쓰는 호출부 `recommend.infrastructure.llm.RecipeCandidateGenerator`, `search.infrastructure.llm.ProductIngredientNormalizer`
- **범위 밖**: `review.application.summary.ReviewSummaryService` — 같은 `ChatClient`를 쓰고 같은 결함을 공유하지만 팀원 소유 도메인이라 관찰만 남기고 변경을 제안하지 않는다. `chat`은 빈 스텁이라 감사할 코드가 없다
- 감사자: 사용자가 가져온 ssoss 비교 조사 결과를 이 세션에서 코드·jar(`spring-ai-retry-1.1.8`·`spring-ai-google-genai-1.1.8`·`google-genai-1.37.0`) 바이트코드로 재확인했다. `safe-refactor`가 정한 "신선한 서브에이전트 감사" 대신 조사 결과 검증으로 대체한 점을 명시한다
- 기준: `audit-checklist.md` 1~16항목(16번 LLM·프롬프트 포함), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- **왜 도메인이 아니라 cross-cutting인가**: 지적 사항이 전부 "같은 `ChatClient` 인프라를 쓰는 호출부가 같은 결함을 공유"하는 모양이다. 인프라 한 곳을 고치면 호출부 2곳이 같이 바뀌므로, `safe-refactor`의 "한 번에 한 도메인" 원칙을 지키면서 한 라운드로 다룬다. 호출부 파일은 `recommend`·`search`에 있지만 변경 성격은 인프라 적용이라 여기서 관리한다

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `spring.ai.retry`는 죽은 설정이고, 실제 재시도는 SDK 기본값(5회·지연 60초)이 통제 없이 걸려 있음

- **Priority**: High
- **Category**: Performance (Resilience)
- **문제**: `application-prod.yml:43-48`의 재시도 설정(3회, 1초×2, 상한 10초)은 Gemini 호출에 적용되지 않는 죽은 설정이다. **처음 진단은 "그래서 재시도 0회"였는데 구현 착수 시 SDK 바이트코드를 다시 열어 보니 반대였다** — Google GenAI SDK는 재시도 옵션을 안 줘도 기본 인터셉터(총 5회 시도, 지연 1초→최대 60초, 429·5xx 대상)를 항상 붙인다. 즉 429·5xx는 이미 재시도되고 있었고, 문제는 "아무도 모르는 재시도가 최대 5회 걸려 최악 응답 시간(5 × 30초 + 지연)이 통제되지 않는다"는 것이다. 사용자가 가져온 조사와 `ssoss-llm-comparison.md`의 "재시도 0회" 서술은 Spring AI 레이어만 보고 SDK 레이어를 놓친 오류였다(2026-09-04 정정)
- **근거**:
  - `spring-ai-retry-1.1.8.jar`의 `RetryUtils` 바이트코드: `RetryTemplateBuilder.retryOn(TransientAiException)`·`retryOn(ResourceAccessException)` 두 타입만 재시도 대상으로 등록한다
  - `google-genai-1.37.0.jar`: 429는 `com.google.genai.errors.ClientException`, 5xx는 `ServerException`으로 던지며, 둘 다 `ApiException` → `BaseException` → `RuntimeException` 계열이다. 위 두 타입 어디에도 속하지 않는다
  - `spring-ai-google-genai-1.1.8.jar`의 `GoogleGenAiChatModel` 바이트코드: `RetryTemplate.execute(...)`로 감싸지만, SDK 예외를 `TransientAiException`으로 변환하는 코드가 없다(catch 블록은 `java.lang.Exception` 하나뿐)
  - `LlmClientConfig.java:29-32`가 만드는 `Client`에 `HttpOptions.retryOptions(...)`가 없다 — 그러면 `google-genai-1.37.0.jar`의 `ApiClient` 생성자가 `HttpRetryOptions.builder().build()`(전부 기본값)로 `RetryInterceptor`를 등록한다(바이트코드 확인). `RetryInterceptor` 기본값: 시도 5회, 초기 지연 1.0초, 최대 60.0초, 배수 2.0, 대상 408·429·500·502·503·504
- **왜 문제인가**: 동기 추천 API와 Kafka 리스너가 Gemini 장애 시 최악 몇 분까지 붙잡힐 수 있는데, 그 상한을 정한 곳이 코드 어디에도 없다. yml 주석("기본값은 최대 10회 재시도…")은 Spring AI 재시도 기본값을 근거로 삼았지만 실제 동작(SDK 5회)과 다르므로 STOP §1(문서·구현 정합) 위반이다. 재시도가 다 실패하면 레시피 추천은 빈 배열, 재료명은 빈 값으로 떨어진다([`search/001-index-path.md`](../search/001-index-path.md) B-2)
- **개선 방법**:
  1. `LlmClientConfig.googleGenAiClient()`에서 `HttpOptions.builder().timeout(...).retryOptions(HttpRetryOptions.builder().attempts(n).initialDelay(s).maxDelay(s).expBase(2.0).jitter(...).httpStatusCodes(429, 500, 502, 503, 504))`로 SDK 레벨 재시도를 건다. Builder 메서드 목록은 `HttpRetryOptions$Builder` 바이트코드로 확인했다(`attempts`·`initialDelay`·`maxDelay`·`expBase`·`jitter`·`httpStatusCodes`)
  2. 죽은 설정인 `spring.ai.retry.*` 블록과 그 주석을 `application-prod.yml`에서 삭제한다(STOP §4 — 교체된 구 경로는 같은 변경에서 제거)
  3. 재시도 횟수·지연·전체 상한을 코드 주석에 근거와 함께 남긴다 — 구현 시 총 3회 시도·초기 1초·상한 10초로 정했다(최악 약 3 × 30초 + 3초). 근거는 아래 "판단이 서지 않는 항목" 4번, 값은 커밋 전 사용자 확인 대상
  4. G1 리서치 필요: `HttpRetryOptions`의 `initialDelay`·`maxDelay` 단위(초로 추정)와 기본 재시도 대상 코드를 Google GenAI Java SDK 1.37.0 공식 문서로 확인한다 — `researcher` 서브에이전트 또는 인라인 `WebFetch`
- **검증 방법** (수동): 429를 마음대로 일으킬 수는 없으므로, 확인용으로만 재시도 대상 코드에 404를 잠시 추가하고 존재하지 않는 모델명(`SPRING_AI_GEMINI_MODEL=no-such-model`)으로 기동해 추천 API를 1회 호출한다. `org.springframework.ai: DEBUG`·SDK 로그에 같은 요청이 설정한 횟수만큼 반복되고 지연이 점점 길어지는지 본 뒤 404를 목록에서 되돌린다. 재시도가 없던 Before에서는 같은 절차로 요청이 1회만 찍힌다
- **API 영향**: No Impact
- **예상 변경 파일**: `config/LlmClientConfig.java`, `src/main/resources/application-prod.yml`
- **예상 변경 라인 수**: 코드 15줄 내외 + yml 8줄 삭제
- **위험도**: Low — 재시도가 늘어나면 최악 응답 시간이 길어지므로 전체 상한을 같이 정한다(C-1)
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 성능(일시 장애 내성) · 문서-구현 정합 회복

### A-2. 구조화 출력을 프롬프트 문구로만 요청하고 API 레벨에서 강제하지 않음

- **Priority**: High
- **Category**: LLM-Prompt
- **문제**: 호출부 2곳 모두 `.call().entity(...)`로 JSON 파싱을 기대하지만, `ChatOptions`에 `responseMimeType`·`responseSchema`를 넣지 않는다. JSON 준수는 "다른 텍스트는 절대 출력하지 마세요" 같은 프롬프트 문구에만 의존하고, 파싱이 실패하면 `catch (Exception e)`가 빈 값·폴백으로 흡수한다
- **근거**:
  - `RecipeCandidateGenerator.java:121-127` — `.user(...).call().entity(new ParameterizedTypeReference<>(){})`, 옵션 없음. 프롬프트 114줄 "다른 텍스트는 절대 출력하지 마세요"
  - `ProductIngredientNormalizer.java:40-46` — `.options(ChatOptions.builder().temperature(0.0).build())`만 있고 응답 형식 옵션 없음
  - `spring-ai-google-genai-1.1.8.jar`의 `GoogleGenAiChatOptions$Builder` 바이트코드에 `responseMimeType(String)`·`responseSchema(String)`가 있다 — 같은 의존성에 이미 있는 기능을 안 쓰고 있다
  - ssoss는 같은 버전에서 `responseMimeType(APPLICATION_JSON_VALUE)` + `responseSchema(converter.getJsonSchema())`로 강제한다(비교 문서 "구조화 출력 강제" 축)
- **왜 문제인가**: 모델이 JSON 앞뒤에 설명 문장이나 코드펜스를 붙이면 파싱이 깨지고, 그 실패는 429·타임아웃과 같은 `catch (Exception e)` 경로로 들어가 구분되지 않는다(B-1). 레시피 추천은 빈 배열을, 재료명은 정규식 폴백을 사용자에게 조용히 돌려준다
- **개선 방법**: 호출부 2곳에서 `BeanOutputConverter<T>`로 JSON 스키마를 만들고 `GoogleGenAiChatOptions.builder().responseMimeType("application/json").responseSchema(converter.getJsonSchema())`를 `.options(...)`에 넘긴다. `ProductIngredientNormalizer`는 기존 `temperature(0.0)`과 같은 빌더에 합친다. 프롬프트의 "JSON만 출력" 문구는 스키마 강제와 중복이므로 같은 변경에서 제거한다(STOP §4). 공통 헬퍼는 두 호출부가 같은 3줄을 반복하는 게 확인된 뒤에만 뽑는다(YAGNI)
- **검증 방법** (수동): 로컬 기동 후 추천 API 1회 호출, 상품 이벤트 1건 발행. `org.springframework.ai: DEBUG` 로그의 요청 본문에 `responseMimeType: application/json`과 `responseSchema`가 들어가고, 응답 본문이 코드펜스·설명 문장 없이 JSON만인지 확인한다. Before에서는 요청 본문에 두 필드가 없다
- **API 영향**: No Impact
- **예상 변경 파일**: `recommend/infrastructure/llm/RecipeCandidateGenerator.java`, `search/infrastructure/llm/ProductIngredientNormalizer.java`
- **예상 변경 라인 수**: 호출부 각 10줄 내외
- **위험도**: Low — `responseSchema`는 Gemini가 지원하는 JSON Schema 부분집합만 받으므로, `RecipeCandidates`처럼 중첩 record가 있는 타입은 G1에서 지원 범위를 확인한다
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 응답 파싱 안정성 · 프롬프트 토큰 소폭 감소(형식 지시 제거)

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. 호출 결과(토큰·지연·실패 종류)가 어디에도 기록되지 않음

- **Priority**: Medium
- **Category**: LLM-Prompt (관측)
- **문제**: 호출부 2곳 모두 성공 시 `log.debug`/`log.info`, 실패 시 `catch (Exception e)` 한 줄이다. 429·5xx·타임아웃·JSON 파싱 실패가 같은 경로로 들어가고, 토큰 수·응답 시간은 접근조차 하지 않는다
- **근거**:
  - `RecipeCandidateGenerator.java:42-45`, `ProductIngredientNormalizer.java:59-62` — `catch (Exception e)` → 로그 후 폴백
  - `grep -rn 'getUsage\|getMetadata' baro-ai/src/main/java` 0건
  - `GoogleGenAiChatModel` 바이트코드는 `ChatModelObservationContext`·`ObservationRegistry`를 받게 돼 있지만, `baro-ai/build.gradle`에 actuator·micrometer 의존성이 없어 registry가 NOOP이다. `application-prod.yml:91-105`의 `management.*` 블록은 actuator 없이 적혀 있는 죽은 설정이다
- **왜 문제인가**: "Gemini가 계속 JSON 파싱에 실패하고 있다"·"무료 티어 한도에 매일 걸린다" 같은 사실을 로그를 뒤지지 않고는 알 수 없다. A-1·A-2의 효과도 기록이 없으면 확인할 수 없다
- **개선 방법** (두 갈래 — 구현 시 (가) 채택. `researcher`가 actuator만으로 `gen_ai.client.token.usage`·`gen_ai.client.operation.duration`이 켜지는 것을 자동설정 조건에서 확인했고 코드 변경이 0줄이라서):
  - (가) **Spring AI 관측 활성화**: `spring-boot-starter-actuator` 추가만으로 `GoogleGenAiChatModel`이 이미 갖춘 관측 훅이 켜져 `gen_ai.client.token.usage`·`gen_ai.client.operation` 지표가 `/actuator/metrics`에 노출된다(1.1.8 공식 문서로 지표 이름 재확인 필요 — G1). 죽어 있던 `management.*` 설정도 살아난다. 코드 변경 0줄
  - (나) **호출부 로그 보강**: `.call().responseEntity(...)`로 `ChatResponse.getMetadata().getUsage()`를 읽어 INFO 한 줄에 토큰·소요 시간을 남기고, `catch`에서 `ApiException.code()`(429/5xx)·`InterruptedIOException`(타임아웃)·그 외(파싱)를 구분해 WARN 메시지를 달리한다. 상태 enum 7종·전용 예외 계층은 만들지 않는다(D-2)
  - 어느 쪽이든 두 호출부에 같은 `catch` 분류 코드가 생기면 그때 package-private 헬퍼 하나로 모은다
- **검증 방법** (수동): (가)는 로컬 기동 후 추천 API를 1회 호출하고 `curl localhost:8092/actuator/metrics/gen_ai.client.token.usage` 응답에 측정값이 있는지 확인. (나)는 같은 호출 뒤 INFO 로그에 토큰·소요 시간 한 줄이 찍히는지, 잘못된 API 키로 기동해 호출하면 WARN 메시지가 "인증/클라이언트 오류"로 분류돼 찍히는지 확인
- **API 영향**: No Impact
- **예상 변경 파일**: (가) `build.gradle` · (나) 호출부 2파일
- **예상 변경 라인 수**: (가) 1줄 · (나) 40줄 내외
- **위험도**: Low
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 관측 가능성 · A-1·A-2 효과 측정 수단 확보

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 요청 전체 타임아웃 예산이 없음 — A-1과 함께 상한만 정한다

`LlmClientConfig.java:25`의 호출당 30초 상수 하나뿐이고, "추천 API 응답까지 최대 몇 초"라는 예산은 없다. ssoss의 `Generation.DEADLINE`(60초) → 호출 타임아웃 역산 → `invokeAll` 예산 구조는 채널 병렬 호출이 있어서 필요한 것인데, baro-ai는 요청당 LLM 호출이 1회이고 병렬이 없어 코디네이터를 둘 이유가 없다. **다만 A-1로 재시도가 실제로 걸리기 시작하면 최악 응답 시간이 `시도 횟수 × 30초 + 지연 합`으로 늘어나므로**, A-1 구현 시 "재시도 포함 전체 상한 n초"를 값과 근거로 코드 주석에 남긴다. 별도 항목으로 구현하지 않는 이유는 지금 그 상한을 강제할 병렬 실행 지점이 없기 때문이다.

### C-2. `application-prod.yml`의 `management.*` 블록이 actuator 없이 적혀 있음

B-1 (가)를 택하면 자연히 살아나고, (나)를 택하면 죽은 설정으로 남는다. (나) 선택 시 STOP §4에 따라 같은 변경에서 삭제할지 사용자에게 확인한다. 지금 별도 항목으로 두지 않는 이유는 B-1 결정에 종속되기 때문이다.

### C-3. 테스트 인프라 부재 — 2026-09-04 사용자 결정으로 이번 라운드 제외

원래 B 항목이었다. `baro-ai/build.gradle`에 `testImplementation` 선언이 없고 `src/test`에 Java 파일이 0개라 `{{테스트 명령}}`이 컴파일만 검증한다(근거: `grep -n testImplementation baro-ai/build.gradle` 0건, 루트 `build.gradle:18-25` "얇은 부모" 주석). 사용자가 "테스트 코드는 일단 없어도 된다"고 결정해 이번 라운드에서 뺐고, 그 대신 A/B 항목의 검증 방법을 전부 로컬 수동 절차로 적었다. 테스트를 들이기로 하면 `spring-boot-starter-test` 추가 5줄이 시작점이다.

### C-4. 프롬프트 조립 테스트 없음 — C-3과 같은 이유로 제외

프롬프트가 `RecipeCandidateGenerator.buildRecipeCandidateTemplate()`(56-115줄)·`ProductIngredientNormalizer.PROMPT`(82-101줄)에 있고, 무엇이 들어가고 빠지는지 확인하는 테스트가 없다. 이전 라운드에서 "LLM이 상품명을 그대로 되읊어야 재료로 인정되던" 버그가 늦게 발견된 이유이기도 하다([`recommend/refactor-log.md`](../recommend/refactor-log.md)). C-3이 풀리면 문자열 assert 2~3개로 시작한다. ssoss식 `PromptComposer` 계층은 그때도 만들지 않는다 — 호출부 2곳, 채널 분기 없음.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 포트-어댑터(`ContentGenerator` 인터페이스) 도입 안 함

ssoss는 도메인 포트 ← 인프라 어댑터로 나누고 ArchUnit으로 강제한다. baro-ai는 `spring-boot-java.md`가 "구현체가 하나뿐인 의존성에 인터페이스를 미리 만들지 않는다"를 명시하고, 실제 구현체(Gemini)도 하나다. 호출부가 `ChatClient`를 직접 주입받는 지금 구조가 이 저장소 규칙에 맞다. 제공자를 두 개 이상 동시에 써야 할 때 다시 본다.

### D-2. ssoss식 실패 상태 enum 7종 + 전용 예외 계층 이식 안 함

ssoss의 `GenerationResultStatus`·`LlmCallFailedException`은 실패 결과를 DB에 저장하고 나중에 사용자에게 보여주는 도메인이라 필요하다. baro-ai는 실패하면 즉시 폴백하고 결과를 저장하지 않으므로, 분류의 소비자는 로그·지표뿐이다. B-1의 `catch` 분기(3~4갈래 WARN 메시지) 이상은 소비자가 없는 추상화다.

### D-3. 자유 입력 텍스트 가드레일(`[참고 범위]` 절) — 이번 범위에 해당 호출부 없음

범위 안의 두 호출부는 내부 데이터(상품명·카테고리·재료명)만 프롬프트에 넣어 인젝션 표면이 낮다. 리뷰 원문을 그대로 삽입하는 `ReviewSummaryService.java:77-113`은 팀원 소유라 여기서 고치지 않는다. 이 패턴은 `chat` 도메인을 `specify`로 설계할 때 스펙에 요구사항으로 넣는 것이 맞다 — 비교 문서 "배울 점 4"에 이미 기록돼 있다.

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

- **Resilience (재시도)** — **Now**. A-1과 동일하므로 여기서 반복하지 않는다.
- **Monitoring (Spring AI 관측 + actuator)** — **Now**, B-1 (가)와 동일. 근거는 B-1에 있다. 추가로 적을 것은 장단점뿐이다 — 장점: 코드 변경 없이 토큰·지연·오류율이 지표로 남고 이후 Prometheus 연동이 가능하다. 단점: 의존성 1개 추가와 `/actuator/*` 노출 범위(`include: "*"`)를 게이트웨이 뒤에서만 열도록 재확인해야 한다(보안, 게이트웨이 경유 여부는 `harness-map.md` `{{API 문서}}` 슬롯 참고).
- **Circuit Breaker · Bulkhead** — **Later**. 지금은 LLM 호출이 요청당 1회, 컨슈머당 1회이고 트래픽 규모 추정치가 없다([미정]). 재시도(A-1)로 429 처리가 가능해진 뒤에도 폴백이 빈번하다는 지표(B-1)가 나오면 그때 본다.

## 판단이 서지 않는 항목

1. **`ReviewSummaryService`의 temperature 0.7 상속** — `application-prod.yml:55` 주석("결정적 추출이 필요한 호출은 호출부에서 0으로 덮어쓴다")과 달리 리뷰 요약은 override가 없다. 리뷰 요약이 "추출"인지 "생성"인지는 팀원의 설계 판단이라 여기서 분류하지 않는다. 확인 필요: 사용자가 팀원에게 전달할지, 아니면 yml 주석을 "레시피 후보·리뷰 요약 공통 기본값"으로 사실에 맞게 고칠지
2. **임베딩 자동설정이 `LlmClientConfig.googleGenAiClient` 빈을 재사용하는지** — 챗 자동설정(`GoogleGenAiChatAutoConfiguration`)은 `Client` 빈을 주입받는 것을 바이트코드로 확인했으나, 임베딩 자동설정 jar는 이번에 열지 못했다. 재사용한다면 A-1의 재시도·30초 타임아웃이 임베딩 호출에도 같이 걸리므로, `embedding` 도메인 감사 전에 확인해야 한다. 확인 방법: `spring-ai-autoconfigure-model-google-genai-embedding-1.1.8.jar`의 `*EmbeddingAutoConfiguration` 바이트코드에서 `Client` 파라미터 유무
3. **Spring AI 1.1.8의 관측 지표 이름·활성 조건** — B-1 (가)의 전제. 공식 문서(버전 고정)로 `gen_ai.client.token.usage`가 1.1.8에 존재하고 `ObservationRegistry` 빈만 있으면 켜지는지 확인한다(G1)
4. **A-1 재시도 값 [미정 → 잠정 3회·1초·상한 10초]** — 공식 rate-limits 문서가 모델별 고정 수치 게시를 중단해(2026-09-02 갱신) 무료 티어 RPM(분당 요청 수 한도)·`Retry-After` 유무를 공식 근거로 확정하지 못했다(`researcher` 2026-09-04). 그래서 값은 "SDK 기본 5회보다 짧고, 순간 429 한두 번은 넘길 수 있는" 최소치로 잡았다. AI Studio 콘솔에서 실제 한도를 확인한 뒤 조정한다
5. **트래픽 규모 추정치 [미정]** — C-1·Circuit Breaker의 Later 판단은 "요청당 1회 호출"이라는 구조 근거만 있고 초당 요청 수는 모른다. 사용자 확인 필요

## 승인 대기

2026-09-04 사용자 승인으로 A-1 → A-2 → B-1 순서로 구현했고, 반영 내역·검증·잠정값은 [`refactor-log.md`](refactor-log.md)에 있다. C/D는 이번 라운드에서 수정하지 않았다. 테스트 인프라(C-3)는 사용자 결정으로 제외했다.
