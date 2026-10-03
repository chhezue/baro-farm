---
paths:
  - ".claude/agents/**"
  - ".claude/skills/**"
  - ".claude/hooks/**"
  - ".claude/settings*.json"
  - ".claude/rules/README.md"
---

# `.claude/rules` — AI 에이전트 규칙

Claude Code가 이 저장소에서 작업할 때 참조하는 **프로젝트 전용 AI 설정**입니다.

**저장소 구조 (2026-09-04부터):** `baro-farm-be`는 5인이 같이 쓰는 모노레포이고 `.git`은 루트 하나뿐이라, 이 `.claude/`는 **저장소 루트**에 있어야 세션이 어디서 시작되든(=루트에서 열든) 로드된다 — `baro-ai/.claude/`처럼 서브디렉터리에 두면 세션이 루트 기준으로 뜰 때 전혀 로드되지 않는다(2026-09-04 실측: 서브디렉터리 배치 후 세션 스킬·에이전트 목록에 하나도 안 뜸). 다만 규칙 본문·행동 범위는 여전히 **`baro-ai/` 작업 전제**다 — 다른 모듈(`baro-order` 등) 얘기는 아직 없다. 정확한 강제 방식(참조는 자유·**쓰기는 `baro-ai/`+`.claude/`로 제한**)은 [`core-guardrails.md`](core-guardrails.md) STOP §0, 실제 경위는 `harness-map.md` "이 프로젝트 특이사항" 참고.

이 저장소엔 루트 `CLAUDE.md`가 없다 — 프로젝트 지도는 [`baro-ai/AGENTS.md`](../../baro-ai/AGENTS.md) 하나뿐이고, `.claude/`는 **에이전트 행동·워크플로·안전장치**를 담습니다.

이 폴더가 정의하는 "무엇을"의 서술형 총정리(왜 이렇게 됐는지, 실제 인시던트 이력, 수치)는 [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) 참고 — 이 README는 구조 인덱스, 그 문서는 발표·질의 대비용 내러티브다.

## 디렉터리 구조

```
.claude/
├── settings.json          ← PreToolUse 훅 등록 (버전 관리)
├── settings.local.json    ← 개인 권한 allowlist (버전 관리)
├── hooks/
│   ├── deny-out-of-scope-write.sh
│   ├── deny-dangerous-bash.sh
│   ├── deny-db-migration.sh
│   ├── warn-breaking-change.sh
│   └── auto-format-java.sh
├── agents/                ← 서브에이전트 정의 (파일 생성 시 즉시 등록)
│   ├── researcher.md              # G1 외부 문서 조사 전용 (Edit/Write 없음)
│   ├── doc-reviewer.md            # G3 문서 품질 리뷰 전용 (Edit/Write 없음)
│   └── spring-reviewer.md         # G3 Java 변경 리뷰 전용 (Edit/Write 없음)
├── rules/                 ← 상황별 AI 규칙 (.md + paths frontmatter)
│   ├── README.md                  ← 이 파일 (구조·사용법)
│   ├── core-guardrails.md         # ⛔ STOP §0(수정 범위) · §1~§6 · 금지 요약 (always-load)
│   ├── core-workflow.md           # 3 트랙 × 4 게이트 사이클 (always-load)
│   ├── core-scope.md              # priority(must/could)·[미정] 처리 (always-load)
│   ├── core-followup.md           # 후속 제안 · Defer · ERD 제안 (always-load)
│   ├── core-tools.md              # Claude Code 도구 매핑 (always-load)
│   ├── core-reporting.md          # 비전공자용 쉬운 설명 (보고·채팅만, always-load)
│   ├── harness-map.md             # 슬롯·스택 옵션 — 프로젝트가 채우는 유일한 파일 (always-load)
│   ├── baro-ai-performance-refactor.md  # 이 저장소 고유, 한시적 — 현재 이니셔티브(근거 기반 성능 리팩토링) 범위·근거 기준
│   ├── spring-boot-java.md
│   ├── openapi-conventions.md
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
| `core-scope.md` | priority(must/could) 단정 금지 · `[미정]` 문서 표기(중앙 트래커 없음) | 범위·우선순위 |
| `baro-ai-performance-refactor.md` | **이 저장소 고유, 한시적** — 지금 진행 중인 이니셔티브(캐싱·프롬프트 튜닝·비동기 처리 등 근거 기반 성능 리팩토링)의 범위(search/recommend/embedding/event-log/chat, review 제외)와 근거 기준. 이니셔티브 종료 시 갱신·삭제 | **이 저장소 고유** — 다른 프로젝트로 옮길 때 가져가지 않음 |
| `core-followup.md` | 💡 후속 제안 · ✅ Defer 이슈 분리 · 💡 ERD 적극 제안 | 완료 후·범위 미루기 |
| `core-tools.md` | **도구 우선순위**(Claude Code 기본 > OMC > Superpowers > 프로젝트 문서) · **트랙 × 게이트 → 도구** 매핑 | 워크플로 도구 연동·채택 판단 |
| `core-reporting.md` | 사용자 보고(채팅·`refactor-log.md`·완료 요약)는 용어 풀어쓰기·비유 위주로 쉽게. **코드 `//` 주석은 대상 아님**(`spring-boot-java.md` Comments가 SSOT) | 사용자 대상 설명 vs 코드 주석 스타일 분리 |

