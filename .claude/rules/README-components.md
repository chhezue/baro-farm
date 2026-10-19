---
paths:
  - ".claude/agents/**"
  - ".claude/skills/**"
  - ".claude/hooks/**"
  - ".claude/settings*.json"
  - ".claude/rules/README.md"
  - ".claude/rules/README-components.md"
---

# `.claude/rules` — 구성 요소 (Skills · Agents · Hooks · 유지보수)

디렉터리 구조·파일별 역할·Rules(규칙 목록)·작명 규칙은 [`README.md`](README.md) — 여기는 Skills·Agents·Hooks·`settings.json`·`CLAUDE.md`/`AGENTS.md`와의 관계·유지보수 체크리스트를 다룬다.

## Skills

에이전트가 **특정 요청**을 받으면 스킬 파일을 읽고 단계를 따른다.

| 스킬 | 트리거 예시 | 산출물 |
|------|-------------|--------|
| `specify` | **A 트랙** — 새 기능, 리팩터 계획, 아키텍처 결정 | `docs/specs/{domain}/{feature}.md` (**스펙 SSOT**, 도메인 amend 시 `ADDED`/`MODIFIED`/`REMOVED` delta 섹션) |
| `safe-refactor` | **B 트랙** — 기존 코드 아키텍처 감사·무손실 리팩토링 (API 계약·비즈니스 로직 불변) | `docs/audits/{domain}/{NNN}-{topic}.md`(A/B/C/D 분류) · `refactor-log.md`(반영 이력) — 도메인 1개씩 순차, 매 단계 승인 게이트 |
| `debug` | **C 트랙** — 버그 리포트·`./gradlew test` 실패 (로컬 재현 + 프로덕션 전용 재현) | 없음(재현·조사 절차) — Superpowers `systematic-debugging` 대체, 승인 게이트 없는 절차형 스킬 |
| `preflight` | **G3 게이트** — "완료/통과" 선언 전, 특히 Must Have급·API·DB 변경 | 없음(검증 절차) — `{{테스트 명령}}` + 스펙·이슈 체크리스트 대조 + API 변경 시 Before/After 기록 확인 + 문서 50줄+ 변경 시 `doc-reviewer` |
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
| `spring-reviewer` | G3 | Java 변경 diff를 외부 호출 경계·비동기 유실·하네스 계약(ErrorCode)·레이어 재사용·캡슐화 5축으로 리뷰 (기준: `spring-boot-java.md`·`spring-boot-java-design.md`). Critical/High/Medium/Low 4등급 + `파일:줄` 강제. **baro-ai에는 ArchUnit이 없어 구조 규칙도 리뷰 대상이다** |

**호출 기준:** 2개 이상 문서를 비교해야 하면 `researcher`, 단일 페이지면 인라인 `WebFetch`. 새 문서·50줄+ 문서 변경이면 `doc-reviewer`, 오타 수정이면 생략. Java를 3파일 이상·API·DB 범위로 고쳤으면 커밋 전 `spring-reviewer` — 범용 `code-review` 스킬과 **대체 관계가 아니다**(그쪽은 언어 무관 일반 결함, 이쪽은 이 저장소의 Spring·JPA·하네스 계약).

상세: `.claude/rules/core-tools.md`

템플릿·참고 문서는 `skills/{name}/references/`에 둔다.

## Hooks (`settings.json` + `hooks/`)

| 이벤트 | 매처 | 현재 동작 |
|--------|------|-----------|
| `PreToolUse` | `Bash` | `deny-dangerous-bash.sh` — force push, `rm -rf`, `git reset --hard`, `docker compose down -v` 차단(exit 2, fail-closed). `core-guardrails.md` STOP §3이 dev DB 폐기를 허용하는 것과 충돌하지 않는다 — 정책상 허용이지만 되돌릴 수 없어 **사람이 직접 실행**하게 하는 의도된 마찰이다. **알려진 오탐:** 패턴을 명령 문자열 전체에서 찾으므로 위 문구를 인자로 담은 `grep`·`echo` 같은 무해한 명령도 함께 막힌다(2026-09-04 실제 발생). 문구를 쪼개 쓰거나 파일 편집 도구를 쓰고, 훅을 우회하지 않는다 |
| — | — | ~~`warn-breaking-change.sh`~~ — **`settings.json`에 등록돼 있지 않아 동작하지 않는다.** 파일만 남아 있다(`harness-map.md`의 "OpenAPI 계약 보호" 옵션이 꺼져 있어 도입 보류) |
| `PreToolUse` | `Write\|Edit` | `deny-out-of-scope-write.sh` — `baro-ai/`·`.claude/` 밖 파일 쓰기 차단(exit 2, fail-closed) — `core-guardrails.md` STOP §0. 5인 공용 모노레포에서 다른 모듈에 영향 없이 하네스를 저장소 루트에 둘 수 있게 하는 핵심 백스톱. 저장소 밖 절대경로(Claude Code 세션 파일 등)는 이 하네스의 관심사가 아니므로 통과시킨다(2026-09-04 수정 — 이전엔 경로 접두사 제거 실패를 감지 못해 세션 파일까지 오탐 차단했다) |
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
- [ ] 훅 추가·삭제 시 이 README(`README.md`) **디렉터리 구조 다이어그램 + Hooks 절 표**, [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) §5 표, [`docs/harness/layer3-deterministic-hooks.md`](../../baro-ai/docs/harness/layer3-deterministic-hooks.md)의 훅 표·흐름까지 **4곳** 동시 갱신 (이번 감사에서 이 README 자체의 디렉터리 다이어그램이 훅 1개 누락된 채 방치됐던 사례 있음 — 같은 파일 안에서도 표와 다이어그램이 따로 놀 수 있으니 둘 다 확인)
- [ ] 규칙·스킬 개수가 바뀌면(추가/삭제) `README.md` **Always-load 표 + Skills 표**, [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) §3·§4의 대응 표, [`docs/harness/layer1-human-gate.md`](../../baro-ai/docs/harness/layer1-human-gate.md)(규칙)·[`docs/harness/layer2-workflow-skills.md`](../../baro-ai/docs/harness/layer2-workflow-skills.md)(스킬)의 파일 표를 함께 갱신 (harness-engineering.md·docs/harness/는 발표·질의 대비용 서술형 문서라 별도 유지되며, 내용이 어긋나면 `README.md`가 맞음 — 하지만 방치하면 발표 자료가 stale해짐)
- [ ] 서브에이전트(`agents/*.md`) 추가·삭제 시 `README.md` **디렉터리 구조 다이어그램 + Agents 절 표**, [`docs/harness-engineering.md`](../../baro-ai/docs/harness-engineering.md) §4, [`docs/harness/layer2-workflow-skills.md`](../../baro-ai/docs/harness/layer2-workflow-skills.md) 서브에이전트 문단을 함께 갱신. **에이전트는 파일을 만들면 같은 세션에서 즉시 등록**된다(2026-09-03 실측 정정 — 이전에는 "세션 시작 시 등록"으로 잘못 기재돼 있었음) — 첫 호출이 드물게 실패하면 잠시 뒤 재시도하거나 새 세션에서 확인
- [ ] 레이어·PK 등 구조 규칙 변경 시 ArchUnit 구조 테스트(ArchUnit) 반영 검토 — 일부 규칙은 prose가 아니라 `./gradlew test`가 실제로 검증함
