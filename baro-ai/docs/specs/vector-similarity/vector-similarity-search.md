# 임베딩 유사도 검색

상품 임베딩 · 벡터 검색 · 다양화 · 유사 상품 추천을 다룬다. 상품명을 벡터로 만들어 상품 문서에 함께 저장하고, 어떤 벡터(기준 상품 또는 사용자 프로필)와 가까운 상품을 Elasticsearch에서 찾아 서로 비슷한 것끼리 몰리지 않게 골라내는 작업 단위의 스펙이다. 유사 상품 추천 API가 이 단위의 얼굴이고, [`taste-vector`](../taste-vector/taste-vector-pipeline.md)의 개인화 추천도 같은 검색·다양화 코드를 쓴다. 감사([`audits/vector-similarity/`](../../audits/vector-similarity/))가 이 문서를 계약값의 근거로 삼는다.

> 상태: Implemented (2026-09-05 감사 001 반영본)
> MVP: In scope
> 관련 BR: N/A

## 언제 이 문서를 보는가

- 벡터 검색 방식(`script_score` 전수 계산)과 다양화 알고리즘의 실제 동작을 확인할 때
- 임베딩 모델·차원·ES 매핑을 바꾸기 전에 지금 값을 확인할 때
- 다양화가 어떤 순서로 상품을 고르는지 설명해야 할 때

## 목표

기준 벡터와 코사인 유사도가 높은 상품을 넉넉히 뽑은 뒤, 그중 서로 멀리 떨어진 대표 상품 Top-K를 돌려준다.

## 배경

유사도 순으로만 자르면 "청송 사과·부사 사과·홍로 사과"처럼 사실상 같은 상품이 도배된다. 그래서 후보를 `topK×3`개 가져와 **유사도 순서를 지키면서 같은 카테고리를 최대 2개까지만** 담는다(2026-09-09).

