# baro-ai Elasticsearch 구조 및 임베딩 파이프라인 상세 문서

이 문서는 `baro-ai` 모듈에서 Elasticsearch가 어떻게 구현되어 있는지, 현재 스키마 구조, 검색 로직(키워드 + 자동완성), 그리고 상품 임베딩 파이프라인을 코드 기준으로 정리한 문서다.

---

## 1) 전체 아키텍처 개요

`baro-ai`는 검색/추천/로그를 Elasticsearch에 저장하고 조회하는 구조다.

- 검색 인덱스: `product`
- 자동완성 인덱스: `product-autocomplete`
- 사용자 프로필 벡터 인덱스: `user_profile_embeddings`
- 로그 인덱스: 검색/장바구니/주문 로그 전용 도큐먼트 인덱스

핵심 특징:

- 키워드 검색: `match_phrase` + `match` + `fuzzy` 가중치 조합
- 자동완성: `edge_ngram` analyzer + `match_phrase_prefix`
- 벡터 검색: `dense_vector(1536)` + `script_score(cosineSimilarity)`
- 인덱싱 트리거: `product-events` Kafka 소비 기반 실시간 반영

---

## 2) Elasticsearch 연결 및 런타임 설정

### 2.1 연결 설정

- `spring.elasticsearch.uris`를 통해 ES 엔드포인트를 주입 (`application-prod.yml`)
- `ElasticsearchConfig`에서 `RestClient`, `ClientConfiguration` Bean 구성

운영 설정 포인트:

- 기본 URI: `http://elasticsearch:9200`
- connect timeout: 10초
- socket timeout: 30초

### 2.2 Spring AI VectorStore 설정과 실제 사용 방식

Spring AI의 VectorStore 추상화는 쓰지 않는다. 예전에는 `spring-ai-elasticsearch-store` 의존성을 두고
`application-prod.yml`에서 그 자동설정을 제외했는데, 실제로 쓰는 코드가 없어 2026-09-04에 의존성과
제외 설정을 함께 제거했다.

즉, 현재 구조는 Spring AI의 VectorStore 추상화 자동구성이 아니라,  
Spring Data Elasticsearch + 커스텀 쿼리(`NativeQuery`) 기반 직접 구현이다.

---

## 3) 인덱스 스키마 구조

## 3.1 `product` 인덱스 (상품 검색 + 벡터 검색)

매핑 클래스: `ProductDocument` (`@Document(indexName = "product")`)

- `productId`: `@Id` (UUID)
- `productName`: `@MultiField`
  - main: `text` + `nori` analyzer (일반 검색용)
  - subfield: `productName.raw` = `keyword` (fuzzy 검색용)
- `productCategoryId`: `keyword`
- `productCategoryCode`: `keyword`
- `productCategoryName`: `keyword`
- `price`: `long`
- `status`: `keyword`
- `updatedAt`: `date`
- `ingredientName`: `keyword` — 상품명에서 뽑은 요리 재료명 (예: "계란"). 색인 시점에 1회 계산
- `vector`: `dense_vector`, `dims=1536`

설계 의도:

- 텍스트 검색(형태소 기반)과 exact/raw 비교를 멀티필드로 분리
- 추천/유사도 검색을 위해 벡터를 동일 도큐먼트에 포함
- `ingredientName`은 레시피 추천이 읽는다. 상품이 바뀔 때만 달라지는 값이라 추천 요청마다 LLM으로 다시 뽑지 않고 상품 도큐먼트에 저장해 둔다 — 근거: [`decisions/002-ingredient-name-at-index-time.md`](decisions/002-ingredient-name-at-index-time.md)

## 3.2 `product-autocomplete` 인덱스 (자동완성)

매핑 클래스: `ProductAutocompleteDocument` (`@Document(indexName = "product-autocomplete")`)

- `productId`: `@Id` (UUID)
- `productName`: `text`, analyzer=`autocomplete_analyzer`, searchAnalyzer=`standard`
- `status`: `keyword`

인덱스 세팅 파일:

- `settings/autocomplete-analyzer.json`
  - `edge_ngram_tokenizer` (`min_gram=1`, `max_gram=20`)
  - `token_chars`: `letter`, `digit`
  - `lowercase` filter

즉, 색인 시 prefix 조각을 만들어 자동완성 응답 속도를 높이고, 검색 시 표준 analyzer로 입력어를 처리한다.

## 3.3 `user_profile_embeddings` 인덱스 (개인화 추천용)

매핑 클래스: `UserProfileEmbeddingDocument` (`@Document(indexName = "user_profile_embeddings")`)

