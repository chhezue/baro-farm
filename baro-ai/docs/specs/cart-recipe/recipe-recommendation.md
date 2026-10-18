# 장바구니 레시피 추천

보유 재료 · LLM(Large Language Model, 거대 언어 모델) 레시피 후보 · 서버 검증 · 부족 재료 상품을 다룬다. 사용자의 장바구니를 `baro-shopping`에서 가져와 요리 재료 목록으로 바꾸고, LLM이 만든 레시피(2026-09-06부터 1개)를 서버가 검증한 뒤, 부족한 재료를 살 수 있는 상품으로 연결하는 작업 단위의 스펙이다. 장바구니 API의 실제 계약과 카테고리 체계까지 포함해 코드 기준으로 적었다. 감사([`audits/cart-recipe/`](../../audits/cart-recipe/), 이전 라운드는 [`audits/recommend/`](../../audits/recommend/))가 이 문서를 근거로 삼는다.

> 상태: Implemented (2026-09-06 프롬프트 재설계 반영본, `baro-shopping` 장바구니 API 포함)
> MVP: In scope
> 관련 BR: N/A

## 언제 이 문서를 보는가

- 레시피 추천이 빈 응답을 돌려줄 때 어느 단계에서 끊겼는지 좁힐 때
- 후보 선택 규칙·제외 카테고리·LLM 호출 옵션의 현재 값을 확인할 때
- 장바구니 API 계약(응답 envelope, 폴백 문자열)을 확인할 때

## 목표

장바구니에 담긴 재료로 만들 수 있는 현실적인 레시피 하나를 제안하고, 그 레시피에 1~2개쯤 부족한 재료를 바로 담을 수 있게 상품을 붙여 준다.

## 배경

