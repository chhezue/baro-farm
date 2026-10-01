# Audit Template

`{{감사 로그}}/audit.md`를 새로 작성할 때 이 구조를 그대로 복사해서 채운다. 감사 단계는 **코드를 수정하지 않는다** — 이 문서만 산출한다.

---

```markdown
# {Domain} Architecture Audit — {YYYY-MM-DD}

{개요 — 필수. 이 감사가 무엇을 대상으로 했고, A/B로 몇 건이 나왔으며, 지금 어떤 상태(승인 대기·반영 완료)인지 2~3문장. 처음 여는 사람이 이 문단만 읽고 문서의 정체를 알 수 있어야 한다 — `.claude/rules/doc-writing.md` "H1 바로 아래에 개요를 둔다".}

## 범위

- 패키지: `{루트 패키지}.{domain}` (+ 하위 패키지 나열)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~16항목(baro-ai는 16번 LLM/프롬프트 포함), `core-guardrails.md` ⛔ STOP
- **baro-ai-performance-refactor.md 활성화 중이면**: 근거 기준(적극적 증거 필수)이 이 절 전체에 적용됨 — 아래 A-item "근거" 필드 필수

## ✅ A. 반드시 수정해야 하는 사항

버그 · 잠재적 버그 · 성능 문제 · 메모리 낭비 · 보안 문제 · API 계약은 유지하면서 개선 가능한 구조적 문제.

### A-1. {제목}

- **Priority**: Critical / High / Medium / Low
- **Category**: Performance / Readability / Spring / JPA / Architecture / Security / Cleanup / Dead Code / LLM-Prompt
- **문제**: 현재 어떤 문제가 있는지
- **근거** (Category가 Performance·LLM-Prompt이거나 `baro-ai-performance-refactor.md` 활성화 중이면 필수): 코드로 확인한 사실 또는 재현 가능한 관찰. "느릴 것 같다"·"비효율적으로 보인다"는 근거 아님 — `baro-ai-performance-refactor.md` "근거 기준" 참고
- **왜 문제인가**: 유지보수 / 성능 / 가독성 관점
- **개선 방법**: 구체적인 리팩토링 방안
- **검증 방법** (Performance·LLM-Prompt만): 개선 효과를 어떻게 확인할지 — 테스트, 반복 호출 스크립트, 토큰 수 비교 등 로컬에서 재현 가능한 방법
- **API 영향**: No Impact / Breaking(baro-ai는 계약 변경 허용 — 무엇이 바뀌는지 명시. `baro-ai-performance-refactor.md` 없는 프로젝트에서는 No Impact 고정)
- **예상 변경 파일**: 경로 나열
- **예상 변경 라인 수**: 대략치
- **위험도**: Low / Medium / High
- **테스트 영향도**: 기존 테스트 영향 여부, 커버리지 공백 여부
- **예상 효과**: 코드 감소 / 유지보수성 / 성능 / 테스트성

(A-2, A-3 ... 반복)

## ✅ B. 유지보수성 향상을 위한 리팩토링

중복 코드 제거 · 책임 분리 · 공통화 · 보일러플레이트 제거 · Dead Code 제거 · Legacy 코드 제거.

### B-1. {제목}

(A와 동일 포맷)

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- 각 항목마다 **왜 지금 안 하는지 이유**를 반드시 포함 (네이밍 아쉬움, 함수 길이, 패키지 구조 대안, 개인 취향, Optional 사용 방식 차이 등)

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- 각 항목마다 **왜 현재 구조가 더 나은지 이유**를 반드시 포함 (과도한 추상화 우려, AOP 실익 없음, 공통화 시 가독성 저하, 성능상 현재가 유리 등)

## 15. 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있는 경우만)

"최신 기술이라서" / "많은 회사가 쓰니까"는 금지. 아래 카테고리 중 **실제 효과가 있다고 판단되는 것만**:

Concurrency · Redis · Event Architecture · Async Processing · Database · Monitoring · Resilience · Security · API

각 제안에 반드시 포함:

- 왜 필요한지 / 현재 프로젝트에서 적용 가치가 있는지
- **근거**: 코드로 확인한 현재 동작 또는 재현 가능한 관찰 (`baro-ai-performance-refactor.md` 활성화 중이면 필수)
- 도입 시 장단점 · 구현 난이도 · 우선순위
- **Now**(지금 도입) / **Later**(트래픽 증가 시) / **Never**(도입 안 하는 게 좋음) 중 하나 + 이유

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
```