- `userId`: `@Id`
- `userProfileVector`: `dense_vector`, `dims=1536` (타입은 `List<Double>`)
- `lastUpdatedAt`: `date`
- `sourceSearchLogIds`: `keyword[]` (최대 5개 의도)
- `sourceCartLogIds`: `keyword[]` (최대 5개 의도)
- `sourceOrderLogIds`: `keyword[]` (최대 5개 의도)
- `sourceProductIds`: `keyword[]` (cart/order 기반, 중복 제거)
- `preferredCategoryCode`: `keyword`

---

## 4) 검색 구조 상세

## 4.1 API 엔드포인트

`SearchController`

- `GET /api/v1/search`
  - 파라미터: `q`, `categories`, `priceMin`, `priceMax`, `page`, `size`
  - optional header: `X-User-Id`
- `GET /api/v1/search/autocomplete`
  - 파라미터: `q`, `size`

## 4.2 키워드 검색 쿼리 (`ProductSearchService`)

`NativeQuery` 기반 `bool` 쿼리를 생성한다.

### should 조합

1. 정확 구문 검색 (`match_phrase`)
- field: `productName`
- `slop=1`
- boost: `2.0`

2. 일반 키워드 검색 (`match`)
- field: `productName`
- operator: `OR`
- boost: `1.0`

3. 오탈자 허용 (`fuzzy match`)
- 조건: 검색어 길이 3 이상일 때만
- field: `productName.raw` (`keyword`)
- `fuzziness=AUTO`
- `prefixLength=1`
- boost: `0.3`

추가 규칙:

- `minimum_should_match = 1`

### filter 조합

- `status` in `ON_SALE`, `DISCOUNTED`
- `productCategoryCode` terms 필터 (옵션)
- `price` range 필터 (옵션)

### 정렬

1) `_score desc`  
2) `updatedAt desc`

### 부가 동작

`X-User-Id` + 검색어가 있으면:

- 검색 로그 저장
- 사용자 프로필 임베딩 비동기 갱신 (`updateUserProfileAsync`)

검색 실패와 로그 저장 실패를 분리해, 로그 처리 오류가 검색 결과를 깨지 않도록 설계되어 있다.

---

## 5) 자동완성 구조 상세

## 5.1 인덱싱

상품 인덱싱 시 `ProductIndexService.indexProduct()`가

- `product` 인덱스 저장
- `product-autocomplete` 인덱스 저장

을 동시에 수행한다.

## 5.2 조회 쿼리

`ProductAutocompleteRepository.findByPrefix`의 `@Query`:

- `must`: `match_phrase_prefix(productName, query=?0, max_expansions=10)`
- `filter`: `status` in `ON_SALE`, `DISCOUNTED`

`ProductIndexService.autocomplete()`:

- 2글자 미만 입력 시 빈 결과 반환
- `PageRequest.of(0, size)`로 상위 N개
- `@Cacheable("autocomplete", key = "#query + '-' + #size")`

## 5.3 Completion Suggester 사용 여부

현재 코드 기준으로 Elasticsearch `completion` field / completion suggester는 사용하지 않는다.  
자동완성은 `edge_ngram` + `match_phrase_prefix` 전략이다.

---

## 6) 벡터 검색 및 추천 구조

## 6.1 상품 벡터 저장

`ProductDocument.vector`에 1536차원 float 벡터를 저장한다.

벡터 생성은 `ProductEmbeddingService.embedProduct(productName)`에서 수행:

- 모델 빈: `googleGenAiTextEmbedding`
- 기본 모델명: `gemini-embedding-001`, 출력 차원 1536 고정 (`application-prod.yml`)
- 실패 시 예외를 던지며, 호출부(`ProductIndexService`)에서 캐치하여 인덱싱은 계속 진행

## 6.2 벡터 검색 쿼리 (`VectorProductSearchService`)

벡터 유사도 검색은 ES `script_score` 사용:

- 기본 스크립트:
  - `cosineSimilarity(params.query_vector, 'vector') + 1.0`
- bool 필터:
  - `status` in `ON_SALE`, `DISCOUNTED`
  - 제외 상품 ID는 `mustNot ids`

카테고리 보너스가 있는 경우:

- `productCategoryCode == target_category_code`일 때 추가 점수(`category_bonus`) 부여

주의:

- 현재 구현은 native `knn` DSL 기반 ANN 쿼리가 아니라 `script_score` 방식이다.

## 6.3 추천 서비스와 연계

- 유사상품 추천: `SimilarProductRecommendService`
  - 기준 상품 벡터로 후보(topK*3) 검색 후 메도이드 다양성 적용
- 개인화 추천: `PersonalizedRecommendService`
  - 사용자 프로필 벡터로 후보 검색
  - 이미 경험한 상품(`sourceProductIds`) 제외
  - 선호 카테고리 보너스 적용
  - 메도이드 다양성 적용

---

## 7) 상품 임베딩 파이프라인 (실시간 인덱싱)

## 7.1 이벤트 입력

