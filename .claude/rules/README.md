---
paths:
  - ".claude/agents/**"
  - ".claude/skills/**"
  - ".claude/hooks/**"
  - ".claude/settings*.json"
  - ".claude/rules/README.md"
  - ".claude/rules/README-components.md"
---

# `.claude/rules` — AI 에이전트 규칙

Claude Code가 이 저장소에서 작업할 때 참조하는 **프로젝트 전용 AI 설정**입니다.

**저장소 구조 (2026-09-04부터):** `baro-farm-be`는 5인이 같이 쓰는 모노레포이고 `.git`은 루트 하나뿐이라, 이 `.claude/`는 **저장소 루트**에 있어야 세션이 어디서 시작되든(=루트에서 열든) 로드된다 — `baro-ai/.claude/`처럼 서브디렉터리에 두면 세션이 루트 기준으로 뜰 때 전혀 로드되지 않는다(2026-09-04 실측: 서브디렉터리 배치 후 세션 스킬·에이전트 목록에 하나도 안 뜸). 다만 규칙 본문·행동 범위는 여전히 **`baro-ai/` 작업 전제**다 — 다른 모듈(`baro-order` 등) 얘기는 아직 없다. 정확한 강제 방식(참조는 자유·**쓰기는 `baro-ai/`+`.claude/`로 제한**)은 [`core-guardrails.md`](core-guardrails.md) STOP §0, 실제 경위는 `harness-map.md` "이 프로젝트 특이사항" 참고.

이 저장소엔 루트 `CLAUDE.md`가 없다 — 프로젝트 지도는 [`baro-ai/AGENTS.md`](../../baro-ai/AGENTS.md) 하나뿐이고, `.claude/`는 **에이전트 행동·워크플로·안전장치**를 담습니다.

이 폴더가 정의하는 "무엇을"의 서술형 총정리(왜 이렇게 됐는지, 실제 인시던트 이력, 수치)는 [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) 참고 — 이 README는 구조 인덱스, 그 문서는 발표·질의 대비용 내러티브다.

이 파일은 디렉터리 구조·파일별 역할·Rules 목록·작명 규칙을 다룬다. Skills·Agents·Hooks·`settings.json`·유지보수 체크리스트는 [`README-components.md`](README-components.md) 참고.

## 디렉터리 구조

```
.claude/
├── settings.json          ← PreToolUse 훅 등록 (버전 관리)
├── settings.local.json    ← 개인 권한 allowlist (버전 관리)
├── hooks/
│   ├── deny-out-of-scope-write.sh
│   ├── deny-dangerous-bash.sh
│   ├── deny-db-migration.sh
│   ├── warn-breaking-change.sh   # 미등록 — settings.json에 없어 동작하지 않는다
│   └── auto-format-java.sh
├── agents/                ← 서브에이전트 정의 (파일 생성 시 즉시 등록)
│   ├── researcher.md              # G1 외부 문서 조사 전용 (Edit/Write 없음)
│   ├── doc-reviewer.md            # G3 문서 품질 리뷰 전용 (Edit/Write 없음)
│   └── spring-reviewer.md         # G3 Java 변경 리뷰 전용 (Edit/Write 없음)
├── rules/                 ← 상황별 AI 규칙 (.md + paths frontmatter)
│   ├── README.md                  ← 이 파일 (구조·사용법)
│   ├── README-components.md       # Skills·Agents·Hooks·유지보수 체크리스트 (README.md 분리분, path-scoped)
│   ├── core-guardrails.md         # ⛔ STOP §0(수정 범위) · §1~§6 · 금지 요약 (always-load)
│   ├── core-workflow.md           # 3 트랙 × 4 게이트 사이클 (always-load)
│   ├── core-workflow-git.md       # 브랜치·커밋·PR·이슈 컨벤션 (core-workflow.md 분리분, always-load)
│   ├── core-scope.md              # priority(must/could)·[미정] 처리 (always-load)
│   ├── core-followup.md           # 후속 제안 · Defer · ERD 제안 (always-load)
│   ├── core-tools.md              # Claude Code 도구 매핑 (always-load)
│   ├── core-reporting.md          # 비전공자용 쉬운 설명 (보고·채팅만, always-load)
│   ├── harness-map.md             # 슬롯·스택 옵션 — 프로젝트가 채우는 유일한 파일 (always-load)
│   ├── baro-ai-performance-refactor.md  # 이 저장소 고유, 한시적 — 현재 이니셔티브(근거 기반 성능 리팩토링) 범위·근거 기준
│   ├── spring-boot-java.md
│   ├── spring-boot-java-design.md # SOLID/OOP·ACID·Style·Tests (spring-boot-java.md 분리분)
│   ├── openapi-conventions.md
│   ├── openapi-conventions-operation.md # @Schema·@Operation 상세 (openapi-conventions.md 분리분)
│   ├── java-comments.md
│   ├── testing.md
│   ├── doc-writing.md             # 문서 작성 (docs/·.claude/ 마크다운, path-scoped)
└── skills/                ← 반복 워크플로 스킬
    ├── specify/
    │   └── SKILL.md            # 스펙 템플릿은 baro-ai/docs/templates/reference-doc.md
    ├── safe-refactor/
    │   ├── SKILL.md            # 감사 템플릿은 baro-ai/docs/templates/how-to-doc.md
    │   └── references/
    │       └── audit-checklist.md
    ├── preflight/
    │   └── SKILL.md
    ├── defer/
    │   └── SKILL.md
    ├── debug/
    │   └── SKILL.md
    └── retro/
        └── SKILL.md
```