2026-09-09 이전에는 "이미 고른 것들과 가장 먼 후보"를 하나씩 추가하는 greedy(max-min, farthest-first) 방식이었다. 그 방식은 첫 항목 이후로 기준 벡터를 보지 않아, 유사 상품을 찾아 달라는 요청에 **가장 덜 유사한 조합**을 돌려주고 있었다. 후보끼리 거리를 재려고 후보마다 벡터를 실어 와야 했던 것도 그 방식의 대가였다. 경위: [`audits/vector-similarity/refactor-log.md`](../../audits/vector-similarity/refactor-log.md) 2026-09-09

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 상품 벡터 | 상품명을 임베딩 모델로 변환한 벡터(모델·차원은 데이터 모델 표) | `ProductDocument.vector` |
| 후보 규모 | 다양화 전에 ES에서 가져오는 상품 수(값은 아래 비즈니스 규칙) | `candidateSize` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED`이면서 벡터가 있는 문서만 후보로 삼는 고정 조건 | `buildBoolQuery` |
| 카테고리 상한 | 유사도 순서를 지키면서 같은 `productCategoryCode`를 최대 2개까지만 담는다. 상한 때문에 개수가 모자라면 남은 후보로 채운다 | `VectorRecommendPipeline.selectWithCategorySpread` |
| 추천 공통 경로 | 기준 벡터를 받아 후보 조회 → 다양화 → 응답 변환까지 수행. 두 추천 서비스가 공유한다 | `VectorRecommendPipeline` |

## 흐름

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 상품 벡터 생성 | `ProductIndexService.indexProducts` → `ProductEmbeddingService.embedProducts` | 상품 묶음당 `batchEmbedContents` 1회. 묶음 크기는 호출 경로가 정한다 — Kafka 이벤트는 poll당 최대 5건, 재색인 API는 한 페이지(최대 100건). 묶음 호출이 실패하면 상품별로 폴백하고, 그래도 실패하면 벡터 `null`로 색인 계속 | [`ProductIndexService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductIndexService.java), [`ProductEmbeddingService.java`](../../../src/main/java/com/barofarm/ai/embedding/application/ProductEmbeddingService.java) |
| 2. 기준 벡터 확보 | `SimilarProductRecommendService` / `PersonalizedRecommendService` | 유사 상품은 기준 상품 문서의 벡터, 개인화는 프로필 벡터. 이후 단계는 공통 경로에 넘긴다 | 각 서비스 |
| 3. 벡터 검색 | `VectorRecommendPipeline.recommend` → `VectorProductSearchService.findSimilarProductDocumentsByVector` | `script_score` + `cosineSimilarity(params.query_vector,'vector') + 1.0`, 상태·`exists(vector)` 필터, `mustNot ids`(자기 자신 또는 경험 상품). **응답에서 벡터는 제외한다**(`_source` 필터). 2026-09-09에 고르기가 카테고리 상한으로 바뀌면서 앱이 후보 벡터를 볼 이유가 없어졌다 | [`VectorProductSearchService.java`](../../../src/main/java/com/barofarm/ai/search/application/VectorProductSearchService.java) |
| 4. 고르기 | `VectorRecommendPipeline.selectWithCategorySpread` | 유사도 순으로 훑으며 카테고리당 2개까지 담고, 모자라면 남은 후보로 채운다. 후보는 이미 ES가 유사도 순으로 정렬해 주므로 다시 정렬하지 않는다 | [`VectorRecommendPipeline.java`](../../../src/main/java/com/barofarm/ai/recommend/application/VectorRecommendPipeline.java) |
| 5. 응답 | 컨트롤러 | `ResponseDto<ProductSummaryResponse[]>` — 검색 API와 같은 envelope(2026-09-05 통일) | `RecommendationController` |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/similar/{productId}` | 없음 | `topK` 기본 3, 허용 1~50. 기준 상품이 없거나 벡터가 없으면 빈 배열 |

개인화 추천 API는 [`taste-vector`](../taste-vector/taste-vector-pipeline.md)에 있다.

### 에러

범위를 벗어난 추천 개수는 400으로 끊는다(2026-09-05 신설, 상한은 2026-09-09에 33 → 50). 그전에는 `topK=0`·음수가 조용히 빈 배열로 나가 원인을 알 수 없었다. 개인화 추천 API에도 같은 코드가 적용된다.

| 코드 | HTTP | 조건 |
|------|------|------|
| `INVALID_TOP_K` | 400 | `topK < 1` 또는 `topK > 50` (2026-09-09에 33에서 바뀜) |
| `SEARCH_UNAVAILABLE` | 503 | 벡터 후보 조회 자체가 실패(연결 불가·쿼리 오류·인덱스 없음). 2026-09-08 신설 |

**"추천할 것이 없다"와 "검색 엔진이 죽었다"를 구분한다**(2026-09-08). 기준 상품이 없거나 벡터가 아직 없거나 조건에 맞는 상품이 없는 것은 에러가 아니라 **200 + 빈 배열**이다 — 사용자 상태이지 장애가 아니기 때문이다. 반면 조회 자체가 실패한 것은 **503**으로 알린다. 그전에는 둘 다 200 + 빈 배열이라, 엔진이 통째로 죽어도 화면이 비는 것 말고는 아무 흔적이 없었다.

## 데이터 모델

벡터 필드·매핑 옵션·임베딩 모델의 현재 값이다. 매핑 SSOT는 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)이고, 여기서는 이 단위의 판단에 필요한 항목만 뽑았다.

| 항목 | 값 | 근거 |
|------|-----|------|
| ES 접속 | `spring.elasticsearch.*`(연결 대기 1초·응답 대기 **3초**)만으로 구성. 설정 클래스 없음. 30초는 2026-09-09에 3초로 낮췄다 — 실제 조회가 1만 건에서도 p95 8ms 미만이라 근거 없는 기본값이었다 | `application.yml` |
| 벡터 필드 | `product.vector`, `dense_vector`, `dims=768`, `float[]` | `ProductDocument` |
| 사용자 벡터 | 저장하지 않는다. 개인화 추천 요청 시 행동 로그로 계산해 만든 768차원 `float[]`을 그대로 쓴다(2026-09-08) | `TasteProfile`, `UserProfileEmbeddingService.computeProfile` |
| 매핑 옵션 | `index`·`similarity`를 명시하지 않는다. 로컬 ES 8.12·운영 ES 9.1.2 모두 기본값이 `index=true`라 HNSW 그래프가 만들어질 가능성이 높으나, `script_score`는 그 그래프를 쓰지 않는다 | `@Field(type = Dense_Vector, dims = 768)` |
| 인덱스 생성 | 운영은 `es-init-indices` Job이 nori 애널라이저 설정만 만들고 매핑은 Spring Data가 문서 어노테이션으로 만든다. 로컬 docker-compose ES(8.12.0)에는 nori 플러그인이 없다 | `k8s/infra/elasticsearch/templates/es-init-indices-job.yaml`, `docker-compose.data.yml:108` |
| 임베딩 모델 | Gemini `gemini-embedding-001`, 출력 차원 768 고정(공식 권장값 768·1536·3072 중 하나) | [`decisions/003`](../../decisions/003-gemini-migration.md) · 차원 근거는 [`audits/vector-similarity/003`](../../audits/vector-similarity/003-embedding-dims-and-candidate-multiplier.md) A-1 |

## 비즈니스 규칙

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| `topK` 허용 범위 | 1~50(`VectorRecommendPipeline.MAX_TOP_K`). 벗어나면 400 `INVALID_TOP_K`(조회 시작 전에 끊는다). 2026-09-09에 다양화를 없애면서 배수 근거(100÷3=33)가 사라져 50으로 되돌렸다(변경 이력) | `VectorRecommendPipeline.validateTopK` |
| 후보 규모 | `topK × 3` (상한 없음 — `topK` 상한 50에서 최대 150). 2026-09-09에 후보 상한 100을 없앴다 | `VectorRecommendPipeline.CANDIDATE_MULTIPLIER` |
| 유사도 점수 | `cosine + 1.0`(0~2 범위) | `VectorProductSearchService` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED` | 동일 |
| 제외 상품 | 유사 상품: 자기 자신 1개. 개인화: 경험 상품 목록 | `mustNot ids` |
| 카테고리 일치 보너스 | **없다.** 두 추천 경로 모두 순수 코사인만 쓴다(2026-09-05 삭제 — 최종 정렬에 반영되지 않아 후보 선정에만 영향을 주던 값이고, 상품 벡터에 이미 카테고리 성격이 담겨 있다) | — |
| 쏠림 방지 | 유사도 순서를 그대로 두고 **같은 카테고리를 최대 2개**까지만 담는다. 그래도 `topK`가 안 차면 상한을 풀어 남은 후보로 채운다 — 상한은 쏠림을 막자는 것이지 결과 수를 줄이자는 것이 아니다 | `selectWithCategorySpread`, `MAX_PER_CATEGORY = 2` |
| 최종 정렬 | 하지 않는다. ES가 이미 유사도 순으로 돌려주므로 그 순서를 유지한다 | `VectorRecommendPipeline` |
| 벡터 없는 상품 | ES 쿼리의 `exists` 필터로 후보에서 제외한다. 임베딩 실패로 벡터가 없는 상품은 재색인 전까지 추천에 나오지 않는다 | `buildBoolQuery` |
| 상품 이벤트 순서 | `product-events`는 키 없이 발행되어 같은 상품의 생성·수정 순서가 보장되지 않는다 | `baro-shopping/.../ProductEventProducer.java:26` |

