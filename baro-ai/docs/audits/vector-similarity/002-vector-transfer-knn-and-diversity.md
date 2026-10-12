# Vector-similarity Architecture Audit 002 — 벡터 전송량·`knn` 전환 판단·다양화 효과 (2026-09-06)

임베딩 유사도 검색 작업 단위의 추천 요청 경로(`VectorRecommendPipeline` → `VectorProductSearchService` → `MaxMinDiversityService`)를 2026-09-06 비판 평가에서 다시 훑은 결과다. 반드시 고칠 A 항목 1건(추천 요청마다 후보 벡터 약 2MB를 받아 옴), 유지보수성 B 항목 2건(참조 비교 `contains`, 단위 테스트)과 **측정 계획 2건**(M-1 `script_score` vs `knn`, M-2 다양화 효과)이 나왔다. `001` C-1이 "상품 수 `[미정]`"으로 미뤄 둔 `knn` 판단은 이번에 **실측으로 닫는 것**이 목표다 — 어느 쪽 결론이 나오든 기록한다. **2026-09-06 승인 완료 · B 2건 반영 · M-1·M-2 측정 완료** — M-1은 "전환하지 않는다"로 닫혔고([`decisions/004`](../../decisions/004-script-score-over-knn.md)), A-1 방향은 M-2 결과를 놓고 사용자 결정 대기다.

## 범위

- 패키지: `recommend.application.VectorRecommendPipeline`·`MaxMinDiversityService`, `search.application.VectorProductSearchService`, `search.domain.ProductDocument`(벡터 필드 매핑)
- 감사자: 메인 세션(2026-09-06 비판 평가). 서브에이전트를 띄우지 않았고, 이 세션은 해당 코드를 수정한 적이 없다
- 기준: `audit-checklist.md` 1~16항목, `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md` 근거 기준 적용 — 특히 "C/D 판단도 실제 규모에 근거" 조항. 상품 수가 `[미정]`이라 M-1은 규모를 **가정 여러 개로 잰다**(1천·1만·10만)
- 로컬 인프라: Docker 29.6.2 동작 확인(2026-09-06). ES 컨테이너는 없어 `docker-compose.data.yml`의 `elasticsearch`(8.12.0, nori 없음)를 올려 쓴다. **운영은 9.1.2**(`docker/elasticsearch/Dockerfile`)라 `index_options` 기본값이 다르다(`001` C-1: 9.1은 `bbq_hnsw`) — M-1 결과에 버전 차이를 명시한다
- 상품 시드 데이터는 저장소에 없다(`scripts/init-db/`는 카테고리 22개만). M-2는 상품명을 직접 만들어 임베딩한다

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 추천 요청마다 후보 상품 벡터 약 2MB를 Elasticsearch에서 받아 온다

- **Priority**: Medium
- **Category**: Performance
- **문제**: 벡터 후보 조회가 `_source` 필터 없이 `ProductDocument` 전체를 받는다. 후보가 최대 100건이고 문서 하나가 벡터 포함 약 21KB라, 추천 1회에 ES → 앱 구간으로 약 2.1MB가 오간다. 검색 경로(`keyword-search/001` A-3)와 레시피 보유 재료 조회(`cross-cutting` 2026-09-05)는 같은 이유로 벡터를 빼고 받도록 고쳤는데, 이 경로만 남았다
- **근거**:
  - `VectorProductSearchService.java:100-127` — `NativeQuery.builder()`에 `withSourceFilter` 호출 없음(grep 0건)
  - `VectorRecommendPipeline.java:31·38` — `CANDIDATE_MULTIPLIER = 3`, `MAX_CANDIDATE_SIZE = 100`. topK 기본 5(개인화)·3(유사)면 후보 15·9건, 상한 50이면 100건
  - 문서 크기 실측: `keyword-search/001` A-3 — `GET product/_doc/{id}` 전체 21,160바이트, 벡터 제외 약 371바이트
  - **벡터를 받는 이유는 명시적이다**: `VectorProductSearchService` Javadoc 2항 — 다양화(`MaxMinDiversityService`)가 후보 간 코사인 거리를 앱에서 계산해야 하므로 벡터를 유지한다. 즉 이 비용은 다양화의 비용이다
