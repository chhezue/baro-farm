# Audits — 감사·리팩터 기록

`safe-refactor`(B 트랙)와 `retro`(하네스 회고)의 출력 위치. `{{감사 로그}}` 슬롯의 기본값.

| 파일 | 쓰는 스킬 |
|------|-----------|
| `{domain}/{NNN}-{kebab-topic}.md` | `safe-refactor` — 감사 결과. **도메인 안에서 001부터 증가하는 번호 + 감사 범위**(예: `search/001-index-path.md`, `cross-cutting/001-llm-call-infra.md`). 한 도메인이 여러 라운드로 나뉘므로 `audit.md` 하나로는 이름이 충돌한다 — `decisions/`의 번호 규칙과 같은 방식(2026-09-04) |
| `{domain}/refactor-log.md` | `safe-refactor` — 반영 이력 append. 도메인당 1개, 각 절은 어느 감사(`001` 등)를 반영했는지 번호로 가리킨다 |
| `harness-retro.md` | `retro` — 하네스 자체 개선 후보 |
| `{topic}-comparison.md` | 도메인 축이 아닌 **외부 저장소 비교 조사** — 감사에 쓸 근거를 모으는 배경 문서. `safe-refactor` 감사 자체는 아니므로 A/B/C/D 분류를 강제하지 않는다(예: `ssoss-llm-comparison.md`) |
