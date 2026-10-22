# Layer 2 — Workflow Enforcement (승인 게이트가 있는 반복 워크플로)

> 분류: **skill** (`.claude/skills/*/SKILL.md`) · 강제 수단: 절차 + 사용자 승인 게이트 · 대응 다이어그램: "Layer 2: safe-refactor 플로우"

## 1. 기본 사항

### 이 레이어가 나타내는 것

에이전트가 매번 임의의 방식으로 작업하지 않도록, 반복되는 작업 유형마다 **단계와 승인 지점을 고정한** 절차입니다. 공통 명제는 하나 — **LLM의 자기 보고를 신뢰하지 않는다.** "테스트 통과했습니다"가 아니라 `./gradlew :baro-ai:test`의 실제 종료 코드를 요구하고, "안 바꿨습니다"가 아니라 바뀐 파일 목록을 요구합니다.

### 파일 위치와 분류

| 스킬 | 사이클 위치 | 파일 | 트리거 | 산출물 | 승인 게이트 |
|---|---|---|---|---|---|
| `specify` | **A 트랙** | [`.claude/skills/specify/SKILL.md`](../../../.claude/skills/specify/SKILL.md) + `docs/templates/reference-doc.md` | 새 API·엔티티·인증 흐름, DB 스키마 변경, 3파일+ 리팩터 | `docs/specs/{domain}/{feature}.md` | **있음** (Approved 전 구현 금지) |
| `safe-refactor` | **B 트랙** | [`.claude/skills/safe-refactor/SKILL.md`](../../../.claude/skills/safe-refactor/SKILL.md) + `references/audit-checklist.md`·`docs/templates/how-to-doc.md` | 기존 코드 아키텍처 감사·무손실 리팩터 | `docs/audits/{domain}/{NNN}-{topic}.md` · `refactor-log.md` | **있음** (감사·구현 각각) |
| `debug` | **C 트랙** | [`.claude/skills/debug/SKILL.md`](../../../.claude/skills/debug/SKILL.md) | 버그 리포트·테스트 실패 | 없음(조사 절차) | 없음 |
| `preflight` | **G3 게이트** | [`.claude/skills/preflight/SKILL.md`](../../../.claude/skills/preflight/SKILL.md) | "완료/통과" 선언 직전 | 없음(검증 절차) | 없음 |
| `defer` | **G4 게이트** | [`.claude/skills/defer/SKILL.md`](../../../.claude/skills/defer/SKILL.md) | 「다른 이슈로 빼」 | Draft 스펙 + 스펙 amend + (확인 후) 이슈 | **있음** (이슈 생성 전) |
| `retro` | **G4 게이트** | [`.claude/skills/retro/SKILL.md`](../../../.claude/skills/retro/SKILL.md) | 작업 완료 후 회고·「이번에 배운 거 정리」 | `docs/audits/harness-retro.md` append | **있음** (파일 기록 전 · 규칙 반영 전 2단계) |

**`retro`만 메인 컨텍스트에서 돕니다 (2026-09-04 신설):** 다른 스킬과 달리 이 스킬은 서브에이전트나 `context: fork`로 돌리면 **망가집니다** — 회고의 입력이 세션 대화 이력 자체인데, 서브에이전트는 그 이력에 접근할 수 없고 트랜스크립트 파일 경로도 공식 규약이 아닙니다. `safe-refactor`이 self-grading 편향을 피하려 일부러 컨텍스트를 격리하는 것과 정확히 **반대 방향**입니다. 같은 저장소 안에서 "격리가 이득인 작업"과 "격리가 손해인 작업"이 갈린다는 게 이 스킬의 설계 포인트입니다.