우선순위: `core-guardrails` ⛔ > `core-workflow` > specify > core-tools > 일반 관례

### Path-scoped (`paths:` frontmatter)

| 파일 | `paths` | 요약 |
|------|---------|------|
| `spring-boot-java.md` | `**/*.java` | 레이어·enum·Entity·**ErrorCode·AOP**·**SOLID/OOP·ACID**·스타일·테스트 |
| `openapi-conventions.md` | `**/*.java` | `@Schema`·`@Operation`·`@Parameter`·`@ApiResponses`(FE용 섹션 템플릿·JWT) — 2026-08-27 `spring-boot-java.md`에서 분리 |
| `java-comments.md` | `**/*.java` | `//`·Javadoc 작성 스타일(역할 줄·다단계 Why·레이어별 초점) — 2026-08-27 `spring-boot-java.md`에서 분리 |
| `testing.md` | `**/*Test.java`, `**/src/test/**` | JUnit 5·프로필·테스트 네이밍 |
| `doc-writing.md` | `**/docs/**/*.md`, `.claude/**/*.md` | 문서 유형(학습·문제해결·참조·설명) → 정보 구조(개요 필수·가치 먼저·제목) → 문장(한 문장 한 생각·메타 담화 제거·용어 일관). **채팅 보고는 `core-reporting.md`, 코드 주석은 `java-comments.md`** — 독자가 달라 겹치지 않음 |
| `README.md`(이 파일) | `agents/**`·`skills/**`·`hooks/**`·`settings*.json`·이 파일 | 구조 인덱스 — 사람이 보는 디렉터리 맵이라 행동 규칙이 아님. **구성 요소를 추가·삭제할 때**(아래 유지보수 체크리스트가 실제로 필요할 때)만 로드된다. 계기·실측: [`docs/harness/layer1-human-gate.md`](../../baro-ai/docs/harness/layer1-human-gate.md) §4-1 |

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
| **훅** | 동작을 접두사로 — 차단 `deny-`, 경고 `warn-`, 자동 실행 `auto-` | `deny-out-of-scope-write.sh`, `deny-db-migration.sh`, `warn-breaking-change.sh`, `auto-format-java.sh` |
| **에이전트** | 역할 명사, 20자 이내 | `spring-reviewer`, `doc-reviewer`, `researcher` |

**개명하지 않는 경우:** 이미 짧고 뜻이 통하면 그대로 둔다. 흔한 영어 단어를 이름으로 쓰면 문서·코드의 일반 용법과 섞여 일괄 치환이 불가능해지므로(`verify`가 Java 60여 파일에 "검증하다"로 존재했다), 새 이름을 고를 때 저장소 전체에서 그 단어가 몇 번 쓰이는지 먼저 센다.

