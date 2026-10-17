# 용어집 — baro-ai

baro-ai 코드·문서에서 반복해서 쓰는 도메인 용어의 SSOT. `harness-map.md`의 `{{용어집}}` 슬롯이 가리키는 문서다. 같은 개념을 문서마다 다른 말로 부르지 않기 위해 여기 기준으로 통일한다. 2026-09-04부터 용어를 **작업 단위**(취향 벡터·장바구니 레시피·임베딩 유사도 검색·키워드 검색)별로 묶었고, 다른 모듈이 정하는 전제(이벤트 발행 조건 등)도 baro-ai가 알아야 하는 만큼 함께 실었다.

## 언제 이 문서를 보는가

- 새 문서·스펙을 쓸 때 용어를 통일하고 싶을 때
- 코드의 변수·클래스명이 무슨 개념인지 처음 볼 때
- 포트폴리오·발표에서 한 기능을 어떤 이름으로 부를지 정할 때

## 공통

| 용어 | 의미 | 코드/문서 근거 |
|------|------|-----------------|
| 작업 단위 | baro-ai를 감사·문서·설명하는 기본 단위. 패키지가 아니라 비즈니스 기능 4개 | [`specs/README.md`](../specs/README.md) |
| 이벤트 봉투 (HistoryEnvelope) | `baro-common`이 Kafka로 보내는 `{event, ts, userId, payload}`. `cart-events`·`order-events`의 값 형식(JSON 문자열, 키는 userId) | `HistoryEnvelope`, `HistoryLogWriter` |
| 상태 필터 | 검색·자동완성·벡터 검색이 공통으로 적용하는 `ON_SALE`·`DISCOUNTED`만 노출 조건 | `applyStatusFilter`, `buildBoolQuery` |
| 상품 상태 (ProductStatus) | `ON_SALE`·`DISCOUNTED`·`SOLD_OUT`·`HIDDEN`·`DELETED`. 판매자 수정으로만 바뀌고 재고 0이 자동으로 `SOLD_OUT`을 만들지 않는다 | `baro-shopping` `ProductStatus` |
| 카테고리 코드 | 3단계 22개, 전부 `AGRI_*`(농산물). 리프 코드가 이벤트·문서의 `productCategoryCode` | `scripts/init-db/02-init-categories.sql` |
| dense_vector | Elasticsearch 벡터 필드 타입. baro-ai는 768차원 고정(2026-09-07 이전 1536) | `ProductDocument.vector`, `UserProfileEmbeddingDocument.userProfileVector` |
| 코사인 유사도 | 두 벡터의 방향 유사도(−1~1). 벡터 검색·다양화의 공통 기준 | ES `script_score(cosineSimilarity)`, `MaxMinDiversityService` |
| topK | 추천 API가 최종 반환하는 개수. 후보는 이보다 넉넉히 가져온 뒤 다양화로 줄인다(값: [`vector-similarity` 스펙](../specs/vector-similarity/vector-similarity-search.md) 비즈니스 규칙) | `RecommendationController` |
| `X-User-Id` | 게이트웨이가 넣어 주기로 한 사용자 식별 헤더. prod 라우트에는 인증 필터가 없어 클라이언트 값이 그대로 전달된다 | `baro-gateway` `application-prod.yml`, [`platform.md`](platform.md) |
| Sentiment · 베스트 리뷰 | review 도메인(팀원 소유) 용어 — `POSITIVE`/`NEGATIVE`/`MIXED`, 매일 배치로 갱신되는 대표 리뷰 목록 | `Sentiment.java`, `BestReviewListDocument` |

## 취향 벡터 (`taste-vector`)

