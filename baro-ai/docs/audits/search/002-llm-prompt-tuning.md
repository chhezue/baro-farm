# Search Architecture Audit 002 — LLM 프롬프트 튜닝 (2026-09-04)

search 도메인에서 유일하게 LLM(Large Language Model, 거대 언어 모델)을 호출하는 `ProductIngredientNormalizer`(상품명 → 요리 재료명 추출)의 프롬프트 내용·모델 옵션을 감사한 결과다. 반드시 고칠 A 항목, 유지보수성 B 항목 모두 **0건**이다 — 재시도·구조화 출력 강제·관측은 이미 [`cross-cutting/001-llm-call-infra.md`](../cross-cutting/001-llm-call-infra.md)에서 감사·반영됐다. 이번 라운드가 새로 점검한 토큰 수·few-shot 구성·모델 선택 근거·배치화 가능성은 코드로 확인한 결과 "반드시 고쳐야 한다"는 근거가 나오지 않아 전부 참고(C)·유지(D) 항목으로 분류했다. 이 문서는 감사 시점의 근거·분류를 남기는 스냅샷이며, **A/B가 없으므로 이번 라운드는 승인·구현 단계 없이 감사만으로 종료할 것을 제안한다.**

## 범위

- 대상 코드: `com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer`(프롬프트 문자열·호출 시 옵션만)
- 대상 설정: `application-prod.yml`의 `spring.ai.google.genai.chat.options.*`(이 호출에 실제로 적용되는 `model`·`temperature` — `temperature`는 호출부가 `0.0`으로 덮어씀, `ProductIngredientNormalizer.java:23,50`)
- 감사자: `researcher`가 조사한 ssoss-server few-shot 사용 패턴을 컨텍스트로 받아 "참고" 절에 그대로 옮기고, 나머지는 코드를 직접 읽어 확인했다(신선한 서브에이전트 스캔)
- 기준: `audit-checklist.md` 16번(프롬프트 토큰·모델 선택·응답 파싱 안정성 재확인·재시도 재확인·배치화 가능성), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다
- **범위 밖 (중복 조사 안 함)**: Kafka 컨슈머·색인 흐름 → [`search/001-index-path.md`](001-index-path.md); 재시도·구조화 출력 강제·관측 → [`cross-cutting/001-llm-call-infra.md`](../cross-cutting/001-llm-call-infra.md) A-1/A-2/B-1(이미 반영, 아래 두 불릿에서 코드로만 재확인); `RecipeCandidateGenerator.java`(recommend 도메인) → 다음 라운드
- **STOP §1(문서·구현 정합) 확인**: `application-prod.yml:48-49`의 주석("결정적 추출이 필요한 호출은 호출부에서 0으로 덮어쓴다")과 `ProductIngredientNormalizer.java:21-23,50`의 실제 override가 정확히 일치한다. `ProductIngredientNormalizer.java:12-14`의 클래스 Javadoc("추천 요청 경로에서는 호출하지 않는다")도 `002-ingredient-name-at-index-time.md` 결정과 일치한다 — 이번 범위에서 위반 발견 없음
- **재확인 — 응답 파싱 안정성** (체크리스트 16번, 이번 라운드 대상 아님. 코드가 실제로 그 상태인지만 확인): `ProductIngredientNormalizer.java:48-53`에 `GoogleGenAiChatOptions.builder().responseMimeType(MediaType.APPLICATION_JSON_VALUE).responseSchema(RESPONSE_CONVERTER.getJsonSchema())`가 이미 적용돼 있다. `cross-cutting/001-llm-call-infra.md` A-2에서 승인·반영된 상태 그대로다.
- **재확인 — 재시도·타임아웃** (체크리스트 16번, 이번 라운드 대상 아님): `LlmClientConfig.java:39-53`에 SDK 레벨 재시도(총 3회 시도, 초기 지연 1초, 상한 10초)와 30초 호출 타임아웃이 걸려 있다. `cross-cutting/001-llm-call-infra.md` A-1에서 승인·반영된 상태 그대로다. `application-prod.yml`에도 죽은 `spring.ai.retry.*` 블록이 이미 삭제돼 있다(STOP §4 정리 완료 상태 재확인).

## ✅ A. 반드시 수정해야 하는 사항

