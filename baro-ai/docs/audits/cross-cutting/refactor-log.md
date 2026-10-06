# Cross-cutting Refactor Log

도메인에 속하지 않는 공유 인프라(`com.barofarm.ai.config`와 그 인프라를 쓰는 호출부)에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다. 감사 결과 원본은 [`001-llm-call-infra.md`](001-llm-call-infra.md)에 있다.

## 2026-09-04 — LLM 호출 인프라: 재시도 통제·응답 스키마 강제·관측 지표

**반영 항목**: [`001-llm-call-infra.md`](001-llm-call-infra.md) A-1(재시도)·A-2(구조화 출력)·B-1(관측). 테스트 인프라(C-3)·프롬프트 테스트(C-4)는 사용자 결정으로 제외.

### 왜 문제였나 (쉬운 말로)

**재시도.** 설정 파일에 "실패하면 3번까지 다시 시도"라고 적어 뒀는데, 이 설정은 Gemini 호출에 전혀 영향을 주지 않는 죽은 설정이었습니다. 처음 조사에서는 "그래서 재시도가 아예 안 된다"고 진단했는데, 구현 직전에 구글 라이브러리 내부를 다시 열어 보니 반대였습니다. 구글 라이브러리는 **아무 설정을 안 해도 스스로 5번까지 재시도**하고, 시도 사이 대기 시간을 최대 60초까지 늘립니다. 즉 재시도가 없는 게 아니라, 아무도 모르는 재시도가 걸려 있어 Gemini가 느려지면 한 번의 요청이 몇 분까지 늘어질 수 있는 상태였습니다. 이 정정은 [`001-llm-call-infra.md`](001-llm-call-infra.md) A-1과 [`ssoss-llm-comparison.md`](../ssoss-llm-comparison.md)에도 남겼습니다.

**응답 형식.** 서버는 Gemini 답을 정해진 형식(JSON)으로 읽는데, 지금까지는 "다른 텍스트는 절대 출력하지 마세요"라고 부탁만 했습니다. Gemini에는 형식을 강제하는 기능이 있고 이미 쓰는 라이브러리에 들어 있었는데 안 쓰고 있었습니다. 부탁만으로는 모델이 설명 문장을 앞에 붙이는 순간 파싱이 깨지고, 추천이 조용히 빈 목록이 됐습니다.

**기록.** 호출마다 토큰을 얼마나 썼는지, 몇 초 걸렸는지, 왜 실패했는지가 어디에도 남지 않아 "요즘 Gemini가 자주 실패한다"는 걸 로그를 뒤지지 않고는 알 수 없었습니다.

### 무엇을 바꿨나

- 재시도를 구글 라이브러리에 **명시적으로** 걸었습니다. 총 3번 시도, 대기 1초에서 시작해 최대 10초. Gemini가 완전히 멈춘 최악의 경우에도 한 요청이 약 93초 안에 끝납니다(3 × 30초 + 대기). 죽은 설정은 삭제했습니다.
- 두 호출부(레시피 후보 생성·재료명 추출)에 JSON 스키마를 API 옵션으로 넘겨 형식을 강제했습니다. 프롬프트에 손으로 적어 둔 출력 예시 블록과, 라이브러리가 자동으로 덧붙이던 형식 지시문을 둘 다 걷어냈습니다(둘 다 스키마와 중복).
- `spring-boot-starter-actuator`를 추가해 Spring AI가 이미 갖춘 관측 훅을 켰습니다. 코드 변경 없이 `/actuator/metrics`에 토큰 사용량(`gen_ai.client.token.usage`)과 호출 시간·오류(`gen_ai.client.operation.duration`)가 노출됩니다. 노출 범위는 `health,metrics`로 좁혔습니다(기존 설정은 `*`였는데 actuator가 없어 죽어 있었음).

> 아래 표는 개발자 참조용이라 코드 식별자를 그대로 쓴다. 쉬운 설명은 바로 위 절에 있다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| Gemini 재시도 | SDK 기본값(총 5회, 지연 1→60초) — 코드·설정 어디에도 안 적힘 | `HttpRetryOptions`(총 3회, 지연 1→10초)를 `LlmClientConfig`에서 명시 |
| 최악 응답 시간(호출 1회, Gemini 무응답) | 5 × 30초 + 최대 60초 지연 누적 | 3 × 30초 + 3초 |
| `spring.ai.retry.*` | yml에 있으나 무효 | 삭제 |
| 응답 형식 강제 | 프롬프트 문구 + `.entity()` 자동 지시문 | `GoogleGenAiChatOptions.responseMimeType/responseSchema` (API 레벨) |
| 프롬프트 형식 블록 | 레시피 프롬프트 18줄 JSON 예시 | 삭제 (필드 정의 절은 유지) |
| 토큰·지연·오류 지표 | 없음 | actuator 지표 2종 |
| API 응답 계약 | — | **변경 없음** |

