# Recommend Architecture Audit 001 — LLM 프롬프트 튜닝 (2026-09-04)

`recommend` 도메인에서 유일하게 LLM(Large Language Model, 거대 언어 모델)을 호출하는 `RecipeCandidateGenerator`(보유 재료 → 레시피 후보 3개 생성)의 프롬프트 내용·모델 옵션·few-shot 도입 여부를 감사한 결과다. 반드시 고칠 A 항목은 **0건**이고, 유지보수성 B 항목은 프롬프트 자체가 아니라 인접 도메인 클래스의 주석 정합성 문제 **1건**이다. 이번 라운드의 핵심 질문이었던 "완전한 input→output few-shot 예시 도입"은 코드로 확인한 사실(이미 명시적 규칙으로 강제되는 다양성 요구, 서버 재검증이 대체하는 항목, 과거 실제 사고가 few-shot과 무관한 다른 유형이었다는 점, 동기 요청 경로라는 점)을 근거로 **D(도입하지 않는 것이 더 좋음)**로 판단했다. 이 문서는 감사 시점의 근거·분류를 남기는 스냅샷이며, **A가 없고 B가 1건(경미한 주석 정리)뿐이므로 사용자가 원하면 B-1만 짧게 반영하고 이번 라운드를 종료할 것을 제안한다.**

## 범위

- 대상 코드: `com.barofarm.ai.recommend.infrastructure.llm.RecipeCandidateGenerator`(프롬프트 문자열·호출 시 옵션), 그리고 그 출력 계약을 담는 `recommend.domain.CandidateRecipePlan`(감사 중 발견된 주석 정합성 문제만, 아래 B-1)
- 대상 설정: `application-prod.yml`의 `spring.ai.google.genai.chat.options.*`(`model`·`temperature` — `RecipeCandidateGenerator.java:113-125`에서 `responseMimeType`·`responseSchema`만 오버라이드하고 `model`·`temperature`는 override가 **없어** 전역 기본값(`gemini-2.5-flash-lite`, `0.7`)을 그대로 쓴다. `application-prod.yml:48` 주석 "레시피 후보 생성용 기본값"이 이 호출부를 가리키는 게 맞다는 뜻이고, 실제로도 정확히 일치한다 — STOP §1 위반 없음)
- 감사자: `researcher`가 조사한 ssoss-server few-shot 사용 패턴을 컨텍스트로 받아 "참고" 절에 그대로 옮기고, 나머지(핵심 질문 포함)는 코드를 직접 읽어 확인했다(신선한 서브에이전트 스캔)
- 기준: `audit-checklist.md` 16번(프롬프트 토큰·few-shot·모델 선택·응답 파싱 안정성 재확인·재시도 재확인·배치화 가능성), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다
- **범위 밖 (중복 조사 안 함)**: 재시도·구조화 출력 강제·관측 → [`cross-cutting/001-llm-call-infra.md`](../cross-cutting/001-llm-call-infra.md) A-1/A-2/B-1(이미 반영, 아래 두 불릿에서 코드로만 재확인); `ProductIngredientNormalizer.java`(search 도메인) → [`search/002-llm-prompt-tuning.md`](../search/002-llm-prompt-tuning.md); `ReviewSummaryService.java`(review 도메인) → 팀원 소유, 범위 밖; 요청 전체 타임아웃 예산 → `cross-cutting/001-llm-call-infra.md` C-1이 이미 "동기 HTTP 경로인 `RecipeCandidateGenerator`에 가치가 크다"고 언급·문서화한 상태이고, 이번 라운드에서 새로 판단하지 않는다; `enhanceWithServerValidation`(154-195줄)의 상세 재검증 로직 자체 → LLM 호출·프롬프트가 아니므로 상세히 다루지 않는다. 다만 이 메서드가 **이미 존재해서 LLM의 자체 보고 값(사용한 재료·부족한 재료)을 신뢰하지 않고 서버가 다시 계산한다**는 사실은 few-shot 판단(D-1)의 근거로 아래에서 활용한다
- **STOP §1(문서·구현 정합) 확인**: `application-prod.yml:48-49` 주석과 `RecipeCandidateGenerator.java`의 실제 override(없음)가 정확히 일치한다. 다만 감사 중 **다른 정합성 문제 1건**을 발견했다 — 아래 B-1
- **재확인 — 응답 파싱 안정성** (체크리스트 16번, 이번 라운드 대상 아님): `RecipeCandidateGenerator.java:113-125`에 `GoogleGenAiChatOptions.builder().responseMimeType(MediaType.APPLICATION_JSON_VALUE).responseSchema(RESPONSE_CONVERTER.getJsonSchema())`가 이미 적용돼 있다. `cross-cutting/001-llm-call-infra.md` A-2에서 승인·반영된 상태 그대로다.
- **재확인 — 재시도·타임아웃** (체크리스트 16번, 이번 라운드 대상 아님): `LlmClientConfig.java:39-53`의 `googleGenAiClient` 빈(총 3회 시도, 초기 지연 1초, 상한 10초, 호출당 30초 타임아웃)을 `chatClient`(55-59줄, `@Primary`) 하나가 감싸고, `RecipeCandidateGenerator`는 이 빈을 그대로 주입받는다(`RecipeCandidateGenerator.java:29`). `cross-cutting/001-llm-call-infra.md` A-1에서 승인·반영된 상태 그대로다.
- **재확인 — 배치화 전제**: `RecipeRecommendService.java:88`에서 `recipeCandidateGenerator.generateRecipeCandidates(ownedNames)`가 요청(장바구니 조회) 1건당 정확히 1회만 호출된다. 장바구니 상품이 몇 개든 재료명은 `loadOwnedIngredientNames`(172-196줄)가 Elasticsearch 조회 1회로 모아 하나의 리스트로 넘기므로, 상품 수가 늘어도 LLM 호출 횟수는 늘지 않는다 — 이미 "요청 1건 = 호출 1회"다.

