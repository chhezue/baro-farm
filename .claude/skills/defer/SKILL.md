---
name: defer
description: 사용자가 「다른 이슈로 빼」·「후속 이슈로」·「이번 Milestone 밖」 등으로 범위를 미루라고 할 때, Draft 스펙 작성부터 이슈 생성까지 같은 턴에 수행한다.
---

# Defer Follow-up Workflow

이번 요청 범위에서 일부를 **다음 이슈·다음 Milestone으로 미룰 때** 쓰는 스킬. 이슈만 만들고 끝내면 문서·구현·트래킹이 어긋나므로, 아래 단계를 **같은 턴**에 전부 수행한다.

`specify`가 "구현 전 스펙"을, `safe-refactor`이 "감사 전 승인"을 강제하듯, 이 스킬은 "범위를 뺄 때도 문서화 없이 이슈만 던지지 않기"를 강제한다.

## When to Use

- 사용자가 「다른 이슈로 빼」, 「후속 이슈로」, 「이번 Milestone 밖」 등으로 범위를 미루라고 지시할 때
- Must Have급 구현 완료 후 발견한 개선점을 별도 이슈로 분리할 때 (`core-followup.md` 💡 후속 제안과는 별개 — 제안 자체가 아니라 **실제로 분리하기로 확정**된 경우)

## Steps

1. **`{{스펙 저장소}}/{kebab-case}.md`** — Draft 스펙 작성 (`specify` 템플릿 축약 가능). Must Have·완료 기준·선행·Out of Scope 포함
2. **관련 Approved 스펙** — `deferred:` 헤더 또는 Out of Scope 표에 **스펙 경로 + `#n` 링크** 추가 · 본문에서 lazy/임시 구현을 **`#n` 위임**으로 명시
3. **`{{스펙 저장소}}`의 README** — 도메인 폴더 표·이슈 매핑에 행 추가
4. **이슈 생성 — ⚠️ 먼저 사용자에게 확인** — `gh issue create`는 새 이슈 생성이므로 `core-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절이 적용된다. 사용자가 이 스킬을 트리거하며 이미 "이슈 만들어줘"까지 명시했다면 그대로 진행, 범위 분리만 요청했다면 이슈 생성 여부를 짧게 재확인한 뒤 진행.
   **⚠️ 중요 (이슈 템플릿 준수):** 이슈 생성 시 반드시 `.github/CONTRIBUTING.md`와 `.github/ISSUE_TEMPLATE/`의 양식을 준수하여 다음 항목을 정확히 포함할 것:
   - 제목: `[Type] 한글 설명` 형식 (예: `[Feat]`, `[Chore]`)
   - 메타데이터: `gh issue create --label "kind:..." --label "priority:..." --milestone "..."` 플래그를 사용하여 라벨과 마일스톤 필수 지정
   - 본문: 템플릿에 명시된 섹션(한 줄 요약, 배경, 목표, 선행 이슈, 완료 기준 등) 준수 및 스펙 경로 포함
5. **현재 구현** — 해당 범위 코드·Must 체크리스트에서 제거 또는 **최소 C(임시)**로 두고 `#n`·스펙 URL 주석
6. **사용자 보고** — 이슈 URL(생성한 경우) + 스펙 경로

## 금지

- 이슈만 생성하고 `docs/` 미작성
- Approved 스펙 amend 없이 lazy 동작만 남기기
- 사용자 확인 없이 4단계에서 바로 `gh issue create` 실행 (`core-workflow.md` 위반)
