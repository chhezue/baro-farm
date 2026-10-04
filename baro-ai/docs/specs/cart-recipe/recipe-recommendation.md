# 장바구니 레시피 추천

보유 재료 · LLM(Large Language Model, 거대 언어 모델) 레시피 후보 · 서버 검증 · 부족 재료 상품을 다룬다. 사용자의 장바구니를 `baro-shopping`에서 가져와 요리 재료 목록으로 바꾸고, LLM이 만든 레시피 후보 3개를 서버가 검증·채점해 하나를 고른 뒤, 부족한 재료를 살 수 있는 상품으로 연결하는 작업 단위의 스펙이다. 장바구니 API의 실제 계약과 카테고리 체계까지 포함해 코드 기준으로 적었다. 감사([`audits/cart-recipe/`](../../audits/cart-recipe/), 이전 라운드는 [`audits/recommend/`](../../audits/recommend/))가 이 문서를 근거로 삼는다.

> 상태: Implemented (2026-09-04 코드 기준 역작성, `baro-shopping` 장바구니 API 포함)
> MVP: In scope
> 관련 BR: N/A (스코어링 정책은 아래 표가 유일한 표현 — [`audits/recommend/002`](../../audits/recommend/002-recipe-scoring.md))

## 언제 이 문서를 보는가

- 레시피 추천이 빈 응답을 돌려줄 때 어느 단계에서 끊겼는지 좁힐 때
- 레시피 후보 채점식·제외 카테고리·LLM 호출 옵션의 현재 값을 확인할 때
- 장바구니 API 계약(응답 envelope, 폴백 문자열)을 확인할 때

## 목표

장바구니에 담긴 재료로 만들 수 있는 현실적인 레시피 하나를 제안하고, 그 레시피에 1~2개쯤 부족한 재료를 바로 담을 수 있게 상품을 붙여 준다.

## 배경