## ✅ A. 반드시 수정해야 하는 사항

**없음.** 이번 라운드에서 코드로 확인 가능한 결함(버그·성능 저하·구조 문제)을 찾지 못했다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `CandidateRecipePlan`의 필드 주석이 실제 LLM 출력 계약과 다름

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `CandidateRecipePlan.java:15`의 `cookingMethod` 주석은 `// 요리 방법 (예: "볶음", "찌개", "구이")`이고 `:17`의 `mealType` 주석은 `// 식사 종류 (예: "점심", "저녁", "간식")`이다. 그런데 실제로 LLM에게 강제하는 값은 `RecipeCandidateGenerator.java:98,100`의 `<출력 필드 정의>` 절이 정의한 닫힌 집합 — `cookingMethod`는 `"ONE_POT"`·`"STIR_FRY"`·`"NO_COOK"`·`"OVEN_BAKE"`, `mealType`은 `"MAIN"`·`"SIDE"`·`"SOUP"` — 뿐이다. 주석의 예시 값은 실제로 한 번도 나올 수 없는 값들이다(`difficulty` 주석 `:16`은 `"EASY"/"NORMAL"/"CHALLENGE"`로 프롬프트와 정확히 일치해서 문제 없음)
- **근거**: `grep -n "볶음\|찌개\|구이\|점심\|저녁\|간식" baro-ai/src/main/java/com/barofarm/ai/recommend/`는 `CandidateRecipePlan.java:15,17`(주석)과 `RecipeCandidateGenerator.java:78,95,101`(프롬프트 안의 별개 예시 문장, 필드 계약과 무관)만 나온다. 반대로 실제 필드 계약 값 `grep -n "ONE_POT\|STIR_FRY\|NO_COOK\|OVEN_BAKE\|MAIN\|SIDE\|SOUP"`은 `RecipeCandidateGenerator.java:98,100`에만 있고 `CandidateRecipePlan.java`에는 없다 — 주석이 실제 계약을 반영하지 못하고 있다는 뜻이다
- **왜 문제인가**: `java-comments.md`는 "주석은 이름이 못 담는 것을 사실대로 남긴다"를 원칙으로 하는데, 이 주석은 사실과 다른 예시를 남겨 신규 개발자가 `CandidateRecipePlan`만 보고 `cookingMethod`에 `"볶음"` 같은 문자열이 들어온다고 오해할 수 있다. `BeanOutputConverter`가 만드는 JSON 스키마는 Jackson 리플렉션 기반이라 `//` 주석을 읽지 않으므로 런타임 동작(Gemini에 실제로 보내는 스키마)에는 영향이 없다 — 순수하게 사람이 읽는 문서 정합성 문제다
- **개선 방법**: 두 주석을 `RecipeCandidateGenerator.java:98,100`의 실제 값으로 교체한다 (`// 조리 방식 ("ONE_POT"/"STIR_FRY"/"NO_COOK"/"OVEN_BAKE")`, `// 식사 유형 ("MAIN"/"SIDE"/"SOUP")`)
- **검증 방법**: 변경 후 `grep -n "cookingMethod\|mealType" CandidateRecipePlan.java RecipeCandidateGenerator.java`로 두 파일의 값 목록이 문자열 그대로 일치하는지 육안 대조
- **API 영향**: No Impact — 주석만 바뀐다, JSON 계약·필드명·타입 변경 없음
- **예상 변경 파일**: `recommend/domain/CandidateRecipePlan.java`
- **예상 변경 라인 수**: 2줄
- **위험도**: Low
- **테스트 영향도**: 없음(자동화 테스트 0개, 컴파일에도 영향 없음)
- **예상 효과**: 가독성 · 문서-구현 정합 회복

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 모델 tier(`gemini-2.5-flash-lite`) 선택 근거 — search 라운드와 같은 결론, 반복 서술 안 함

