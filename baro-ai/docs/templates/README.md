# 문서 템플릿 (`docs/templates/`)

새 문서를 만들 때 **유형을 먼저 고르고** 해당 템플릿을 복사해서 채우는 곳입니다. 유형별로 독자가 원하는 것이 다르고 그에 맞는 구조도 다르기 때문에, 빈 파일에서 시작하는 것보다 훨씬 빠르고 결과가 고릅니다.

유형 판정 기준과 작성 규칙 SSOT는 [`.claude/rules/doc-writing.md`](../../../.claude/rules/doc-writing.md)입니다.

## 유형별 템플릿

| 유형 | 독자의 목적 | 템플릿 |
|------|-------------|--------|
| **학습** | 처음 접해서 전체 흐름을 알고 싶다 | [`learning-doc.md`](learning-doc.md) |
| **문제 해결** | 지금 막힌 것을 풀고 싶다 | [`how-to-doc.md`](how-to-doc.md) |
| **참조** | 값·계약·목록을 정확히 확인하고 싶다 | [`reference-doc.md`](reference-doc.md) |
| **설명** | 왜 이렇게 됐는지 이해하고 싶다 | [`decisions/README.md`](../decisions/README.md)의 ADR 템플릿 |

## 이 저장소 고유 문서에는 전용 템플릿이 따로 있습니다

아래 세 가지는 위 4유형과 별개로 **작업 산출물** 성격이라 각자의 템플릿을 씁니다.

| 문서 | 템플릿 | 만드는 시점 |
|------|--------|-------------|
| 기능 스펙 (`docs/specs/`) | [`spec-template.md`](../../../.claude/skills/specify/references/spec-template.md) | A 트랙 — 구현 전 |
| 아키텍처 감사 (`docs/audits/`) | [`audit-template.md`](../../../.claude/skills/safe-refactor/references/audit-template.md) | B 트랙 — 리팩터 전 |
| 기술 결정 (`docs/decisions/`) | [`decisions/README.md`](../decisions/README.md) 내장 | 되돌리기 어려운 선택을 할 때 |

## 쓰는 법

1. 유형을 정한다 (`doc-writing.md`의 판정 순서)
2. 해당 템플릿의 코드블록 안쪽을 복사해 새 파일에 붙인다
3. 대괄호 `[...]` 안내문을 실제 내용으로 바꾼다 — 안내문이 남아 있으면 미완성이다
4. 템플릿 하단 체크리스트로 자가 점검한다
5. 새 문서이므로 **G3 문서 품질 게이트** 대상이다 — `doc-reviewer`로 확인한다