## Skills

에이전트가 **특정 요청**을 받으면 스킬 파일을 읽고 단계를 따른다.

| 스킬 | 트리거 예시 | 산출물 |
|------|-------------|--------|
| `specify` | **A 트랙** — 새 기능, 리팩터 계획, 아키텍처 결정 | `docs/specs/{domain}/{feature}.md` (**스펙 SSOT**, 도메인 amend 시 `ADDED`/`MODIFIED`/`REMOVED` delta 섹션) |
| `safe-refactor` | **B 트랙** — 기존 코드 아키텍처 감사·무손실 리팩토링 (API 계약·비즈니스 로직 불변) | `docs/audits/{domain}/{NNN}-{topic}.md`(A/B/C/D 분류) · `refactor-log.md`(반영 이력) — 도메인 1개씩 순차, 매 단계 승인 게이트 |
| `debug` | **C 트랙** — 버그 리포트·`./gradlew test` 실패 (로컬 재현 + 프로덕션 전용 재현) | 없음(재현·조사 절차) — Superpowers `systematic-debugging` 대체, 승인 게이트 없는 절차형 스킬 |
| `preflight` | **G3 게이트** — "완료/통과" 선언 전, 특히 Must Have급·API·DB 변경 | 없음(검증 절차) — `./gradlew test` + 스펙·이슈 체크리스트 대조 + API 변경 시 `oasdiff` + 문서 50줄+ 변경 시 `doc-reviewer` |
| `defer` | **G4 게이트** — 「다른 이슈로 빼」·「후속 이슈로」·「이번 Milestone 밖」 | Draft 스펙 + Approved 스펙 amend + `docs/specs/README.md` 갱신 + (확인 후) GitHub 이슈 |
| `retro` | **G4 게이트** — 작업 완료 후 회고, 「이번에 배운 거 정리」 | `docs/audits/harness-retro.md` append (승인 후) — 하네스 개선 후보만, 코드·설계 개선은 `core-followup.md` 담당. **메인 컨텍스트 실행**(세션 대화 이력이 입력이라 fork 금지) |

**워크플로:** `트랙 분류 → G1 리서치 → G2 승인 → 구현 → G3 검증 → G4 회고 → gh issue/PR`

## Agents (`agents/`)

트랙과 무관하게 **게이트에서 호출하는 조사·리뷰 전용 서브에이전트**다. 셋 다 `tools` 화이트리스트에서 `Edit`/`Write`를 뺐고, 별도 컨텍스트에서 실행돼 메인 대화의 토큰을 아낀다.

⚠️ **도구 목록이 쓰기를 완전히 막지는 못한다.** 셋 다 `Bash`를 갖고 있어 `sed -i`·리다이렉션으로 파일을 고칠 수 있고, `auto-format-java.sh` 훅은 `Edit|Write` 매처라 Bash 경유 수정을 잡지 못한다. 마지막 한 겹은 각 에이전트 지침의 "수정하지 않는다" 규범이다 — 이 층을 결정론적 차단으로 오해하지 않는다 (2026-09-04 `spring-reviewer` 스모크 테스트에서 발견).

| 에이전트 | 게이트 | 역할 |
|----------|--------|------|
| `researcher` | G1 | 외부 라이브러리·SDK·provider 문서 조사. **로컬 `build.gradle` 버전 확인 → 공식 문서(버전 고정) → 릴리즈 노트** 순서 강제, 블로그·StackOverflow 근거 인용 금지. 결론·근거 URL·문서 버전만 고정 포맷으로 반환 |
| `doc-reviewer` | G3 | 문서 유형·정보 구조·문장 3단계 리뷰 (기준: `doc-writing.md`). advisory — 커밋을 막지 않음 |
| `spring-reviewer` | G3 | Java 변경 diff를 트랜잭션 경계·N+1·하네스 계약(ErrorCode·`@TripActivity`·트레일러)·레이어 재사용·캡슐화 5축으로 리뷰 (기준: `spring-boot-java.md`). Critical/High/Medium/Low 4등급 + `파일:줄` 강제. **ArchUnit이 이미 검증하는 규칙은 지적 대상에서 제외** |