- **왜 문제인가**: 검색에서 아낀 것을 추천에서 그대로 쓴다. topK 기본값 기준 요청당 190~315KB, 상한 기준 2.1MB를 ES 노드·네트워크·역직렬화에 소비하는데, 그 대가로 얻는 다양화 효과는 잰 적이 없다(M-2)
- **개선 방법**: **M-2 결과에 따라 셋 중 하나.** (a) 다양화가 효과 없으면 `MaxMinDiversityService` 삭제 + `withSourceFilter(vector 제외)` → 전송량 57분의 1. (b) 효과 있으면 `MAX_CANDIDATE_SIZE`·`CANDIDATE_MULTIPLIER`를 M-2가 보여 주는 최소값으로 낮춘다(예: 2배수·상한 30이면 최대 630KB). (c) 효과 있고 후보도 줄일 수 없으면 현 상태 유지 + 이유를 Javadoc에 실측 수치로 적는다. 어느 쪽이든 "잰 적 없음"은 끝난다
- **검증 방법**: 로컬 ES에서 `GET /api/v1/recommendations/similar/{id}?topK=50` 1회의 ES 응답 크기(`Content-Length` 또는 `org.elasticsearch.client` DEBUG의 response 길이)를 Before/After로 비교
- **API 영향**: No Impact (응답 DTO에는 벡터가 원래 없다)
- **예상 변경 파일**: `search/application/VectorProductSearchService.java`, `recommend/application/VectorRecommendPipeline.java`, (a)면 `recommend/application/MaxMinDiversityService.java` 삭제
- **예상 변경 라인 수**: (a) −199/+5, (b) 2줄, (c) 주석만
- **위험도**: (a) Medium — 추천 목록의 구성이 바뀐다(정책 변경이므로 M-2 근거 + 사용자 승인 필수), (b)(c) Low
- **테스트 영향도**: B-2와 연동
- **예상 효과**: (a)면 요청당 ES→앱 전송 최대 2.1MB → 약 37KB, (b)면 후보 수에 비례해 감소

## 📏 측정 계획 (A/B 판정의 전제 — 코드 변경 없음)

`001`은 측정 계획을 C 항목 안에 뒀지만, 이번에는 측정이 A-1 판정의 전제라 A와 B 사이에 별도 절로 뒀다. 결과는 문서 끝 "측정 결과" 절에 있다.

### M-1. `script_score` 선형 스캔 vs native `knn` — 상품 수 3단계에서 지연 실측

- **무엇을 재나**: 같은 1536차원 난수 벡터 N건(1,000 / 10,000 / 100,000)을 넣은 인덱스에서, 현재 쿼리(`script_score` + `cosineSimilarity`, 상태 필터)와 `knn` 쿼리(`num_candidates` = 후보 수 × 2, 같은 필터)의 p50/p95 지연(각 200회)과 **결과 일치율**(top-k 겹침 비율 — `knn`은 근사라 100%가 아닐 수 있다)
- **왜 난수로 충분한가**: 지연은 벡터 값이 아니라 문서 수·차원·인덱스 구조에 의존한다. 정확도(일치율)만 실데이터와 다를 수 있으므로 그 수치는 "참고"로 표시한다
- **이미 확인된 사실**(`001` C-1): 서버 기본값 `index: true`라 **HNSW 그래프는 색인 시 이미 만들어진다**. `script_score`는 그 그래프를 쓰지 않고 필터 통과 문서를 전부 선형 스캔한다. 즉 지금은 그래프를 만들어 놓고 안 쓰는 상태다 — 색인 비용은 내고 조회 이득은 없다
- **도구**: `baro-ai/scripts/vectorbench/`(Python, `requests`만) — 인덱스 생성·시드·두 쿼리 반복·통계·JSON 원본 저장. 앱·Kafka·Gemini 불필요
- **결론 규칙(미리 정한다 — 결과 보고 정하지 않기 위해)**:
  - 1만 건에서 `script_score` p95가 **50ms 미만**이면 → **전환하지 않는다.** 이유: 정확 계산이고 튠 파라미터가 없는데 이득이 체감 밖. 결정 기록으로 남긴다(`decisions/004`)
  - 1만 건에서 50ms를 넘거나 10만 건에서 수백 ms면 → 전환을 다음 라운드 A 항목으로 올린다. 재현율 손실을 함께 적는다
  - 5만 건 안팎이 분기점이면 → 상품 수 예상치를 사용자에게 묻는다(`[미정]` 해소 필요)