`RecipeCandidateGenerator`도 override 없이 같은 전역 모델(`gemini-2.5-flash-lite`)을 쓴다. 품질/비용/지연 트레이드오프가 문서화돼 있지 않다는 점, `flash-lite`가 부족하다는 증거가 없다는 점, 정량 비교 데이터가 없어 D로 확정할 근거도 없다는 점 모두 [`search/002-llm-prompt-tuning.md`](../search/002-llm-prompt-tuning.md) C-1과 동일한 상황이라 그 문서를 참조하고 여기서 반복하지 않는다. 다만 한 가지 차이는 언급할 가치가 있다 — search의 `ProductIngredientNormalizer`는 **결정적 추출**(`temperature=0.0`으로 override)인 반면, `RecipeCandidateGenerator`는 override 없이 전역 `temperature=0.7`을 그대로 쓴다. 레시피 후보 생성은 다양성이 필요한 **생성형** 작업이라 이 값 자체는 과업 성격에 부합해 보인다는 관찰이지만, "0.7이 최적값"이라는 정량 근거(다른 temperature와 비교한 데이터)는 없어 C로 남긴다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. few-shot(완전한 input→output 예시) 추가 안 함

`buildRecipeCandidateTemplate`(59-108줄)에 "보유 재료 [사과, 계란] → 레시피 3개 JSON" 같은 완전한 입력→출력 예시를 추가하는 것을 검토했으나, 코드로 확인한 아래 네 가지 사실을 근거로 도입하지 않는 편이 낫다고 판단한다.

첫째, few-shot이 전통적으로 메우는 역할(암묵적으로만 기대되던 규칙을 예시로 보여주기)이 이미 명시적 규칙으로 대체돼 있다. `<후보 생성 규칙>`(81-90줄)은 "반드시 3개의 독립적인 레시피 후보를 생성해야 합니다", "각 레시피는 한식, 양식, 중식, 일식 등 다양한 스타일을 가져야 합니다", "cookingMethod/difficulty/mealType 조합은 후보마다 달라야 합니다"를 문장으로 직접 강제한다 — search 도메인의 `ProductIngredientNormalizer`가 "카테고리를 그대로 답으로 쓰지 말라"는 규칙을 지키는 어려운 사례(품종명 일반화)를 예시로 보여줘야 했던 것과 달리, 여기서는 다양성 요구 자체가 이미 규칙 텍스트로 존재해서 예시가 새로 가르쳐야 할 암묵적 지식이 남아있지 않다.