### 변경 파일

- `config/LlmClientConfig.java` — `HttpRetryOptions` 추가, 상수 3개와 근거 주석
- `src/main/resources/application-prod.yml` — `spring.ai.retry` 블록 삭제, actuator 노출 범위 `health,metrics`
- `build.gradle` — `spring-boot-starter-actuator`
- `recommend/infrastructure/llm/RecipeCandidateGenerator.java` — `BeanOutputConverter` 상수, `.options(GoogleGenAiChatOptions...)`, `.content()` + `convert()`, 프롬프트 JSON 블록 삭제
- `search/infrastructure/llm/ProductIngredientNormalizer.java` — 같은 패턴 (temperature 0 유지)
- `docs/audits/ssoss-llm-comparison.md` — "재시도 0회" 결론 정정 문단

### 검증

- `./gradlew :baro-ai:compileJava :baro-ai:spotlessApply :baro-ai:checkstyleMain` 통과
- 자동화 테스트 없음(사용자 결정). 아래 수동 절차는 Gemini API 키와 로컬 기동이 필요해 **이 세션에서는 실행하지 못했다** — 사용자가 로컬에서 확인해야 한다:
  1. 재시도: `LlmClientConfig`의 재시도 대상에 404를 잠시 추가하고 `SPRING_AI_GEMINI_MODEL=no-such-model`로 기동 → 추천 API 1회 호출 → 로그에 같은 요청이 3번 찍히는지 확인 후 되돌린다
  2. 스키마: `org.springframework.ai: DEBUG` 로그의 요청 본문에 `responseMimeType`·`responseSchema`가 있고 응답이 JSON만인지 확인
  3. 지표: `curl localhost:8092/actuator/metrics/gen_ai.client.token.usage`
- 스키마 문자열은 Spring AI가 Jackson으로 `com.google.genai.types.Schema`에 담아 보내므로 `$schema`·`additionalProperties` 같은 미지원 키는 자동으로 떨어진다(바이트코드 확인). 다만 `type: "object"` 같은 값이 SDK enum으로 정상 역직렬화되는지는 실호출로만 확인 가능 — 위 2번 절차

### 남긴 것 · 알려진 제약

- **재시도 값(3회·1초·10초)은 잠정치** — 무료 티어 한도의 공식 수치를 못 얻어(rate-limits 문서가 모델별 표를 폐지) SDK 기본보다 짧은 최소치로 잡았다. AI Studio 콘솔에서 실제 한도 확인 후 조정. 커밋 전 사용자 확인 대상
- **레시피 스키마에 서버 계산 필드가 포함된다** — `RecipeCandidates`에서 만든 스키마에는 `usedOwnedIngredients`·`missingIngredientsCore`(서버가 덮어쓰는 값)도 들어간다. 프롬프트에 "빈 배열로 두라"고 적어 두었지만, LLM 응답 전용 DTO를 분리하면 스키마에서 빠진다 — 후속 후보
- 임베딩 호출이 같은 `Client` 빈을 쓰는지(재시도·타임아웃 적용 범위)는 미확인 — [`001-llm-call-infra.md`](001-llm-call-infra.md) 판단 항목 2번, `embedding` 도메인 라운드에서 확인

## 2026-09-05 — 의존성·설정·죽은 코드 정리 (감사 002 A-1·A-2·B-1~B-7, B-8 제외)

**반영 항목**: [`002-dependencies-config-and-dead-code.md`](002-dependencies-config-and-dead-code.md)의 A 2건·B 7건. **B-8(ES 클라이언트 버전 고정 해제)은 반영하지 않았다** — 아래 "남긴 것" 참고. A-2(cart·order 역직렬화 방어)는 [`taste-vector/refactor-log.md`](../taste-vector/refactor-log.md)에서 이미 반영했고, 이번에는 B-6의 공통 메서드로 옮기기만 했다. 착수 시점 결정은 감사 문서의 "구현 결과 (2026-09-05)" 절에 있다.

### 왜 문제였나 (쉬운 말로)

