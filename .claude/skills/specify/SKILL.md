---
name: specify
description: 요구사항을 정리하고 docs/specs/에 스펙 파일을 생성한다. 기능 추가, 리팩터링 계획, 아키텍처 결정이 필요할 때 사용.
---

# Specify Workflow

"바로 구현" 대신 **문서 → 승인 → 코드** 순서를 강제하는 계획 스킬입니다.

**이 스킬은 `core-workflow.md` 사이클의 A 트랙**이며, 아래 Steps는 **G1 리서치 → G2 승인**까지를 담당합니다. 승인 이후의 구현·G3 검증·G4 회고는 `core-workflow.md`가 SSOT입니다.

## When to Use

- 새 REST API·엔티티·인증 흐름
- DB 스키마 변경 (엔티티·컬럼·관계)
- 3개 이상 파일에 걸친 리팩터
- BR-* / PRD 해석이 엇갈릴 때

**생략 가능:** 오타, 단일 테스트, 로그 한 줄, 명확한 버그 핫픽스.

## Steps

1. **의도 정리** — 사용자 요청을 한 문단으로 미러링
2. **범위 확인** — `{{제품 범위}}`에 포함되는지 표시
3. **클라이언트 전제** — API·인증·푸시·링크면 `{{클라이언트 전제}}`와 충돌 없는지
4. **모호함 해소** — 불명확하면 `AskUserQuestion` (API shape, 권한, 엣지 케이스)
5. **외부 리서치 (G1)** — 외부 라이브러리·SDK·provider 스펙에 의존하는 설계면 **스펙을 쓰기 전에** 확인한다. 2개 이상 문서를 비교해야 하면 `researcher` 서브에이전트, 단일 페이지면 인라인 `WebFetch`. 확인한 URL·문서 버전은 스펙 본문에 근거로 남긴다 — 상세 기준은 `core-workflow.md` G1
6. **스펙 작성** — `{{스펙 저장소}}/{kebab-case}.md` ([템플릿](references/spec-template.md), 도메인 폴더는 아래 "Spec Naming" 참고). 문서 자체의 작성 기준은 `doc-writing.md`
7. **충돌 검토** — `{{아키텍처 개요}}`, `erd.md`, 기존 specs
8. **승인 대기 (G2)** — 구현 시작 전 사용자 OK

## Spec Naming

- kebab-case: `trip-room-create.md`, `user-social-login.md`
- 한 파일 = 한 기능 또는 한 리팩터 단위
- 대형 기능은 `trip-schedule-phase1.md`처럼 단계 분리
- **도메인 폴더** — `{{스펙 저장소}}`의 README의 폴더↔패키지 매핑 표 기준으로 저장 위치를 정한다: `auth/`, `user/`(+googlecalendar), `user-schedule/`, `trip/`(recommendation 포함), `notification/`, 도메인 무관은 `cross-cutting/`. 두 도메인에 걸치면 **주로 바뀌는 상태가 속한 도메인** 기준으로 정한다 — 애매하면 사용자에게 한 줄로 확인

## Spec Must Include

- Must Have / Nice to Have (체크리스트)
- 영향 받는 BR-* 번호 (없으면 "해당 없음")
- API 표 또는 "API 없음" (앱·React 클라이언트 계약 — `platform.md` 참고)
- 완료 기준 (`{{테스트 명령}}`, 수용 조건)
- `[미정]` 항목과 결정 필요자
- **기존 Approved 스펙을 amend하는 경우**: `ADDED`/`MODIFIED`/`REMOVED` delta 섹션 필수(템플릿 참고) — `REMOVED`는 core-guardrails.md STOP §4에 따라 같은 PR에서 실제 삭제 대상

## Output (사용자에게)

```
📄 docs/specs/{domain}/{name}.md
• (핵심 결정 1)
• (핵심 결정 2)
• (핵심 결정 3)
승인해 주시면 구현을 시작합니다.
```

## After Approval

- 스펙의 완료 기준을 구현 중 체크
- 설계 변경 시 스펙을 먼저 수정한 뒤 코드 반영
