# Elasticsearch 인덱스 매핑

`baro-ai`가 만들고 읽는 Elasticsearch 인덱스의 매핑·analyzer·연결 설정을 코드 기준으로 적은 참조 문서다. **매핑의 SSOT는 각 `@Document` 클래스**이고 이 문서는 그것을 한 자리에 옮겨 적은 것이다 — 어긋나면 코드가 맞다. 쿼리가 어떻게 조립되는지, 색인·추천 흐름이 어떻게 도는지는 여기 두지 않고 각 작업 단위 스펙이 담당한다(아래 "흐름·쿼리는 어디에").

## 언제 이 문서를 보는가

- 어떤 인덱스에 어떤 필드가 어떤 타입으로 있는지 확인할 때
- 필드·차원·analyzer를 바꾸기 전에 영향 범위(재색인 필요 여부)를 볼 때
- 인덱스 이름이 코드 어디에서 정해지는지 찾을 때

## 인덱스 목록

| 인덱스 | 매핑 클래스 | 무엇 | 만드는 곳 |
|--------|-------------|------|-----------|
| `product` | `search.domain.ProductDocument` | 상품 검색·자동완성·벡터 검색·재료명. 자동완성은 별도 인덱스가 아니라 이 인덱스의 서브필드(2026-09-08 통합) | 상품 이벤트 색인(`ProductIndexService`) |
| `user_behavior_logs` | `log.domain.UserBehaviorLogDocument` | 장바구니·주문 행동 로그(2026-09-09에 두 인덱스를 하나로 합침) | `CartEventConsumer`·`OrderEventConsumer` |
| `review_event` · `product_review_summary` · `product_best_review_index` | `review.*` | 리뷰 감정 분석·요약 — **팀원 소유**, 이 문서에서 다루지 않는다 | `review` 패키지 |

`user_profile_embeddings` 인덱스는 더 이상 없다 — 취향 벡터는 2026-09-08부터 저장하지 않고 개인화 추천 요청 안에서 계산해 쓰고 버린다([`audits/taste-vector/refactor-log.md`](audits/taste-vector/refactor-log.md) 2026-09-08). 클러스터에 남은 옛 인덱스는 삭제 대상이다([`audits/open-items.md`](audits/open-items.md) A절).

## `product`

| 필드 | 타입 | 비고 |
|------|------|------|
| `productId` | `@Id` (UUID) | |
| `productName` | `text`, `nori` | 형태소 검색. 아래 서브필드 둘을 갖는다 |
| `productName.word` | `text`, `standard` | 오탈자 보정(`fuzzy`) — 띄어쓰기 단위 토큰 |
| `productName.ac` | 색인 `autocomplete_analyzer`(edge_ngram 1~20자) / 조회 `standard` | 자동완성 접두어 |
| `productCategoryId` · `productCategoryCode` · `productCategoryName` | `keyword` | 카테고리 SSOT는 `scripts/init-db/02-init-categories.sql` |
| `price` | `long` | |
| `status` | `keyword` | 구매 가능 상태는 `ProductDocument.PURCHASABLE_STATUSES`(`ON_SALE`·`DISCOUNTED`) |
| `updatedAt` | `date` | 이벤트가 실어 보낸 값(발행 시각). 정렬 2순위 |
| `ingredientName` | `keyword` | 상품명에서 뽑은 요리 재료명(예: "계란"). 색인 시점에 LLM으로 1회 계산 — [`decisions/002`](decisions/002-ingredient-name-at-index-time.md) |
| `vector` | `dense_vector`, `dims=768` | Gemini `gemini-embedding-001` 출력 차원 768 고정 — [`audits/vector-similarity/003`](audits/vector-similarity/003-embedding-dims-and-candidate-multiplier.md). 차원을 바꾸면 인덱스 재생성·전량 재색인 |

analyzer 설정은 `src/main/resources/settings/autocomplete-analyzer.json`(`ProductDocument`의 `@Setting`이 가리킨다): `edge_ngram_tokenizer`(`min_gram=1`, `max_gram=20`, `token_chars` letter·digit) + `lowercase`.

