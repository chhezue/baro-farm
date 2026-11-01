# Audits — 감사·리팩터 기록

`safe-refactor`(B 트랙)와 `retro`(하네스 회고)의 출력 위치. `{{감사 로그}}` 슬롯의 기본값. 폴더 이름은 2026-09-04부터 **작업 단위**(`taste-vector`·`cart-recipe`·`vector-similarity`·`keyword-search`·`cross-cutting`)를 쓴다. 그 전에 만든 `search/`·`recommend/` 폴더는 이력이라 그대로 두고, 아래 대응표로 어느 단위에 속하는지 본다.

| 파일 | 쓰는 스킬 |
|------|-----------|
| `{unit}/{NNN}-{kebab-topic}.md` | `safe-refactor` — 감사 결과. **단위 안에서 001부터 증가하는 번호 + 감사 범위**(예: `keyword-search/001-query-path.md`). `decisions/`의 번호 규칙과 같은 방식 |
| `{unit}/refactor-log.md` | `safe-refactor` — 반영 이력 append. 단위당 1개, 각 절은 어느 감사(`001` 등)를 반영했는지 번호로 가리킨다 |
| `harness-retro.md` | `retro` — 하네스 자체 개선 후보 |
| `full-module/{NNN}-{kebab-topic}.md` | 작업 단위 하나가 아니라 **모듈 전체**를 한 번에 훑은 감사. 파일 번호 규칙(`001`부터)만 같고, 폴더 이름이 작업 단위가 아니며 분류도 A/B/C/D가 아니라 P0~P3이다 — 아래 절에 이유를 적었다 |
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
| [`cart-recipe/002-goldenset-before-after.md`](cart-recipe/002-goldenset-before-after.md) | 프롬프트 재설계 Before/After 골든셋 측정 (A/B/C/D 아닌 측정 보고서) | 실행 합격 69.0%→90.5%, 출력 토큰 −70.8% |
| [`cart-recipe/003-pantry-staples-and-variants.md`](cart-recipe/003-pantry-staples-and-variants.md) | 기본 양념 확대안·재료명 변종 가설 측정 (측정 보고서) | 가설 2건 모두 기각, 코드 변경 없음 |
| [`cart-recipe/004-index-time-ingredient-name.md`](cart-recipe/004-index-time-ingredient-name.md) | 색인 시점 재료명 추출 프롬프트 (측정 보고서) | 16/17→17/17, 프롬프트 반영 |
| [`vector-similarity/001-vector-search-and-diversity.md`](vector-similarity/001-vector-search-and-diversity.md) | 상품 임베딩 · 벡터 검색 · 다양화 · ES 설정 | A 2 · B 5 · C 2 · D 5 |
| [`keyword-search/001-query-path.md`](keyword-search/001-query-path.md) | 검색·자동완성 조회 경로 (색인 경로는 `search/001`) | A 4 · B 4 · C 6 · D 3 |
| [`cross-cutting/002-dependencies-config-and-dead-code.md`](cross-cutting/002-dependencies-config-and-dead-code.md) | 의존성 · 설정 · Kafka 팩토리 · 죽은 패키지 · 응답 규격 (LLM 인프라는 `001`) | A 2 · B 8 · C 5 · D 4 |

## 2026-09-06 저녁 로컬 통합 확인 (앱·ES·Kafka를 실제로 띄움)

nori를 넣은 Elasticsearch 9.1.2와 Kafka로 앱을 띄워, 그동안 "기동해야만 드러난다"며 미뤄 둔 확인을 처음 수행했다(환경: `baro-ai/docker/local/`). 통과 6건으로 `open-items.md` A-2가 17건에서 11건으로 줄었고, **코드만 읽어서는 못 봤을 A 항목 2건**이 새로 나왔다.