| 용어 | 의미 | 코드/문서 근거 |
|------|------|-----------------|
| 행동 로그 | 장바구니 변경·주문 확정 1건을 ES에 저장한 문서 | `CartLogDocument`·`OrderLogDocument` |
| 프로필 벡터 (취향 벡터) | 행동 로그에 등장한 상품들의 벡터를 행동 비중대로 섞어 길이를 1로 맞춘 768차원 벡터. 문서 ID는 userId. 외부 임베딩 API를 부르지 않는다 | `UserProfileEmbeddingDocument.userProfileVector` |
| 행동 비중 | 로그 1건이 취향에 기여하는 정도 = **행동 종류 그 자체**(주문 3·담기 2·수량 변경 1·삭제 0). 곱하는 축이 더 없다 — 경과 시간은 2026-09-06, 수량은 2026-09-07에 뺐다. 값: [`taste-vector` 스펙](../specs/taste-vector/taste-vector-pipeline.md) 비즈니스 규칙 | `calculateEventWeight` |
| 행동 가중치 | 행동 종류별 계수. 주문 확정 > 장바구니 담기 > 수량 변경 순이고, 취소·삭제는 0이라 취향에 전혀 반영되지 않는다 | `calculateEventWeight` |
| 가중 평균 · L2 정규화 | 상품 벡터를 비중대로 더한 뒤 길이를 1로 맞추는 것. 길이를 맞춰야 상품 벡터와 코사인 유사도로 비교할 수 있다 | `blendIntoProfileVector` |
| 갱신 스킵 게이트 | 조회 기간 안의 로그가 최소 개수에 못 미치면 프로필을 만들지 않는 조건(값: `taste-vector` 스펙 비즈니스 규칙) | `MIN_TOTAL_LOGS_FOR_EMBEDDING` |
| 경험 상품 (Source Product IDs) | 비중이 0이 아닌 장바구니·주문 로그의 상품 ID. 개인화 추천에서 제외한다. 삭제·취소한 상품은 여기 들어가지 않아 다시 추천될 수 있다 | `sourceProductIds` |
| 프로필 갱신 트리거 | 장바구니 이벤트 1건·주문 이벤트 1건마다 재계산. 두 경로 모두 전용 빈을 거쳐 실제로 비동기다 | `UserProfileRefreshTrigger` |
| 주문 확정 시점 | `baro-order`가 `inventory-confirmed`를 받아 `AWAITING_PAYMENT → CONFIRMED`로 바꾸는 순간. `ORDER_CONFIRMED` 이벤트는 여기서만 발행 | `InventoryConfirmedConsumer` |
| 게스트 장바구니 | `X-Session-Key`만 있는 장바구니. userId가 없어 Kafka 이벤트가 발행되지 않는다 | `HistoryLogWriter.sendToKafka` |

## 장바구니 레시피 (`cart-recipe`)

| 용어 | 의미 | 코드/문서 근거 |
|------|------|-----------------|
| 요리 재료명 (ingredientName) | 상품명에서 브랜드·산지·수량·단위를 걷어낸 한 단어(예: "계란"). 상품 색인 시 LLM(Large Language Model, 거대 언어 모델)으로 1회 계산해 저장. 빈 값은 "아직 모른다"는 뜻이며 추천은 그 상품을 건너뛰고 재색인 대상으로 센다 | `ProductDocument.ingredientName`, [`decisions/002`](../decisions/002-ingredient-name-at-index-time.md) |
| 보유 재료 | 장바구니 상품들의 요리 재료명 집합(제외 카테고리 제거 후 — 목록이 비어 지금은 전부 통과). 레시피 후보 생성의 입력 | `RecipeRecommendService.loadOwnedIngredientNames` |
| 레시피 제외 카테고리 | 레시피 재료로 쓰지 않을 카테고리 코드. 설정 파일로 관리하며 **2026-09-07부터 비어 있다**(과일 4종을 뺐다가 근거가 재현되지 않아 풀었다) | `recipe-recommend.yml` |
| 레시피 후보 | LLM이 만드는 레시피. 2026-09-06부터 1개(응답은 배열, 이름이 있는 첫 항목을 쓴다) | `RecipeCandidateGenerator.generateRecipe` |
| 핵심 재료 | 요리 정체성을 정하는 3~5개. "추가 재료"(`recipeIngredientsExtra`)는 2026-09-06에 삭제했다 | `recipeIngredientsCore` |
| 기본 양념 가정 | 소금·후추·식용유·간장 등은 보유로 간주, 재료 목록에 넣지 않음 | 프롬프트 `<정책>` |
| 부족 핵심 재료 | 핵심 재료 − 보유 재료. LLM 값을 버리고 서버가 정규화 비교로 다시 계산 | `CandidateRecipePlan.from`, `subtractNormalized` |
| 후보 원본 | LLM이 돌려주는 그대로의 레시피 후보. 서버가 계산하는 보유·부족 재료 두 필드가 없다 | `RecipeCandidateDraft` |
| 장바구니 스냅샷 (CartInfo) | `baro-shopping` `GET /api/v1/carts`가 `ResponseDto`로 감싸 주는 장바구니. 항목 정보는 조회 시점 재조회, 실패 시 한글 폴백 문자열 | `CartClient`, `baro-shopping` `CartItemInfo` |

## 임베딩 유사도 검색 (`vector-similarity`)