**호출 기준:** 2개 이상 문서를 비교해야 하면 `researcher`, 단일 페이지면 인라인 `WebFetch`. 새 문서·50줄+ 문서 변경이면 `doc-reviewer`, 오타 수정이면 생략. Java를 3파일 이상·API·DB 범위로 고쳤으면 커밋 전 `spring-reviewer` — 범용 `code-review` 스킬과 **대체 관계가 아니다**(그쪽은 언어 무관 일반 결함, 이쪽은 이 저장소의 Spring·JPA·하네스 계약).

상세: `.claude/rules/core-tools.md`

템플릿·참고 문서는 `skills/{name}/references/`에 둔다.

## Hooks (`settings.json` + `hooks/`)

| 이벤트 | 매처 | 현재 동작 |
|--------|------|-----------|
| `PreToolUse` | `Bash` | `deny-dangerous-bash.sh` — force push, `rm -rf`, `git reset --hard`, `docker compose down -v` 차단(exit 2, fail-closed). `core-guardrails.md` STOP §3이 dev DB 폐기를 허용하는 것과 충돌하지 않는다 — 정책상 허용이지만 되돌릴 수 없어 **사람이 직접 실행**하게 하는 의도된 마찰이다. **알려진 오탐:** 패턴을 명령 문자열 전체에서 찾으므로 위 문구를 인자로 담은 `grep`·`echo` 같은 무해한 명령도 함께 막힌다(2026-09-04 실제 발생). 문구를 쪼개 쓰거나 파일 편집 도구를 쓰고, 훅을 우회하지 않는다 |
| `PreToolUse` | `Bash` | `warn-breaking-change.sh` — `git commit`에 DTO/ErrorCode/Controller 변경이 스테이징됐는데 `Breaking-Change-Reason:` 트레일러가 없으면 advisory 경고(항상 exit 0, 커밋을 막지 않음) |
| `PreToolUse` | `Write\|Edit` | `deny-out-of-scope-write.sh` — `baro-ai/`·`.claude/` 밖 파일 쓰기 차단(exit 2, fail-closed) — `core-guardrails.md` STOP §0. 5인 공용 모노레포에서 다른 모듈에 영향 없이 하네스를 저장소 루트에 둘 수 있게 하는 핵심 백스톱 |
| `PreToolUse` | `Write\|Edit` | `deny-db-migration.sh` — `baro-ai/` 안 `db/migration/` 경로 또는 Flyway 네이밍(`V1__x.sql`, `R__x.sql`) 파일 생성 차단(exit 2, fail-closed) — `core-guardrails.md` STOP §3. `baro-ai/` 밖은 위 훅이 이미 막으므로 대상 아님(방어적 재확인만) |
| `PostToolUse` | `Edit\|Write` | `auto-format-java.sh` — Java 파일 저장 시 `spotlessApply` 자동 포맷(non-blocking) |

**agent-type 훅 관련 교훈:** `warn-breaking-change.sh`는 처음엔 `agent`-type(서브에이전트가 diff를 읽고 판단)으로 시도했으나, staged 아닌 working tree 변경까지 오판해 "절대 막지 마라"는 명시적 지시에도 커밋을 막는 사고가 있었다 — non-blocking을 LLM 판단에 맡기지 않고 `command`-type(exit code로 결정론적 통제)으로 확정했다. advisory-only 훅은 command-type을 기본으로 한다.

## `settings.json` / `settings.local.json`