**운영 지표가 한 줄도 수집되고 있지 않았다.** 배포 설정은 30초마다 우리 서버의 지표 주소를 긁어 가도록 돼 있는데, 정작 그 주소를 열어 두지 않아 계속 "그런 주소 없음"만 받고 있었습니다. 앞선 라운드에서 붙인 AI 호출 비용·지연 지표도 전부 서버 안에만 머물렀습니다. 이 이니셔티브의 남은 판단들(재시도 횟수, 앞단 관문의 5초 제한)이 죄다 "지표를 보고 정한다"를 전제로 하는데 그 지표가 도착하지 않고 있었던 셈입니다. 주소를 열었습니다.

**로그가 요청마다 네 줄씩 붙고, 응답 내용 일부가 그대로 남았다.** 운영 설정이 프레임워크 로그 세 종류를 상세 모드로 켜 두고 있었습니다. 요청 1건에 최소 4~5줄이 붙고, 그중 한 줄은 **응답 본문 앞부분을 그대로 기록**합니다. 그 본문에는 개인화 추천 결과와 장바구니 재료가 들어갑니다. 프레임워크 로그를 끄고, AI 요청 내용을 봐야 할 때만 **재배포 없이 켰다 끄는 손잡이**(actuator의 로그 레벨 변경)를 열어 뒀습니다.

**로컬에서 그냥 실행하면 뜨지 않는 서버였다.** 기본 프로필이 `local`로 지정돼 있는데 그 이름의 설정 파일이 없어서, 아무 설정 없이 실행하면 기동이 실패합니다. 문서가 "prod로 띄우세요"라고 안내하던 이유입니다. 게다가 설정 서버(Config Server) 클라이언트가 딸려 있어, 배포 환경에서는 그걸 끄는 환경변수 두 개를 따로 넣어 주고 있었고 로컬 안내에는 그게 없었습니다. 환경마다 달라지는 값은 이미 전부 환경변수로 주고 있어 프로필로 갈릴 것이 없었으므로, **설정 파일을 하나로 합치고** 쓰지도 않는 설정 서버·서비스 이름 조회(Eureka) 의존성을 걷어냈습니다. 이제 그냥 실행해도 뜹니다.

**빌드 파일이 실제와 다른 말을 하고 있었다.** 데이터베이스 관련 의존성(JPA·MySQL 드라이버)이 선언돼 있는데 이 서비스에는 테이블도 엔티티도 없습니다. 오히려 그것 때문에 "이 자동 설정을 끄라"는 예외 설정이 따로 필요했습니다. 검증 라이브러리도, 실제로는 켜지지 않아 쓰이지 않는 HTTP 클라이언트(OkHttp)도, 대상이 아예 없는 버전 강제 6줄도 남아 있었습니다. 읽는 사람이 매번 "이 서비스가 MySQL도 쓰나?"를 확인해야 했습니다. 전부 지웠습니다.

**빈 껍데기 파일 4개가 API 목록에 자리를 만들고 있었다.** 챗봇 관련 클래스 4개가 내용 없이 존재해서, 엔드포인트가 하나도 없는데도 Swagger 문서에 경로 접두사가 잡혔습니다. 목적이 아직 정해지지 않은 기능이고 그 기록은 스펙 문서가 이미 하고 있어, 코드는 지우고 문서만 남겼습니다.

**같은 설정 코드가 네 벌 있었고, 그중 하나만 방어가 돼 있었다.** 이벤트를 받는 설정이 종류마다 12줄씩 거의 같게 복사돼 있었습니다. 그래서 "깨진 메시지 한 건에 멈추지 않게" 하는 방어를 넣을 때 실제로는 상품 쪽에만 들어갔고, 나머지는 앞선 라운드에서 따로 손대야 했습니다. 공통 부분을 한 메서드로 모아 다음에 같은 방어를 넣을 때 한 곳만 고치면 되게 했습니다. 덧붙여 "믿을 수 있는 패키지" 설정이 네 곳에 있었는데, 바로 위에서 타입 헤더를 무시하도록 해 둔 탓에 **읽히지도 않는 설정**이었고 주석은 사실과 반대로 적혀 있어 함께 정리했습니다.

**같은 API가 성공할 때와 실패할 때 응답 모양이 달랐다.** 추천 API는 성공하면 값을 그대로 돌려주는데, 실패하면 공통 예외 처리기가 `{상태, 데이터, 메시지}` 형태로 감싸 보냅니다. 호출하는 쪽은 응답 모양만 봐서는 성공인지 실패인지 알 수 없었습니다. 검색 API가 이미 쓰고 있는 형태로 통일했습니다.

### 무엇을 바꿨나

