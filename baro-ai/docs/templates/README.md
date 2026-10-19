# 문서 산출물 등록부 (`docs/templates/`)

이 저장소의 **모든 마크다운 문서가 어디에 쌓이고 어떤 형식을 따르는지**를 한 곳에 모은 표다. 스킬이 자동으로 만드는 산출물뿐 아니라 사람이 직접 쓰는 문서도 전부 포함한다 — 어느 쪽이든 `baro-ai/docs/` 밖에 두거나 등록부에 없는 형식으로 쓰지 않는다. 새 문서를 쓸 때는 여기서 유형을 찾아 해당 템플릿을 복사하고, 새 산출물을 만드는 스킬을 추가할 때는 여기에 행을 먼저 추가한다.

문서 루트는 **`baro-ai/docs/`** 하나다(`harness-map.md`의 `{{문서 루트}}` 슬롯). 저장소 루트 `docs/`는 5인 공용 영역이고 `deny-out-of-scope-write.sh`가 쓰기를 차단하므로, 이 하네스가 만드는 산출물은 예외 없이 `baro-ai/docs/` 아래에 둔다.

유형 판정 순서와 문장·구조 작성 기준은 [`.claude/rules/doc-writing.md`](../../../.claude/rules/doc-writing.md)가 SSOT다.

## 언제 이 문서를 보는가

- 새 문서를 만들기 전에 어떤 템플릿을 쓸지 정할 때
- 스킬이 만든 산출물이 어디에 저장돼야 하는지 확인할 때
- md를 새로 만드는 스킬·에이전트를 추가할 때 (등록부에 행 추가가 먼저다)

## 산출물 등록부

| 산출물 | 만드는 것 | 저장 경로 | 유형 | 템플릿 |
|--------|-----------|-----------|------|--------|
| 기능 스펙 | `specify` · `defer` 스킬 | `docs/specs/{unit}/{kebab-case}.md` (`{unit}` = 작업 단위 폴더, [`specs/README.md`](../specs/README.md)) | 참조 | [`reference-doc.md`](reference-doc.md) |
| 아키텍처 감사 | `safe-refactor` 스킬 (Audit 단계) | `docs/audits/{unit}/{NNN}-{kebab-topic}.md` (작업 단위 안에서 001부터 증가, 규칙은 [`audits/README.md`](../audits/README.md)) | 문제 해결 | [`how-to-doc.md`](how-to-doc.md) |
| 측정 보고서 | 사용자 승인 후 수동 작성(골든셋 등 Before/After 측정 결과) | `docs/audits/{unit}/{NNN}-{kebab-topic}.md` — 감사와 같은 번호 체계를 쓰되 A/B/C/D 분류 대신 결과·해석 구조 | 설명 | [`explanation-doc.md`](explanation-doc.md) |
| 리팩터 반영 이력 | `safe-refactor` 스킬 (G4 Report) | `docs/audits/{unit}/refactor-log.md` | 참조 | [`reference-doc.md`](reference-doc.md) |
| 하네스 회고 백로그 | `retro` 스킬 | `docs/audits/harness-retro.md` | 참조 | [`reference-doc.md`](reference-doc.md) |
| 미결 항목 색인 | 사용자 승인 후 수동 작성(리팩터 라운드 종료 시 갱신) | `docs/audits/open-items.md` | 참조 | [`reference-doc.md`](reference-doc.md) |
| 아키텍처 결정 (ADR) | 사용자 승인 후 수동 작성 | `docs/decisions/{번호}-{kebab-case}.md` | 설명 | [`explanation-doc.md`](explanation-doc.md) |
| 제품 범위·용어 | 사용자 승인 후 수동 작성 | `docs/product/mvp.md` · `glossary.md` | 참조 | [`reference-doc.md`](reference-doc.md) |
| 클라이언트 전제 | 사용자 승인 후 수동 작성 | `docs/product/platform.md` | 설명 | [`explanation-doc.md`](explanation-doc.md) |
| 하네스 설계 배경 | 사용자 승인 후 수동 작성 | `docs/harness/` · `docs/harness-engineering.md` | 설명 | [`explanation-doc.md`](explanation-doc.md) |
| 온보딩·개념 입문 | (아직 없음) | `docs/` | 학습 | [`learning-doc.md`](learning-doc.md) |
| ES 인덱스 매핑 | 사용자 승인 후 수동 작성 | `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` | 참조 | [`reference-doc.md`](reference-doc.md) |
| 임베딩 입출력 예시 | 사용자 승인 후 수동 작성 | `docs/EMBEDDING_EXAMPLE.md` | 참조 | [`reference-doc.md`](reference-doc.md) |
| 구현·기능 설명 | 사용자 승인 후 수동 작성 | `docs/IMPLEMENTATION.md` · `FEATURES.md` · `REVIEW_ANALYSIS_PHASES.md` | 설명 | [`explanation-doc.md`](explanation-doc.md) |
| 기능·기술 문서 (주제별) | 사용자 승인 후 수동 작성 | `docs/features/{unit}.md` — 절 구성·참고 자료는 [`features/README.md`](../features/README.md) | 설명 (Google 설계 문서의 목표·비목표·대안·교차 관심사 절을 더함) | [`explanation-doc.md`](explanation-doc.md) 골격 + `features/README.md` 절 표 |
| 포트폴리오 케이스 스터디 | 사용자 승인 후 수동 작성 | `docs/portfolio/{unit}.md` — 절 구성은 [`portfolio/README.md`](../portfolio/README.md) | 설명 (배경 → 분석 → 대안 → 해결 → 자평 → 한계 → 회고) | [`explanation-doc.md`](explanation-doc.md) 골격 + `portfolio/README.md` 절 표 |
| 기술 비교·분석 메모 | 사용자 승인 후 수동 작성 | `docs/audits/{주제}.md` (예: `ssoss-llm-comparison.md`) | 설명 | [`explanation-doc.md`](explanation-doc.md) |
| 모듈 README·폴더 인덱스 | 사용자 승인 후 수동 작성 | `docs/README.md` · 하위 `README.md` | 참조(인덱스) | [`reference-doc.md`](reference-doc.md) |