둘째, 지금 있는 유일한 인라인 예시(78줄, "보유 재료가 ['사과','계란']일 때…")는 완전한 input→output이 아니라 "이런 접근은 피하고 이런 접근을 택하라"는 개념 설명용 부분 예시다 — `cookingMethod`·`difficulty`·`mealType`·`recipeIngredientsCore`·`recipeIngredientsExtra`·`instructions` 같은 실제 출력 필드 값을 전혀 보여주지 않는다. 완전한 JSON 예시를 추가하는 것은 지금과 다른 종류의 변화라는 뜻이고, 그만큼 신중히 판단해야 한다.

셋째, few-shot이 주로 노리는 효과 중 하나인 "출력 필드의 형식 정확성"은 이미 두 겹으로 보장돼 있다. `responseSchema`(A-2, 이미 반영)가 JSON 형태 자체를 강제하고, `usedOwnedIngredients`·`missingIngredientsCore`는 애초에 LLM에게 "빈 배열로 두세요. 서버가 다시 계산합니다"(106줄)라고 지시한 뒤 `enhanceWithServerValidation`(154-195줄)이 `IngredientProcessingUtil.normalizeForCompare`·`subtractNormalized`로 서버가 처음부터 다시 계산해 덮어쓴다(183-194줄). 즉 few-shot으로 정확도를 높이고 싶은 대상 필드 중 최소 2개는 애초에 LLM의 값을 쓰지 않으므로 few-shot의 효용이 없다.

넷째, 이 도메인에 실제로 있었던 프로덕션 사고([`recommend/refactor-log.md`](refactor-log.md) "레시피 추천의 LLM 호출을 3회에서 1회로" 절의 Before/After 표, 27-33줄)를 확인한 결과 few-shot으로 막을 수 있는 유형이 아니었다. 그 사고는 "LLM이 원본 상품명을 그대로 되읊어야 재료로 인정"되던 문제로, `originalProductNames.contains(...)`가 공백 하나만 어긋나도 재료를 조용히 탈락시켰다 — 이는 지금은 삭제된 별도 클래스(`ProductNameNormalizer`, 재료 추출용)의 **문자열 비교/정규화 로직 버그**였고, `RecipeCandidateGenerator`의 다양성·형식 문제와는 무관하다. 실제 해결책도 few-shot이 아니라 비교 로직을 `normalizeForCompare` 기준으로 통일하고 서버가 재계산하는 구조(지금의 `enhanceWithServerValidation`)로 옮긴 것이었다. 즉 이 도메인에서 실제로 벌어진 사고는 "few-shot이 있었다면 막혔을 유형"이 아니라 "서버 재검증이 있어야 막히는 유형"이었고, 그 대책은 이미 반영돼 있다.

이 네 가지에 더해 구조적 비용도 있다 — `RecipeCandidateGenerator`는 (재확인 절에서 확인했듯) 사용자 요청마다 동기적으로 호출되는 실시간 경로다(`RecipeRecommendService.recommendFromCartWithMissing` → `cartClient.getCart` 동기 호출 직후). search의 `ProductIngredientNormalizer`처럼 상품 색인 시점(드물게 발생)에만 도는 호출이 아니라, 사용자가 추천을 요청할 때마다 실행되므로 few-shot 블록을 추가하면 그 토큰 비용이 매 요청마다 반복된다.

**참고(근거 아님)**: 아래 "참고" 절의 ssoss-server 조사와 Anthropic 공식 문서 인용은 위 판단을 뒷받침하는 정황일 뿐 판단 근거가 아니다 — 이 판단의 근거는 위 네 가지(코드로 확인한 다양성 규칙의 명시성, 기존 예시의 성격, 서버 재검증 범위, 과거 실제 사고의 원인)다.

### D-2. 배치화 — 실시간 동기 요청 경로라 구조적으로 적용 대상이 없음