## 파일별 역할

| 경로 | 역할 | 적용 시점 |
|------|------|-----------|
| `settings.json` | Bash 실행 전 등 **이벤트 → 훅 스크립트** 매핑 | 에이전트가 도구를 호출하기 직전 |
| `hooks/*.sh` | 훅 본문 — 위험 명령 차단 등 | `settings.json`이 지정한 이벤트 |
| `rules/*.md` (frontmatter 없음) | **항상** 로드되는 코딩·도메인 규칙 | 세션 시작 시 |
| `rules/*.md` (`paths:` frontmatter) | **glob에 매칭되는 파일**을 읽을 때만 로드되는 규칙 | 해당 파일 접근 시 |
| `skills/*/SKILL.md` | 다단계 워크플로 (스펙 작성 등) | 에이전트가 해당 작업을 인식할 때 |
| `agents/*.md` | 서브에이전트 정의 (frontmatter의 `tools`·`model` + 본문=시스템 프롬프트) | **파일을 만들면 등록** — 첫 호출이 실패하면 잠시 뒤 재시도하거나 새 세션에서 확인 |

## Rules (`rules/`)

`.md` = Markdown + YAML frontmatter(`paths:`). `paths`가 없으면 세션 시작 시 항상 로드되고, 있으면 매칭 파일을 읽을 때만 로드된다 (Cursor `.mdc`의 `globs`/`alwaysApply`에 대응).

### Always-load (하네스)

| 파일 | 요약 | SSOT 범위 |
|------|------|-----------|
| `core-guardrails.md` | ⛔ **수정 범위(`baro-ai/`+`.claude/` 밖 금지)** · 문서 정합 · ErrorCode/AOP · DB 마이그레이션 금지 · **레거시(교체=같은 PR 삭제)** · **API Breaking-Change-Reason 트레일러** · `how-it-works.md` 갱신 + 금지 요약 표 | **하지 말 것 (STOP §0~§6)** |
| `core-workflow.md` | 진입(트랙 분류) · **3 트랙 × 4 게이트 사이클** · 구현 중 지킬 것 | **어떤 순서로 할 것** |
| `core-workflow-git.md` | Git 브랜치·커밋 메시지·PR·이슈 컨벤션 — 2026-09-04 `core-workflow.md`에서 분리 | **Git 컨벤션** |
| `core-scope.md` | priority(must/could) 단정 금지 · `[미정]` 문서 표기(중앙 트래커 없음) | 범위·우선순위 |
| `baro-ai-performance-refactor.md` | **이 저장소 고유, 한시적** — 지금 진행 중인 이니셔티브(캐싱·프롬프트 튜닝·비동기 처리 등 근거 기반 성능 리팩토링)의 범위(작업 단위 4개: 취향 벡터·장바구니 레시피·임베딩 유사도 검색·키워드 검색 + cross-cutting, review 제외)와 근거 기준. 이니셔티브 종료 시 갱신·삭제 | **이 저장소 고유** — 다른 프로젝트로 옮길 때 가져가지 않음 |
| `core-followup.md` | 💡 후속 제안 · ✅ Defer 이슈 분리 · 💡 ERD 적극 제안 | 완료 후·범위 미루기 |
| `core-tools.md` | **도구 우선순위**(Claude Code 기본 > OMC > Superpowers > 프로젝트 문서) · **트랙 × 게이트 → 도구** 매핑 | 워크플로 도구 연동·채택 판단 |
| `core-reporting.md` | 사용자 보고(채팅·`refactor-log.md`·완료 요약)는 용어 풀어쓰기·비유 위주로 쉽게. **코드 `//` 주석은 대상 아님**(`spring-boot-java.md` Comments가 SSOT) | 사용자 대상 설명 vs 코드 주석 스타일 분리 |

우선순위: `core-guardrails` ⛔ > `core-workflow` > specify > core-tools > 일반 관례

### Path-scoped (`paths:` frontmatter)