- **클라이언트 제약**(`001` C-1): Spring Data ES 5.3.6은 `withKnnQuery`(쿼리 DSL `knn` 절)만 있고 최상위 `knn` 옵션은 없다. 벤치마크는 REST로 직접 재므로 영향 없고, 전환하게 되면 이 제약을 구현 항목에 적는다

### M-2. 다양화(Max-Min)의 효과 — 켜고 끈 결과의 차이를 숫자로

- **무엇을 재나**: 현실적인 상품명(카테고리 22개 × 각 10~15개, 약 250건)을 Gemini `gemini-embedding-001`(1536)로 임베딩해 로컬 ES에 넣고, 기준 상품 20개에 대해 topK=5·10으로 **다양화 ON**(현재 코드: 후보 3배 → Max-Min)과 **OFF**(ES 유사도 상위 k 그대로)를 비교한다:
  1. 선택된 k개의 **평균 상호 코사인 거리**(클수록 서로 다름)
  2. 선택된 k개의 **카테고리 중복률**(같은 카테고리 쌍 / 전체 쌍)
  3. 기준 상품과의 **평균 유사도 손실**(다양화가 얼마나 덜 비슷한 것을 골랐나 — 대가)
- **왜 이 셋인가**: 1·2는 다양화가 약속한 것, 3은 그 대가다. 3 없이 1·2만 보면 항상 "효과 있음"이 나온다
- **왜 진짜 임베딩이 필요한가**: 난수 벡터는 서로 거의 직교해 다양화가 의미 없다. 상품명 임베딩이어야 "감귤 5종이 뭉치는" 현실 구조가 생긴다. 250건 × 1회 임베딩이라 무료 한도 안이다
- **도구**: M-1과 같은 폴더 `scripts/vectorbench/diversity.py`. 다양화 알고리즘은 **Java 코드를 그대로 옮긴다**(첫 후보 고정 → 최소거리 갱신 → 최대 선택 → 유사도순 재정렬). Java를 직접 부르지 않는 이유: 앱을 띄우려면 Kafka·`baro-shopping`까지 필요해서다. 옮긴 코드가 원본과 같은지는 B-2의 단위 테스트가 같은 예제로 확인한다
- **결론 규칙**: 카테고리 중복률이 OFF 대비 **10%p 이상** 내려가고 유사도 손실이 **0.05 미만**이면 → 다양화 유지, `CANDIDATE_MULTIPLIER`·`MAX_CANDIDATE_SIZE`는 측정에서 효과가 포화되는 최소값으로 낮춘다(A-1 (b)). 그보다 작으면 → 삭제를 사용자에게 제안한다(A-1 (a)). 문턱값은 사용자가 조정할 수 있다 — 결과를 보기 전에 적어 둔다는 것이 핵심

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `selected.contains(candidate)`가 참조 비교에 기대고 있다

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `ProductDocument`에 `equals`/`hashCode`가 없어(`@Getter`만) `List.contains`는 참조 동일성으로 동작한다. 지금은 같은 리스트의 같은 인스턴스라 맞지만, 후보 목록을 한 번만 복사하거나 ES가 같은 상품을 두 번 돌려주면 조용히 어긋난다. 또 k×N 루프 안에서 O(k) 선형 탐색이다
- **근거**: `MaxMinDiversityService.java:93`, `ProductDocument.java:27-29`(`@Getter`, `@EqualsAndHashCode` 없음)
- **왜 문제인가**: 동작이 "같은 인스턴스"라는 우연에 기대고 있어, 후보 목록을 복사하거나 중복 문서가 섞이는 순간 같은 상품이 두 번 뽑히거나 무한히 건너뛴다. 읽는 사람도 `contains`가 값 비교인 줄 오해한다
- **개선 방법**: `boolean[] picked = new boolean[validCandidates.size()]` 인덱스 플래그로 바꾼다. `ProductDocument`에 `equals`를 추가하지 않는다(엔티티에 값 동등성을 넣으면 벡터 배열 비교까지 끌려온다)
- **API 영향**: No Impact
- **예상 변경 파일**: `recommend/application/MaxMinDiversityService.java`
- **예상 변경 라인 수**: 5줄
- **위험도**: Low
- **테스트 영향도**: B-2로 고정