**서브에이전트 (`.claude/agents/`, 2026-09-03 신설):** 스킬이 "절차"라면 에이전트는 "게이트에서 부르는 전담 인력"입니다. `researcher`(G1 — 외부 문서 조사), `doc-reviewer`(G3 — 문서 품질 리뷰), `spring-reviewer`(G3 — Java 변경 리뷰, 2026-09-04 추가) 셋 다 `tools` 화이트리스트에서 `Edit`/`Write`를 뺐고, 별도 컨텍스트에서 돌아 원문이 메인 대화를 오염시키지 않습니다. 다만 셋 다 `Bash`를 갖고 있어 **도구 목록만으로 쓰기가 완전히 봉쇄되지는 않습니다**(`sed -i` 우회 가능) — 마지막 한 겹은 지침의 규범입니다. 훅(L3)의 `Edit|Write` 매처도 Bash 경유 수정은 잡지 못하므로, 이 층을 "결정론적 차단"으로 오해하지 않는 것이 중요합니다.

> **`spring-reviewer`가 ArchUnit과 역할을 나누는 방식:** 에이전트 지침은 `ArchitectureTest`가 결정론적으로 잡는 7개 규칙(레이어 의존, 필드 주입 금지, UUID PK 등)을 **지적 대상에서 제외**하도록 적혀 있습니다 — 기계가 잡는 것을 LLM이 다시 보는 건 비용만 늘고 신호를 흐리기 때문입니다. **다만 baro-ai에는 그 `ArchitectureTest` 자체가 없습니다**(이식하면서 따라온 전제로, `.claude/agents/spring-reviewer.md`와 `.claude/rules/spring-boot-java.md`가 아직 그 테스트를 사실처럼 서술합니다 — 규칙 쪽 정리가 필요한 항목입니다). 실제로 이 에이전트가 보는 것은 트랜잭션 경계·N+1·`ErrorCode`/`@TripActivity` 누락처럼 **정적 규칙으로 표현할 수 없어 ArchUnit이 못 잡는 것**에 집중합니다. 조사한 공개 컬렉션 3종(wshobson·VoltAgent·hesreallyhim)은 모두 `tools`를 제한하지 않거나 `Write`/`Edit`을 포함시켜 리뷰어가 코드를 고칠 수 있게 방치돼 있어, 그 부분은 채택하지 않고 기존 `doc-reviewer` 구조를 따랐습니다. **`.claude/agents/*.md`는 파일을 만들면 등록**됩니다 — 2026-09-03 신설 당시 첫 호출은 `Agent type not found`로 실패했다가 잠시 뒤 사용 가능해졌으므로, 새 에이전트를 만든 직후 호출이 실패하면 재시도하거나 새 세션에서 확인합니다.

**로딩 메커니즘:** 스킬은 `name` + `description` 한 줄만 항상 컨텍스트에 노출되고, 에이전트가 그 작업을 인식해 호출할 때 **SKILL.md 전문이 로드**됩니다. 규칙(Layer 1)이 "항상 켜져 있는 배경"이라면 스킬은 "필요할 때 꺼내 읽는 절차서"입니다.

> **`description` 한 줄이 곧 트리거입니다 — 2026-09-04에 이게 깨져 있는 걸 발견했습니다.** `preflight`(당시 `verify`)의 frontmatter가 `description: "완료/통과" 선언 전에 …`로 시작해, 값이 큰따옴표로 열리는 바람에 YAML이 `완료/통과`까지만 읽고 나머지를 버렸습니다. 그 결과 스킬 목록에 설명 대신 문서 제목("Verify Workflow")이 노출됐고, **에이전트가 이 스킬을 언제 불러야 하는지 판단할 근거가 없는 상태**였습니다. `#128` 개명 작업 중 우연히 드러났습니다.
>
> 시사점은 두 가지입니다. ① 스킬은 파일이 존재한다고 작동하지 않습니다 — `description`이 파싱돼야 후보에 오릅니다. ② 이 실패는 **조용합니다.** 에러도, 로그도 없고 그저 스킬이 덜 불릴 뿐이라, 게이트가 작동하지 않는데도 아무도 모릅니다. frontmatter 파싱 검증은 exit code로 판정 가능하므로 훅(L3)으로 승격할 수 있는 후보입니다.

