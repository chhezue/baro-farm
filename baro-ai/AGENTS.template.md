# {프로젝트 이름}

{한 줄 설명}. AI 에이전트가 작업할 때 참고하는 프로젝트 지도입니다.

> **이 파일은 템플릿입니다.** `AGENTS.md`로 복사한 뒤 `{...}` 자리를 채우고 이 인용문을 지우세요.
> 함께 채울 것: [`.claude/rules/harness-map.md`](.claude/rules/harness-map.md) (슬롯 17개·스택 옵션 7개)
> 채운 예: [`examples/tripfit/AGENTS.md`](examples/tripfit/AGENTS.md)

## How We Build

**0. 문서·구현 정합 (최우선)** — 스펙·결정·문서 간 값·계약이 어긋나면 **구현하지 말고 사용자에게 질문**. 상세: `.claude/rules/core-guardrails.md` ⛔ STOP.
**priority: must/could** — 판단 기준·용어를 임의 재서술하지 말고 SSOT 확인. SSOT: `{{우선순위 SSOT}}` · `.claude/rules/core-scope.md`.

기획·검증 기준을 먼저 고정하고, 그에 맞춰 구현합니다. **계획 축**은 `{{우선순위 SSOT}}`, **기획**은 `{{제품 범위}}`, **기능 설계**는 `{{스펙 저장소}}`, **아키텍처 선택**은 `{{결정 기록}}`에 둡니다. DB·인증·다파일 변경 시 스펙 필수 (`core-workflow` 규칙). 구현 후 `{{테스트 명령}}`과 PR·CI로 검증합니다.

## Tech Stack

- {언어·버전}
- {프레임워크·버전}
- {빌드 도구}
- {DB — 런타임·테스트}
- {테스트 프레임워크}
- {배포}

## Conventions

- 패키지: `{루트 패키지}` — {레이어 구조 한 줄}
- DB/API 네이밍은 기능 추가 시 `{{아키텍처 개요}}` 기준으로 통일
- 주석: {주석 규칙 — 스택 팩이 있으면 그 파일을 가리킬 것}
- 범위 밖 리팩터링·포맷 변경 금지 — 요청된 작업만 수정
- **절대 마음대로 커밋하지 않는다** — 사용자가 명시적으로 요청할 때만 실행
- 목적·주제별로 나눠 **최대 5개**까지 커밋할 수 있다
- **작업이 끝나면 묻지 않아도 커밋 분할안을 먼저 제안한다** (제안 ≠ 실행 — 승인 후 실행) (상세: `.github/CONTRIBUTING.md`, `.claude/rules/core-workflow.md`)
- **문서·스펙·결정 정합 최우선** — 문서 간·문서-구현 간 충돌 시 질문 없이 구현·기본값 변경 금지 (`.claude/rules/core-guardrails.md` ⛔ 섹션)
- **`[미정]` 항목:** 기획 미확정 항목은 해당 문서에 표기만 남김 (`core-scope`)
- **DB:** {마이그레이션 정책 — `harness-map.md`의 "DB 마이그레이션 금지" 옵션과 일치시킬 것}
- **레거시:** 현행 Approved와 다른 코드·호환 레이어·**교체된 구 메서드/상수**는 **같은 PR에서 즉시 삭제** (`core-guardrails` STOP §4)
- 비밀값(`.env`, API 키)은 코드·커밋에 포함하지 않음

## Important Paths

| 경로 | 용도 |
|------|------|
| `{소스 루트}` | 애플리케이션·도메인 코드 |
| `{설정 루트}` | 환경별 설정 |
| `{테스트 루트}` | 단위·통합 테스트 |
| [`.claude/rules/harness-map.md`](.claude/rules/harness-map.md) | **슬롯 SSOT** — 규칙이 부르는 역할 → 실제 경로 |
| [`docs/README.md`](docs/README.md) | **문서 SSOT** |
| [`.claude/rules/README.md`](.claude/rules/README.md) | 규칙·스킬·훅 구조 |
| [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) | **Git SSOT** — 브랜치·커밋·PR |

## Product Context

- **클라이언트**: {누가 무엇으로 붙는지} — `{{클라이언트 전제}}`
- 새 기능 구현 전 `{{제품 범위}}`로 범위 확인
- 용어는 `{{용어집}}` 기준
- DB 설계는 `{{스키마 SSOT}}` 참조

## Workflow

1. **계획** — 큰 기능은 Plan 모드 또는 `specify` 스킬로 `{{스펙 저장소}}`에 스펙 작성
2. **승인** — 스펙 확인 후 구현
3. **실행** — Agent 모드, 필요 시 `Agent` 서브에이전트
4. **검증** — `{{테스트 명령}}` 통과, 변경 범위 최소화

상세: `.claude/rules/core-workflow.md` (3 트랙 × 4 게이트) · 도구 매핑 `core-tools.md`

## Commands

```bash
{최초 셋업}
./scripts/install-git-hooks.sh   # 최초 1회 — pre-commit·commit-msg 훅 설치
{로컬 실행}
{{테스트 명령}}
{{빌드 명령}}
```

## 금기사항

- `git push --force` (main/master)
- `rm -rf` 등 파괴적 shell 명령 (`deny-dangerous-bash.sh` 훅이 차단)
- 테스트 없이 핵심 로직만 추가 (요청이 없는 한)