LLM은 레시피 아이디어에는 강하지만 "보유·부족" 분류를 틀리거나 필드를 비운다. 그래서 창의적 부분(후보 3개 생성)만 LLM에 맡기고, 보유·부족 재료 계산과 최종 선택은 서버가 집합 연산과 점수로 다시 한다. 상품명 → 재료명 변환은 요청 시점이 아니라 상품 색인 시점에 1회만 한다([`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md)).

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 요리 재료명 | 상품명에서 브랜드·산지·수량·단위를 걷어낸 한 단어 재료(예: "계란"). 색인 시 LLM으로 1회 계산 | `ProductDocument.ingredientName` |
| 보유 재료 | 장바구니 상품들의 요리 재료명 집합(제외 카테고리 걸러낸 뒤) | `loadOwnedIngredientNames` |
| 레시피 후보 | LLM이 한 번에 만드는 레시피 3개 | `RecipeCandidates` |
| 핵심 재료 / 추가 재료 | 요리의 정체성을 정하는 재료 / 있으면 좋은 재료(개수 규칙은 프롬프트, 아래 데이터 모델) | `recipeIngredientsCore`·`Extra` |
| 부족 핵심 재료 | 핵심 재료 − 보유 재료(서버 계산) | `missingIngredientsCore` |
| 판매 기회 보너스 | 부족 핵심 재료 수에 따른 가·감점(값은 아래 비즈니스 규칙) | `calculateScore` |
| 기본 양념 가정 | 소금·후추·식용유 등은 보유로 간주, 재료 목록에 넣지 않음 | 프롬프트 `<정책>` |
| 레시피 제외 카테고리 | 설정 파일에 적힌 카테고리 코드. 이 카테고리 상품은 보유 재료에서 뺀다(목록은 아래 데이터 모델) | `recipe-recommend.yml` |

## 흐름

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 장바구니 조회 | `CartClient.getCart` (Feign `shopping-service`, `X-User-Id`) → `baro-shopping` `GET /api/v1/carts` | 응답은 **`ResponseDto<CartInfo>`**(status·data·message). 장바구니가 없어도 404가 아니라 200 + 빈 `CartInfo`. 항목마다 상품 서비스를 다시 조회해 상품명·카테고리를 채우며, 실패하면 한글 폴백 문자열이 들어간다 | `baro-shopping/.../CartController.java:33-40`, `CartService.java:160-207`, `CartItemInfo.java:29-36` |
| 2. 제외 필터 | `RecipeRecommendService.isRecipeEligible` | `productCategoryCode`가 제외 목록(과일 4개)에 있으면 제거 | [`RecipeRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/RecipeRecommendService.java) |
| 3. 보유 재료 | `loadOwnedIngredientNames` | 상품 ID로 ES `product` 문서를 한 번에 조회(`findAllById`) → `ingredientName`이 빈 문서는 건너뛰고 건수 WARN | 동일 |
| 4. 후보 생성 | `RecipeCandidateGenerator.generateRecipeCandidates` | Gemini `gemini-2.5-flash-lite`, temperature 0.7(전역), 응답 JSON 스키마 강제, 재시도 총 3회·호출당 30초. 실패 시 빈 후보 | [`RecipeCandidateGenerator.java`](../../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGenerator.java) |
| 5. 서버 검증 | `enhanceWithServerValidation` | LLM이 적은 `usedOwned`·`missingCore`를 버리고 정규화 비교(`normalizeForCompare`: trim·소문자·공백 제거)로 다시 계산 | 동일 |
| 6. 최적 후보 선택 | `RecipeCandidates.chooseBest` | 점수 최대 → 동점이면 `EASY > NORMAL > CHALLENGE` → 그래도 동점이면 셔플로 무작위 | [`RecipeCandidates.java`](../../../src/main/java/com/barofarm/ai/recommend/domain/RecipeCandidates.java) |
| 7. 부족 재료 상품 | `searchProductsByIngredientName` | 부족 핵심 재료마다 키워드 검색 API 내부 호출(`userId=null`, size 2), **순차 실행**. 실패하거나 0건이면 그 재료의 카드는 빠지고 `missingCoreIngredients`에는 남음 | 동일 |
| 8. 응답 | `RecipeRecommendResponse` | recipeName · ownedIngredients · missingCoreIngredients · missingRecommendations · instructions | 동일 |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/recipes/{userId}` | 없음(경로 userId 신뢰) | 실제 장바구니 기반 |
| POST | `/api/v1/recommendations/recipes/test` | 없음 | `CartInfo`를 body로 직접 넣는 **테스트용**. 운영 컨트롤러에 남아 있음 |

응답은 envelope 없이 `RecipeRecommendResponse` 하나. 장바구니 조회 실패·빈 장바구니·보유 재료 없음·후보 없음이 전부 **200 + 빈 필드**로 나가고 원인은 로그에만 남는다.

## 데이터 모델

이 단위가 읽는 ES 필드, Feign으로 받는 장바구니 DTO, LLM 응답 스키마, 설정 파일 값이다.

| 항목 | 값 | 근거 |
|------|-----|------|
| 읽는 ES 필드 | `product.ingredientName`(keyword), 부족 재료 검색은 키워드 검색 규칙 그대로 | [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) |
| Feign 요청 DTO | `CartInfo`(cartId·buyerId·items·totalPrice·createdAt·updatedAt), `CartItemInfo`(itemId·productId·productName·productCategoryName·productCategoryCode·quantity·unitPrice·lineTotalPrice·inventoryId·unit) | `recommend.infrastructure.client.dto` |
| LLM 응답 스키마 | `RecipeCandidates{candidates[CandidateRecipePlan]}` — cookingMethod(`ONE_POT`/`STIR_FRY`/`NO_COOK`/`OVEN_BAKE`), difficulty(`EASY`/`NORMAL`/`CHALLENGE`), mealType(`MAIN`/`SIDE`/`SOUP`), recipeName, recipeIngredientsCore, recipeIngredientsExtra, instructions, staplesAssumed, usedOwnedIngredients·missingIngredientsCore(서버가 덮어씀) | `CandidateRecipePlan` |
| 설정 | `baro.ai.recommend.exclude-recipe-category-codes` = `AGRI_FRUIT`·`AGRI_FRUIT_SEASONAL`·`AGRI_FRUIT_STORAGE`·`AGRI_FRUIT_BERRY` | `recipe-recommend.yml` |

## 비즈니스 규칙

### 장바구니·카테고리 전제 (`baro-shopping` — baro-ai가 바꿀 수 없는 것)

| 규칙 | 값 | 근거 |
|------|-----|------|
| 장바구니 조회 응답 | `ResponseDto<CartInfo>`. 없으면 200 + `CartInfo.empty()` | `CartController.java:34-40`, `CartService.java:41-53` |
| 항목 정보 원천 | 조회 시점에 상품·재고 서비스를 항목마다 재조회(N+1). 실패 시 `"(상품명을 불러오지 못했습니다)"`·`"(카테고리 코드를 불러오지 못했습니다)"`·`unit=-1` 폴백 | `CartItemInfo.java:29-36` |
| `CART_NOT_FOUND`(404) | 조회에서는 나지 않고 수정·삭제 계열에서만 | `CartService.findCart` |
| 카테고리 | 22개 전부 농산물. 레시피 재료가 아닌 상품 카테고리는 존재하지 않음 | `02-init-categories.sql` |

### baro-ai

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| 제외 카테고리 | 과일 4개 코드(설정) | `RecommendProperties` |
| 보유 재료 판정 | `ingredientName`이 비어 있지 않은 상품만. 정규화 후 중복 제거 | `loadOwnedIngredientNames` |
| 요청당 LLM 호출 | 후보 생성 1회 | [`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md) |
| 후보 개수·다양성 | 3개, 핵심 재료 3~5개·추가 재료 1~3개, 한식·양식·중식·일식 등 스타일과 cookingMethod/difficulty/mealType 조합이 서로 달라야 함(프롬프트 규칙) | 프롬프트 `<후보 생성 규칙>` |
| 판매 유도 | 핵심 재료의 30~50%는 보유 재료에 없는 새 재료로(프롬프트 규칙) | 동일 |
| 점수 | `보유 핵심 × 10 + 보유 추가 × 3 + 판매 기회 보너스` | `calculateScore` |
| 판매 기회 보너스 | 부족 핵심 0개 −10 · 1~2개 +15 · 3개 이상 −5 | 동일 |
| 동점 | 난이도 쉬운 순, 그다음 무작위(셔플) | `chooseBest` |
| 부족 재료 상품 수 | 재료당 최대 2개, 키워드 검색 상태 필터 적용 | `MISSING_PRODUCTS_LIMIT_PER_INGREDIENT` |
| 재료명 추출(색인 시) | temperature 0, 카테고리명을 힌트로 전달, 실패·빈 답은 `""` | `ProductIngredientNormalizer` ([`audits/search/002`](../../audits/search/002-llm-prompt-tuning.md)) |
| 단계별 실패 | 장바구니 실패 → 전부 빈 값 / 보유 재료 0 → 빈 값 / LLM 실패 → `ownedIngredients`만 / 상품 검색 실패 → 그 재료 카드만 빠짐 | `recommendFromCart` |