- **운영 지표가 실제로 수집되게 했다** — 지표 주소를 열고, 로그 레벨을 런타임에 바꾸는 손잡이도 함께 열었다.
- **로그를 줄이고 개인 정보가 새는 경로를 닫았다** — 프레임워크 상세 로그 3종을 끄고, AI 요청 내용은 필요할 때만 켠다.
- **설정 파일을 하나로 합치고 안 쓰는 인프라 의존성을 걷어냈다** — 프로필 없이 그냥 실행하면 뜬다.
- **빌드 파일을 실제 스택과 맞췄다** — DB·검증·OkHttp·죽은 버전 강제 제거.
- **이벤트 수신 설정 네 벌을 공통 메서드로 모으고, 빈 껍데기 4개를 지웠다.**
- **추천 API 응답을 검색 API와 같은 형태로 통일했다.**

아래 표는 개발자 참조용이다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| A-1 지표 노출 | `include: health,metrics` — `/actuator/prometheus`가 404, ServiceMonitor가 30초마다 헛수고 | `health,metrics,prometheus,loggers` |
| A-2·B-6 컨슈머 팩토리 | 12줄짜리 팩토리 4벌, 역직렬화 방어는 product에만 | 공통 메서드 1개(product·cart·order). review 팩토리는 팀원 소유라 그대로 |
| B-6 신뢰 패키지 | `addTrustedPackages(...)` 4곳 + 사실과 반대인 주석 2줄 | 삭제(타입 헤더를 무시하므로 읽히지 않던 설정) |
| B-6 죽은 yml | `spring.kafka.consumer`의 역직렬화기·offset 3줄 | 삭제(`group-id`·`bootstrap-servers`만 유지) |
| 판단 3 그룹 이름 | 어노테이션 `search-service`가 팩토리 `ai-service`를 덮음 | 어노테이션 값 삭제 → `ai-service` 계열로 통일 |
| B-1 DB 의존성 | `starter-data-jpa`·`mysql-connector-j` + H2용 테스트 yml(키가 잘못 중첩돼 어차피 무효) | 삭제. 자동설정 제외 2줄은 유지(baro-common이 JPA를 끌어옴) + 이유 주석 |
| B-2 인프라 클라이언트 | `starter-config`·`eureka-client`·`@EnableDiscoveryClient`·`eureka.client.enabled: false` | 삭제 |
| B-3 빈 덮어쓰기 | `allow-bean-definition-overriding: true` | 삭제(이름 충돌을 기동 시점에 잡는다) |
| B-4 로그 | `org.springframework.web`·`DispatcherServlet`·`org.elasticsearch`·`org.springframework.ai` DEBUG | 전부 root(INFO). AI 로그는 `loggers`로 임시 상향 |
| B-5 응답 | 추천 3개 API가 날 값, 에러만 envelope | `ResponseDto<…>`로 통일 |
| B-7 죽은 의존성 | `feign-okhttp`(활성 속성 없어 미사용)·`starter-validation`(사용 0건)·swagger `force` 6줄(대상 없음)·`spring-dotenv`(`.env` 없음) | 삭제 |
| 판단 1 chat | 빈 컨트롤러·서비스·DTO 2개 | 패키지 삭제 |
| 판단 6 프로필 | `application.yml`(프로필 지정만) + `application-prod.yml` | `application.yml` 하나 |

### 변경 파일

**코드·설정 (7개 · 삭제 6)**

- `src/main/resources/application.yml` — `application-prod.yml` 내용을 흡수하고 위 표의 yml 변경 전부
- 삭제: `src/main/resources/application-prod.yml`, `src/test/resources/application-test.yml`, `chat` 패키지 4개 파일
- `build.gradle` — JPA·MySQL·validation·okhttp·dotenv·eureka·config 스타터, swagger force 6줄 삭제
- `AiApplication.java` — `@EnableDiscoveryClient` 삭제
- `config/KafkaConsumerConfig.java` — 공통 팩토리 메서드(171줄 → 149줄)
- `search/infrastructure/event/ProductEventConsumer.java` — `groupId` 어노테이션 값·도달 불가 분기 삭제
- `recommend/presentation/RecommendationController.java` — 응답 3개를 `ResponseDto`로

**문서 (11개)**