### B-2. 코사인·다양화 단위 테스트

- **Priority**: High
- **Category**: Architecture (테스트성)
- **문제**: `cosineSimilarity`(null·길이 0·차원 불일치·영벡터 → NaN)와 `selectDiverse`(첫 후보 고정, 가장 먼 것 선택, 유사도순 재정렬, 유효 후보 ≤ k면 그대로)가 검증 없이 있다
- **근거**: 테스트 0개. 두 메서드 모두 외부 의존 없음(`ProductDocument` 생성자로 3~4차원 벡터를 만들 수 있다)
- **왜 문제인가**: M-2가 이 알고리즘을 Python으로 옮겨 재는데, 원본과 같은 답을 내는지 확인할 기준이 없으면 측정 자체를 믿을 수 없다. NaN 처리 분기도 회귀 방어가 없다
- **개선 방법**: `MaxMinDiversityServiceTest` — 3차원 벡터 5~6개로 손으로 답을 낼 수 있는 예제. 같은 예제를 M-2의 Python 구현에도 넣어 두 구현이 같은 답을 내는지 확인한다(M-2 신뢰성). `VectorRecommendPipelineTest` — `validateTopK` 경계(0·1·50·51)
- **API 영향**: No Impact
- **예상 변경 파일**: `src/test/java/com/barofarm/ai/recommend/application/MaxMinDiversityServiceTest.java`, `.../VectorRecommendPipelineTest.java`
- **예상 변경 라인 수**: 120줄 내외
- **위험도**: Low
- **테스트 영향도**: 신규
- **예상 효과**: M-2 측정 도구의 신뢰 근거 + 다양화·코사인 회귀 방어

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 임베딩 차원 1536 → 768 축소

`gemini-embedding-001`은 출력 차원을 고를 수 있고(현재 `dimensions: 1536` 명시), 절반이면 저장·전송·코사인 비용이 절반이다. 지금 하지 않는 이유: 추천 품질 영향을 잴 골든셋이 없다. M-2 도구가 생기면 같은 도구로 768/1536을 비교할 수 있어 **다음 라운드 후보**로 남긴다
### C-2. 최종 유사도순 재정렬(`:128`)이 다양화의 순서 효과를 되돌린다

사용자에게 보이는 차이가 "어떤 k개"뿐이고 "어떤 순서"는 아니다. 의도(화면은 유사한 순서가 자연스럽다)가 Javadoc에 있어 결함은 아니다. M-2에서 순서를 포함한 지표는 재지 않는다

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 다양화를 ES 안에서 하는 것(`collapse`·`diversified_sampler`)

카테고리 단위 collapse는 "카테고리가 다르면 다양하다"는 다른 정책이고, 벡터 거리 기반 다양화는 ES 표준 기능에 없다. 옮기면 정책이 바뀌고 쿼리도 복잡해진다
### D-2. M-1에서 nori 이미지를 빌드하는 것

벡터 벤치마크는 `productName` 분석기와 무관하다. 스톡 8.12.0으로 충분하고 빌드 시간을 아낀다. 단 앱 전체를 로컬에서 띄우는 `open-items.md` A절 작업은 nori가 필요하다 — 그건 이 라운드가 아니다

## 판단이 서지 않는 항목

1. **M-2가 "효과 작음"으로 나오면 다양화를 지울 것인가.** 삭제는 추천 목록 구성이 바뀌는 정책 변경이다. 측정 결과와 함께 다시 묻는다 — 지금 미리 답을 정하지 않는다
2. **상품 수 예상치.** M-1이 분기점 근처로 나오면 필요하다. `[미정]`

## 승인 결과 (2026-09-06)

사용자가 A·B 전 항목과 M-1·M-2 측정을 승인했다. 순서: B-2 → B-1 → M-1 → M-2 → (결과 보고) → A-1 방향 결정 → A-1 구현. 판단 항목 1(다양화 삭제 여부)은 M-2 결과를 보여 준 뒤 다시 묻는다. 테스트를 먼저 두는 이유는 M-2의 Python 구현이 Java와 같은지 확인할 예제가 필요해서다.