`settings.json`은 훅 등 팀 공통 설정으로 버전 관리한다. `settings.local.json`은 개인 권한 allowlist — **전역 `~/.config/git/ignore`로 gitignore돼 있어 실제로는 이 머신에만 있고 커밋되지 않는다**(2026-08-27 확인). 팀원 간 공유되지 않으므로 개인별로 자유롭게 정리해도 된다.

## CLAUDE.md / AGENTS.md와의 관계

```
AGENTS.md          → 무엇을, 어디서 찾는지 (프로젝트 지도) — 이 저장소엔 CLAUDE.md 없이 이 파일 하나뿐
docs/README.md     → 기획·아키텍처·스펙 문서 인덱스
deploy/README.md   → Docker·EC2 배포
.dev/README.md     → 임시 세션 로그 (장기 문서는 docs/로)
.claude/rules/      → 어떻게 코딩·배포·검증하는지 (행동 규칙)
  core-guardrails / core-workflow / core-scope / core-followup
  core-tools / core-reporting / harness-map
.claude/skills/    → 큰 작업의 단계별 절차 (specify = 스펙 SSOT)
docs/specs/        → 기능별 설계 산출물 (specify 스킬 결과)
```

## 유지보수 체크리스트

- [ ] 새 도메인 enum·상태 추가 시 `docs/product/glossary.md` 동기화
- [ ] 우선순위·`[미정]` 규칙 변경 → `core-scope.md`만 · 이 저장소 고유 릴리즈 사실 → `{프로젝트}-release.md`만 (`core-*`에 중복 금지)
- [ ] 후속·Defer·ERD 제안 규칙 변경 → `core-followup.md`만
- [ ] 반복되는 코드 리뷰 코멘트 → 해당 `rules/*.md`에 한 줄 규칙으로 승격
- [ ] 위험 명령 패턴 추가 필요 시 `hooks/deny-dangerous-bash.sh` + `settings.json` matcher 동시 수정
- [ ] 훅 추가·삭제 시 이 README **디렉터리 구조 다이어그램 + Hooks 절 표**, [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) §5 표, [`docs/harness/layer3-deterministic-hooks.md`](../../baro-ai/docs/harness/layer3-deterministic-hooks.md)의 훅 표·흐름까지 **4곳** 동시 갱신 (이번 감사에서 이 README 자체의 디렉터리 다이어그램이 훅 1개 누락된 채 방치됐던 사례 있음 — 같은 파일 안에서도 표와 다이어그램이 따로 놀 수 있으니 둘 다 확인)
- [ ] 규칙·스킬 개수가 바뀌면(추가/삭제) 이 README **Always-load 표 + Skills 표**, [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) §3·§4의 대응 표, [`docs/harness/layer1-human-gate.md`](../../baro-ai/docs/harness/layer1-human-gate.md)(규칙)·[`docs/harness/layer2-workflow-skills.md`](../../baro-ai/docs/harness/layer2-workflow-skills.md)(스킬)의 파일 표를 함께 갱신 (harness-engineering.md·docs/harness/는 발표·질의 대비용 서술형 문서라 별도 유지되며, 내용이 어긋나면 이 README가 맞음 — 하지만 방치하면 발표 자료가 stale해짐)
- [ ] 서브에이전트(`agents/*.md`) 추가·삭제 시 이 README **디렉터리 구조 다이어그램 + Agents 절 표**, [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) §4, [`docs/harness/layer2-workflow-skills.md`](../../baro-ai/docs/harness/layer2-workflow-skills.md) 서브에이전트 문단을 함께 갱신. **에이전트는 파일을 만들면 같은 세션에서 즉시 등록**된다(2026-09-03 실측 정정 — 이전에는 "세션 시작 시 등록"으로 잘못 기재돼 있었음) — 첫 호출이 드물게 실패하면 잠시 뒤 재시도하거나 새 세션에서 확인
- [ ] 레이어·PK 등 구조 규칙 변경 시 ArchUnit 구조 테스트(ArchUnit) 반영 검토 — 일부 규칙은 prose가 아니라 `./gradlew test`가 실제로 검증함
