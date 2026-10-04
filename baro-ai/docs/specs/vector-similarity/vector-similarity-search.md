# 벡터 기반 추천 (개인화 · 유사 상품)

> 상태: Implemented
> MVP: In scope
> 관련 BR: N/A

## 목표

사용자 취향 벡터 또는 기준 상품 벡터와 유사한 상품을, **단순 top-K 유사도가 아니라 다양성을 확보해서** 추천한다.

## 배경

벡터 유사도만으로 top-K를 뽑으면 서로 거의 같은 상품만 나열되는 문제가 있다. 이를 막기 위해 후보를 넉넉히(topK*3, 최대 100) 가져온 뒤 **메도이드(k-medoids) 알고리즘**으로 서로 거리가 먼 대표 상품을 선택한다. 개인화 추천과 유사 상품 추천이 이 로직(`MedoidDiversityService`)을 공유한다.

## 요구사항

### Must Have (구현 완료)

- [x] 개인화 추천 — `UserProfileEmbeddingDocument.userProfileVector` 기반 ES 벡터 검색
- [x] 유사 상품 추천 — 기준 `ProductDocument.vector` 기반 ES 벡터 검색
- [x] 메도이드 다양성 선택 — 첫 후보를 초기 메도이드로, 이후 "기존 메도이드들과의 최소 거리가 가장 먼" 후보를 순차 선택
- [x] 같은 카테고리 보너스 점수 0.3 (사용자 선호 카테고리 / 기준 상품 카테고리 일치 시)
- [x] 경험/자기 자신 제외 — 개인화는 `sourceProductIds`(장바구니+주문 이력), 유사 상품은 기준 상품 자신
- [x] 프로필·벡터가 없으면 예외 대신 빈 리스트 반환 (MSA 환경에서 다른 화면 요소가 죽지 않도록)

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/personalized/{userId}` | 없음(path) | `topK`(기본 5) |
| GET | `/api/v1/recommendations/similar/{productId}` | 없음 | `topK`(기본 3) |

구현: [`RecommendationController.java`](../../../src/main/java/com/barofarm/ai/recommend/presentation/RecommendationController.java) · [`PersonalizedRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/PersonalizedRecommendService.java) · [`SimilarProductRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/SimilarProductRecommendService.java) · [`MedoidDiversityService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/MedoidDiversityService.java)

## 데이터 모델

- `UserProfileEmbeddingDocument`(userId, userProfileVector, preferredCategoryCode, sourceProductIds …) — [`embedding` 스펙](../embedding/profile-and-product-embedding.md) 참고
- `ProductDocument.vector` — `dense_vector(1536)`

## 비즈니스 규칙

| 규칙 | 값 |
|------|-----|
| 후보 규모 | `min(topK * 3, 100)` |
| 카테고리 일치 보너스 | 0.3 (선호/기준 카테고리 있을 때만) |
| 후보 수 ≤ topK | 메도이드 생략, ES 유사도 순서 그대로 반환 |
| 최종 정렬 | 메도이드 선택 후 쿼리 벡터 기준 코사인 유사도 내림차순 재정렬 |

## 검증 시나리오

### 정상

- [x] 프로필/기준 상품 벡터 존재 → topK개 다양성 있는 추천
- [x] 후보가 topK 이하 → ES 정렬 그대로 반환(메도이드 생략)

### 엣지 · 실패

- [x] 프로필 없음/벡터 없음 → 빈 리스트(예외 아님)
- [x] 기준 상품을 못 찾음(유사 상품) → 빈 리스트 + warn 로그
- [x] `topK <= 0` → 빈 리스트

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — **자동화 테스트 없음**

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 자동화 테스트 0개 | 확정 | 메도이드 알고리즘처럼 순수 함수 로직은 단위 테스트 비용이 낮은 편이라 우선순위 후보 |
| 벡터 차원(1536) 하드코딩 의존 | [미정] | 임베딩 모델을 바꾸면 코사인 유사도 계산이 조용히 깨질 수 있음 — 검증 로직 없음 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