재확인 절에서 확인했듯 이미 "요청 1건 = 호출 1회"다(`RecipeRecommendService.java:88`, 장바구니 상품이 몇 개든 ES 조회 1회로 재료명을 모아 LLM은 한 번만 호출). search 도메인처럼 "레코드 1건당 호출 1회"를 "레코드 N건당 호출 1회"로 묶는 최적화 여지 자체가 없다 — 여기서 배치화하려면 **서로 다른 사용자의 서로 다른 요청**을 하나의 프롬프트로 묶어야 하는데, 사용자는 자신의 추천 결과를 실시간으로 기다리는 동기 HTTP 요청 안에 있으므로 다른 사용자의 요청이 도착할 때까지 응답을 지연시키는 것은 이 기능의 목적(장바구니 열람 시 즉시 추천)과 충돌한다. search 도메인은 Kafka 컨슈머라는 비동기 처리 지점이 있어 향후 재정규화 배치 경로를 Later로 남길 수 있었지만(`search/002-llm-prompt-tuning.md` 개선 제안 1), `recommend`에는 그런 비동기 처리 지점이 없다 — Never에 해당한다.

### D-3. 프롬프트 지시문 길이 — 현재 그대로 유지

`buildRecipeCandidateTemplate`이 만드는 템플릿 본문(64-107줄, `{ownedIngredients}` 치환 전 고정 부분)은 공백 포함 약 2,177자다(Python `len()` 측정, 재현 가능 — search `ProductIngredientNormalizer`의 약 520자보다 길지만, search는 "상품명 하나에서 단어 하나 추출"이고 이쪽은 "레시피 3개, 각 10개 필드, 여러 비즈니스 규칙(판매 기회를 위한 30~50% 신규 재료, 다양성, 현실성 검증)을 동시에 만족"이라 과업 복잡도 차이를 반영한 길이로 보인다). 절 단위(정책 / 재료 조합 가이드 / 후보 생성 규칙 / 현실성 검증 / 출력 필드 정의)로 나눠 읽어 보면 각 절이 서로 다른 관심사를 담당하고, 문장이 사실상 겹치지 않는다 — "재료 조합 가이드"는 보유 재료 중 무엇을 쓸지, "현실성 검증"은 완성된 레시피의 재료 궁합이 그럴듯한지를 다뤄 표면적으로만 비슷해 보일 뿐 서로 다른 검증 시점을 겨냥한다. `cross-cutting/001-llm-call-infra.md` A-2에서 "JSON만 출력하라"류 형식 지시는 이미 지워졌으므로 남은 문장은 형식이 아니라 비즈니스 규칙뿐이다. 임의로 줄이면 판매 기회 확보(30~50% 신규 재료)나 다양성 강제 같은 정책이 빠질 위험이 있어 `safe-refactor` 절대 원칙(비즈니스 로직·정책 변경 금지)과 충돌한다 — 줄일 근거를 찾지 못해 현행 유지로 분류한다.

## 백엔드 아키텍처 개선 제안

이번 라운드에서 새로 제안할 것이 없다. Resilience(A-1)·Monitoring(B-1)·타임아웃 예산(C-1)은 이미 `cross-cutting/001-llm-call-infra.md`가 다뤘고 그 문서 소관이다.

## 📎 참고: Best Practice·유사 구현 사례