LLM은 레시피 아이디어에는 강하지만 "보유·부족" 분류를 틀리거나 필드를 비운다. 그래서 창의적 부분(레시피 1개 생성)만 LLM에 맡기고, 보유·부족 재료 계산은 서버가 집합 연산으로 다시 한다. 2026-09-05에 서버 채점식을 지웠고, 2026-09-06부터는 후보 자체를 1개만 요청한다. 상품명 → 재료명 변환은 요청 시점이 아니라 상품 색인 시점에 1회만 한다([`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md)).

## 용어

| 용어 | 뜻 | 상세 |
|------|-----|------|
| 요리 재료명 | 상품명에서 브랜드·산지·수량·단위를 걷어낸 한 단어 재료(예: "계란"). 색인 시 LLM으로 1회 계산 | `ProductDocument.ingredientName` |
| 보유 재료 | 장바구니 상품들의 요리 재료명 집합(제외 카테고리를 걸러낸 뒤 — 목록이 비어 지금은 전부 통과). **응답의 `ownedIngredients`는 이것이 아니라 이 요리가 실제로 쓰는 부분집합이다** | `loadOwnedIngredientNames` / `usedOwnedIngredients` |
| 레시피 후보 | LLM이 만드는 레시피. 2026-09-06부터 **1개**(응답은 배열 형태 유지, 이름이 있는 첫 항목을 생성기가 고른다) | `RecipeCandidateGenerator.generateRecipe` |
| 후보 원본 | LLM이 돌려주는 그대로의 후보. 서버가 계산하는 두 필드가 없다 | `RecipeCandidateDraft` |
| 핵심 재료 | 요리의 정체성을 정하는 재료 3~5개(개수 규칙은 프롬프트). 추가 재료(`Extra`)는 2026-09-06에 삭제 | `recipeIngredientsCore` |
| 부족 핵심 재료 | 핵심 재료 − 보유 재료(서버 계산) | `missingIngredientsCore` |
| 기본 양념 가정 | 소금·후추·식용유 등은 보유로 간주, 재료 목록에 넣지 않음 | 프롬프트 `<정책>` |
| 레시피 제외 카테고리 | 설정 파일에 적힌 카테고리 코드. 이 카테고리 상품은 보유 재료에서 뺀다. **2026-09-07부터 목록이 비어 있어 아무것도 빠지지 않는다** | `recipe-recommend.yml` |

## 흐름

| 단계 | 어디서 | 무엇을 | 근거 |
|------|--------|--------|------|
| 1. 장바구니 조회 | `CartClient.getCart` (Feign `shopping-service`, `X-User-Id`) → `baro-shopping` `GET /api/v1/carts` | 응답은 **`ResponseDto<CartInfo>`**(status·data·message). 장바구니가 없어도 404가 아니라 200 + 빈 `CartInfo`. 항목마다 상품 서비스를 다시 조회해 상품명·카테고리를 채우며, 실패하면 한글 폴백 문자열이 들어간다 | `baro-shopping/.../CartController.java:33-40`, `CartService.java:160-207`, `CartItemInfo.java:29-36` |
| 2. 제외 필터 | `RecipeRecommendService.isRecipeEligible` | `productCategoryCode`가 제외 목록에 있으면 제거. **목록이 비어 있어 지금은 전부 통과한다** | [`RecipeRecommendService.java`](../../../src/main/java/com/barofarm/ai/recommend/application/RecipeRecommendService.java) |
| 3. 보유 재료 | `loadOwnedIngredientNames` | 상품 ID로 ES `product` 문서를 한 번에 조회(`ids` 쿼리, `_source`에서 `vector` 제외) → `ingredientName`이 빈 문서는 건너뛰고 건수 WARN | 동일 |
| 4. 후보 생성 | `RecipeCandidateGenerator.generateRecipe` | Gemini `gemini-3.5-flash-lite`, temperature 0.7(전역), 응답 JSON 스키마 강제, 재시도 총 3회·호출당 30초. 프롬프트는 후보 **1개**를 요구하고, 응답 배열에서 이름이 있는 첫 항목을 `Optional`로 돌려준다(실패·빈 응답이면 empty) | [`RecipeCandidateGenerator.java`](../../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGenerator.java) |
| 5. 서버 계산 | `CandidateRecipePlan.from` | LLM 원본 후보에 `usedOwnedIngredients`(핵심 재료 ∩ 보유)·`missingIngredientsCore`(핵심 재료 − 보유)를 정규화 비교(`normalizeForCompare`: trim·소문자·공백 제거)로 채워 넣는다. 둘을 합치면 핵심 재료가 된다 | [`CandidateRecipePlan.java`](../../../src/main/java/com/barofarm/ai/recommend/domain/CandidateRecipePlan.java) |
| 6. 후보 선택 | (4단계에 흡수) | 후보가 1개가 된 뒤 남아 있던 일급 컬렉션 `RecipeCandidates`는 2026-09-06에 삭제했다 — 빈 이름 방어 한 줄은 생성기 안에 있다. 서버 점수 계산은 2026-09-05에 삭제했다 | [`audits/cart-recipe/005`](../../audits/cart-recipe/005-request-path-defects-and-tests.md) B-1 |
| 7. 부족 재료 상품 | `searchMissingIngredientProducts` → `ProductSearchService.searchByKeywords` | 부족 핵심 재료 전부를 **한 번의 `_msearch`**로 조회(재료당 size 2, 검색 로그·프로필 갱신 없음). 검색어는 원문이 아니라 **마지막 어절**만 쓴다(`국거리용 소고기` → `소고기`) — 사용자에게 보이는 `missingCoreIngredients`는 원문 그대로다. 2026-09-06 이전에는 재료마다 순차 호출이었다. 검색이 통째로 실패하면 카드만 비우고 레시피 본문은 살린다. 0건인 재료는 카드가 빠지고 `missingCoreIngredients`에는 남음 | [`audits/cart-recipe/005`](../../audits/cart-recipe/005-request-path-defects-and-tests.md) A-2 |
| 8. 응답 | `RecipeRecommendResponse` | **reason** · recipeName · ownedIngredients(**이 요리가 쓰는 보유 재료**) · missingCoreIngredients · missingRecommendations · instructions | 동일 |

## API / 인터페이스

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/recommendations/recipes/{userId}` | 없음(경로 userId 신뢰) | 실제 장바구니 기반 |

테스트용 `POST /api/v1/recommendations/recipes/test`는 2026-09-05에 삭제했다 — 인증 없이 임의 장바구니로 LLM 호출을 일으킬 수 있었고, A-1·A-2 반영으로 실제 경로에서 확인할 수 있게 됐다.

응답은 `ResponseDto<RecipeRecommendResponse>`(2026-09-05 검색 API와 envelope 통일). 레시피를 만들지 못한 경우도 **200 + 빈 필드**로 나간다 — 빈 장바구니는 오류가 아니라 사용자 상태라서다. 2026-09-06부터 왜 비었는지를 `reason` 필드가 알린다(그 전에는 5가지 상황이 전부 같은 빈 응답이라 화면이 "재료를 담아 보세요"와 "잠시 후 다시 시도"를 구분하지 못했다).

| `reason` | 뜻 | 채워지는 필드 |
|----------|-----|---------------|
| `OK` | 레시피를 만들었다 | 전부 |
| `EMPTY_CART` | 장바구니가 없거나 비었다 | 없음 |
| `NO_ELIGIBLE_ITEMS` | 쓸 수 있는 상품이 하나도 없다. 제외 목록이 빈 지금은 상품 식별자가 전부 비어 온 경우뿐이다 | 없음 |
| `INGREDIENTS_NOT_INDEXED` | 상품 문서에 재료명이 없다 — 재색인 필요 | 없음 |
| `GENERATION_FAILED` | LLM이 레시피를 만들지 못했다 | `ownedIngredients` |
| `UPSTREAM_ERROR` | 장바구니 서비스 등 외부 연동 실패 — 잠시 후 재시도 성격 | 없음 |

이 필드는 `RecommendErrorCode`(4xx 예외)가 아니다 — 예외로 올리지 않는 정책은 그대로고, 이유만 응답에 실린다.

## 데이터 모델

이 단위가 읽는 ES 필드, Feign으로 받는 장바구니 DTO, LLM 응답 스키마, 설정 파일 값이다.

| 항목 | 값 | 근거 |
|------|-----|------|
| 읽는 ES 필드 | `product.ingredientName`(keyword)·`productName`. 부족 재료 검색은 일반 키워드 검색이 **아니라 전용 쿼리**다 — 아래 "부족 재료 상품 매칭" 절 | [`keyword-search`](../keyword-search/product-search-and-autocomplete.md) · [`audits/cart-recipe/006`](../../audits/cart-recipe/006-missing-ingredient-product-match.md) |
| Feign 응답 타입 | `ResponseDto<CartInfo>` — 봉투째 받아 `data()`를 꺼낸다. 호출 상한은 연결 1초·읽기 3초 | `CartClient`, `application.yml` |
| Feign 수신 DTO | `CartInfo`(items), `CartItemInfo`(productId·productName·productCategoryCode) — **실제로 읽는 칸만 옮겨 적는다**(2026-09-06 축소). 상류는 `cartId`·`totalPrice`·`quantity`·`unitPrice` 등을 함께 보내지만 Jackson이 모르는 필드를 무시한다 | `recommend.infrastructure.client.dto` |
| LLM 응답 스키마 | `RecipeCandidateDrafts{candidates[RecipeCandidateDraft]}` — recipeName, recipeIngredientsCore, instructions **3개**(2026-09-06에 8개에서 축소). 서버가 계산하는 두 필드는 스키마에 없다 — 스키마 생성기가 모든 필드를 필수로 요구해서, 넣어 두면 버릴 값을 모델이 매번 출력해야 했다. 조리 방식·난이도·식사 유형·추가 재료·양념 가정 플래그는 응답에 닿지 않아 함께 삭제했다 | `RecipeCandidateDraft` |
| 설정 | `baro.ai.recommend.exclude-recipe-category-codes` = **빈 목록**(2026-09-07 — 과일 4종을 뺐다가 근거가 재현되지 않아 풀었다, [`audits/cart-recipe/007`](../../audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md) A-2) | `recipe-recommend.yml` |

## 비즈니스 규칙

### 장바구니·카테고리 전제 (`baro-shopping` — baro-ai가 바꿀 수 없는 것)

| 규칙 | 값 | 근거 |
|------|-----|------|
| 장바구니 조회 응답 | `ResponseDto<CartInfo>`. 없으면 200 + `CartInfo.empty()` | `CartController.java:34-40`, `CartService.java:41-53` |
| 항목 정보 원천 | 조회 시점에 상품·재고 서비스를 항목마다 재조회(N+1). 실패 시 `"(상품명을 불러오지 못했습니다)"`·`"(카테고리 코드를 불러오지 못했습니다)"`·`unit=-1` 폴백 | `CartItemInfo.java:29-36` |
| `CART_NOT_FOUND`(404) | 조회에서는 나지 않고 수정·삭제 계열에서만. baro-ai 쪽 대응 코드는 2026-09-05에 삭제했다 | `CartService.findCart` |
| 카테고리 | 22개 전부 농산물. 레시피 재료가 아닌 상품 카테고리는 존재하지 않음 | `02-init-categories.sql` |

### baro-ai

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| 제외 카테고리 | **없음**(설정이 빈 목록). 코드는 남아 있어 설정만 채우면 다시 동작한다 | `RecommendProperties` |
| 보유 재료 판정 | `ingredientName`이 비어 있지 않은 상품만. 정규화 후 중복 제거 | `loadOwnedIngredientNames` |
| 요청당 LLM 호출 | 후보 생성 1회 | [`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md) |
| 후보 개수·다양성 | **1개**, 핵심 재료 3~5개. 스타일·조리법 다양성 강제는 2026-09-06에 삭제 — 개수를 채우려는 압력이 억지 요리를 밀어내고 있었다 | 프롬프트 `<지켜야 할 것>` |
| 판매 유도 | **부족 핵심 재료가 최소 1개.** 보유 재료만으로 완성되는 요리면 재료를 끼워 넣지 말고 요리 선택을 돌린다(프롬프트 규칙) | 동일 |
| 후보 선택 | LLM이 낸 순서대로 이름이 있는 첫 번째 후보 | `RecipeCandidateGenerator.generateRecipe` |
| 응답의 보유 재료 | 장바구니 전체가 아니라 **이 요리가 실제로 쓰는 보유 재료**(핵심 재료 ∩ 장바구니). 부족 재료와 합치면 핵심 재료가 된다 | `usedOwnedIngredients` |
| Feign 호출 상한 | 장바구니 조회 연결 1초·읽기 3초. 게이트웨이 응답 상한(5초)보다 짧게 둔다 | `application.yml` |
| 부족 재료 상품 수 | 재료당 최대 2개, 키워드 검색 상태 필터 적용 | `MISSING_PRODUCTS_LIMIT_PER_INGREDIENT` |
| 부족 재료 검색어 | 원문의 **마지막 어절**만 쓴다(`국거리용 소고기` → `소고기`). LLM이 수식어를 붙여 말해도 색인된 재료명과 맞추기 위한 규칙이고, 응답에 나가는 문구는 원문을 유지한다 | `IngredientProcessingUtil.toSearchKeyword` |
| 부족 재료 상품 매칭 | 전용 쿼리 `buildIngredientQuery`. `ingredientName` **정확 일치(`term`)에 boost 4.0**을 주어 상품명 정확 매칭(2.0)보다 위에 두고, 그 아래로 일반 키워드 3중 매칭(정확 2.0·형태소 1.0·오탈자 0.3)을 함께 건다(`minimumShouldMatch=1`). 색인된 재료명이 정답일 때 1위로 오게 하려는 것이다 | `ProductSearchService.buildIngredientQuery` |
| 재료명 추출(색인 시) | temperature 0, 카테고리명을 힌트로 전달, 실패·빈 답은 `""` | `ProductIngredientNormalizer` ([`audits/search/002`](../../audits/search/002-llm-prompt-tuning.md)) |
| 단계별 실패 | 장바구니 실패 → 전부 빈 값(`UPSTREAM_ERROR`) / 빈 장바구니(`EMPTY_CART`)·전부 제외(`NO_ELIGIBLE_ITEMS`)·재료명 없음(`INGREDIENTS_NOT_INDEXED`) → 빈 값 / LLM 실패 → `ownedIngredients`만(`GENERATION_FAILED`) / 상품 검색 실패 → 카드 전부 빠짐, 레시피는 유지(`OK`). 최상위 catch는 스택트레이스를 남긴다(2026-09-06 이전엔 메시지만) | `recommendFromCart` |

## 검증 시나리오

### 정상

- [x] 계란·애호박·두부 → 레시피 1개 + 부족 재료별 상품 ≤ 2개

### 엣지 · 실패

- [x] 장바구니 조회 실패(Feign 예외) → 빈 응답
- [x] 빈 장바구니 → 빈 응답
- [x] 전부 제외 카테고리 → 빈 응답 (제외 목록이 빈 지금은 실제로 발생하지 않지만, 단위 테스트가 목록을 주입해 동작을 고정한다)
- [x] `ingredientName`이 전부 빈 문서 → 빈 응답 + "재색인 필요" WARN
- [x] LLM 실패 → `ownedIngredients`만 채운 응답
- [x] 정상 장바구니인데 봉투를 풀지 못해 `items == null` → 빈 응답 (2026-09-05 `ResponseDto<CartInfo>` 수신으로 해소, 실제 왕복은 미검증)

### 수동 확인 (로컬)

전부 `baro-shopping`을 함께 띄워야 해서 2026-09-05 리팩터에서는 하나도 실행하지 못했다.

- [ ] baro-shopping을 띄우고 `SHOPPING_SERVICE_URL`을 로컬 주소로 준 뒤 추천 API 1회 호출 — "장바구니 조회 실패"·"장바구니가 비어있습니다" WARN이 사라지고 `ownedIngredients`가 채워지는지
- [ ] `SHOPPING_SERVICE_URL`을 일부러 틀린 주소로 두고 호출 — WARN의 `error:` 뒤에 상태 코드·URL이 보이는지(에러 디코더 삭제 효과)
- [ ] `nc -l 8082`로 응답하지 않는 주소를 만들고 호출 — WARN까지 걸리는 시간이 60초에서 3초 근처로 줄었는지
- [ ] 기동 시 응답 스키마를 한 번 찍어 후보 필드가 8개에서 3개로 줄었는지 (스키마 생성·파싱 자체는 같은 라이브러리 버전으로 로컬 검증됨 — 남은 건 실제 Gemini 응답 확인)
- [ ] 같은 장바구니로 3회 호출해 출력 토큰 합계를 Before/After 비교(`/actuator/metrics/gen_ai.client.token.usage`)
- [ ] `POST /api/v1/recommendations/recipes/test`가 404이고 `/v3/api-docs`에서 사라졌는지

## 완료 기준

- [x] 기능 구현
- [ ] 위 "수동 확인 (로컬)" 절차 — baro-shopping 미기동으로 2026-09-05 리팩터에서는 수행하지 못했다
- [x] `./gradlew :baro-ai:test` — 이 단위 20건 통과 (`RecipeRecommendServiceTest` 7 · `IngredientProcessingUtilTest` 8 · `CandidateRecipePlanTest` 5). 저장소 전체는 10클래스 59건

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| **Feign 응답 언래핑 누락** | 해소 · 런타임 미검증 | 2026-09-05 `CartClient`가 `ResponseDto<CartInfo>`를 받도록 고쳤다. baro-shopping을 띄운 실제 왕복 확인은 아직 못 했다 |
| **`shopping-service` 이름이 prod에서 해석 불가** | 해소 · 런타임 미검증 | 2026-09-05 `spring.cloud.openfeign.client.config.shopping-service.url`을 추가했다(기본값 `http://baro-shopping:8080`). 네임스페이스가 다르면 FQDN이 필요해 배포 담당자 확인 `[미정]` |
| 게이트웨이 응답 타임아웃 5초 vs LLM 최악 약 93초 | 확정 | Gemini 재시도가 걸리면 게이트웨이가 먼저 끊는다. 상한을 어느 쪽에 맞출지 `[미정]` |
| 스코어링 가중치 미문서화·프롬프트 정책과 중복 | 해소 | 2026-09-05 점수 로직 삭제(사용자 결정). **되돌리려면 삭제 전 가중치가 필요하다** — 값은 [`audits/recommend/002`](../../audits/recommend/002-recipe-scoring.md) C-1에 남아 있다 |
| 후보 선택 품질 비교 근거 없음 | 확정 | 점수를 지우기 전에도 후보별 점수를 로그로 남기지 않아, 삭제 전후 추천 품질을 비교할 자료가 없다 `[미정]` |
| 테스트용 POST 엔드포인트가 운영에 노출 | 해소 | 2026-09-05 삭제 |
| LLM 스키마에 서버가 덮어쓰는 필드 포함 | 해소 | 2026-09-05 `RecipeCandidateDraft` 분리 |
| 모든 실패가 200 + 빈 값 | 확정(설계) | 레시피 경로는 실패해도 200 + 빈 값으로 나간다. `RecommendErrorCode`는 2026-09-05에 지웠다가 되살아나 있고(`INVALID_TOP_K` 1건), 쓰는 곳은 레시피가 아니라 `VectorRecommendPipeline.validateTopK`다 — 2026-09-06 코드 확인 |
| 폴백 문자열이 `productCategoryCode`로 올 수 있음 | 확정 | 제외 목록과 안 맞아 통과되지만 `ingredientName` 조회는 `productId`로 하므로 영향 없음 |
| ~~부족 재료 검색이 순차~~ | **해소(2026-09-06)** | `ProductSearchService.searchByKeywords`가 `_msearch` 1회로 묶는다. 부분 실패 시 결과 길이가 어긋나면 전부 빈 목록으로 맞춘다 |
| `CandidateRecipePlan` 주석 2줄 불일치 | 해소 | 2026-09-05 record 전환과 함께 정정 |

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
| 2026-09-05 | 감사 001 A·B 전 항목 + 채점식 삭제 반영 — Feign 봉투 언래핑·`url`·타임아웃, 테스트 엔드포인트 삭제, LLM 응답 record 분리, 후보 선택을 `chooseFirst`로 |
| 2026-09-05 | 보유 재료 조회에서 `_source`의 `vector`를 제외 — `ingredientName` 한 필드만 쓰는데 문서마다 1536차원 벡터가 딸려 오고 있었다(문서당 약 21KB → 371바이트, `keyword-search/001` A-3 실측). 결과·정책은 그대로 |
| 2026-09-06 | 프롬프트 재설계 반영 — 후보 3개→1개, 판매 유도를 비율에서 "최소 1개"로, LLM 응답 필드 8개→3개, `ownedIngredients`를 장바구니 전체에서 "이 요리가 쓰는 보유 재료"로. 골든셋 측정 근거: [`audits/cart-recipe/002`](../../audits/cart-recipe/002-goldenset-before-after.md) |
| 2026-09-06 | 감사 005 반영 — 응답에 `reason` 추가(계약 변경, 판단 항목 1 승인), 부족 재료 상품 조회를 재료별 순차 호출에서 `_msearch` 1회로, 최상위 catch에 스택트레이스, `RecipeCandidates` 삭제. 단위 테스트 4클래스 20건 도입(저장소 첫 테스트) |
| 2026-09-07 | 감사 007 반영 — **레시피 제외 카테고리를 비웠다**(과일 4종 해제). 과일이 보유 재료로 들어오므로 `NO_ELIGIBLE_ITEMS`가 거의 나오지 않고 디저트·음료 레시피가 나온다. 프롬프트 규칙 1의 예시도 팔지 않는 과일에서 파는 채소·견과류로 교체. 근거: [`audits/cart-recipe/007`](../../audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md) |