> `debug`가 규칙이 아니라 스킬인 이유: 트리거가 "버그 리포트를 받았는가"라는 **상황**이라 `paths:`(파일 경로 기반) 스코프로는 표현할 수 없었습니다. always-load 규칙에 넣으면 매 세션 토큰을 먹으므로 스킬로 옮겼습니다(2026-08-11).

## 2. 언제 발동하고, 어떤 흐름을 타는가 — `safe-refactor` 기준

다이어그램이 그리는 건 `safe-refactor`입니다. 이게 승인 게이트가 가장 많고 검증이 가장 엄격하기 때문입니다.

### 절대 원칙 (SKILL.md에 명시)

1. ~~API Contract 100% 동일~~ — **baro-ai에서는 해제됨**(2026-09-04 결정: 릴리즈 전이라 지킬 클라이언트가 없다. 더 정확해지면 계약도 바꾼다)
2. 비즈니스 로직 변경 금지 — 내부 구현만 개선
3. YAGNI — 불필요한 추상화·과도한 AOP 분리 금지
4. 성능 악화 금지
5. **`core-guardrails.md` ⛔ STOP이 이 스킬보다 항상 우선**

### 단계별 흐름과 읽는 파일

```
[0] 도메인 1개 선택          ← 여러 도메인 동시 진행 금지
     읽는 것: SKILL.md "작업 단위" 표 (taste-vector / cart-recipe /
              vector-similarity / keyword-search / cross-cutting)

[1] Audit — 읽기 전용 서브에이전트
     Agent 툴(subagent_type: Explore 또는 general-purpose)로 새 컨텍스트 생성
     서브에이전트 프롬프트에 반드시 포함:
       · 대상 패키지 경로
       · references/audit-checklist.md 의 16개 점검 항목 전체
         (16번 = LLM·프롬프트 최적화, baro-ai 전용 추가 항목)
       · A/B/C/D 분류 기준
       · docs/templates/how-to-doc.md 의 감사 채움 예 포맷
       · "코드 수정 금지 — 읽기·분석만"
     산출: docs/audits/{domain}/{NNN}-{topic}.md

[2] 승인 게이트  ← 사람
     감사 문서의 A/B 항목만 사용자에게 요약 보고
     C/D는 애초에 이번 라운드 구현 대상이 아님 (거부가 아니라 범위 밖)
     승인 안 받은 항목은 손대지 않음

[3] Implement — 승인된 A/B만
     우선순위 Critical → High → Medium → Low
     구현 중 API 계약·ErrorCode·엔드포인트를 건드리게 되면
       → 원칙 위반 신호 → 즉시 중단하고 사용자에게 질문

[4] Verify — 무손실을 기계적으로 증명 (3종 전부 통과해야 함)
     ./gradlew :baro-ai:test                              # 전체 통과
     ⚠ baro-ai는 "OpenAPI 계약 보호" 옵션이 꺼져 있어 oasdiff를 쓰지 않는다.
       대신 테스트 전체 통과 + 스펙 "수동 확인" 절을 검증 기준으로 삼는다
     하나라도 실패 → "일단 커밋" 금지 → 원인 분석 → 재수정 → 재검증

[5] Report
     docs/audits/{domain}/refactor-log.md 에 append (Changelog 스타일)
       실행 날짜 · 반영한 A/B 목록 · 변경 파일/라인 수 · 검증 결과
       · 남겨둔 C/D 항목과 그 이유
     사용자에게 짧게 요약 (core-reporting.md 적용)

[6] 다음 도메인 — 사용자 승인 없이 자동 진행 금지
```

이 6단계는 `core-workflow.md`의 **트랙 공통 게이트**와 1:1로 대응합니다 — `[1] Audit`은 진입 + G1 리서치, `[2] 승인`은 G2, `[4] Verify`는 G3, `[5] Report`는 G4입니다. 게이트의 내용 자체는 `core-workflow.md`가 SSOT이고 `SKILL.md`는 대응 관계만 표로 두어 중복을 피합니다(2026-09-03 `#127`).

