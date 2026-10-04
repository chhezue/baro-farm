# 임베딩 유사도 검색

상품 임베딩 · 벡터 검색 · 다양화 · 유사 상품 추천을 다룬다. 상품명을 벡터로 만들어 상품 문서에 함께 저장하고, 어떤 벡터(기준 상품 또는 사용자 프로필)와 가까운 상품을 Elasticsearch에서 찾아 서로 비슷한 것끼리 몰리지 않게 골라내는 작업 단위의 스펙이다. 유사 상품 추천 API가 이 단위의 얼굴이고, [`taste-vector`](../taste-vector/taste-vector-pipeline.md)의 개인화 추천도 같은 검색·다양화 코드를 쓴다. 감사([`audits/vector-similarity/`](../../audits/vector-similarity/))가 이 문서를 계약값의 근거로 삼는다.

> 상태: Implemented (2026-09-04 코드 기준 역작성)
> MVP: In scope
> 관련 BR: N/A

## 언제 이 문서를 보는가

- 벡터 검색 방식(`script_score` 전수 계산)과 다양화 알고리즘의 실제 동작을 확인할 때
- 임베딩 모델·차원·ES 매핑을 바꾸기 전에 지금 값을 확인할 때
- "Medoid"라는 코드명이 실제로 무엇을 하는지 설명해야 할 때

## 목표

기준 벡터와 코사인 유사도가 높은 상품을 넉넉히 뽑은 뒤, 그중 서로 멀리 떨어진 대표 상품 Top-K를 돌려준다.

## 배경