## 검증 시나리오

### 정상

- [x] 기준 상품 벡터 있음 → `topK`개, 서로 다른 카테고리·이름이 섞임
- [x] 후보 ≤ topK → ES 순서 그대로

### 엣지 · 실패

- [x] 기준 상품 없음 / 벡터 없음 → 빈 배열
- [ ] `topK ≤ 0` 또는 `topK > 50` → 400 `INVALID_TOP_K` (2026-09-05 신설, 2026-09-07에 50 → 33, 2026-09-09에 다시 50)
- [x] ES 예외 → **503 `SEARCH_UNAVAILABLE`** + ERROR 로그 + `search.query.failures` 증가 (2026-09-08 변경, 그전에는 빈 배열)

### 수동 확인 (로컬)

2026-09-05 리팩터 시점에 로컬 ES를 띄우지 못해 아래는 모두 **미확인**이다.

- [ ] 벡터 없는 판매 상품 1건을 직접 색인한 뒤 유사 상품 API가 정상 목록을 돌려주는지(감사 001 A-1 검증 절차)
- [ ] `ElasticsearchConfig` 삭제 후 기동해 리포지토리·템플릿 빈이 자동설정으로 뜨는지, 유사 상품 API가 응답하는지
- [ ] 라우팅되지 않는 주소로 기동해 `spring.elasticsearch.connection-timeout` 값대로 응답이 지연되는지(설정이 살아 있다는 증거)
- [ ] 개인화 API가 요청 시 계산한 768차원 프로필로 정상 응답하는지(`float[]` 전환 확인). 2026-09-08부터 프로필을 저장하지 않으므로 "기존 프로필 문서"를 준비할 필요가 없다
- [ ] `GET product/_mapping`으로 `vector` 필드에 `index`·`similarity` 기본값이 실제로 붙어 있는지 확인
- [x] `script_score`와 native `knn`의 응답 시간을 상품 1천·1만·10만 건에서 비교 — 2026-09-06 난수 벡터로 실측, 전환 안 함([`decisions/004`](../../decisions/004-script-score-over-knn.md)). 실제 상품 벡터로 일치율을 다시 보는 것은 상품 수가 수만 건을 넘을 때