| 용어 | 의미 | 코드/문서 근거 |
|------|------|-----------------|
| 상품 벡터 | 상품명을 Gemini `gemini-embedding-001`로 임베딩한 768차원 벡터. 실패 시 `null` | `ProductEmbeddingService`, `ProductDocument.vector` |
| 후보 규모 | 다양화 전에 ES에서 가져오는 상품 수. topK의 배수에 상한을 둔다(값: `vector-similarity` 스펙 비즈니스 규칙) | `candidateSize` |
| max-min 다양화 | 1순위를 고정하고 "이미 고른 것들과의 최소 거리가 가장 큰" 후보를 순차 선택하는 greedy. k-medoids가 아니다 | `MaxMinDiversityService.selectDiverse` |
| 추천 공통 경로 | 기준 벡터 하나로 후보 조회 → 다양화 → 응답 변환까지 수행하는 경로. 유사 상품·개인화 추천이 함께 쓴다 | `VectorRecommendPipeline.recommend` |
| script_score 벡터 검색 | 필터를 통과한 문서 전부의 코사인을 계산하는 방식. HNSW 근사 검색(native `knn`)을 쓰지 않는다 | `VectorProductSearchService` |

## 키워드 검색 (`keyword-search`)

| 용어 | 의미 | 코드/문서 근거 |
|------|------|-----------------|
| 색인 경로 | `product-events` 수신 → 임베딩 → 재료명 → `product-autocomplete` 저장 → `product` 저장 | `ProductEventConsumer`, `ProductIndexService.indexProduct` |
| 상품 이벤트 | `baro-shopping`이 생성·수정·삭제 트랜잭션 커밋 후 발행하는 `PRODUCT_CREATED/UPDATED/DELETED`. 키 없음, 수정은 diff 없이 항상 발행 | `ProductTransactionEventListener`, `ProductEventProducer` |
| 정확 매칭 | `match_phrase` 절(slop·boost: `keyword-search` 스펙) | `applyExactMatch` |
| 일반 매칭 | `match` OR 절(boost: `keyword-search` 스펙) | `applyNormalMatch` |
| 오탈자 매칭 | `productName.word`(띄어쓰기 단위 text) 대상 fuzzy 절. 단어마다 한 글자 오타를 허용한다(길이 조건·boost: `keyword-search` 스펙) | `applyFuzzyMatch` |
| 자동완성 접두어 | `edge_ngram`으로 색인한 상품명 조각. 조회는 확장 없이 `match`(`operator=and`)로 찾는다(길이 범위: `keyword-search` 스펙) | `autocomplete-analyzer.json`, `ProductAutocompleteRepository` |
| 자동완성 캐시 | 자동완성 결과를 인스턴스 안에서만 잠시 기억하는 Caffeine 로컬 캐시(크기·TTL: [`keyword-search` 스펙](../specs/keyword-search/product-search-and-autocomplete.md) 비즈니스 규칙) | `CacheConfig`, [`decisions/001`](../decisions/001-autocomplete-caching.md) |

## 예시

- "이 사용자의 **프로필 벡터**로 후보 15개를 뽑고 **max-min 다양화**로 **topK**=5개를 골랐는데, **경험 상품** 2개는 처음부터 제외됐다."
- "장바구니의 **보유 재료**가 계란·애호박·두부라서 **레시피 후보** 3개 중 **부족 핵심 재료**가 2개인 '애호박 두부전'이 **판매 기회 보너스** +15를 받아 선택됐다."

## 관련 문서

- [`docs/product/mvp.md`](mvp.md) · [`docs/specs/README.md`](../specs/README.md)
- [`docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 코드베이스에서 실제로 쓰이는 용어 기준으로 작성 |
| 2026-09-04 | 작업 단위별로 재편. 발행 모듈 전제(이벤트 봉투·주문 확정 시점·게스트 장바구니·상품 상태·카테고리) 추가. "메도이드" 정의를 실제 알고리즘(max-min 다양화)으로 정정. 수치는 각 스펙의 "비즈니스 규칙" 표에만 두고 용어집에서는 뺐다(`doc-reviewer` 지적 — SSOT 이중화 방지) |
| 2026-09-05 | "카테고리 일치 보너스" 항목 삭제(두 추천 경로 모두 제거됨). "메도이드" 코드명 병기를 빼고 `MaxMinDiversityService`로 갱신. "추천 공통 경로" 추가 |
| 2026-09-05 | "오탈자 매칭"의 대상 필드를 `productName.word`로, "자동완성 접두어"의 조회 방식을 `match`로 갱신 |
| 2026-09-05 | "판매 기회 보너스"·"후보 점수" 삭제(서버 채점 로직 제거). "후보 원본" 추가 |
| 2026-09-07 | 감사 taste-vector/005 반영 — '수량 가중치'·'상위 결과 상품' 용어 삭제(축·신호 자체가 없어짐), '행동 가중치'·'프로필 갱신 트리거'에서 검색 제거 |