`ProductEventConsumer`가 Kafka `product-events` 토픽 구독:

- `PRODUCT_CREATED` -> `indexProduct`
- `PRODUCT_UPDATED` -> `indexProduct` (업데이트 재색인)
- `PRODUCT_DELETED` -> `deleteProduct`

## 7.2 인덱싱 처리 (`ProductIndexService`)

`indexProduct(request)` 처리 순서:

1. 상품명 임베딩 생성 시도 (`embedProduct`)
2. 요리 재료명 추출 시도 (`ProductIngredientNormalizer.normalize`) — LLM 1회, 실패 시 정규식 폴백
3. `ProductDocument` 생성 (`updatedAt=now`, `ingredientName`·`vector` 포함)
4. `ProductAutocompleteDocument` 생성
5. `product-autocomplete` 저장
6. `product` 저장

임베딩 실패 시에도 색인은 계속 진행되므로, 일부 문서는 `vector`가 null/빈 상태일 수 있다.
재료명 추출도 마찬가지로 실패해도 색인을 막지 않는다 — 다만 `ingredientName`이 빈 문서는 레시피 추천에서 그 상품을 보유 재료로 세지 못하므로, 빈 값이 쌓이면 재색인이 필요하다.

## 7.3 삭제 처리

`deleteProduct(productId)`에서:

- `product` 삭제
- `product-autocomplete` 삭제

동시에 정리한다.

---

## 8) 사용자 프로필 임베딩 파이프라인 (개인화 추천 보조)

검색 API 호출 후 조건 충족 시 `updateUserProfileAsync(userId)` 실행:

1. 최근 30일 로그 수집 (search/cart/order, 각 최대 5개)
2. 로그 수가 최소치(총 3개) 미만이면 스킵
3. 가중치(이벤트 타입/수량/시간 감쇠) 반영 대표 텍스트 생성
4. Gemini 임베딩 생성
5. 선호 카테고리 코드 계산
6. `user_profile_embeddings` 저장

이 벡터는 개인화 추천 검색 시 query vector로 사용된다.

---

## 9) 벡터 DB 연동 관점 정리

질문: "별도 벡터 DB 연동이 있는가?"

현재 코드 기준 결론:

- 별도 벡터 DB(Pinecone/Weaviate/Qdrant/pgvector) 연동 없음
- Elasticsearch `dense_vector`에 직접 저장 + `script_score`로 검색
- 의존성도 ES 중심(`spring-boot-starter-data-elasticsearch`)

즉, 현행 구조는 **Elasticsearch 단일 스토어 기반(텍스트 + 벡터 통합)** 이다.

---

## 10) 운영 관점 체크 포인트

1. `script_score` 벡터 검색은 데이터가 커질수록 비용이 커질 수 있어, 트래픽/문서수 증가 시 성능 검증 필요
2. 임베딩 실패 허용 전략으로 인해 일부 상품은 벡터가 없을 수 있으므로 추천 품질 모니터링 필요
3. 자동완성은 `edge_ngram` 기반이라 색인 크기 증가 및 언어 특성(한글 형태소) 튜닝 여지 존재
4. 현재는 status 필터가 검색/자동완성/벡터검색에 일관 적용되어 노출 안정성은 높음

---

## 11) 관련 코드 위치 모음

- ES 설정: `src/main/java/com/barofarm/ai/config/ElasticsearchConfig.java`
- 상품 스키마: `src/main/java/com/barofarm/ai/search/domain/ProductDocument.java`
- 자동완성 스키마: `src/main/java/com/barofarm/ai/search/domain/ProductAutocompleteDocument.java`
- 자동완성 analyzer: `src/main/resources/settings/autocomplete-analyzer.json`
- 검색 서비스: `src/main/java/com/barofarm/ai/search/application/ProductSearchService.java`
- 자동완성 저장/조회: `src/main/java/com/barofarm/ai/search/application/ProductIndexService.java`
- 자동완성 리포지토리: `src/main/java/com/barofarm/ai/search/infrastructure/elasticsearch/ProductAutocompleteRepository.java`
- 벡터 검색 서비스: `src/main/java/com/barofarm/ai/search/application/VectorProductSearchService.java`
- 상품 임베딩: `src/main/java/com/barofarm/ai/embedding/application/ProductEmbeddingService.java`
- 상품 이벤트 소비: `src/main/java/com/barofarm/ai/search/infrastructure/event/ProductEventConsumer.java`
- 사용자 프로필 벡터: `src/main/java/com/barofarm/ai/embedding/domain/UserProfileEmbeddingDocument.java`
- 사용자 프로필 임베딩 서비스: `src/main/java/com/barofarm/ai/embedding/application/UserProfileEmbeddingService.java`
- 운영 설정: `src/main/resources/application-prod.yml`
- 의존성: `build.gradle`