임베딩 실패·재료명 추출 실패는 색인을 막지 않는다 — `vector`가 `null`이거나 `ingredientName`이 빈 문서가 생길 수 있고, 벡터 검색은 `exists(vector)` 필터로 그런 문서를 뺀다. 빈 값이 쌓이면 재색인(`POST /api/v1/search/admin/reindex`)이 필요하다.

## `user_behavior_logs`

| 필드 | 타입 | 비고 |
|------|------|------|
| `id` | `@Id` | `{도메인}-{파티션}-{오프셋}` — 도메인 접두어가 없으면 두 토픽의 같은 번호가 서로를 덮어쓴다 |
| `userId` · `productId` | `keyword` (UUID) | |
| `productName` | `text`, `nori` | |
| `eventType` | `keyword` | 발행 측 `HistoryEventType` enum 그대로 |
| `occurredAt` | `date` | 30일 조회 창·최신순 정렬 기준 |

`quantity`·`categoryCode`는 2026-09-07에 뺐다 — 저장만 되고 읽는 곳이 없었다.

## 연결 설정

`application.yml`의 `spring.elasticsearch.*`만으로 구성한다(별도 설정 클래스 없음 — 2026-09-05 `ElasticsearchConfig` 삭제). 기본 URI `http://elasticsearch:9200`, connect timeout 1초, socket timeout 30초. Spring AI VectorStore 추상화는 쓰지 않는다 — Spring Data Elasticsearch + `NativeQuery` 직접 조립이고, 별도 벡터 DB 연동도 없다.

## 흐름·쿼리는 어디에

| 무엇 | 문서 |
|------|------|
| 키워드 검색 쿼리(should 3종·필터·정렬), 자동완성 조회, 상품 이벤트 색인 순서, 재색인 | [`specs/keyword-search/product-search-and-autocomplete.md`](specs/keyword-search/product-search-and-autocomplete.md) |
| 벡터 검색(`script_score`), 후보 수·`topK` 상한, 카테고리 상한으로 고르기 | [`specs/vector-similarity/vector-similarity-search.md`](specs/vector-similarity/vector-similarity-search.md) |
| 행동 로그 → 취향 벡터 계산 규칙 | [`specs/taste-vector/taste-vector-pipeline.md`](specs/taste-vector/taste-vector-pipeline.md) · 예시 [`EMBEDDING_EXAMPLE.md`](EMBEDDING_EXAMPLE.md) |
| `ingredientName`을 읽는 레시피 추천, 부족 재료 상품 검색(`buildIngredientQuery`) | [`specs/cart-recipe/recipe-recommendation.md`](specs/cart-recipe/recipe-recommendation.md) |

## 관련 코드

- 상품 스키마: `src/main/java/com/barofarm/ai/search/domain/ProductDocument.java`
- 행동 로그 스키마: `src/main/java/com/barofarm/ai/log/domain/UserBehaviorLogDocument.java`
- 질의 조립: `src/main/java/com/barofarm/ai/search/infrastructure/elasticsearch/ProductQueryFactory.java`
- 검색·벡터 검색: `search/application/ProductSearchService.java` · `VectorProductSearchService.java`
- 색인·재색인: `search/application/ProductIndexService.java` · `ProductReindexService.java`
- 상품 임베딩: `embedding/application/ProductEmbeddingService.java` · 취향 벡터: `embedding/application/UserProfileEmbeddingService.java`
- 설정: `src/main/resources/application.yml` · `settings/autocomplete-analyzer.json`

## 관련 문서

- [`specs/README.md`](specs/README.md) — 작업 단위 ↔ 코드 매핑
- [`product/glossary.md`](product/glossary.md) — 용어
- [`.claude/rules/harness-map.md`](../../.claude/rules/harness-map.md) — 이 문서를 `{{스키마 SSOT}}` 슬롯으로 지정