## 완료 기준

- [x] 기능 구현
- [ ] 위 "수동 확인 (로컬)" 절차 — 로컬 ES 미기동으로 2026-09-05 리팩터에서는 수행하지 못했다
- [x] `./gradlew :baro-ai:test` — 통과. 이 단위 몫은 `VectorRecommendPipelineTest` 7건(고르기 규칙 5 + `topK` 경계 2)이고, 상품 임베딩은 `ProductEmbeddingServiceTest` 4건이 따로 본다. 후보 조회는 통합 테스트(`ProductIndexAndSearchIntegrationTest`)가 실물 ES로 확인한다

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `script_score` 전수 계산 | 확정(유지) | HNSW를 쓰지 않는다. 2026-09-06 실측: 1만 건 p95 7.4ms로 `knn`과 지연이 같고, 10만 건에서야 92ms vs 32ms로 갈린다. 상품이 수만 건을 넘으면 다시 연다 — [`decisions/004`](../../decisions/004-script-score-over-knn.md) |
| 카테고리 보너스 | 해소 | 2026-09-05 두 경로 모두 삭제 |
| "메도이드" 명칭 | 해소 | 2026-09-05 `MaxMinDiversityService`로 개명 → 2026-09-09 그 클래스를 삭제하고 `VectorRecommendPipeline.selectWithCategorySpread`로 흡수 |
| ~~후보 조회가 벡터 포함 `_source` 전체~~ | **해소(2026-09-09)** | 벡터를 받던 이유가 max-min 다양화였고, 카테고리 상한으로 바꾸면서 `_source`에서 벡터를 뺐다. 아래는 그전 근거다 — 2026-09-06 실측: k=30 응답이 벡터 포함 약 1,030KB·제외 약 2KB, 기본 topK 5면 후보 15건 ≈ 315KB. 그 대가로 같은 카테고리 쌍이 45%→11.5%(k=5)라 유지 — [`audits/vector-similarity/002`](../../audits/vector-similarity/002-vector-transfer-knn-and-diversity.md) M-2. 키워드 검색·`findAllById`의 벡터 조회는 [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) 리스크 |
| ES 서버 버전 불일치 | 확정 | 로컬 8.12.0 / 운영 이미지 9.1.2 / Java 클라이언트 8.13.3. 운영 헬스 인디케이터가 호환 문제로 꺼져 있다. 인프라 소유자 확인 `[미정]` |
| 사용자 벡터 `List<Double>` vs 상품 벡터 `float[]` | 해소 | 2026-09-05 `float[]`로 통일. 다만 ES 스크립트 파라미터로 넘길 때의 `List<Double>` 변환은 남아 있다 — `JsonData.of(float[])`가 숫자 배열로 직렬화되는지 로컬에서 확인하지 못했다 |
| 임베딩 실패 상품의 재색인 경로 없음 | 확정 | [`audits/search/001`](../../audits/search/001-index-path.md) 개선 제안 1 (Track A) |
| 상품 이벤트 순서 미보장 | 확정 | 발행 모듈 소관. 팀 확인 `[미정]` |

