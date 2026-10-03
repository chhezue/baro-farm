# ssoss-server 대비 LLM 호출 규율 비교

baro-ai가 LLM(현재 Gemini)을 호출하는 세 지점 — 레시피 후보 생성·리뷰 요약·상품명 재료 정규화 — 을 외부 참고 저장소 [`ssoss-server`](https://github.com/Central-MakeUs/ssoss-server)(Central-MakeUs, LLM 기반 콘텐츠 생성 서비스)와 비교한 조사 결과다. `baro-ai-performance-refactor.md` 이니셔티브가 요구하는 "적극적 증거"에 맞춰, 두 저장소의 실제 코드·의존성 바이트코드까지 열어 확인했다. 이전에 채팅으로만 나눴던 비공식 비교(OpenAI 기준, 지금과 파일명이 다름)를 대체하는 문서다.

**조사 시점 기준:** baro-ai는 `chore/baro-ai-harness-docs` 브랜치의 현재 워킹트리(2026-09-04, [`003-gemini-migration.md`](../decisions/003-gemini-migration.md) 반영 이후 — 아직 커밋 전). ssoss-server는 `main` 브랜치를 shallow clone(커밋 `f3ad791`, 2026-08-13)해서 조사했다.

## 결론

LLM 호출 규율(프롬프트 설계·구조화 출력 강제·실패 처리·타임아웃 예산·테스트)만 놓고 보면 ssoss가 확실히 앞선다. 다만 두 저장소가 푸는 문제의 모양이 다르다 — ssoss는 콘텐츠 생성이라는 **한 개 플로우**를 포트-어댑터·전체 데드라인 코디네이터·ArchUnit까지 갖춰 깊게 판 구조인 반면, baro-ai는 검색·임베딩·추천·리뷰·이벤트 파이프라인까지 **넓게** 깐 MSA(여러 개의 작은 서비스로 나눈 구조, Microservice Architecture) 모듈이고 LLM은 그중 세 지점뿐이다. 그래서 ssoss의 구조(포트-어댑터 계층)를 그대로 옮기는 건 과설계지만, **구조화 출력 강제·실패 분류·타임아웃 예산** 세 가지는 규모와 무관하게 지금 바로 가치가 있다.

## 축별 비교

baro-ai와 ssoss를 아래 8개 축으로 비교했다. 각 항목은 `파일:줄` 인용으로 근거를 남겼다.

| 축 | baro-ai 현재 | ssoss | 평가 |
|---|---|---|---|
| **구조** | LLM 호출부가 `recommend/infrastructure/llm/RecipeCandidateGenerator.java`, `review/application/summary/ReviewSummaryService.java`, `search/infrastructure/llm/ProductIngredientNormalizer.java` 3곳에 흩어져 있고 공통 포트 없음. 각 서비스가 `ChatClient`를 직접 주입받음 | `ContentGenerator` 포트(도메인) ← `GeminiContentGenerator` 어댑터(인프라)로 분리, ArchUnit이 계층 의존 방향을 강제 | ssoss가 더 엄격한 계층화. 다만 baro-ai는 호출 지점이 3곳뿐이라 포트 도입 이득이 크지 않을 수 있음 — `spring-boot-java.md`의 "구현체 1개면 concrete 직접 주입" 원칙과 상충하지 않게 판단 필요 |
| **프롬프팅 조립** | 프롬프트가 서비스 메서드 안 텍스트 블록(`RecipeCandidateGenerator.java:56-115`, `ReviewSummaryService.java:82-107`, `ProductIngredientNormalizer.java:74-90`). 채널·정책별 분기 없음 | `GenerationPromptComposer` + `ChannelPromptPolicy`(record)로 절 단위 조립, 섹션 조립 객체 그래프 | ssoss가 조립형이지만, baro-ai 규모(호출 3곳, 채널 분기 없음)에선 지금의 텍스트 블록도 과하지 않음 |
| **구조화 출력 강제** | `.entity(...)`만 호출하고 `ChatOptions`에 `responseMimeType`/`responseSchema` 미설정(`RecipeCandidateGenerator.java:121-127`, `ReviewSummaryService.java:58-61`, `ProductIngredientNormalizer.java:33-37`). 파싱 유도는 프롬프트 문구("JSON only", "다른 텍스트는 절대 출력하지 마세요")뿐 | `GoogleGenAiChatOptions.responseMimeType(APPLICATION_JSON_VALUE)` + `.responseSchema(converter.getJsonSchema())`로 Gemini API 레벨(제약 디코딩)에서 강제 | **ssoss가 명백히 우위.** baro-ai도 동일 의존성(`spring-ai-google-genai:1.1.8`)에 이미 있는 API를 안 쓰고 있음 — 근거가 가장 탄탄한 개선 포인트 |
| **입력 가드레일(인젝션 방어)** | 자유 입력 텍스트(리뷰 본문)를 `String.formatted()`로 그대로 삽입(`ReviewSummaryService.java:77-113`), "이 텍스트에서 뭘 가져오면 안 되는지" 범위 지정 없음. 레시피·정규화 쪽은 재료명·상품명(내부 데이터)이라 인젝션 표면이 낮음 | `StyleSourceSectionComposer`의 `[참고 범위]` 절이 "참고 글에서 가져오는 건 말투뿐, 소재는 옮기지 않는다"를 프롬프트에 명시하고 테스트로 검증 | ssoss가 우위. `review`는 팀원 소유(범위 밖)라 시급하지 않지만, **`chat` 도메인을 구현할 때 반드시 참고할 패턴** |
| **비동기·타임아웃 예산** | HTTP 클라이언트 레벨 30초 캡 하나뿐(`LlmClientConfig.java:22-25,31`). 전체 요청에 대한 데드라인 관리 없음. `AsyncConfig`엔 프로필 업데이트용 executor만 있고 LLM 호출용 없음 — 추천 API는 동기 호출 | `GenerationCoordinator`가 60초 전체 예산(`Generation.DEADLINE`)을 `ExecutorService.invokeAll(tasks, deadlineBudget)`로 채널별 병렬 호출에 강제, 초과분은 `DISCARDED_LATE` 처리 | ssoss가 우위. baro-ai는 현재 병렬 호출이 없어 코디네이터 자체는 불필요하지만, **전체 데드라인 개념 자체가 없다는 게 문제** |
| **실패·에러 분류** | `RecipeCandidateGenerator.java:42-45`, `ReviewSummaryService.java:71-74`, `ProductIngredientNormalizer.java:51-54` 모두 `catch (Exception e)` → 로그 + 빈 값/폴백. rate limit·timeout·서버에러 구분 없음 | `GenerationResultStatus` enum(SUCCEEDED/RATE_LIMITED/SERVER_ERROR/TIMEOUT/EMPTY_OUTPUT 등) + `GeminiCallOutcomeClassifier`가 API 예외 코드로 분류 | ssoss가 명백히 우위 |
| **관측(토큰·지연시간)** | `log.info`/`log.warn`만, 토큰 수·응답시간 미기록 | 호출마다 응답시간·입력/출력 토큰을 `GenerationResult`에 저장 | ssoss가 우위 |
| **비용 가드레일** | 중복 호출 차단 없음. `application-prod.yml:43-48`에 `spring.ai.retry.max-attempts: 3`을 설정해 뒀지만, 실제로는 **적용되지 않는다**(아래 "신규 리스크" 참고) — 429·5xx가 나면 재시도 0회로 즉시 실패한다 | DB 락으로 동일 요청 중복 생성 차단. 재시도 정책도 override하지 않아 baro-ai와 유사하게 재시도가 거의 걸리지 않는 상태를 공유한다(다만 전체 데드라인이 있어 피해 반경은 작음) | 중복 호출 차단은 ssoss가 우위. 재시도는 양쪽 다 사실상 없는 상태로, baro-ai 쪽은 설정이 있는데도 안 걸린다는 점에서 더 나쁘다(있는 줄 알았는데 없는 상태) |
| **테스트** | `src/test` 전체 0개 파일(LLM 포함 전무) | 프롬프트 조립 단위테스트(367줄), MockWebServer 가짜 LLM, `@Tag("live")` 실호출 테스트, ArchUnit 4종 | ssoss가 압도적 우위 |

## ssoss에서 배울 점 (우선순위순)

1. **구조화 출력을 API 레벨로 강제한다.** ssoss의 `GoogleGenAiChatOptions.responseMimeType(APPLICATION_JSON_VALUE)` + `.responseSchema(...)` 패턴을 baro-ai의 세 `.entity()` 호출에도 적용한다. 지금은 프롬프트 문구에만 의존해, 파싱 실패가 나면 `catch (Exception e)`로 조용히 빈 값이 반환된다. 같은 Spring AI 버전(1.1.8)에 이미 있는 기능이라 도입 비용이 낮다.
2. **실패 분류 enum + 예외 타입을 둔다.** `GenerationResultStatus` + 전용 예외 패턴을 도입하면, 지금 `catch (Exception e)` 한 줄로 뭉개지는 rate limit·timeout·파싱실패를 구분해 로그·모니터링에 남길 수 있다.
3. **전체 요청 단위 타임아웃 예산을 둔다.** ssoss의 `Generation.DEADLINE` 패턴처럼, baro-ai도 호출당 30초 캡(`LlmClientConfig.java:25`)만이 아니라 요청 전체(추천 API 응답까지)의 상한을 명시적으로 관리해야 한다 — 특히 동기 HTTP 엔드포인트인 `RecommendationController`에 가치가 크다.
4. **자유 입력 텍스트를 프롬프트에 넣을 때 "가져오지 말 것"을 명시한다.** ssoss의 `[참고 범위]` 절 패턴. baro-ai는 지금 자유 입력을 프롬프트에 넣는 지점이 없어 시급하지 않지만, `chat/application/ChatService.java`(현재 빈 스텁)를 구현할 때 반드시 참고해야 한다.
5. **프롬프트 조립을 순수 함수로 분리해 단위테스트를 붙인다.** `GenerationPromptComposerTest`처럼, 프롬프트에 어떤 문구가 포함/제외되는지를 문자열 assert로 검증한다. baro-ai는 프롬프트가 서비스 메서드에 인라인돼 있어 테스트가 전무한데, `buildRecipeCandidateTemplate`/`buildPrompt`를 별도 메서드로 분리해 두면 테스트 추가 비용이 낮아진다.

## baro-ai가 이미 더 나은 점

- **LLM 자기보고 값의 서버 재검증(그라운딩).** `RecipeCandidateGenerator.enhanceWithServerValidation`(161-197줄)이 LLM이 "썼다/부족하다"고 적어 보낸 재료 목록을 믿지 않고 서버가 `IngredientProcessingUtil.normalizeForCompare`로 직접 재계산한다. ssoss의 콘텐츠 생성 결과는 이런 재검증 없이 LLM 출력을 상대적으로 더 신뢰한다(도메인이 달라 직접 비교는 제한적).
- **규칙 기반 폴백.** `ProductIngredientNormalizer.applyBasicNormalization`(58-69줄)이 LLM 실패 시 정규식으로 폴백한다. ssoss의 콘텐츠 생성은 LLM 없이 대체 불가능한 도메인이라 폴백 개념 자체가 없다.
- **호출 횟수를 구조적으로 줄인 설계.** 상품명 재료 정규화를 추천 요청 시점이 아니라 색인 시점(Kafka 이벤트)에 1회만 수행하고 결과를 Elasticsearch(ES)에 저장한다(`ProductIngredientNormalizer.java:11-13` 주석, [`002-ingredient-name-at-index-time.md`](../decisions/002-ingredient-name-at-index-time.md)) — 추천 요청마다 재호출하지 않는다.
- **감정 분류를 LLM 없이 규칙 기반으로.** `review/infrastructure/sentiment/KeywordSentimentClassifier.java`가 키워드·별점 매칭으로 판정해 LLM 호출 자체가 없다(`review`는 범위 밖이라 참고용).

## 비교 기준으로 삼을 baro-ai 폴더·파일

LLM을 실제로 호출하는 지점은 `chat`을 포함해 정확히 4곳(`chat`은 미구현)이며, 아래를 비교·개선 기준으로 삼는 게 타당하다.

| 파일 | 이유 |
|---|---|
| [`recommend/infrastructure/llm/RecipeCandidateGenerator.java`](../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGenerator.java) | 유일하게 동기 HTTP 요청 경로(`RecommendationController` → `RecipeRecommendService`)에서 호출되는 LLM. 구조화 출력·실패분류·테스트 부재가 가장 체감되는 지점 — **1순위** |
| [`search/infrastructure/llm/ProductIngredientNormalizer.java`](../../src/main/java/com/barofarm/ai/search/infrastructure/llm/ProductIngredientNormalizer.java) | 유일하게 `.param()`으로 Spring AI 템플릿 렌더러를 실제로 타는 호출. temperature를 호출부에서 override하는 유일한 사례이기도 해 "호출 단위 옵션 분리" 비교축의 기준 |
| [`review/application/summary/ReviewSummaryService.java`](../../src/main/java/com/barofarm/ai/review/application/summary/ReviewSummaryService.java) | `review`는 `baro-ai-performance-refactor.md` 범위 밖(팀원 소유)이지만, 자유 입력 텍스트를 프롬프트에 삽입하는 유일한 사례라 "프롬프팅 인젝션 방어" 비교축의 기준으로는 유효 — **코드 변경 제안은 팀원 확인 필요** |
| [`chat/application/ChatService.java`](../../src/main/java/com/barofarm/ai/chat/application/ChatService.java) | 완전 빈 스텁 — ssoss와 1:1 비교 대상은 아니지만, 인젝션 방어·구조화 출력 패턴을 **설계 시점에 미리 참고**할 유일한 신규 도메인 |
| [`config/LlmClientConfig.java`](../../src/main/java/com/barofarm/ai/config/LlmClientConfig.java) | 4개 호출 지점이 공유하는 유일한 인프라 설정. 타임아웃·재시도 정책을 바꾸면 전부에 영향 |

`embedding`·`event`·`log` 도메인은 LLM을 직접 호출하지 않아(임베딩은 별도 API 경로) 이번 비교 기준에서 제외한다.

## 신규 리스크 (Gemini 전환 이후 새로 확인됨)

1. **`spring.ai.retry` 설정이 Gemini 호출에는 실제로 적용되지 않는다.** `application-prod.yml:43-48`에 `max-attempts: 3` 등을 명시해 뒀지만, 이 설정은 죽은 설정이다.
   > **2026-09-04 정정**: 아래 "재시도 0회" 결론은 틀렸다. Google GenAI SDK(`google-genai-1.37.0`)의 `ApiClient`는 재시도 옵션을 안 줘도 기본 `RetryInterceptor`(총 5회, 지연 1→60초, 429·5xx 대상)를 항상 등록한다 — Spring AI 레이어만 보고 SDK 레이어를 놓친 조사였다. 실제 문제는 "재시도가 없다"가 아니라 "통제되지 않는 재시도가 최대 5회 걸려 최악 응답 시간이 정해져 있지 않다"이다. 정정 근거와 조치는 [`cross-cutting/001-llm-call-infra.md`](cross-cutting/001-llm-call-infra.md) A-1. 아래 원문은 이력으로 남긴다.
   - `GoogleGenAiChatModel`(바이트코드 확인)은 호출을 `RetryTemplate.execute(...)`로 감싸지만, 이 템플릿의 재시도 대상은 `TransientAiException`·`ResourceAccessException`·`WebClientRequestException`뿐이다(`SpringAiRetryAutoConfiguration`의 `retryOn` 목록).
   - 반면 Google GenAI SDK(`com.google.genai`)는 429를 `ClientException`, 5xx를 `ServerException`으로 던지는데, 둘 다 `ApiException`→`BaseException`→`RuntimeException` 계열이라 위 세 타입 중 어디에도 속하지 않는다. `GoogleGenAiChatModel`에 이를 변환하는 코드도 없다(catch 블록은 관측용 `Exception` 하나뿐).
   - 즉 429·5xx가 나면 **재시도 0회로 즉시 실패**한다. 원래 우려했던 "재시도가 너무 길어진다"는 리스크는 애초에 발생하지 않지만, 대신 "설정해 뒀다고 믿고 있는 재시도가 실제로는 전혀 동작하지 않는다"는 정반대 문제가 있다 — 무료 티어의 순간적인 429 하나에도 폴백(정규식)이나 빈 응답으로 곧장 떨어진다.
   - 실제로 재시도가 필요하면 `LlmClientConfig`가 직접 만드는 `com.google.genai.Client`의 `HttpOptions`에 `HttpRetryOptions`(시도 횟수·초기 지연·최대 지연·배수·jitter·대상 상태코드)를 지정해야 한다. 임베딩 쪽 자동설정이 이 `Client` 빈을 재사용하는지는 미확인.
2. **temperature가 리뷰 요약에도 레시피용 전역값(0.7)이 그대로 상속된다.** `application-prod.yml:47`의 `spring.ai.google.genai.chat.options.temperature: 0.7`은 주석에 "레시피 후보 생성용 기본값. 결정적 추출이 필요한 호출은 호출부에서 0으로 덮어쓴다"고 적혀 있는데, 실제로 override하는 곳은 `ProductIngredientNormalizer` 한 곳뿐이다. `ReviewSummaryService`는 override 없이 0.7을 그대로 상속받는데, 리뷰 요약은 창작보다 추출·정리에 가까운 작업이라 0.7이 적절한지 근거가 불명확하다.
3. **구조화 출력이 소프트 강제뿐이라 파싱 실패가 조용히 빈 값으로 흡수된다.** 세 호출부 모두 `catch (Exception e)` 후 로그만 남기고 빈 배열/폴백을 반환한다. 토큰·응답시간 기록도 없어(위 "관측" 축), 모니터링 없이는 "LLM이 계속 JSON 파싱에 실패하고 있다"는 걸 알아채기 어렵다 — 위 "배울 점" 1·2번과 직결된다.

## 다음 단계

이 문서는 근거 정리용 조사 결과이며, 여기서 바로 구현으로 이어지지 않는다. 실제로 반영하려면 `safe-refactor` 스킬의 도메인별 감사 절차(Audit → 승인 → Implement → Verify)를 거쳐야 한다 — `recommend`·`search` 도메인 감사 시 이 문서를 근거 자료로 인용할 수 있다. `review` 관련 항목은 팀원 확인 없이 구현하지 않는다.