- `audits/cross-cutting/002-*.md` — "구현 결과" 절
- `specs/taste-vector/*`·`specs/vector-similarity/*`·`specs/cart-recipe/*`·`specs/keyword-search/*` — envelope 문구, 설정 파일 경로
- `specs/README.md`·`specs/chat/chatbot.md` — chat 코드 없음
- `ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`·`IMPLEMENTATION.md` — 설정 파일 경로·로컬 실행 절차
- `README.md`·`FEATURES.md`·`AGENTS.md` — chat 스텁 삭제 반영, 설정 파일·테스트 디렉터리 서술 (감사가 지정한 갱신 대상 목록에는 없었고 `spring-reviewer`가 찾았다)

### 검증

- `./gradlew :baro-ai:build` 통과 (컴파일 · spotless · checkstyle)
- `./gradlew :baro-ai:test`는 `NO-SOURCE` — 자동화 테스트가 없다
- `spring-reviewer` — Critical·High 0건. Medium·Low 4건은 전부 문서-코드 불일치(삭제한 `chat` 클래스를 "빈 클래스로 존재한다"고 서술하던 문서 3곳, `application-prod.yml`을 가리키던 Javadoc 1곳)였고 전부 반영했다. Feign이 Eureka 없이 동작하는 경로, 팩토리 4개의 그룹·offset·poll 값 보존, YAML 병합 누락 여부는 소스·바이트코드로 확인받았다
- `doc-reviewer` — 반드시 고칠 것 1건(`specs/README.md`가 삭제된 `application-*.yml` 글롭을 가리킴) 반영
- **기동 확인은 하나도 하지 못했다.** 이 라운드는 특히 기동으로만 드러나는 변경이 많다 — 프로필 제거, 의존성 6종 삭제, 빈 덮어쓰기 허용 해제, actuator 노출 변경이 전부 그렇다. 컴파일이 통과한 것과 서버가 뜨는 것은 다른 문제다

### 기동 확인이 필요하다 (사람이 직접 실행)

1. **프로필 없이 기동되는지** — `SPRING_PROFILES_ACTIVE` 없이 `./gradlew :baro-ai:bootRun`. Before: `local` 프로필로 떠서 실패. After: 기동
2. **빈 이름 충돌이 없는지** — B-3으로 덮어쓰기 허용을 껐으므로, 충돌이 있으면 `BeanDefinitionOverrideException`으로 기동이 실패한다(그게 목적)
3. **지표가 나오는지** — `curl localhost:8092/actuator/prometheus`가 200이고 `gen_ai_client_token_usage_total`이 보이는지
4. **로그 손잡이** — `curl -X POST localhost:8092/actuator/loggers/org.springframework.ai -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'` 후 추천 API 1회 → 요청 본문에 `responseSchema`가 있는지(001 검증 절차 2번). 확인 후 `{"configuredLevel":null}`로 되돌린다
5. **컨슈머 4개가 그대로 뜨는지** — `/actuator/beans`에 `*ListenerContainerFactory` 4개. 그룹 이름이 바뀌었으므로 상품 이벤트를 1건 발행해 색인되는지 확인
6. **추천 API 응답 모양** — `{"status":200,"data":…,"message":null}`인지

### 남긴 것 · 알려진 제약

- **B-8(ES 버전 고정 해제)은 보류했다** — 고정을 풀면 클라이언트가 8.18.x로 올라가면서 `FetchSourceFilter.of`·`RangeQuery.Builder.field`·`Script.Builder.inline` 3곳이 컴파일에 실패한다. 마이그레이션은 작지만, 운영 ES 9.1.2와의 호환을 인프라 담당자에게 확인하기 전까지 버전을 바꾸지 않기로 이미 정해 둔 항목이라 되돌렸다([`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) 승인 결과). 고정 이유는 `build.gradle` 주석에 남겼다
- **B-5는 계약이 바뀌는 변경이다** — 추천 3개 API의 성공 응답이 `[...]`에서 `{"status":200,"data":[...],"message":null}`로 바뀐다. 호출하는 클라이언트·모듈은 저장소 전체에서 0건이다
- **컨슈머 그룹 이름이 바뀌었다** — 상품 이벤트 그룹이 `search-service`에서 `ai-service`로 바뀌므로 읽던 위치가 초기화된다. `latest`라 과거 이벤트는 건너뛴다. dev 모드라 문제없다는 사용자 확인을 받았다
- **review 소유 항목은 손대지 않았다** — `reviewEventConsumerFactory`(공통 메서드에서 제외), `KafkaProducerConfig`의 중복 `@EnableKafka`, `spring.kafka.producer` yml, `ReviewBatchController`의 응답 형식. 전부 팀원 확인 대상
- **C 항목은 손대지 않았다** — 설정 클래스 패키지 이원화(C-1) 등