| 문서 | 범위 | 결과 |
|------|------|------|
| [`keyword-search/003-local-integration-verification.md`](keyword-search/003-local-integration-verification.md) | 통합 확인 기록 · 색인/검색 실측 | A 0 · C 1(오타 매칭의 합성어 한계) — 확인 6건 통과 |
| [`taste-vector/003-shared-executor-saturation.md`](taste-vector/003-shared-executor-saturation.md) | 공유 실행자 포화 · 취향 데이터 유실 | A 1 — **닫힘(2026-09-07)**. 검색을 신호에서 빼자 유입이 사라져 코드 수정 없이 해소 |
| [`cart-recipe/006-missing-ingredient-product-match.md`](cart-recipe/006-missing-ingredient-product-match.md) | 부족 재료 → 상품 연결 | **A 2 · B 1 · 반영 완료(2026-09-07)** — 색인해 둔 `ingredientName` 정확 일치 절과 마지막 어절 검색어를 넣었다 |
| [`taste-vector/004-executor-fix-measurement.md`](taste-vector/004-executor-fix-measurement.md) | 개선안 A 실적용 Before/After (측정 보고서) | A안은 유실 0%를 만들지만 검색 p95가 최대 7.5배. 이 측정이 005의 계기가 됐다 |
| [`taste-vector/005-signal-and-weight-simplification.md`](taste-vector/005-signal-and-weight-simplification.md) | 검색 신호 제거 · 수량 축 제거 | **A 2 · 반영 완료** — 신호 3종→2종, 가중치 2축→1축. 003이 함께 닫혔다 |

## 2026-09-06 비판 평가 후속 라운드 (4개 단위 동시 감사 · 구현은 단위별)

| 문서 | 범위 | 결과 |
|------|------|------|
| [`cart-recipe/005-request-path-defects-and-tests.md`](cart-recipe/005-request-path-defects-and-tests.md) | 최상위 catch · 부족 재료 N+1 · 일급 컬렉션 · 첫 단위 테스트 · 응답 `reason` | A 2 · B 3 · 판단 1 — **반영 완료** |
| [`taste-vector/002-weight-axes-and-tests.md`](taste-vector/002-weight-axes-and-tests.md) | 가중치 3축 실효성 · 테스트 가능화 | A 0 · B 2 · 판단 2 (시간 축 삭제) — **반영 완료** |
| [`keyword-search/002-index-throughput-and-search-log.md`](keyword-search/002-index-throughput-and-search-log.md) | 색인 배치화 · 검색 로그 비동기 · 페이징 테스트 | A 2 · B 1 · 측정 M-3 — **반영 완료**(묶음 5건·15분) |
| [`vector-similarity/002-vector-transfer-knn-and-diversity.md`](vector-similarity/002-vector-transfer-knn-and-diversity.md) | 벡터 전송량 · `knn` 전환 실측 · 다양화 효과 실측 | A 1 · B 2 · 측정 M-1·M-2 — **반영 완료**: `knn` 전환 안 함(`decisions/004`), 다양화 ×3 유지(당시). **다양화는 2026-09-09에 삭제**돼 카테고리당 상한 2로 바뀌었다(`vector-similarity/refactor-log.md` 2026-09-09) |

같은 클래스를 두 문서가 다룰 때는 한쪽을 SSOT로 정하고 다른 쪽은 참조만 남긴다(`ElasticsearchConfig` → `vector-similarity/001` A-2, cart·order 팩토리 래핑 → `cross-cutting/002` A-2·B-6, 컨슈머 재시도·중복 로그 → `taste-vector/001` A-5, `SimilarProductResponse` → `vector-similarity/001` B-1).

## 2026-09-07 부하 테스트 기반 비판 평가 라운드

사용자의 "리팩터가 다 됐는지 부하 테스트로 확인해 달라"는 요청에서 나온 감사다. 앞선 라운드들이 **단일 요청 기준**으로 닫았던 판단을 동시 요청·실측으로 다시 봤고, 성능 축(차원)과 정합성 축(주석·죽은 필드·설정 모순)에서 각각 A 항목이 나왔다.

