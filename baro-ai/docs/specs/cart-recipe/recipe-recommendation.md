# 장바구니 기반 레시피 추천

> 상태: Implemented
> MVP: In scope
> 관련 BR: N/A

## 목표

사용자의 장바구니 상품으로 만들 수 있는 레시피를 추천하고, 부족한 핵심 재료는 살 수 있는 상품으로 바로 연결한다.

## 배경

`vector-recommendation.md`(ES 벡터 유사도)와 달리 이쪽은 LLM을 쓴다. 흐름은 장바구니 조회, 보유 재료 확인, 레시피 후보 생성과 평가, 부족 재료 산출, 그 재료로 상품 재검색 순이다. `baro-shopping` 모듈을 Feign(`CartClient`)으로 호출한다는 점도 다른 추천과 다르다.

**보유 재료는 LLM으로 뽑지 않는다.** 상품이 색인될 때 미리 계산해 둔 `ProductDocument.ingredientName`을 읽는다 — 근거: [`decisions/002-ingredient-name-at-index-time.md`](../../decisions/002-ingredient-name-at-index-time.md). 그래서 요청 1건당 LLM 호출은 **레시피 후보 생성 1회뿐**이다(2026-09-04 이전에는 3회였다).

## 요구사항

### Must Have (구현 완료)

- [x] 장바구니 조회 (`CartClient` → `baro-shopping`)
- [x] 카테고리 코드 기준 레시피 제외 필터 (`RecommendProperties.excludeRecipeCategoryCodes`)
- [x] 보유 재료 확인 — 장바구니 상품의 `ProductDocument.ingredientName`을 ES에서 한 번에 조회(`RecipeRecommendService.loadOwnedIngredientNames`)
- [x] 레시피 후보 생성 및 최적 후보 선택(`RecipeCandidates.chooseBest`)
- [x] 부족한 핵심 재료 계산 후, 재료당 최대 2개까지 대체 상품을 ES 키워드 검색으로 매칭
- [x] 테스트용 엔드포인트 — 장바구니를 직접 body로 넣어 확인 가능

### Out of Scope (이번 스펙에서 하지 않음)

- 레시피 후보 재생성/캐싱
- LLM 호출 실패 시 재시도

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/recipes/{userId}` | 없음(path) | 실제 장바구니 기반 |
| POST | `/api/v1/recommendations/recipes/test` | 없음 | `CartInfo`를 body로 직접 입력(테스트용) |

구현: [`RecipeRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/RecipeRecommendService.java)

## 데이터 모델

- 응답: `RecipeRecommendResponse`(recipeName, ownedIngredients, missingCore, missingRecommendations, instructions)
- 요청(Feign): `CartInfo`/`CartItemInfo` — [`CartClient.java`](../../../src/main/java/com/barofarm/ai/recommend/infrastructure/client/CartClient.java)

## 비즈니스 규칙

| 규칙 | 값 |
|------|-----|
| 재료당 대체 상품 개수 | 최대 2개 |
| 장바구니 조회 실패 | 예외를 던지지 않고 빈 응답 반환 — "AI 기능은 필수가 아니다"라는 설계 원칙 |
| 빈 장바구니 / 보유 재료 없음 / 후보 없음 | 각 단계마다 빈 응답으로 조기 반환 |
| 요청당 LLM 호출 | 1회 (레시피 후보 생성) |
| `ingredientName`이 비어 있는 상품 | 보유 재료로 세지 않고 건너뛴다. 건너뛴 건수는 WARN 로그 — 재색인으로 해소 |

## 검증 시나리오

### 정상

- [x] 장바구니 상품으로 레시피 1개 + 부족 재료별 대체 상품 반환

### 엣지 · 실패

- [x] 장바구니 조회 실패(Feign 예외) → 빈 응답
- [x] 빈 장바구니 → 빈 응답
- [x] 카테고리 제외 필터로 모든 상품이 걸러짐 → 빈 응답
- [x] 보유 재료 없음(재료명이 색인된 상품이 하나도 없음) → 빈 응답
- [x] 레시피 후보 없음 → `ownedIngredients`만 채운 응답

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — **자동화 테스트 없음**

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 자동화 테스트 0개 | 확정 | LLM 응답을 모킹한 단위 테스트조차 없음 |
| LLM 호출 실패 시 재시도 없음 | 확정 | 실패하면 그 단계에서 바로 빈 응답 — 사용자는 "추천 없음"만 봄 |
| LLM 호출 타임아웃 미설정 | 미해결 | 제공자를 Gemini로 바꾼 뒤에도 그대로다. 무료 티어는 호출 한도가 있어 429가 나면 응답이 지연될 수 있는데 상한이 없다 |
| 실패 원인이 응답에 드러나지 않음 | 미해결 | 장바구니 조회 실패·LLM 실패·재료 없음이 전부 같은 빈 200 응답 — `RecommendErrorCode`가 정의만 되고 던져지지 않음 |
| 이 변경 이전 색인 문서는 `ingredientName` 없음 | 확정 | dev 모드라 전체 재색인으로 해소 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
| 2026-09-04 | 보유 재료 추출을 LLM에서 색인 시점 `ingredientName` 조회로 교체 — 요청당 LLM 3회에서 1회로. 상세: [`audits/recommend/refactor-log.md`](../../audits/recommend/refactor-log.md) |
