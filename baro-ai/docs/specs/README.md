# Specs — 작업 단위별 기능 스펙

baro-ai의 기능 스펙을 **작업 단위 4개**(취향 벡터·장바구니 레시피·임베딩 유사도 검색·키워드 검색)로 나눠 둔 폴더다. `harness-map.md`의 `{{스펙 저장소}}` 슬롯이 이 폴더를 가리키고, `specify` 스킬이 새 스펙을 여기에 만든다. 2026-09-04부터 폴더 이름은 코드 패키지가 아니라 작업 단위다 — 한 기능이 여러 패키지(`search`·`recommend`·`embedding`·`event`·`log`)에 걸쳐 있어 패키지 단위로는 감사·문서·설명 단위로 쓰기 어려웠기 때문이다(사용자 결정). 각 스펙은 baro-ai 코드뿐 아니라 **이벤트를 발행하는 다른 모듈의 규칙**까지 담는다.

- 파일명: kebab-case, 한 파일 = 한 기능 또는 한 리팩터 단위
- 템플릿: [`docs/templates/reference-doc.md`](../templates/reference-doc.md)의 "기능 스펙" 채움 예(참조 유형)
- 상태: `Draft` → `Approved` → `Implemented`
- **역작성(retro-spec) 스펙의 절 구성**: 이미 구현된 기능을 코드 기준으로 적은 스펙은 템플릿의 `## 요구사항`(완료 조건 체크리스트) 대신 `## 용어`·`## 흐름`·`## 비즈니스 규칙`을 둔다. 수치·계약값은 **`## 비즈니스 규칙` 표에만** 적고, 용어집·`## 용어` 표에서는 링크로 가리킨다(값이 바뀔 때 한 곳만 고치기 위함)

## 언제 이 문서를 보는가

- 어떤 클래스가 어느 작업 단위에 속하는지, 감사·스펙을 어느 폴더에 둘지 정할 때
- 옛 패키지 단위 문서(`search/`·`recommend/` 등)가 어디로 갔는지 찾을 때

## 작업 단위 ↔ 코드 ↔ 외부 의존

| 작업 단위 (폴더) | 스펙 | baro-ai 코드 | 발행·호출하는 다른 모듈 | Kafka 토픽 · HTTP |
|------------------|------|--------------|--------------------------|-------------------|
| 취향 벡터 (`taste-vector/`) | [`taste-vector-pipeline.md`](taste-vector/taste-vector-pipeline.md) | `event.consumer.CartEventConsumer`·`OrderEventConsumer`, `event.model.*`, `log.*`, `embedding.application.UserProfileEmbeddingService`, `embedding.domain.UserProfileEmbeddingDocument`, `embedding.infrastructure.*`, `recommend.application.PersonalizedRecommendService`, `config.AsyncConfig` | `baro-shopping` `CartService`(`@TrackHistory` 3곳), `baro-order` `InventoryConfirmedConsumer`(`@TrackHistory(ORDER_CONFIRMED)`), `baro-common` `HistoryAspect`·`HistoryLogWriter` | `cart-events`·`order-events` 소비 / `GET /api/v1/recommendations/personalized/{userId}` |
| 장바구니 레시피 (`cart-recipe/`) | [`recipe-recommendation.md`](cart-recipe/recipe-recommendation.md) · [`recipe-quality-redesign.md`](cart-recipe/recipe-quality-redesign.md)(Approved) · [`pantry-staples-and-catalog-coverage.md`](cart-recipe/pantry-staples-and-catalog-coverage.md)(Draft) | `recommend.application.RecipeRecommendService`, `recommend.domain.*`, `recommend.infrastructure.llm.RecipeCandidateGenerator`, `recommend.infrastructure.client.*`, `recommend.application.config.RecommendProperties`, `search.infrastructure.llm.ProductIngredientNormalizer`(색인 시점) | `baro-shopping` `CartController`(`GET /api/v1/carts`, `ResponseDto<CartInfo>`) | Feign `shopping-service` / `GET /api/v1/recommendations/recipes/{userId}` |
| 임베딩 유사도 검색 (`vector-similarity/`) | [`vector-similarity-search.md`](vector-similarity/vector-similarity-search.md) | `embedding.application.ProductEmbeddingService`, `search.application.VectorProductSearchService`, `recommend.application.MaxMinDiversityService`·`VectorRecommendPipeline`, `recommend.application.SimilarProductRecommendService`, `search.domain.ProductDocument.vector` | (색인 경로는 키워드 검색과 공유) | `GET /api/v1/recommendations/similar/{productId}` |
| 키워드 검색 (`keyword-search/`) | [`product-search-and-autocomplete.md`](keyword-search/product-search-and-autocomplete.md) | `search.infrastructure.event.*`, `search.application.ProductIndexService`·`ProductSearchService`·`search.exception.SearchErrorCode`, `search.infrastructure.elasticsearch.*`, `search.domain.*`, `search.presentation.SearchController`, `config.CacheConfig`·`KafkaConsumerConfig`(product 팩토리) | `baro-shopping` `ProductEventPublisher`(`AFTER_COMMIT`) | `product-events` 소비 / `GET /api/v1/search`, `/search/autocomplete` |
| 공유 인프라 (`cross-cutting/`) | (스펙 없음, 감사만) | `config.LlmClientConfig`·`KafkaProducerConfig` 등, `build.gradle`, `application.yml` | — | — |
| chat (`chat/`) | [`chatbot.md`](chat/chatbot.md) | (없음 — 2026-09-05 빈 스텁 4개 삭제) | — | 미착수 |
| review | (팀원 소유, 스펙 없음) | `review.*` | `baro-order` `ReviewEventProducer`, `baro-shopping` `ReviewSummaryConsumer` | `review-events` 소비 / `review-summary-events` 발행 |

공통 전제: baro-ai HTTP API는 `baro-gateway` `/ai-service/**`로만 들어온다. prod 라우트에는 인증 필터가 없어 `X-User-Id`는 클라이언트가 보낸 값이 그대로 전달된다(각 스펙 리스크 표 참고). 상세: [`product/platform.md`](../product/platform.md).

## 옛 폴더 → 새 위치

| 옛 경로 (2026-09-04 이전) | 지금 |
|---------------------------|------|
| `search/product-search-and-autocomplete.md` | `keyword-search/product-search-and-autocomplete.md` |
| `recommend/vector-recommendation.md` | `vector-similarity/vector-similarity-search.md` (개인화 API 부분은 `taste-vector`) |
| `recommend/recipe-recommendation.md` | `cart-recipe/recipe-recommendation.md` |
| `embedding/profile-and-product-embedding.md` | 사용자 프로필 → `taste-vector/taste-vector-pipeline.md`, 상품 임베딩 → `vector-similarity/` |
| `event-log/event-driven-log-pipeline.md` | `taste-vector/taste-vector-pipeline.md`에 통합 (product 이벤트는 `keyword-search/`) |

감사 문서의 옛 폴더(`audits/search/`·`audits/recommend/`)는 이력이라 옮기지 않았다 — 대응표는 [`audits/README.md`](../audits/README.md).

## 관련 문서

- [`product/glossary.md`](../product/glossary.md) — 작업 단위별 용어
- [`product/mvp.md`](../product/mvp.md) — 작업 단위별 범위
- [`.claude/rules/baro-ai-performance-refactor.md`](../../../.claude/rules/baro-ai-performance-refactor.md) — 작업 단위 정의의 SSOT(범위 표)