**없음.** 이번 라운드에서 코드로 확인 가능한 결함(버그·성능 저하·구조 문제)을 찾지 못했다. 아래 C/D로 분류한 항목들은 각각 "고치는 게 나아 보인다"는 인상은 있었지만, `baro-ai-performance-refactor.md`가 요구하는 근거 기준(코드로 확인한 사실 또는 재현 가능한 관찰)을 충족하는 수준까지 이르지 못해 A로 올리지 않았다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

**없음.** `normalize()`(42-76줄)는 단일 책임(입력 검증 → 호출 → 파싱 → 로깅)이고, 두 호출부(`RecipeCandidateGenerator`는 범위 밖이라 비교 대상이 아님)가 아직 같은 코드를 반복하지 않아 뽑아낼 중복도 없다. Dead Code·Legacy 코드도 발견하지 못했다.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 모델 tier(`gemini-2.5-flash-lite`) 선택 근거가 문서화돼 있지 않음

`application-prod.yml:47`의 기본 모델 `gemini-2.5-flash-lite`가 이 추출 호출에도 그대로 적용되는데, `flash-lite`를 고른 품질/비용/지연 트레이드오프 검토가 문서 어디에도 없다. `grep -rn "flash-lite" baro-ai --include=*.md`는 `docs/IMPLEMENTATION.md`·`docs/README.md`(둘 다 값만 나열)와 `docs/audits/recommend/refactor-log.md`(OpenAI→Gemini 전환표, tier 비교 아님)만 나온다. `docs/decisions/003-gemini-migration.md`는 **제공자**(OpenAI vs Gemini) 선택 근거만 다루고, Gemini 내부 tier(`flash-lite` vs `flash` vs `pro`) 비교는 다루지 않는다. `flash-lite`가 이 추출 태스크(단어 하나를 뽑는 결정적 작업, `temperature=0`)에 실제로 부족하다는 증거(오답률, 재색인 대상 급증 등)는 없다. 근거 없이 "더 비싼 모델로 바꾸자"는 제안은 `baro-ai-performance-refactor.md`가 금지하는 "베스트 프랙티스라서"류 변경이 된다. 반대로 "현행 유지가 맞다"고 D로 확정하기에도 정량 비교가 없어 근거가 부족하다 — 그래서 C로 남기고 문서화만 권장한다.

### C-2. 배치화는 이번 라운드 범위 안에서 실행할 수 없음

상품 1건당 LLM 1회 호출(Kafka 이벤트마다)이라 지시문(81-100줄, 아래 D-1)이 매번 다시 전송된다. `ProductEventConsumer.onMessage(ProductEvent event)`(단건 파라미터, 배치 리스너 아님)와 `KafkaConsumerConfig.java:46`의 `PRODUCT_MAX_POLL_RECORDS = 1`(같은 파일 42-45줄 주석: "한 번에 1건만 받으면 최악의 경우에도 그 제한 안에 든다") — poll 하나당 정확히 레코드 1건만 받도록 **의도적으로** 고정돼 있다. 이 값은 `search/001-index-path.md` A-1에서 "레코드 1건당 원격 호출 2회(임베딩·재료명) × 각 30초 상한이라 여러 건을 한 번에 받으면 Kafka의 5분 처리 제한을 넘겨 컨슈머가 그룹에서 쫓겨난다"는 근거로 확정한 값이다. 여러 상품을 묶어 LLM 프롬프트 1회로 처리하려면 (1) 컨슈머 안에서 이벤트를 버퍼링하거나 (2) `max.poll.records`를 올려야 하는데, 둘 다 `001-index-path.md` A-1이 막으려던 조건(레코드당 원격 호출 시간 초과로 인한 rebalance 루프)을 다시 불러온다. 이는 프롬프트 내용이 아니라 Kafka 소비 방식 재설계이므로 이번 라운드 범위(search 프롬프트 내용 자체) 밖이고, `001-index-path.md` 소관이다. 실제 상품 이벤트 유입량도 `001-index-path.md` 판단 항목 4에서 이미 `[미정]`으로 남아 있어, 배치화로 얻는 절감폭을 수치로 뒷받침할 수도 없다.

정량 근거는 방향성만 있다 — `PROMPT` 상수(81-100줄, 공백 포함 약 520자, Python `len()`으로 측정)를 N건 배치로 처리하면 지시문·예시 부분(고정 약 520자)을 N번이 아니라 1번만 보내면 되므로 상품당 지시문 오버헤드가 이론상 1/N로 준다는 방향은 있다. 다만 실제 토큰 수·가격은 Gemini 토크나이저로 측정하지 못했다(이번 감사 도구로는 접근 불가) — "느낌"이 아니라 "측정 못 함"이라는 사실을 그대로 남긴다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 프롬프트 지시문 길이·토큰 수 — 현재 그대로 유지