| 파일 | `paths` | 요약 |
|------|---------|------|
| `spring-boot-java.md` | `**/*.java` | 패키지 구조·레이어·Entity·Enum·**ErrorCode·AOP**·표기규칙·네이밍 원칙 |
| `spring-boot-java-design.md` | `**/*.java` | **SOLID/OOP·ACID/트랜잭션 경계**·스타일·API 추가 시·Configuration·Tests — 2026-09-04 `spring-boot-java.md`에서 분리 |
| `openapi-conventions.md` | `**/*.java` | 설명 문자열 어노테이션 공통 규칙(전 어노테이션 공통 금지·허용) — 2026-08-27 `spring-boot-java.md`에서 분리 |
| `openapi-conventions-operation.md` | `**/*.java` | `@Schema`(Entity·DTO·enum)·`@Operation`·`@Parameter`·`@ApiResponses`(FE용 섹션 템플릿·JWT) — 2026-09-04 `openapi-conventions.md`에서 분리 |
| `java-comments.md` | `**/*.java` | `//`·Javadoc 작성 스타일(역할 줄·다단계 Why·레이어별 초점) — 2026-08-27 `spring-boot-java.md`에서 분리 |
| `testing.md` | `**/*Test.java`, `**/src/test/**` | JUnit 5·프로필·테스트 네이밍 |
| `doc-writing.md` | `**/docs/**/*.md`, `.claude/**/*.md` | 문서 유형(학습·문제해결·참조·설명) → 정보 구조(개요 필수·가치 먼저·제목) → 문장(한 문장 한 생각·메타 담화 제거·용어 일관). **채팅 보고는 `core-reporting.md`, 코드 주석은 `java-comments.md`** — 독자가 달라 겹치지 않음 |
| `README.md`(이 파일) | `agents/**`·`skills/**`·`hooks/**`·`settings*.json`·이 파일·`README-components.md` | 구조 인덱스 — 사람이 보는 디렉터리 맵이라 행동 규칙이 아님. **구성 요소를 추가·삭제할 때**(유지보수 체크리스트가 실제로 필요할 때)만 로드된다. 계기·실측: [`docs/harness/layer1-human-gate.md`](../../baro-ai/docs/harness/layer1-human-gate.md) §4-1 |
| `README-components.md` | 위와 동일 | Skills·Agents·Hooks·`settings.json`·CLAUDE.md 관계·유지보수 체크리스트 — 2026-09-04 `README.md`에서 분리 |

### 규칙 추가·분리 가이드

1. **한 규칙 = 한 관심사** (코어 하네스 ~120줄, 형제 ~70줄 권장)
2. 전역 STOP → `core-guardrails` · 작업 순서 → `core-workflow` (둘 다 frontmatter 없음, always-load)
3. 우선순위·`[미정]` → `core-scope` · 후속/Defer/ERD → `core-followup` (**중복 금지**, 링크만)
4. 파일 타입별 → `paths:` frontmatter
5. 반복 실수 → 해당 규칙에 짧게 추가

## 작명 규칙

구성요소를 추가할 때 이름을 정하는 기준이다. 2026-09-04 개명(`#128`)에서 확정했다 — 그 전에는 동어반복(`debug-bug`)·내부 은어(`harness-*`)·29자짜리 이름이 한 폴더에 섞여 있었다.

| 종류 | 규칙 | 예 |
|------|------|-----|
| **스킬** | 짧은 **동사** 하나 | `spec`이 아니라 `specify`, `preflight`, `defer`, `debug` |
| **규칙** | **적용 시점·대상이 드러나는 명사구.** 프로젝트 무관은 `core-` 접두사, 이 저장소 고유는 접두사 없이 대상을 밝힌다 | `core-guardrails`, `harness-map`, `baro-ai-performance-refactor` |
| **훅** | 동작을 접두사로 — 차단 `deny-`, 경고 `warn-`, 자동 실행 `auto-` | `deny-out-of-scope-write.sh`, `deny-db-migration.sh`, `warn-breaking-change.sh`(미등록), `auto-format-java.sh` |
| **에이전트** | 역할 명사, 20자 이내 | `spring-reviewer`, `doc-reviewer`, `researcher` |

**개명하지 않는 경우:** 이미 짧고 뜻이 통하면 그대로 둔다. 흔한 영어 단어를 이름으로 쓰면 문서·코드의 일반 용법과 섞여 일괄 치환이 불가능해지므로(`verify`가 Java 60여 파일에 "검증하다"로 존재했다), 새 이름을 고를 때 저장소 전체에서 그 단어가 몇 번 쓰이는지 먼저 센다.

## 나머지 구성 요소

Skills·Agents·Hooks·`settings.json`·`CLAUDE.md`/`AGENTS.md`와의 관계·유지보수 체크리스트 → [`README-components.md`](README-components.md) 참고