### 왜 1단계가 서브에이전트인가

방금 짠 코드를 같은 대화에서 스스로 평가하면 자기 판단을 재확인하는 **self-grading 편향**이 생깁니다. `code-review`/`simplify` 스킬이 별도 컨텍스트를 쓰는 것과 같은 이유입니다. SKILL.md는 여기서 한 발 더 나가서, **이번 세션에서 안 건드린 도메인이라도** 신선한 컨텍스트를 쓰라고 명시합니다 — 같은 대화 맥락 자체가 편향원이라고 보기 때문입니다.

### `preflight`가 SSOT인 지점

`safe-refactor`의 4단계는 검증 절차를 자체 정의하지 않고 `preflight` 스킬을 참조합니다(중복 정의 방지). `preflight`의 8단계:

1. `./gradlew :baro-ai:test` 실제 실행
2. 스펙·이슈 체크리스트를 **실제 코드**와 대조 (STOP §1.5)
3. API 계약이 바뀌었으면 스펙·`refactor-log`에 Before/After를 남겼는지 (baro-ai는 `oasdiff`를 쓰지 않는다)
4. 레거시 재점검 (STOP §4)
5. 문서를 새로 만들었거나 50줄 이상 고쳤으면 `doc-reviewer` 서브에이전트 (advisory, 2026-09-03 추가)
6. Java를 3파일 이상·API·DB 범위로 고쳤으면 `spring-reviewer` 서브에이전트 (2026-09-04 추가)
7. Must Have급이면 `code-review`/`simplify`를 서브에이전트로 한 번 더
8. 실제 통과한 항목만 `[x]`, 실패·미검증은 숨기지 않고 그대로 보고

### 2-1. 호출 비용 — 비싼 건 모델이 아니라 입력 (2026-09-04)

서브에이전트는 컨텍스트를 아끼려고 도입했는데, **호출을 잘못하면 오히려 더 든다.** 2026-09-04 세션의 실측입니다.

| 호출 | 소비 토큰 | 왜 그만큼 들었나 |
|---|---|---|
| `doc-reviewer` (문서 12개) | **약 128,000** | 호출자가 "12개를 **전문으로** 읽어라"라고 지시 |
| `researcher` (공개 컬렉션 조사) | 약 121,000 | 외부 문서 다수 fetch — 조사 성격상 불가피 |
| `spring-reviewer` (커밋 1개) | 약 75,000 | diff + 주변 코드 — 적정 |

`doc-reviewer` 지침에는 원래 **"신규 파일은 전문, 기존 파일은 `git diff`로 변경 범위 파악"**이 명시돼 있었습니다. 호출자가 그 규약을 프롬프트로 덮어써서 12개 전부를 전문 읽기로 만든 것이 원인입니다. 즉 **비용이 샌 지점은 에이전트 설계가 아니라 호출 방식**이었습니다.

여기서 나온 규약은 `core-tools.md` "서브에이전트 호출 규약" 절에 세 줄로 고정했습니다 — 대상은 경로 목록으로만 넘기고, 전문 읽기는 신규 파일에만 지시하고, 10개를 넘으면 나눠 부릅니다. 같은 규약을 지켜 다시 부른 호출은 약 112,000 토큰으로 줄었습니다.

**모델을 낮추는 것은 해법이 아니었습니다.** 세 에이전트 모두 `sonnet`인데, 같은 세션에서 `doc-reviewer`는 "한 문서 안에서 스킬 개수가 4개와 5개로 어긋난다"는 교차 확인을 잡아냈습니다. 이런 판단이 필요한 리뷰어를 더 작은 모델로 내리면 절감액(호출당 수천 토큰)보다 놓치는 비용이 큽니다 — 입력을 줄이는 쪽이 한 자릿수 더 효과적입니다.

## 3. 실제 사례 — `recommend` 도메인 (2026-09-04)