| 문서 | 범위 | 결과 |
|------|------|------|
| [`vector-similarity/003-embedding-dims-and-candidate-multiplier.md`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) | 임베딩 차원 · `topK` 상한과 다양화 배수의 정합 | **A 2 · 반영 완료 · 로컬 재색인 확인** — 차원 1536→768(전송 절반·동시 30 p95 3.5배), `topK` 상한 50→33(**2026-09-09 다양화 삭제로 다시 50**). 측정 M-1~M-3. D 3 · 판단 1(배포 재색인 시점) |
| [`cross-cutting/003-stale-comments-and-write-only-fields.md`](cross-cutting/003-stale-comments-and-write-only-fields.md) | 사실이 아닌 주석 · 쓰기 전용 필드 · 도달 불가 코드 | **A 1 · B 5 · 반영 완료** — 설명 12곳, 필드 2개, 에러 코드 1종, 중복 상수 정리. C 2 · D 1 |
| [`cart-recipe/007-fruit-exclusion-and-prompt-example.md`](cart-recipe/007-fruit-exclusion-and-prompt-example.md) | 프롬프트 예시 · 과일 제외 규칙 재검토 | **A 2 · 반영 완료** — 예시 교체(골든셋 84건×2팔, 차이 없음)와 **과일 제외 해제**. 제외의 원인은 과일이 아니라 옛 프롬프트의 후보 3개 압력이었고(M-2), 과일을 넣어도 할루시네이션이 늘지 않는다(M-3, 보유 재료 사용률 79.4% vs 63.9%). M-4에서 장바구니 스텁으로 레시피 경로를 처음 끝까지 통과시켰다 |

## 2026-09-08 모듈 전체 감사 (작업 단위가 아니라 baro-ai 전부)

사용자 요청으로 모듈 전체를 한 번에 훑었다. 기존 감사가 작업 단위 하나를 좁게 보는 것과 달리 아키텍처·동시성·AI 설계·코드 품질을 한 자리에서 비교했고, 결과를 A/B/C/D가 아니라 **P0~P3 우선순위**로 분류했다(P0 = 동작하지 않거나 잘못된 설계, P1 = 백엔드 판단을 코드에 드러내기 위한 보강, P2 = 있으면 좋은 것, P3 = 건드리지 않아도 되는 것). `core-scope.md`의 `priority: must/could` 라벨과는 다른 축이다.

| 문서 | 범위 | 결과 |
|------|------|------|
| [`full-module/001-complexity-and-overengineering.md`](full-module/001-complexity-and-overengineering.md) | 복잡도 핫스팟 · 과잉 설계 · 불필요한 비즈니스 로직 | 핫스팟 13 · 과잉 설계 6 · 불필요 로직 10 |
| [`full-module/002-missing-backend-decisions.md`](full-module/002-missing-backend-decisions.md) | 부족한 백엔드 설계 판단 · 기술 사용 감사 · 동시성/트랜잭션 · AI 아키텍처 | 미비 판단 10 · 근거 없는 상수 15+ |
| [`full-module/003-target-architecture-and-priority.md`](full-module/003-target-architecture-and-priority.md) | 목표 구조 · P0~P3 우선순위 · 진행 순서 | P0 8 · P1 8 · P2 13 · P3 8 |
| [`full-module/004-measured-open-questions.md`](full-module/004-measured-open-questions.md) | 측정이 필요했던 세 항목의 실측·판정 (2026-09-09) | 2건 기각 · 1건 측정 불가 |

**반영:** P0~P3을 2026-09-08(P0·P1)과 2026-09-09(P2·P3)에 걸쳐 반영했다. 각 단위의 `refactor-log.md` 해당 날짜 절 참고.

빠진 것은 셋이다. ① review 패키지 소유 항목(팀원 몫) ② 측정이 있어야 판단되는 항목 세 개는 2026-09-09에 실제로 재고 [`full-module/004`](full-module/004-measured-open-questions.md)에 판정을 남겼다 — 둘은 기각, 하나(재구매 정책)는 데이터가 없어 제품 결정으로 넘겼다 ③ P3-2·P3-3·P3-4 — 앞의 둘은 `java-comments.md`가 "되돌리지 않는다"고 명시한 항목이라 감사 쪽이 틀렸고, P3-4는 springdoc이 API 제목·설명을 프로퍼티로 받지 않아 불가능하다.

## 관련 문서

- [`open-items.md`](open-items.md) — 미결 항목 색인
- [`harness-retro.md`](harness-retro.md) — 하네스 회고 백로그
- [`../specs/README.md`](../specs/README.md) — 작업 단위 ↔ 코드 매핑
- [`../templates/README.md`](../templates/README.md) — 감사·측정 보고서·인계 문서의 유형과 템플릿
