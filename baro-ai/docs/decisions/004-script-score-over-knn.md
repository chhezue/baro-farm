# 004 — 벡터 검색은 `script_score` 선형 스캔을 유지한다 (HNSW `knn`으로 바꾸지 않는다)

> 상태: 확정 (2026-09-06)

유사 상품·개인화 추천의 후보 조회를 Elasticsearch native `knn`(HNSW 근사 검색)으로 바꿀지에 대한 결정이다. **바꾸지 않는다.** 근거는 상품 수 3단계에서 실측한 지연이다.

## 맥락

`VectorProductSearchService`는 `script_score` + `cosineSimilarity`로 필터를 통과한 문서 전부를 선형 스캔한다. 그런데 서버 기본값(`index: true`)이라 **HNSW 그래프는 색인 시 이미 만들어지고 있다** — 그래프를 만들어 놓고 조회에서 쓰지 않는 상태다([`audits/vector-similarity/001`](../audits/vector-similarity/001-vector-search-and-diversity.md) C-1). "그러니 `knn`으로 바꾸자"가 자연스러운 다음 문장이지만, 이 이니셔티브의 근거 기준은 "일반적으로 좋다"를 근거로 인정하지 않는다. 그래서 잰 뒤 정했다.

## 결정

- **`script_score`를 유지한다.** 상품 1만 건까지는 p95 8ms 미만이라 바꿀 이유가 없다.
- 상품이 **수만 건을 넘어 10만 건에 가까워지면** 이 결정을 다시 연다. 그때의 판단 재료(재현율 손실·`num_candidates` 튜닝)도 아래에 미리 적어 둔다.
- 결론 규칙은 측정 **전에** 감사 문서에 적었다: "1만 건에서 p95 50ms 미만이면 전환하지 않는다"([`audits/vector-similarity/002`](../audits/vector-similarity/002-vector-transfer-knn-and-diversity.md) M-1).

## 근거

2026-09-06 로컬 ES 8.12.0에서 1536차원 난수 벡터, k=30, 운영과 같은 필터(판매 상태·벡터 존재)로 쟀다.

| 상품 수 | `script_score` p50 / p95 | `knn` nc=60 p50 / p95 · 일치율 | `knn` nc=150 p50 / p95 · 일치율 |
|---------|--------------------------|-------------------------------|--------------------------------|
| 1,000 | 4.9 / 7.3 ms | 5.0 / 6.9 ms · 93% | 5.2 / 6.6 ms · 100% |
| 10,000 | 6.6 / **7.4 ms** | 5.5 / 6.4 ms · 31% | 6.5 / 7.3 ms · 56% |
| 100,000 | 64.8 / 92.3 ms | 65.6 / 200.9 ms · 5% | **23.5 / 31.5 ms** · 10% |

(wall = 클라이언트 왕복. 원본: `audits/vector-similarity/vectorbench/knn-bench-20260906-144800.json`·`-145920.json`, 도구: `scripts/vectorbench/knn_bench.py`)

읽는 법:

- **1만 건까지 두 방식의 지연이 같다.** 선형 스캔이 8ms 안에 끝나는 규모에서 근사 검색은 얻는 게 없고, 대신 결과가 정확 계산과 달라진다(일치율 31~56%).
- **10만 건에서 갈린다.** 선형 스캔 92ms vs `knn`(nc=150) 32ms. 여기서부터는 바꿀 가치가 생기지만, 그때도 `num_candidates`를 k의 5배로 줘야 하고(2배면 오히려 느렸다 — 필터와 그래프 탐색이 엇갈린다) 일치율은 10%다.
- **일치율은 참고값이다.** 난수 벡터는 서로 거의 직교해 HNSW가 가장 불리한 데이터다. 실제 상품 임베딩은 군집이 있어 일치율이 훨씬 높을 것이다 — 그래서 지연은 그대로 믿고, 일치율은 "근사 검색은 정확 계산과 다르다"는 사실만 취한다.

## 고려한 대안

| 대안 | 기각 이유 |
|------|-----------|
| 지금 `knn`으로 전환 | 1만 건에서 이득 0. Spring Data ES 5.3.6은 최상위 `knn` 옵션 빌더가 없어(`withKnnQuery`만) 클라이언트 코드도 우회가 필요하다. 이득 없는 변경에 우회 비용까지 |
| `index: false`로 매핑해 HNSW 그래프 생성 비용을 없애기 | 그래프를 안 쓰는 건 사실이지만, 나중에 전환할 때 전체 재색인이 필요해진다. 색인은 상품 이벤트 때만 일어나 그래프 비용이 조회 경로에 없다 — 남겨 둔다 |
| 상품 수 예상치를 물어 정하기 | 예상치는 `[미정]`이고, 3단계를 다 재면 예상치 없이도 결론이 난다 |

## 트레이드오프

유지하는 대가로 감수하는 것들이다.

| 항목 | 내용 |
|------|------|
| 만들어 놓고 안 쓰는 그래프 | HNSW 그래프는 색인 때마다 계속 만들어진다. 조회 경로에는 비용이 없지만 색인 시간·디스크는 쓴다. 나중에 전환할 때 재색인 없이 바로 쓰기 위해 남긴다 |
| 분기점을 지켜봐야 한다 | 상품 수가 수만 건에 가까워지면 이 결정을 다시 열어야 하는데, 그걸 알려 주는 장치는 없다. `GET product/_count`를 사람이 본다 |
| 갑작스러운 전환 | 분기점을 놓치면 추천 지연이 먼저 나빠진 뒤에야 전환하게 된다. 그때는 `num_candidates` 튜닝과 실데이터 일치율 측정을 같이 해야 해서 하루짜리 작업이 아니다 |

## 영향

코드 변경은 없다. `VectorProductSearchService` Javadoc에 이 결정과 근거 경로를 적었고, 스펙 리스크 표의 "측정 후 결정" 문구를 "유지"로 바꿨다. 측정 도구(`scripts/vectorbench/knn_bench.py`)는 남겨 두어 상품 수가 늘었을 때 같은 조건으로 다시 잴 수 있다.

## 다시 열 때 볼 것

- `GET product/_count`가 5만 건을 넘는가
- 그 시점의 실제 상품 벡터로 `knn_bench.py`를 다시 돌려 일치율을 본다 — 난수가 아니라 실데이터라야 재현율 판단이 된다
- `num_candidates`는 k의 5배부터 시작한다(2배는 이번 측정에서 손해였다)

## 관련 문서

- [`audits/vector-similarity/002`](../audits/vector-similarity/002-vector-transfer-knn-and-diversity.md) — 측정 계획·결론 규칙
- [`audits/vector-similarity/001`](../audits/vector-similarity/001-vector-search-and-diversity.md) C-1 — 매핑 기본값·클라이언트 지원 확인
- [`specs/vector-similarity/vector-similarity-search.md`](../specs/vector-similarity/vector-similarity-search.md)