## 검증 시나리오

### 정상

- [x] 계란·애호박·두부 → 레시피 1개 + 부족 재료별 상품 ≤ 2개

### 엣지 · 실패

- [x] 장바구니 조회 실패(Feign 예외) → 빈 응답
- [x] 빈 장바구니 → 빈 응답
- [x] 전부 제외 카테고리 → 빈 응답
- [x] `ingredientName`이 전부 빈 문서 → 빈 응답 + "재색인 필요" WARN
- [x] LLM 실패 → `ownedIngredients`만 채운 응답
- [ ] 정상 장바구니인데 Feign 역직렬화가 `ResponseDto`를 풀지 못해 `items == null` → 빈 응답(아래 리스크)

### 수동 확인 (로컬)

- [ ] `CartClient.getCart` 응답 로그에서 `items`가 채워지는지 확인(언래핑 문제 재현)
- [ ] 후보 3개의 점수를 로그로 찍어 동점 빈도 확인([`audits/recommend/002`](../../audits/recommend/002-recipe-scoring.md) 판단 항목 3)

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — 자동화 테스트 없음

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| **Feign 응답 언래핑 누락** | 확정(코드) · 런타임 미검증 | `baro-shopping`은 `ResponseDto<CartInfo>`를 주는데 `CartClient`는 `CartInfo`를 직접 받는다. 커스텀 `Decoder`가 없어 `items`가 `null`이 되고 "장바구니가 비어있습니다"로 빈 응답이 될 가능성이 높다. 감사 [`audits/cart-recipe/001`](../../audits/cart-recipe/) 대상 |
| **`shopping-service` 이름이 prod에서 해석 불가** | 확정 | Eureka 꺼짐, simple discovery·Feign `url` 설정 없음, k8s Service 이름은 `baro-shopping`. 로컬은 `application-local.yml`이 없어 `[불확실]`. 배포 담당자 확인 `[미정]` |
| 게이트웨이 응답 타임아웃 5초 vs LLM 최악 약 93초 | 확정 | Gemini 재시도가 걸리면 게이트웨이가 먼저 끊는다. 상한을 어느 쪽에 맞출지 `[미정]` |
| 스코어링 가중치 미문서화·프롬프트 정책과 중복 | 확정 | [`audits/recommend/002`](../../audits/recommend/002-recipe-scoring.md) C-1. 단순화 여부는 사용자 결정 `[미정]` |
| 테스트용 POST 엔드포인트가 운영에 노출 | 확정 | 삭제 후보 |
| LLM 스키마에 서버가 덮어쓰는 필드 포함 | 확정 | 응답 전용 DTO 분리 후보 |
| 모든 실패가 200 + 빈 값 | 확정(설계) | `RecommendErrorCode`는 선언만 있고 던지지 않음. 유지할지 `[미정]` |
| 폴백 문자열이 `productCategoryCode`로 올 수 있음 | 확정 | 제외 목록과 안 맞아 통과되지만 `ingredientName` 조회는 `productId`로 하므로 영향 없음 |
| 부족 재료 검색이 순차 | 확정(설계) | 재료 ≤ 5개·같은 클러스터라 병렬화 근거 없음 — 하지 않는다 |
| `CandidateRecipePlan` 주석 2줄 불일치 | 확정 | [`audits/recommend/001`](../../audits/recommend/001-llm-prompt-tuning.md) B-1 |

## 관련 문서

- [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) — 재료명이 만들어지는 색인 경로, 부족 재료 검색 규칙
- [`decisions/002-ingredient-name-at-index-time.md`](../../decisions/002-ingredient-name-at-index-time.md)
- [`decisions/003-gemini-migration.md`](../../decisions/003-gemini-migration.md)
- [`audits/cross-cutting/001-llm-call-infra.md`](../../audits/cross-cutting/001-llm-call-infra.md) — 재시도·스키마 강제·지표
- [`audits/recommend/001`](../../audits/recommend/001-llm-prompt-tuning.md) · [`002`](../../audits/recommend/002-recipe-scoring.md) · [`refactor-log.md`](../../audits/recommend/refactor-log.md)
- [`product/glossary.md`](../../product/glossary.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
| 2026-09-04 | 보유 재료 추출을 LLM에서 색인 시점 `ingredientName` 조회로 교체 — 요청당 LLM 3회에서 1회로 |
| 2026-09-04 | `recommend/`에서 작업 단위 `cart-recipe/`로 이동. 장바구니 API 실제 계약·카테고리 전제·스코어링 규칙·Feign 언래핑 리스크 추가 |