`safe-refactor`을 baro-ai에서 전체 사이클로 돌린 사례입니다. 레시피 추천이 요청 한 건마다 외부 AI 서비스를 세 번 연달아 호출하던 구조를 한 번으로 줄였습니다.

- 신선한 서브에이전트로 `recommend` 패키지 읽기 전용 감사 → A/B 항목을 사용자에게 요약 보고 → 승인
- 구현 → `./gradlew :baro-ai:test` 통과
- 기록 → [`docs/audits/recommend/refactor-log.md`](../audits/recommend/refactor-log.md), 결정문 → [`decisions/002-ingredient-name-at-index-time.md`](../decisions/002-ingredient-name-at-index-time.md)
- **미완 부분도 그대로 기록:** baro-ai는 자동화 테스트가 0개라 이 명령이 컴파일만 검증한다는 사실을 로그에 명시했습니다

대표적 개선 1건 (사용자 보고문에서 발췌한 표현):

> 장바구니를 열어 레시피 추천을 받을 때마다, 서버가 외부 AI 서비스에 **세 번 연달아** 물어보고 있었습니다. 첫 번째는 "이 상품 이름에서 재료 이름만 뽑아줘", 두 번째는 "그 재료 목록이 맞는지 다시 확인해줘", 세 번째가 진짜 목적인 "이 재료로 만들 수 있는 레시피 알려줘"였습니다.

→ 앞의 두 번은 **매번 같은 답이 나오는 질문**이었습니다. "대란 30구는 계란이다"는 그 상품의 성질이지 누가 담았는지와 무관하니까요. 그래서 재료명을 상품 색인 시점에 한 번만 계산해 저장하고, 추천 때는 읽기만 하도록 바꿨습니다 — 비용을 자주 일어나는 쪽(추천 조회)에서 드물게 일어나는 쪽(상품 등록)으로 옮긴 것입니다. 정상 경로 3회 → 1회, 최악 경로 N+3회 → 1회로 줄었고 API 응답 계약은 그대로입니다.

**이 사례가 하네스에 대해 말해주는 것:** 감사 단계에서 "LLM을 3번 부른다"는 사실 자체는 코드를 읽으면 바로 보입니다. 정작 어려운 판단은 "그중 둘이 캐시 가능한 질문인가"였고, 그건 [`baro-ai-performance-refactor.md`](../../../.claude/rules/baro-ai-performance-refactor.md)의 근거 기준("느릴 것 같다는 근거가 아니다")이 강제한 확인 과정에서 나왔습니다.

## 4. AI-native 관점에서의 강조 포인트

| 순위 | 강조할 것 | 근거 |
|---|---|---|
| 1 | **컨텍스트 격리로 self-grading 편향을 구조적으로 차단** | 감사 1단계를 강제로 별도 서브에이전트에 위임. "AI가 자기 결과를 평가하면 안 된다"는 인지적 한계를 알고 아키텍처로 푼 것 — 프롬프트로 "객관적으로 봐줘"라고 부탁하는 것과 질적으로 다름 |
| 2 | **검증 기준이 "breaking 없음"이 아니라 "diff 0"** | 무손실 리팩터의 정의를 도구 출력으로 환원. LLM이 "계약 안 바꿨다"고 말할 여지 자체를 없앰 |
| 3 | **미검증 항목을 숨기지 않는 것을 규칙화** | `preflight` 8단계. 실행하지 못한 확인은 "미실행"으로 남긴다 — 완료율을 부풀리지 않는 게 오히려 신뢰 신호 |
| 4 | 승인 게이트 (A/B만 구현, C/D는 범위 밖) | 흔한 패턴이라 단독으로는 약함 |

**면접 활용 팁:** 이 레이어는 [Layer 3](layer3-deterministic-hooks.md)보다 한 단계 약합니다 — 결국 에이전트가 "절차를 따르기로 선택"해야 작동하기 때문입니다. 그래서 "절차로 되는 것(Layer 2)과 절차로 안 되는 것(Layer 3)을 어떻게 나눴는가"를 함께 말하는 게 가장 강한 구성입니다.