### 측정 결과 (2026-09-06)

**M-1 — `script_score` vs `knn`** (로컬 ES 8.12.0, 난수 1536차원, k=30, 운영과 같은 필터, 원본 `vectorbench/knn-bench-*.json`)

| 상품 수 | `script_score` wall p50 / p95 | `knn` nc=60 | `knn` nc=150 |
|---------|-------------------------------|-------------|--------------|
| 1,000 | 4.9 / 7.3 ms | 5.0 / 6.9 ms · 일치 93% | 5.2 / 6.6 ms · 100% |
| 10,000 | 6.6 / **7.4 ms** | 5.5 / 6.4 ms · 31% | 6.5 / 7.3 ms · 56% |
| 100,000 | 64.8 / 92.3 ms | 65.6 / 200.9 ms · 5% | 23.5 / 31.5 ms · 10% |

결론 규칙(1만 건 p95 < 50ms → 전환 안 함)에 따라 **전환하지 않는다.** 분기점은 수만~10만 건 사이다. 결정 기록: [`decisions/004`](../../decisions/004-script-score-over-knn.md). 일치율은 난수 데이터라 참고값이다.

곁가지로 A-1의 근거 수치도 같은 측정에서 나왔다: k=30 응답이 벡터 포함 **1,030,914바이트**, 벡터 제외 **1,927바이트** — 535배.

**M-2 — 다양화 ON/OFF** (상품명 154개 · 카테고리 14개를 `gemini-embedding-001`로 임베딩, 기준 상품 20개, 원본 `vectorbench/diversity-20260906-145847.json`, 도구 `scripts/vectorbench/diversity.py` — Python 구현은 `MaxMinDiversityServiceTest`와 같은 예제로 자기 검증)

| k | 팔 | 후보 수 | 같은 카테고리 쌍 비율 | 기준과의 평균 유사도 | 상호 거리 |
|---|-----|---------|----------------------|---------------------|-----------|
| 5 | OFF | — | **45.0%** | 0.794 | 0.238 |
| 5 | ON ×2 | 10 | 14.0% (−31.0pp) | 0.770 (−0.024) | 0.280 |
| 5 | ON ×3 (현재) | 15 | 11.5% (−33.5pp) | 0.758 (−0.036) | 0.298 |
| 5 | ON ×5 | 25 | 7.5% (−37.5pp) | 0.740 (−0.053) | 0.323 |
| 10 | OFF | — | **25.8%** | 0.771 | 0.255 |
| 10 | ON ×2 | 20 | 11.9% (−13.9pp) | 0.744 (−0.027) | 0.298 |
| 10 | ON ×3 (현재) | 30 | 8.6% (−17.2pp) | 0.727 (−0.044) | 0.321 |
| 10 | ON ×5 | 50 | 5.4% (−20.3pp) | 0.707 (−0.064) | 0.338 |

결론 규칙(중복률 −10pp 이상 **그리고** 유사도 손실 0.05 미만)을 ×2·×3이 만족하고 ×5는 손실이 넘는다. **다양화는 유지한다.** ×3은 규칙 안에서 효과가 가장 큰 값이고, ×2는 비용(후보 수·전송량)이 3분의 2다. 이 선택과 A-1 방향은 사용자 결정 — 판단 항목 1 갱신.

계획과 다른 점: 상품명은 250개·22개 카테고리가 아니라 **154개·14개 하위 카테고리**로 잤다(상위 카테고리는 상품이 직접 붙지 않는다). Gemini 무료 티어 한도 때문에 10개씩 7초 간격으로 보냈다.

### 구현 결과 (2026-09-06)

B-1(인덱스 플래그)·B-2(테스트 8건: 다양화 6·topK 2) 반영. A-1은 M-2 결과를 본 사용자 결정 **(c) — ×3 유지 + 실측치 기록**으로 닫았다: `VectorRecommendPipeline.CANDIDATE_MULTIPLIER`·`VectorProductSearchService` Javadoc과 스펙 리스크 표에 비용(k=30 1,030KB vs 2KB)과 효과(45%→11.5%)를 적었다. 코드 상수는 그대로다.
