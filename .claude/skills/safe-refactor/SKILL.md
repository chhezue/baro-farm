---
name: safe-refactor
description: 기존 도메인 코드를 아키텍처 감사 후 비즈니스 로직을 100% 유지하며 리팩토링한다(baro-ai는 API 계약 동결 원칙이 해제됨 — 더 정확해진다면 계약도 바꿀 수 있음, 상세 `baro-ai-performance-refactor.md`). 도메인 1개씩 순차 진행하며 감사·구현 각 단계마다 사용자 승인이 필요할 때 사용.
---

# Refactor Audit Workflow

새 기능 개발이 아니라 **기존 코드 품질 개선**이 목적일 때 쓰는 스킬. `specify`가 "구현 전 스펙"을 강제하듯, 이 스킬은 "감사 → 승인 → 구현 → 기계적 검증" 순서를 강제한다.

**이 스킬은 `core-workflow.md` 사이클의 B 트랙이다.** 아래 6단계는 트랙 공통 게이트와 이렇게 대응한다 — 게이트의 내용 자체는 `core-workflow.md`가 SSOT이며 여기서 중복 정의하지 않는다.

| 이 스킬 | 공통 게이트 |
|---------|-------------|
| 1. Audit | 진입 + **G1 리서치**(외부 라이브러리·Spring 기능 판단이 필요하면 `researcher`) |
| 2. 승인 | **G2 승인** — A/B 항목 확정 |
| 3. Implement | 구현 — 코딩 중 지킬 것 |
| 4. Verify | **G3 검증** — `preflight` 스킬 (`oasdiff`는 [스택 옵션: OpenAPI 계약 보호]가 켜진 프로젝트만. baro-ai는 꺼짐) |
| 5. Report | **G4 회고** — `refactor-log.md` append + 문서 갱신 점검 |
| 6. 다음 도메인 | 다음 사이클 진입 (승인 필요) |

점검 항목은 [`references/audit-checklist.md`](references/audit-checklist.md)가, `audit.md`의 절 구성과 A/B/C/D 분류 포맷은 [`docs/templates/how-to-doc.md`](../../../baro-ai/docs/templates/how-to-doc.md)의 "아키텍처 감사" 채움 예가 SSOT다 — 감사는 문제 해결 유형이다. 이 스킬은 그걸 한 번에 실행하는 대신 **도메인 단위로 쪼개고 매 단계 승인 게이트를 둔다.**

## 절대 원칙 (변경 불가)

1. ~~API Contract 100% 동일~~ — **baro-ai에서는 해제됨.** 릴리즈 전 프로젝트라 하위 호환 부담이 없고, 더 정확·명확해지는 방향이면 Request/Response Body·Endpoint·ErrorCode를 바꿀 수 있다. 근거·방침은 [`baro-ai-performance-refactor.md`](../../rules/baro-ai-performance-refactor.md) "API 계약 변경 방침" 참고. (이 원칙이 살아있는 프로젝트에 이 스킬을 다시 이식할 때는 원래대로 켠다)
2. **비즈니스 로직·정책 변경 금지** — 계약 표현 방식이 바뀌는 것과 로직·정책이 바뀌는 것은 다르다. 이건 그대로 유지
3. **YAGNI** — 필요 없는 추상화 금지, 과도한 AOP/Utility 분리 금지. baro-ai는 "전문성 있게" 고치는 것과 "오버엔지니어링"을 구분한다 — 판단 기준은 `baro-ai-performance-refactor.md`
4. **성능 악화 금지**
5. **`core-guardrails.md` ⛔ STOP이 이 스킬보다 항상 우선** — 특히 레거시 즉시 삭제(§4), ErrorCode·AOP same-turn(§2)는 이 스킬에서도 그대로 적용(계약이 바뀌어도 §2는 지킨다). review 패키지(`com.barofarm.ai.review`)를 건드리게 되면 **범위 위반 신호** — 멈추고 사용자에게 질문 (`baro-ai-performance-refactor.md` 범위 표)

## When to Use

