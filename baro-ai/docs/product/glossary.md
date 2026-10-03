# 용어집 — baro-ai

baro-ai 코드·문서에서 반복해서 쓰는 도메인 용어의 SSOT. `harness-map.md`의 `{{용어집}}` 슬롯이 가리키는 문서다. 같은 개념을 문서마다 다른 말로 부르지 않기 위해 여기 기준으로 통일한다.

## 언제 이 문서를 보는가

- 새 문서·스펙을 쓸 때 용어를 통일하고 싶을 때
- 코드의 변수·클래스명이 무슨 개념인지 처음 볼 때

## 용어

| 용어 | 의미 | 코드/문서 근거 |
|------|------|-----------------|
| 프로필 벡터 (User Profile Vector) | 사용자의 최근 검색·장바구니·주문 로그를 가중치 합성해 만든 임베딩 벡터. 개인화 추천의 입력값 | `UserProfileEmbeddingDocument.userProfileVector` |
| 소스 상품 ID (Source Product IDs) | 프로필 벡터 생성에 쓰인 로그에서 나온 상품 ID 목록. 개인화 추천에서 "이미 경험한 상품"을 제외하는 데 씀 | `UserProfileEmbeddingDocument.sourceProductIds` |
| 선호 카테고리 코드 (Preferred Category Code) | 장바구니·주문 로그를 이벤트 가중치로 합산해 도출한, 사용자가 가장 선호하는 카테고리 1개 | `UserProfileEmbeddingService.calculatePreferredCategoryCode` |
| 메도이드 (Medoid) / 메도이드 다양성 | 벡터 유사도 top-K가 서로 비슷한 상품만 뽑는 문제를 막기 위해, 후보들 사이의 거리를 기준으로 서로 먼 대표 상품을 순차 선택하는 k-medoids 방식 알고리즘 | `MedoidDiversityService.selectDiverseMedoids` |
| 카테고리 일치 보너스 (Category Match Bonus) | 추천 후보가 사용자 선호/기준 상품과 같은 카테고리일 때 주는 가산점(현재 0.3 고정) | `PersonalizedRecommendService`, `SimilarProductRecommendService` |
| 시간 가중치 (Time Weight) | 로그가 최근일수록 프로필 벡터에 더 크게 반영되도록 하는 지수감쇠 계수 (7일 기준 반감, 0.3~3.0배로 clamp) | `UserProfileEmbeddingService.calculateTimeWeight` |
| 이벤트 가중치 (Event Weight) | 로그 이벤트 종류별 관심도 가중치 (예: 주문확정=3, 장바구니추가=2, 취소/삭제=0) | `UserProfileEmbeddingService.calculateCartEventWeight`/`calculateOrderEventWeight` |
| topK | 추천/검색에서 최종적으로 반환할 결과 개수. 후보는 이보다 넉넉히(topK*3, 최대 100) 가져온 뒤 필터링 | `RecommendationController` 쿼리 파라미터 |
| Sentiment | 리뷰의 감정 분류 값 — `POSITIVE`/`NEGATIVE`/`MIXED` 세 가지(review 도메인, 팀원 소유) | `Sentiment.java` |
| 베스트 리뷰 (Best Review List) | 상품·감정별로 선정된 대표 리뷰 목록. 매일 새벽 배치로 갱신되며 기존 목록과 50% 이상 달라야 교체됨(review 도메인) | `BestReviewListDocument`, `BestReviewReplacementPolicy` |
| 요리 재료명 (ingredientName) | 상품명에서 브랜드·수량·단위를 걷어내고 남긴 재료 이름(예: "계란"). 상품 색인 시점에 1회 계산해 상품 문서에 저장한다. 빈 값은 LLM 추출이 실패해 아직 재료명을 모른다는 뜻이며, 추천은 그 상품을 건너뛰고 재색인 대상으로 센다(이 카탈로그는 전부 농산물이라 "재료가 아닌 상품"은 없다) | `ProductDocument.ingredientName` |
| 보유 재료 | 장바구니에 담긴 상품들의 요리 재료명을 모은 목록. 레시피 후보 생성의 입력이 된다 | `RecipeRecommendService.loadOwnedIngredientNames` |
| dense_vector | Elasticsearch의 벡터 필드 타입. baro-ai에서는 1536차원 고정 (Gemini `gemini-embedding-001`의 출력 차원을 1536으로 맞춘다) | `ProductDocument.vector`, `UserProfileEmbeddingDocument.userProfileVector` |
| 코사인 유사도 (Cosine Similarity) | 두 벡터의 방향이 얼마나 비슷한지를 -1~1로 나타낸 값. baro-ai 전역의 벡터 유사도 계산 기준 | `MedoidDiversityService.cosineSimilarity`, ES `script_score(cosineSimilarity)` |

## 예시

- "이 사용자의 **프로필 벡터**로 **메도이드** 다양성을 적용해서 **topK**=5개를 뽑았는데, 그중 절반이 **선호 카테고리**와 일치해서 **카테고리 일치 보너스**를 받았다."

## 관련 문서

- [`docs/product/mvp.md`](mvp.md)
- [`docs/specs/`](../specs/README.md) — 용어가 실제로 쓰이는 스펙들
- [`docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 코드베이스에서 실제로 쓰이는 용어 기준으로 작성 |
