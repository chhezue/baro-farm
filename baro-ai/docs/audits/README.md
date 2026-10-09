# Audits — 감사·리팩터 기록

`safe-refactor`(B 트랙)와 `retro`(하네스 회고)의 출력 위치. `{{감사 로그}}` 슬롯의 기본값. 폴더 이름은 2026-09-04부터 **작업 단위**(`taste-vector`·`cart-recipe`·`vector-similarity`·`keyword-search`·`cross-cutting`)를 쓴다. 그 전에 만든 `search/`·`recommend/` 폴더는 이력이라 그대로 두고, 아래 대응표로 어느 단위에 속하는지 본다.

| 파일 | 쓰는 스킬 |
|------|-----------|
| `{unit}/{NNN}-{kebab-topic}.md` | `safe-refactor` — 감사 결과. **단위 안에서 001부터 증가하는 번호 + 감사 범위**(예: `keyword-search/001-query-path.md`). `decisions/`의 번호 규칙과 같은 방식 |
| `{unit}/refactor-log.md` | `safe-refactor` — 반영 이력 append. 단위당 1개, 각 절은 어느 감사(`001` 등)를 반영했는지 번호로 가리킨다 |
| `harness-retro.md` | `retro` — 하네스 자체 개선 후보 |
| `{topic}-comparison.md` | 도메인 축이 아닌 **외부 저장소 비교 조사** — 감사에 쓸 근거를 모으는 배경 문서. A/B/C/D 분류를 강제하지 않는다(예: `ssoss-llm-comparison.md`) |

## 옛 폴더 ↔ 작업 단위

| 옛 문서 | 속하는 작업 단위 | 비고 |
|---------|------------------|------|
| [`search/001-index-path.md`](search/001-index-path.md) | 키워드 검색 (색인 경로) | A-1·B-1·B-2 반영 완료 |
| [`search/002-llm-prompt-tuning.md`](search/002-llm-prompt-tuning.md) | 장바구니 레시피 (색인 시점 재료명 프롬프트) | A/B 0건 |
| [`search/refactor-log.md`](search/refactor-log.md) | 키워드 검색 | 자동완성 캐시·컨슈머 설정·폴백 제거 이력 |
| [`recommend/001-llm-prompt-tuning.md`](recommend/001-llm-prompt-tuning.md) | 장바구니 레시피 | B-1(주석 2줄)만 |
| [`recommend/002-recipe-scoring.md`](recommend/002-recipe-scoring.md) | 장바구니 레시피 | C-1, 사용자 결정 대기 |
| [`recommend/refactor-log.md`](recommend/refactor-log.md) | 장바구니 레시피 · 공유 인프라 | Gemini 전환·재료명 색인 시점 이동 이력 |
| [`cross-cutting/001-llm-call-infra.md`](cross-cutting/001-llm-call-infra.md) | 공유 인프라 | 재시도·스키마 강제·지표 반영 완료 |

새 감사는 작업 단위 폴더에 `001`부터 만든다. 옛 폴더에는 더 이상 새 문서를 추가하지 않는다.

## 작업 단위별 감사 (2026-09-04 이후)

| 문서 | 범위 | 결과 (2026-09-05 전부 승인 — 각 문서 "승인 결과" 절) |
|------|------|------------------|
| [`taste-vector/001-profile-refresh-path.md`](taste-vector/001-profile-refresh-path.md) | 행동 로그 수집 → 프로필 갱신 → 개인화 추천 | A 5 · B 4 · C 3 · D 4 |
| [`cart-recipe/001-cart-integration-and-request-path.md`](cart-recipe/001-cart-integration-and-request-path.md) | 장바구니 Feign 연동 → 레시피 요청 경로 (프롬프트·스코어링은 `recommend/001`·`002`) | A 2 · B 5 · C 6 · D 4 |
| [`cart-recipe/002-goldenset-before-after.md`](cart-recipe/002-goldenset-before-after.md) | 프롬프트 재설계 Before/After 골든셋 측정 (A/B/C/D 아닌 측정 보고서) | 실행 합격 69.0%→90.5% |
| [`cart-recipe/003-pantry-staples-and-variants.md`](cart-recipe/003-pantry-staples-and-variants.md) | 기본 양념 확대안·재료명 변종 가설 측정 (측정 보고서) | 가설 2건 모두 기각, 코드 변경 없음 |
| [`cart-recipe/004-index-time-ingredient-name.md`](cart-recipe/004-index-time-ingredient-name.md) | 색인 시점 재료명 추출 프롬프트 (측정 보고서) | 16/17→17/17, 프롬프트 반영 |
| [`vector-similarity/001-vector-search-and-diversity.md`](vector-similarity/001-vector-search-and-diversity.md) | 상품 임베딩 · 벡터 검색 · 다양화 · ES 설정 | A 2 · B 5 · C 2 · D 5 |
| [`keyword-search/001-query-path.md`](keyword-search/001-query-path.md) | 검색·자동완성 조회 경로 (색인 경로는 `search/001`) | A 4 · B 4 · C 6 · D 3 |
| [`cross-cutting/002-dependencies-config-and-dead-code.md`](cross-cutting/002-dependencies-config-and-dead-code.md) | 의존성 · 설정 · Kafka 팩토리 · 죽은 패키지 · 응답 규격 (LLM 인프라는 `001`) | A 2 · B 8 · C 5 · D 4 |

같은 클래스를 두 문서가 다룰 때는 한쪽을 SSOT로 정하고 다른 쪽은 참조만 남긴다(`ElasticsearchConfig` → `vector-similarity/001` A-2, cart·order 팩토리 래핑 → `cross-cutting/002` A-2·B-6, 컨슈머 재시도·중복 로그 → `taste-vector/001` A-5, `SimilarProductResponse` → `vector-similarity/001` B-1).