`PROMPT` 상수(81-100줄) 전체는 공백 포함 약 520자다(Python `len()` 측정, 재현 가능). 규칙 4개(90-94줄)는 각각 다른 대상을 담당한다 — 브랜드/산지/수량/단위/포장/등급 제거, 출력 형식(공백 없는 한 단어), 카테고리를 힌트로만 쓰기, 근거 없는 재료 지어내지 않기. 문장이 서로 겹치지 않는다. `cross-cutting/001-llm-call-infra.md` A-2에서 "JSON만 출력하라"류 형식 지시 문구를 이미 구조화 출력 강제(`responseMimeType`/`responseSchema`)로 대체하며 지웠으므로, 지금 남은 문장은 형식이 아니라 추출 규칙만 담고 있다. 줄일 근거(중복 문장·불필요 지시문)를 코드에서 찾지 못했고, 임의로 줄이면 브랜드·산지·등급 제거 같은 규칙이 빠져 추출 품질이 떨어질 위험이 있다(`safe-refactor` 절대 원칙 2 — 비즈니스 로직·정책 변경 금지와도 충돌).

### D-2. few-shot 예시 3개 구성 — 현재 그대로 유지

96-99줄의 예시 3개를 직접 읽어 보면 서로 다른 난이도를 담당한다.

| 예시 | 카테고리 | 제거해야 하는 것 |
|------|----------|-------------------|
| "곰곰 무항생제 신선한 대란, 30구, 1개" → "계란" | 계란 | 브랜드+등급+포장+수량 |
| "제주 서귀포 고당도 노지 감귤 3kg (박스)" → "감귤" | 제철 과일 | 산지+당도 수식어+포장 |
| "횡성 한우 등심 300g 1등급" → "소고기" | 육류 | 산지+등급+부위, **품종명("한우")을 상위 종("소고기")으로 일반화** — 규칙 3번("카테고리를 그대로 답으로 쓰지 마세요")이 요구하는 가장 어려운 케이스 |

카테고리도 계란/제철 과일/육류로 겹치지 않는다. 개수를 늘리면 매 호출 토큰이 늘고, 줄이면(2개 이하) 규칙 3번이 요구하는 "카테고리 힌트를 그대로 베끼지 않기" 케이스가 빠질 위험이 있다 — 지금 3개가 다양성과 길이의 균형점이라고 판단한다.

**참고(근거 아님)**: 아래 "참고" 절의 ssoss-server 조사는 baro-ai가 이미 few-shot 3개를 갖고 있다는 관찰과, Anthropic 공식 문서의 "예시가 다양하지 않으면 모델이 의도치 않은 패턴을 학습할 수 있다"는 일반 원칙을 전달한다. 위에서 직접 읽은 3개 예시가 이미 서로 다른 카테고리·다른 난이도를 담당하므로 이 우려에 해당하지 않는다고 판단했다 — 이 판단의 근거는 프롬프트 텍스트를 직접 읽은 것이고, ssoss 조사·Anthropic 문서는 참고만 했다.

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

### 1. 재정규화 배치 경로에서 LLM 호출을 묶어 보내기 — **Later**

- **왜 필요한지**: C-2에서 다룬 실시간 Kafka 경로가 아니라, `search/001-index-path.md` 개선 제안 1("재색인·재정규화 경로", 현재 Later·미구현)이 실제로 만들어지면 그 경로는 애초에 "`ingredientName`이 빈 문서 여러 건"을 한 번에 처리하는 배치 성격이라, Kafka의 순서·5분 처리 제한 없이 자연스럽게 여러 상품을 묶어 LLM 프롬프트 1회에 넣을 수 있다.
- **근거**: C-2와 동일한 문자 수 관찰(지시문 약 520자를 N건이 나눠 갖는 구조) + `grep -rn -iE 'reindex|republish|backfill|renormalize' baro-ai/src/main/java` 0건(재정규화 경로 자체가 아직 없음, `001-index-path.md`에 이미 기록된 사실 재확인).
- **장단점**: 장점은 지시문 오버헤드 절감(정확한 비율은 미측정)과 재정규화 처리량 향상. 단점은 프롬프트를 "상품 여러 개 입력 → 재료명 여러 개 출력" 배열 구조로 다시 설계해야 하고, 응답 파싱이 일부만 실패했을 때 배치 전체를 버릴지 부분 성공을 허용할지 정책을 새로 정해야 한다.
- **Later인 이유**: 재정규화 경로 자체가 아직 없다(성격상 Track A `specify` 대상, `001-index-path.md`가 이미 그렇게 분류함). 그 스펙을 쓸 때 프롬프트 배치화를 요구사항에 포함시키는 것이 순서상 맞고, 지금 이 라운드(코드 리팩터, 대상 코드 없음)에서 미리 구현하지 않는다.

