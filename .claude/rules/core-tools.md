# Workflow Tools — 트랙·게이트별 도구 매핑

이 프로젝트는 **Claude Code를 기본 개발 환경**으로 쓴다. 이 문서는 `core-workflow.md`가 정의한 **트랙·게이트의 각 지점에서 어떤 도구를 쓰는지**를 매핑한다. 새 워크플로 도구를 채택할지 판단하는 기준은 문서 뒤쪽 [도구 우선순위](#도구-우선순위) 절에 있다.

Harness 형제: `core-guardrails.md` (⛔ STOP) · `core-scope.md` (priority·`[미정]`) · `core-followup.md` (후속·Defer·ERD) · `harness-map.md` (슬롯·옵션)

## 진입 (매 턴)

1. **문서·스펙·decisions 확인** — 작업 시작 전 `core-workflow.md`의 "진입 — 트랙 분류" 순서 따름
2. **`core-guardrails.md` ⛔** — 문서·스펙·decisions와 충돌 시 다른 어떤 관례보다 **우선**. 구현 중단·사용자 질문

## 트랙 × 게이트 → 도구

구조 SSOT는 `core-workflow.md`("사이클 — 3 트랙 × 4 게이트"). 여기는 **각 지점에서 무슨 도구를 쓰는지**만 매핑한다.

### 트랙 (택 1 — 진입 시 결정)

| 트랙 | 언제 | Claude Code | 이 프로젝트 |
|------|------|-------------|---------|
| **A. 기능** | 새 기능·API·엔티티·정책 변경 | `EnterPlanMode`(Plan Mode)로 탐색·설계 → 승인 | **`specify`** 스킬 → `{{스펙 저장소}}` → **Approved 후** 구현. 이미 Approved면 Plan Mode 생략하고 바로 구현 |
| **B. 감사·리팩터** | 기존 코드 품질 개선 (계약·로직 불변) | `Agent`(`Explore`/`general-purpose`, 읽기 전용)로 신선한 컨텍스트 감사 | **`safe-refactor`** 스킬 → `{{감사 로그}}` → 도메인 1개씩, 감사·구현 각각 승인 후 진행 |
| **C. 버그** | 버그 리포트·`{{테스트 명령}}` 실패 | 재현 → 원인 분리 → 수정 | **`debug`** 스킬 → 로컬 재현 절차 + 프로덕션 전용 재현(EC2 로그·DB) |

### 게이트 (트랙 공통 — 전부 통과)

| 게이트 | Claude Code | 이 프로젝트 |
|--------|-------------|---------|
| **G1 리서치** | `WebFetch`/`WebSearch` · `Agent` 서브에이전트 | 2개 이상 문서 비교면 **`researcher`**(`.claude/agents/researcher.md`), 단일 페이지면 인라인 `WebFetch`. 소스 우선순위·버전 고정은 `core-workflow.md` G1 |
| **G2 승인** | `AskUserQuestion` · 채팅 확인 | A=스펙 승인 · B=감사 문서(`docs/audits/{domain}/{NNN}-*.md`) A/B 항목 승인 · C=원인 가설 승인. 새 이슈·브랜치·PR 생성은 **항상 먼저 확인** |
| **G3 검증** | **`preflight`** 스킬 · `code-review`/`simplify` 서브에이전트 | `{{테스트 명령}}` + 이슈·스펙 체크리스트 + API 변경 시 스펙·`refactor-log.md`에 Before/After 기록(`oasdiff`는 이 저장소에서 쓰지 않는다). 문서 50줄+ 변경·신규 문서면 **`doc-reviewer`**(기준: `doc-writing.md`), Java 3파일+·API·DB 변경이면 **`spring-reviewer`**(기준: `spring-boot-java.md`) |
| **G4 회고** | — | 프로젝트 문서 갱신 점검(승인 후 반영) · `core-followup.md` 💡 후속 제안 · 「다른 이슈로」는 **`defer`** 스킬 · 하네스 개선 후보는 **`retro`** 스킬 · B 트랙은 `refactor-log.md` append |

### 서브에이전트 호출 규약

**에이전트 지침에 이미 적힌 읽기 범위를 호출자가 덮어쓰지 않는다** — 비용을 지배하는 건 모델이 아니라 입력 크기다.

- 대상은 **경로 목록으로만** 넘기고, 어디까지 읽을지는 에이전트 지침에 맡긴다
- "전문을 읽어라"는 **신규 파일에만** 지시한다 (기존 파일은 diff로 충분)
- 대상이 10개를 넘으면 **나눠 부른다**

계기·실측: [`docs/harness/layer2-workflow-skills.md`](../../baro-ai/docs/harness/layer2-workflow-skills.md) "호출 비용" 절

### 그 외

| 상황 | 도구 |
|------|------|
| 독립 작업 병렬 | `Agent` 툴 서브에이전트 (`Explore`, `general-purpose`) — 중복 시 하나만 사용 |
| PR·머지 전(요청 시) · **API·DB 등 Must Have급 변경은 커밋 전 기본 권장** | `code-review` / `simplify` — 둘 다 fresh 서브에이전트 컨텍스트에서 diff만 보고 판단 |

**왜 서브에이전트 컨텍스트인가:** 방금 짠 코드를 같은 대화에서 스스로 리뷰하면 자기 판단을 재확인하는 편향이 생기기 쉽다(생성과 평가를 같은 컨텍스트에서 하면 self-grading 편향). `code-review`/`simplify`는 별도 컨텍스트에서 diff·기준만 보고 판단하므로 이 편향을 줄인다 — 한 줄·단일 파일 핫픽스까지 매번 강제하진 않되, API·DB·다파일 변경처럼 되돌리기 비싼 작업은 커밋 전에 기본으로 돌린다.

버그·테스트 실패 절차(로컬 재현 + 프로덕션 전용 재현)와 TDD 메모는 **`debug`** 스킬로 옮겼다 (2026-08-11, 항상 로드되는 이 파일의 크기를 줄이기 위해 — 상황 트리거라 `paths:` 스코프는 적용 불가, 스킬 방식 채택).

## 프로젝트 규칙과 겹칠 때

- **스펙 SSOT:** API·DB·BR → `.claude/skills/specify/` (Plan Mode로 스펙 형식을 **대체 금지**)
- **Plan Mode vs specify:** 택1. 스펙 **Approved**면 Plan Mode 생략 → 바로 구현
- **서브에이전트:** 독립 조사·병렬 탐색은 `Agent` 툴 (`Explore`, `general-purpose`, 필요 시 `fork`)

## 금지

- Approved 스펙·decisions와 다른 값을 “더 나은 방법”으로 덮어쓰기
- `preflight` 스킬 없이 “테스트 통과”·“이슈 완료” 주장
- Milestone·스펙·이슈 없이 대형 기능 구현 시작
- 트랙을 건너뛰고 게이트만 형식적으로 통과 — 트랙이 정해져야 G2 승인 대상이 무엇인지 결정된다

## 도구 우선순위

```
1. Claude Code 기본 기능
2. OMC (Oh My Claude Code)
3. Superpowers
4. 프로젝트 문서 (.claude/rules/, .claude/skills/)
```

상위 단계로 필요를 충분히 채우면 하위 단계로 내려가지 않는다. OMC·Superpowers는 **서드파티 플러그인**이며 이 저장소에는 설치돼 있지 않다 — 실제로 쓰려면 `/plugin marketplace add`로 먼저 설치해야 하고, 이는 새 의존성을 들이는 결정이므로 사용자 승인 없이 임의로 설치하지 않는다. ("OMC"라는 이름은 서로 무관한 여러 서드파티 프로젝트가 공유하고 있어 공식으로 통용되는 단일 표준이 없다 — 특정 OMC를 지목하지 않는 한 실질적으로 "해당 없음"으로 취급한다.)

### OMC·Superpowers를 채택하는 경우 (전부 만족해야 함)

- 문서를 더 단순하게 만들 수 있다
- 반복 작업을 제거할 수 있다
- 유지보수성을 높인다
- 토큰 사용량 또는 작업량이 감소한다

### 채택하지 않는 경우 (하나라도 해당하면 배제)

- 기능이 있다는 이유만으로 쓴다
- 기존 문서보다 복잡해진다
- 토큰 소비만 증가한다
- Claude Code 기본 기능으로 충분히 해결 가능하다

**이 저장소의 현재 결론 (2026-07-23 감사):** Superpowers가 제공하는 `brainstorming`/`writing-plans`/`executing-plans`는 Plan Mode로, `requesting-code-review`는 이미 설치된 `code-review`/`simplify` 스킬로, `dispatching-parallel-agents`는 `Agent` 툴로 이미 충분히 대체돼 있어 위 배제 조건("Claude Code 기본 기능으로 충분히 해결 가능")에 해당 — 설치하지 않는다. 유일한 gap인 `systematic-debugging`도 **`debug`** 스킬로 충분해 플러그인 설치 비용을 정당화하지 못한다. `test-driven-development`는 오히려 이 저장소의 "유의미한 테스트만" 원칙(`testing.md`)보다 엄격해 **기존 문서보다 복잡해지는** 배제 조건에 해당한다. 상황이 바뀌면(예: 이 gap이 실제로 반복 비용을 유발하면) 이 절을 갱신하고 재평가한다.

## 워크플로 (한 줄)

```
트랙 분류 → G1 리서치 → G2 승인(스펙/감사/가설) → 구현 → G3 검증(test·preflight·doc-reviewer) → G4 회고 → gh issue/PR
```
