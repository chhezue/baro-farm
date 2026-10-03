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