- 사용자가 "아키텍처 감사", "리팩토링 감사·정리" 등을 요청하거나 `safe-refactor {domain}` 형태로 도메인을 지정할 때
- 새 API·기능 추가가 아니라 기존 코드 내부 품질(중복·Dead Code·Legacy·Spring 관례·성능·구조) 개선이 목적일 때

## 도메인 (docs/specs/ 폴더·패키지와 1:1 대응)

전체 매핑은 [`docs/specs/README.md`](../../../baro-ai/docs/specs/README.md)가 SSOT. 현재 이니셔티브의 범위·순서 제안은 [`baro-ai-performance-refactor.md`](../../rules/baro-ai-performance-refactor.md)를 따른다.

| 도메인 | 대응 패키지 | 이 이니셔티브 대상 |
|--------|-------------|---------------------|
| `search` | `com.barofarm.ai.search` | ✅ |
| `recommend` | `com.barofarm.ai.recommend` | ✅ |
| `embedding` | `com.barofarm.ai.embedding` | ✅ |
| `event-log` | `com.barofarm.ai.event`, `com.barofarm.ai.log` | ✅ |
| `chat` | `com.barofarm.ai.chat` | ✅ (단 미구현 — 감사 대상 코드 없음) |
| `cross-cutting` | `com.barofarm.ai.config` | ✅ (위 5개 도메인에 영향 있을 때만, review 전용 config 제외) |
| `review` | `com.barofarm.ai.review` | ❌ 팀원 소유 — 코드 변경 안 함 |

**한 번에 한 도메인만 진행한다.** 여러 도메인을 동시에 감사·구현하지 않는다. 순서는 사용자가 지정하지 않으면 `baro-ai-performance-refactor.md`의 제안 순서(search → embedding → recommend → event-log → chat)로 제안하되, 확정은 사용자에게 묻는다.

## Steps

### 1. Audit — 읽기 전용, 신선한 서브에이전트

- `Agent` 툴(`subagent_type: Explore` 또는 `general-purpose`, 읽기 전용)로 **새 서브에이전트**를 띄워 해당 도메인 패키지만 스캔한다.
  - **왜 서브에이전트인가:** 방금 짠 코드를 같은 대화에서 스스로 평가하면 자기 판단을 재확인하는 self-grading 편향이 생기기 쉽다 — `code-review`/`simplify` 스킬이 서브에이전트 컨텍스트를 쓰는 것과 동일한 이유(`core-tools.md`). 이 도메인은 아직 이번 세션에서 건드리지 않았더라도, 신선한 컨텍스트가 dead code·중복을 더 냉정하게 찾는다.
  - 프롬프트에 반드시 포함: 대상 패키지 경로, `references/audit-checklist.md` 16개 점검 항목 전체(baro-ai는 16번 LLM/프롬프트 포함), A/B/C/D 분류 기준, `baro-ai/docs/templates/how-to-doc.md`의 "아키텍처 감사" 채움 예 포맷, `baro-ai-performance-refactor.md`의 근거 기준, "코드 수정 금지 — 읽기·분석만".
- 산출물: `{{감사 로그}}/audit.md` (신규 작성, **코드는 건드리지 않음**)

### 2. 승인

- `audit.md`의 A/B 항목을 사용자에게 요약 보고하고, 실제로 진행할 항목을 확인받는다 (`AskUserQuestion` 또는 채팅).
- 승인받지 않은 항목은 손대지 않는다. C/D는 애초에 이번 라운드에서 구현 대상이 아니다.

### 3. Implement

- 승인된 A/B 항목만, 우선순위(Critical → High → Medium → Low) 순으로 구현한다.
- `core-guardrails.md` "구현 — 코딩 중 지킬 것" 절 전체 준수 — 요청 범위만, drive-by 리팩터 금지, 레거시는 같은 변경에서 삭제.
- 구현 중 API 계약·ErrorCode·엔드포인트를 건드리게 되면 **즉시 멈추고 사용자에게 확인** (원칙 위반 신호).

### 4. Verify — 비즈니스 로직 무손실 + 의도한 계약 변경만 있는지 증명