`researcher` 서브에이전트가 [ssoss-server](https://github.com/Central-MakeUs/ssoss-server)(외부 유사 저장소)의 few-shot 사용 패턴을 조사한 결과다. **이건 A/B 판정의 근거가 아니다** — 위 D-1의 판단 근거를 대체하지 않고, 참고만 하는 자리다.

> ssoss-server는 창작형 콘텐츠 생성 태스크에 few-shot(완전한 입력→출력 예시 쌍)을 쓰지 않는다. 유일한 "예시" 사용은 회원 본인 과거 글 1건을 "문체만 참고, 소재는 가져오지 말 것"이라는 스코프 제한과 함께 넣는 것뿐이고, 이는 여러 개의 입력→출력 예시를 주는 고전적 few-shot과 다르다. Anthropic 공식 문서(platform.claude.com/docs, 확인일 2026-09-04)는 "예시가 다양하지 않으면 모델이 의도치 않은 패턴을 학습할 수 있다"고 명시하며, 3-5개의 관련성·다양성 있는 예시를 권장한다. 검색 요약 기준(원문 미확인) arXiv 등 일부 문헌은 "고정된 few-shot 예시가 있으면 모델이 예시의 주제·표현을 그대로 재활용하는 경향(replicate or heavily draw from the exemplars)이 보고된다"고 한다 — 원문 확인은 안 됐다.

`RecipeCandidateGenerator`는 `recommend` 도메인 유일의 **창작형(생성형)** LLM 호출이라는 점에서 ssoss-server의 창작형 콘텐츠 생성 태스크와 성격이 비슷하다 — ssoss가 창작형 태스크에 완전한 few-shot을 쓰지 않는다는 관찰은 D-1의 네 번째 근거(과거 실제 사고가 few-shot과 무관했다는 사실)와 방향이 같지만, D-1의 판단은 이 참고 자료가 아니라 이 저장소 코드를 직접 읽은 결과다.

## 판단이 서지 않는 항목

1. **`responseSchema`가 배열 길이 제약(`recipeIngredientsCore` 3~5개, `recipeIngredientsExtra` 1~3개 등)까지 강제하는지** — `cross-cutting/001-llm-call-infra.md` A-2는 "구조화 출력을 강제한다"까지만 확인했고, `BeanOutputConverter.getJsonSchema()`가 만드는 스키마에 `minItems`/`maxItems`가 포함되는지, Gemini의 `responseSchema` 지원 범위가 그런 제약까지 받아들이는지는 이번 감사에서 실행해 확인하지 못했다. 포함되지 않는다면 이 개수 제약은 지금도 프롬프트 문장에만 의존하고 있는 셈이다. 확인 방법: 로컬 기동 후 `RESPONSE_CONVERTER.getJsonSchema()` 반환값을 로그로 찍어 `minItems`/`maxItems` 키 유무를 확인하거나, `researcher`로 Gemini `responseSchema` 공식 문서(버전 고정)에서 배열 제약 지원 여부를 확인한다.
2. **실제 생성된 레시피 후보가 다양성 규칙(한식/양식/중식/일식, cookingMethod/difficulty/mealType 조합)을 얼마나 잘 지키는지** — D-1의 "규칙이 이미 명시적이라 few-shot의 한계효용이 낮다"는 판단은 규칙 텍스트를 근거로 한 것이지, 실제 모델 출력을 표본 수집해 위반율을 측정한 것은 아니다(테스트·로그 수집 인프라가 없다 — `cross-cutting/001-llm-call-infra.md` C-3/C-4와 같은 이유로 이번 라운드 범위 밖). 사용자가 이 판단을 더 확실히 하고 싶으면, 같은 보유 재료 조합으로 여러 번 호출해 다양성 위반 빈도를 수동으로 표본 조사하는 절차가 필요하다.

## 승인 대기

A 항목이 없고 B-1(주석 2줄 정리)만 있다. C-1(모델 tier 근거 문서화, search와 동일 결론 참조)·D-1(few-shot 도입 안 함)·D-2(배치화 불가)·D-3(프롬프트 길이 유지)는 사용자에게 보고만 하고 **코드 변경 없이 이번 라운드를 종료할 것을 제안한다.** 사용자가 B-1만이라도 반영을 원하면 그건 위험도 Low·2줄짜리 주석 수정이라 별도로 짧게 처리할 수 있다.

## 구현 결과 (2026-09-05)

B-1을 반영했다. 다만 이 항목만 따로 고친 것이 아니라, [`cart-recipe/001`](../cart-recipe/001-cart-integration-and-request-path.md) B-3이 `CandidateRecipePlan`을 record로 재작성하면서 필드 주석을 실제 계약값으로 다시 썼다 — `cookingMethod`는 `"ONE_POT"`·`"STIR_FRY"`·`"NO_COOK"`·`"OVEN_BAKE"`, `mealType`은 `"MAIN"`·`"SIDE"`·`"SOUP"`. 같은 라운드에서 `RecipeCandidateGenerator`의 "LLM #2"·"한식/일식/양식 각 1개씩" 주석(3회 호출 시절의 잔재)도 함께 정정했다. 반영 이력은 [`cart-recipe/refactor-log.md`](../cart-recipe/refactor-log.md)에 있다.