## 📎 참고: Best Practice·유사 구현 사례

`researcher` 서브에이전트가 [ssoss-server](https://github.com/Central-MakeUs/ssoss-server)(외부 유사 저장소, Central-MakeUs — [`ssoss-llm-comparison.md`](../ssoss-llm-comparison.md)에서 이미 8개 축으로 비교한 저장소)의 few-shot 사용 패턴을 조사한 결과다. **이건 A/B 판정의 근거가 아니다** — 위 각 항목의 "근거" 필드를 대체하지 않는다.

> ssoss-server는 창작형 콘텐츠 생성 태스크에 few-shot(완전한 입력→출력 예시 쌍)을 쓰지 않는다. 유일한 "예시" 사용은 회원 본인 과거 글 1건을 "문체만 참고, 소재는 가져오지 말 것"이라는 스코프 제한과 함께 넣는 것뿐이고, 이는 여러 개의 입력→출력 예시를 주는 고전적 few-shot과 다르다. baro-ai의 `ProductIngredientNormalizer.java`(추출형 태스크, 정답이 하나로 정해짐)는 이미 few-shot 예시 3개를 프롬프트에 갖고 있다(82-99줄) — 이건 추출형 태스크에 적절해 보인다는 관찰. Anthropic 공식 문서(platform.claude.com/docs, 확인일 2026-09-04)는 "예시가 다양하지 않으면 모델이 의도치 않은 패턴을 학습할 수 있다"고 명시한다. baro-ai/build.gradle에 명시된 Spring AI 버전과 Google GenAI 공식 문서는 few-shot 관련 특별한 제약을 언급하지 않는다.

## 판단이 서지 않는 항목

1. **Google GenAI SDK/Gemini API에 별도 Batch 모드(비동기 대량 처리, 통상 더 저렴한 가격)가 있는지** — 이번 감사에서 SDK 공식 문서를 확인하지 않았다. 있다면 위 "백엔드 아키텍처 개선 제안 1"의 구현 방식(직접 배치 프롬프트 설계 vs SDK Batch API 활용)이 달라질 수 있다. 확인 방법: `researcher`로 Google GenAI Java SDK(`build.gradle`에 고정된 버전) 공식 문서에서 Batch API 존재 여부·제약(동기/비동기, 최소 배치 크기, 지연 SLA)을 확인한다.
2. **`gemini-2.5-flash-lite`가 이 추출 태스크에 실제로 충분한 품질인지** — 정량 비교(`flash-lite` vs `flash` vs `pro`의 정확도) 데이터가 없다(C-1). 사용자가 실측을 원하면 같은 상품명 샘플을 세 tier에 돌려 정답률을 비교하는 별도 절차가 필요하다.
3. **상품 이벤트 실제 유입량 [미정]** — `search/001-index-path.md` 판단 항목 4와 같은 값. 배치화(C-2)로 얻는 절감폭을 수치로 정하려면 필요하다. 서버가 다시 뜨면 실측 가능.

## 승인 대기

A/B 항목이 없어 구현 대상이 없다. C-1(모델 tier 근거 문서화)·C-2(배치화 보류)·D-1/D-2(현행 유지)는 사용자에게 보고만 하고 **코드 변경 없이 이번 라운드를 종료할 것을 제안한다.** "백엔드 아키텍처 개선 제안 1"(재정규화 배치)은 `search/001-index-path.md` 개선 제안 1이 Track A(`specify`)로 스펙화될 때 같이 검토한다. 사용자가 C-1(모델 tier 근거를 결정 문서로 남기는 것)만이라도 진행을 원하면, 그건 코드 변경이 아닌 문서 작업이라 별도로 짧게 처리할 수 있다.