유사도 순으로만 자르면 "청송 사과·부사 사과·홍로 사과"처럼 사실상 같은 상품이 도배된다. 그래서 후보를 `topK×3`개 가져와 "이미 고른 것들과 가장 먼 후보"를 하나씩 추가하는 greedy 방식으로 다양성을 확보한다. 코드와 문서는 이를 "메도이드(Medoid)"라고 불러 왔지만 k-medoids(중심점 재계산 반복)가 아니라 **max-min(farthest-first) 다양화**다. 이 문서부터 그렇게 부른다.

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 상품 벡터 | 상품명을 임베딩 모델로 변환한 벡터(모델·차원은 데이터 모델 표) | `ProductDocument.vector` |
| 후보 규모 | 다양화 전에 ES에서 가져오는 상품 수(값은 아래 비즈니스 규칙) | `candidateSize` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED`만 후보로 삼는 고정 조건 | `buildBoolQuery` |
| 카테고리 일치 보너스 | 기준 카테고리와 같은 후보의 ES 점수에 더하는 가산점(값은 아래 비즈니스 규칙) | `buildCategoryWeightedScript` |
| max-min 다양화(코드명 메도이드) | 1순위를 고정하고, 남은 후보 중 선택된 것들과의 최소 거리가 가장 큰 후보를 순차 선택 | `MedoidDiversityService` |

## 흐름

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 상품 벡터 생성 | `ProductIndexService.indexProduct` → `ProductEmbeddingService.embedProduct` | 상품 이벤트 1건마다 상품명으로 임베딩 1회. 실패하면 벡터 `null`로 색인 계속 | [`ProductIndexService.java`](../../../src/main/java/com/barofarm/ai/search/application/ProductIndexService.java), [`ProductEmbeddingService.java`](../../../src/main/java/com/barofarm/ai/embedding/application/ProductEmbeddingService.java) |
| 2. 기준 벡터 확보 | `SimilarProductRecommendService` / `PersonalizedRecommendService` | 유사 상품은 기준 상품 문서의 벡터, 개인화는 프로필 벡터 | 각 서비스 |
| 3. 벡터 검색 | `VectorProductSearchService.findSimilarProductDocumentsByVector` | `script_score` + `cosineSimilarity(params.query_vector,'vector') + 1.0`(+보너스), 상태 필터, `mustNot ids`(자기 자신 또는 경험 상품). 후보 문서 전체(`_source`, 벡터 포함)를 가져온다 | [`VectorProductSearchService.java`](../../../src/main/java/com/barofarm/ai/search/application/VectorProductSearchService.java) |
| 4. 다양화 | `MedoidDiversityService.selectDiverseMedoids` | 벡터 없는 후보 제거 → 후보 ≤ topK면 그대로 → 아니면 max-min 선택 → 기준 벡터와의 코사인으로 재정렬 | [`MedoidDiversityService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/MedoidDiversityService.java) |
| 5. 응답 | 컨트롤러 | `ProductRecommendResponse[]` envelope 없이 반환 | `RecommendationController` |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/similar/{productId}` | 없음 | `topK` 기본 3. 기준 상품이 없거나 벡터가 없으면 빈 배열 |

개인화 추천 API는 [`taste-vector`](../taste-vector/taste-vector-pipeline.md)에 있다.

## 데이터 모델

벡터 필드·매핑 옵션·임베딩 모델의 현재 값이다. 매핑 SSOT는 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)이고, 여기서는 이 단위의 판단에 필요한 항목만 뽑았다.

| 항목 | 값 | 근거 |
|------|-----|------|
| 벡터 필드 | `product.vector`, `dense_vector`, `dims=1536`, `float[]` | `ProductDocument` |
| 사용자 벡터 | `user_profile_embeddings.userProfileVector`, `dense_vector`, `dims=1536`, `List<Double>` | `UserProfileEmbeddingDocument` |
| 매핑 옵션 | `index`·`similarity`를 명시하지 않는다. 로컬 ES 8.12·운영 ES 9.1.2 모두 기본값이 `index=true`라 HNSW 그래프가 만들어질 가능성이 높으나, `script_score`는 그 그래프를 쓰지 않는다 | `@Field(type = Dense_Vector, dims = 1536)` |
| 인덱스 생성 | 운영은 `es-init-indices` Job이 nori 애널라이저 설정만 만들고 매핑은 Spring Data가 문서 어노테이션으로 만든다. 로컬 docker-compose ES(8.12.0)에는 nori 플러그인이 없다 | `k8s/infra/elasticsearch/templates/es-init-indices-job.yaml`, `docker-compose.data.yml:108` |
| 임베딩 모델 | Gemini `gemini-embedding-001`, 출력 차원 1536 고정 | [`decisions/003`](../../decisions/003-gemini-migration.md) |

## 비즈니스 규칙

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| 후보 규모 | `min(topK × 3, 100)` | `SimilarProductRecommendService`, `PersonalizedRecommendService` |
| 유사도 점수 | `cosine + 1.0`(0~2 범위) | `VectorProductSearchService` |
| 상태 필터 | `ON_SALE`·`DISCOUNTED` | 동일 |
| 제외 상품 | 유사 상품: 자기 자신 1개. 개인화: 경험 상품 목록 | `mustNot ids` |
| 카테고리 일치 보너스 | 0.3(기준 카테고리가 있을 때). 없으면 보너스 0. `categoryMatchBonus`가 null이면 코드 기본값 0.2가 쓰이지만 호출부 2곳 모두 0.3 또는 null 카테고리라 0.2는 도달하지 않는다 | `buildScriptParams` |
| 다양화 첫 선택 | ES 점수 1순위(보너스 포함) | `medoids.add(validCandidates.get(0))` |
| 다양화 거리 | 코사인 거리 `1 − cos`. 음수 유사도(거리 계산 실패)는 후보에서 건너뜀 | `cosineDistance` |
| 최종 정렬 | 기준 벡터와의 **순수 코사인** 내림차순. 카테고리 보너스는 여기서 사라진다 | `selectDiverseMedoids` 말미 |
| 벡터 없는 상품 | 후보에서 제외. 임베딩 실패로 벡터가 없는 상품은 재색인 전까지 추천에 나오지 않는다 | `validCandidates` |
| 상품 이벤트 순서 | `product-events`는 키 없이 발행되어 같은 상품의 생성·수정 순서가 보장되지 않는다 | `baro-shopping/.../ProductEventProducer.java:26` |

## 검증 시나리오

### 정상

- [x] 기준 상품 벡터 있음 → `topK`개, 서로 다른 카테고리·이름이 섞임
- [x] 후보 ≤ topK → ES 순서 그대로

### 엣지 · 실패

- [x] 기준 상품 없음 / 벡터 없음 → 빈 배열
- [x] `topK ≤ 0` → 빈 배열
- [x] ES 예외 → 빈 배열 + ERROR 로그

### 수동 확인 (로컬)

- [ ] `GET product/_mapping`으로 `vector` 필드에 `index`·`similarity` 기본값이 실제로 붙어 있는지 확인
- [ ] 같은 기준 상품으로 `script_score`와 native `knn` 쿼리 결과·응답 시간을 비교(상품 수는 `[미정]`이라 결론은 측정 후)

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — 자동화 테스트 없음

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `script_score` 전수 계산 | 확정 | HNSW를 쓰지 않는다. 상품 수 `[미정]`이라 native kNN 전환은 측정 후 결정. 감사 대상 |
| 카테고리 보너스가 최종 순서에 반영되지 않음 | 확정 | 의도인지 결함인지 `[미정]` — 사용자 확인 |
| "메도이드" 명칭 | 확정 | 실제는 max-min 다양화. 클래스명·용어집 정정 후보 |
| 후보 조회가 벡터 포함 `_source` 전체 | 확정 | 다양화에 벡터가 필요하므로 이 경로는 필요. 키워드 검색·`findAllById`의 벡터 조회는 [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) 리스크 |
| ES 서버 버전 불일치 | 확정 | 로컬 8.12.0 / 운영 이미지 9.1.2 / Java 클라이언트 8.13.3. 운영 헬스 인디케이터가 호환 문제로 꺼져 있다. 인프라 소유자 확인 `[미정]` |
| 사용자 벡터 `List<Double>` vs 상품 벡터 `float[]` | 확정 | 양방향 변환 코드 존재. 통일 후보 |
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
| 2026-09-04 | `recommend/vector-recommendation.md`를 작업 단위 "임베딩 유사도 검색"으로 이동·재작성. 개인화 추천 API는 `taste-vector`로, 상품 임베딩은 `embedding` 스펙에서 이쪽으로 이동 |
| 2026-09-04 | "메도이드"가 실제로는 max-min 다양화임을 명시. ES 버전·매핑 옵션·이벤트 순서 리스크 추가 |