LLM의 "안 바꿨다"는 자기 보고를 신뢰하지 않고, 아래를 전부 통과해야 다음 단계로 간다. baro-ai는 [스택 옵션: OpenAPI 계약 보호]가 꺼져 있어 `oasdiff` 자체를 쓰지 않는다 — 대신:

```bash
{{테스트 명령}}
```

- **API 계약이 바뀌었으면**: "안 바뀐 것"을 증명하는 게 아니라 **바뀐 부분이 audit.md에서 승인받은 범위와 정확히 일치하는지** 컨트롤러·DTO diff를 직접 읽어 확인한다. 승인 안 된 계약 변경이 섞여 있으면 되돌린다.
- **비즈니스 로직은 그대로**여야 한다 — 조건문·계산식·분기가 바뀌었으면 절대 원칙 2 위반, 원인 파악 후 되돌리거나 사용자에게 확인
- **테스트가 없는 도메인**(baro-ai는 전 도메인 테스트 0개)은 `{{테스트 명령}}`이 컴파일만 검증한다는 뜻이다 — `baro-ai-performance-refactor.md`의 "근거 기준"에 따라 감사 단계에서 이미 요구한 재현 방법(반복 호출 스크립트·수동 curl 등)으로 대신 검증하고, 그 결과를 refactor-log.md에 남긴다.
- Must Have급 변경(3파일 이상)이면 `code-review` 또는 `simplify` 스킬로 최종 게이트를 한 번 더 돌리는 걸 권장한다.
- 하나라도 실패하면 "일단 커밋"하지 않고 원인 분석 → 재수정 → 재검증.

### 5. Report

- `{{감사 로그}}/refactor-log.md`에 append (Changelog 스타일):
  - 실행 날짜, 반영한 A/B 항목 목록, 변경 파일·라인 수, 검증 결과(`{{테스트 명령}}`, oasdiff diff 0 확인), 남겨둔 C/D 항목과 이유
  - **H1 바로 아래에는 이 로그가 무엇인지 설명하는 개요 한 문단을 유지한다** (`doc-writing.md` — 날짜 섹션부터 시작하지 않는다)
- `audit.md`·`refactor-log.md`를 새로 만들었거나 50줄 이상 고쳤으면 **`doc-reviewer`** 서브에이전트로 확인 (G3 문서 품질 게이트).
- 사용자에게 짧게 요약 보고 — 설명 문체는 `core-reporting.md`를 따른다.

### 6. 다음 도메인

- 사용자 승인 없이 다음 도메인으로 자동 진행하지 않는다.

## 브랜치·이슈·커밋 (2026-09-04 사용자 결정)

- **이슈 생성 없이 브랜치만 만들어 진행한다** — 감사·리팩터는 대개 이슈 없이 시작한다. 브랜치를 만든다면 `core-workflow.md` "Git 컨벤션" 절의 `feature/issue-{번호}-{설명}` 형식을 따르되, 대응 이슈가 없으면 형식을 강제하지 않는다.
- 5인 공용 모노레포이므로 브랜치는 `main`에서 새로 분기하고, PR을 올릴 단계가 되면 팀 컨벤션(커밋 로그 관례: `[Type] #이슈 - 설명`)을 다시 확인한다.
- 커밋은 `AGENTS.md` 공통 규칙대로 **사용자가 명시적으로 요청할 때만** 진행한다.

## 금지

- 여러 도메인 동시 감사·구현
- 승인 없는 A/B 항목 구현, 또는 감사(audit.md 작성)와 같은 턴에 바로 구현 (승인 단계 생략)
- **API·ErrorCode·엔드포인트를 audit.md에 없던 항목으로 즉흥 변경** — baro-ai는 계약 변경 자체는 허용하지만, 그것도 A/B 항목으로 감사·승인을 거쳐야 한다. "고치다 보니 더 나아 보여서" 식 drive-by 계약 변경은 여전히 금지
- 비즈니스 로직·정책을 "계약이 바뀌니까 김에" 같이 바꾸기 — 계약 변경과 로직 변경은 별개 승인 대상
- 전체 테스트 통과 전 "완료" 보고
- C/D 항목을 이유 없이 나열만 하고 넘어감 (템플릿 원칙: 반드시 이유 포함)