**마크다운을 만들지 않는 것들.** 등록된 훅 4개 중 셋은 stderr 메시지만 내보내고, `auto-format-java.sh`는 `.java` 파일을 포맷할 뿐 문서를 만들지 않는다. `preflight`·`debug` 스킬과 `doc-reviewer`·`spring-reviewer`·`researcher` 에이전트는 결과를 채팅으로만 보고한다. 이들의 결과를 문서로 남겨야 한다고 판단되면 위 등록부에 행을 추가하는 것이 먼저다 — 임의 경로에 파일을 만들지 않는다.

## 유형별 템플릿

toss의 [테크니컬 라이팅 가이드](https://github.com/toss/technical-writing)가 나눈 네 가지 유형을 그대로 쓴다. 이 저장소가 만드는 산출물은 전부 이 넷 중 하나에 속하며, **유형 밖의 전용 템플릿은 두지 않는다.**

| 유형 | 독자의 목적 | 템플릿 |
|------|-------------|--------|
| **학습** | 처음 접해서 전체 흐름을 알고 싶다 | [`learning-doc.md`](learning-doc.md) |
| **문제 해결** | 지금 막힌 것을 풀고 싶다 | [`how-to-doc.md`](how-to-doc.md) |
| **참조** | 값·계약·목록을 정확히 확인하고 싶다 | [`reference-doc.md`](reference-doc.md) |
| **설명** | 왜 이렇게 됐는지 이해하고 싶다 | [`explanation-doc.md`](explanation-doc.md) |

원칙과 구조만 참고하고 **원문 문장은 옮기지 않는다.** toss 가이드는 CC BY-NC-SA 4.0이라 문장을 그대로 복사하면 라이선스가 이 저장소로 전염된다.

## 쓰는 법

1. `doc-writing.md`의 판정 순서로 유형을 정한다.
2. 위 등록부에 이미 있는 산출물이면 해당 템플릿의 **"작업 산출물로 쓸 때"** 절에 있는 채움 예를 복사한다. 없으면 **"골격"** 절을 복사한다.
3. 대괄호 `[...]` 안내문을 실제 내용으로 바꾼다 — 안내문이 남아 있으면 미완성이다.
4. 템플릿 하단 체크리스트로 자가 점검한다.
5. 새 문서이므로 **G3 문서 품질 게이트** 대상이다 — `doc-reviewer`로 확인한다.

## 관련 문서

- [`.claude/rules/doc-writing.md`](../../../.claude/rules/doc-writing.md) — 유형 판정·정보 구조·문장 기준
- [`.claude/rules/harness-map.md`](../../../.claude/rules/harness-map.md) — `{{문서 루트}}` 등 경로 슬롯
- [`.claude/agents/doc-reviewer.md`](../../../.claude/agents/doc-reviewer.md) — 문서 품질 리뷰 기준