## 관련 문서

- [`taste-vector-pipeline.md`](../taste-vector/taste-vector-pipeline.md) — 이 검색을 쓰는 개인화 추천
- [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) — 상품 벡터가 만들어지는 색인 경로
- [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md) — 매핑 SSOT
- [`decisions/003-gemini-migration.md`](../../decisions/003-gemini-migration.md)
- [`product/glossary.md`](../../product/glossary.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-09 | 다양화를 Greedy Max-Min에서 **카테고리 상한(같은 카테고리 최대 2개)**으로 교체 — 옛 방식은 첫 항목 이후 기준 벡터를 보지 않아 "가장 덜 유사한 조합"을 돌려주고 있었다. 후보 응답에서 벡터를 빼고(문서당 약 10KB) `topK` 상한을 33 → 50으로 올렸다 |
| 2026-09-08 | 벡터 후보 조회 실패를 빈 목록이 아니라 `SEARCH_UNAVAILABLE`(503)로 알리도록 변경 — 장애와 "추천할 것 없음"이 같은 응답이던 문제. 사용자 벡터는 저장되지 않고 요청 시 계산된 값을 받는다 |
| 2026-09-04 | `recommend/vector-recommendation.md`를 작업 단위 "임베딩 유사도 검색"으로 이동·재작성. 개인화 추천 API는 `taste-vector`로, 상품 임베딩은 `embedding` 스펙에서 이쪽으로 이동 |
| 2026-09-04 | "메도이드"가 실제로는 max-min 다양화임을 명시. ES 버전·매핑 옵션·이벤트 순서 리스크 추가 |
| 2026-09-05 | 감사 001 A·B 전 항목 반영 — `exists(vector)` 필터, `ElasticsearchConfig` 삭제·타임아웃 설정 이관, 카테고리 보너스 삭제, `MaxMinDiversityService` 개명, `VectorRecommendPipeline` 공유, 사용자 벡터 `float[]` 통일, 음수 유사도 처리 정정 |
| 2026-09-05 | `topK` 허용 범위 1~50 신설 — 벗어나면 400 `INVALID_TOP_K`. 감사 항목이 아니라 교차 검토에서 나온 계약 변경이다(검색 API의 `page`·`size` 검증과 같은 기준). 개인화 추천 API도 같이 적용 |
| 2026-09-06 | 감사 002 반영 — `knn` 전환 여부를 실측으로 닫음(유지, `decisions/004`), 다양화 효과 실측(유지, 후보 배수 3 확정), `MaxMinDiversityService` 참조 비교 제거, 다양화·topK 단위 테스트 |
