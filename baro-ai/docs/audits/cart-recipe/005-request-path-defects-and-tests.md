# Cart-recipe Architecture Audit 005 — 요청 경로 결함·단순화·단위 테스트 (2026-09-06)

장바구니 레시피 작업 단위의 요청 경로(`RecipeRecommendService` → `RecipeCandidateGenerator` → `CandidateRecipePlan`)를 2026-09-06 비판 평가에서 다시 훑은 결과다. 반드시 고칠 A 항목 2건(최상위 catch가 스택트레이스를 버림, 부족 재료 검색이 재료 수만큼 ES를 순차 호출), 유지보수성 B 항목 3건(후보 1개짜리 일급 컬렉션 제거, 순수 함수 단위 테스트, 테스트 의존성 추가)이 나왔고, 응답 실패 이유를 구분해 내보낼지는 정책이라 판단 항목으로 넘겼다. **2026-09-06 전부 승인·반영 완료**(판단 항목은 `reason` 필드 추가로 결정) — 문서 끝 "승인 결과" 절.

## 범위

- 패키지: `recommend.application.RecipeRecommendService`, `recommend.domain.*`, `recommend.infrastructure.llm.*`
- 감사자: 메인 세션(2026-09-06 비판 평가). 이 세션은 컨텍스트 압축 뒤 해당 코드를 처음 읽었고 이번 라운드에서 아직 수정하지 않았다 — 서브에이전트를 따로 띄우지 않았음을 명시한다
- 기준: `audit-checklist.md` 1~16항목, `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md` 근거 기준(적극적 증거 필수) 적용
- 자동화 테스트는 이번 라운드부터 **도입 대상**이다(사용자 결정 2026-09-06 — "제안 전부 구현"). 저장소에 테스트 클래스 0개, `build.gradle`에 테스트 의존성 없음

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 레시피 기능 전체를 감싼 최상위 catch가 예외 객체를 버려 스택트레이스가 남지 않는다

- **Priority**: High
- **Category**: Architecture (관측 불가)
- **문제**: `recommendFromCartWithMissing`은 장바구니 조회·재료 조회·LLM 호출·부족 재료 검색 전체를 `catch (Exception e)`로 감싸 빈 응답으로 돌린다. 그런데 로그에 `e.getMessage()`만 넘기고 예외 객체를 넘기지 않아, 장애가 나면 "실패했다"는 한 줄만 남고 어디서 터졌는지 알 수 없다
- **근거**: `RecipeRecommendService.java:85-86` — `log.warn("레시피 추천 실패(...). userId: {}, error: {}", userId, e.getMessage());`. SLF4J는 마지막 인자가 `Throwable`일 때만 스택트레이스를 찍는다. 내부 catch(`searchProductsByIngredientName:181`, `RecipeCandidateGenerator:67`)는 `e`를 넘기고 있어 이 한 곳만 어긋난다
- **왜 문제인가**: 이 catch가 잡는 것 중 하나가 `baro-shopping` Feign 장애다. 운영에서 장바구니 서비스가 죽으면 레시피 API는 200 빈 응답을 내고, 로그에는 원인 없는 WARN만 남는다
- **개선 방법**: 마지막 인자로 `e`를 넘긴다
- **API 영향**: No Impact
- **예상 변경 파일**: `recommend/application/RecipeRecommendService.java`
- **예상 변경 라인 수**: 1줄
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 장애 조사 가능

### A-2. 부족 재료 상품 검색이 재료 수만큼 Elasticsearch를 순차 호출한다 (N+1)

- **Priority**: Medium
- **Category**: Performance
- **문제**: 부족 핵심 재료(프롬프트가 3~5개를 요구)마다 `productSearchService.search(...)`를 따로 부른다. 재료 4개면 ES 왕복 4번이 순차로 일어나고, 각 호출은 정확 구문·형태소·오탈자 3중 should 절과 필터를 전부 조립한다
- **근거**:
  - `RecipeRecommendService.java:148-155` — `missingCore.stream().map(ingredient -> new IngredientRecommendResponse(ingredient, searchProductsByIngredientName(ingredient, 2)))`. 스트림은 순차다
  - `:170-175` — 각 호출이 `productSearchService.search(null, new ProductSearchRequest(ingredientName, null, null, null), 0, size)` → `operations.search(query, ...)` 1회
  - Spring Data Elasticsearch 5.3.6 `ElasticsearchOperations`에 `multiSearch(List<? extends Query>, Class<T>)`가 있다(설치된 jar 기준) — ES `_msearch` 한 번으로 묶을 수 있다
- **왜 문제인가**: 레시피 API 한 번의 지연 = 장바구니 Feign + ES ids 조회 + LLM(수 초) + ES 검색 N회. LLM이 지배적이지만 ES N회는 재료 수에 비례해 늘고, 게이트웨이 응답 상한(5초, `open-items.md` B-3)과 겹치는 구간이다
- **개선 방법**: `ProductSearchService`에 키워드 목록을 받아 `multiSearch`로 한 번에 조회하는 메서드를 추가하고(기존 `search`의 쿼리 조립부를 private 빌더로 뽑아 공유), `RecipeRecommendService`는 그것을 한 번 부른다. 검색 로그·프로필 갱신 부가 작업은 `userId == null`이라 지금도 타지 않으므로 새 메서드에는 넣지 않는다. **키워드 검색 단위의 클래스를 건드린다** — 조회 로직 자체는 그대로고 진입 메서드만 하나 늘어난다
- **검증 방법**:
  - 단위: `ElasticsearchOperations`를 mock으로 두고 부족 재료 4개 시나리오에서 `multiSearch`가 정확히 1회, `search`가 0회 호출되는지 확인
  - 로컬 ES: `org.elasticsearch.client: DEBUG`로 요청 로그를 켜고 레시피 1회 호출 시 `_msearch` 1건만 나가는지 확인(Before: `_search` N건)
- **API 영향**: No Impact (응답 형식·순서 동일)
- **예상 변경 파일**: `search/application/ProductSearchService.java`, `recommend/application/RecipeRecommendService.java`
- **예상 변경 라인 수**: 40줄 내외
- **위험도**: Low
- **테스트 영향도**: 새 테스트 대상
- **예상 효과**: ES 왕복 N → 1

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. 후보가 1개인데 남아 있는 일급 컬렉션 `RecipeCandidates` 제거

- **Priority**: Low
- **Category**: Cleanup
- **문제**: 프롬프트가 후보 1개만 요구하도록 바뀐 뒤(`recipe-quality-redesign.md`), `RecipeCandidates`는 "이름이 빈 응답 걸러내기" 한 가지만 한다. 그 한 줄 방어를 위해 도메인 레코드 1개 + `chooseFirst()`가 남아 있다
- **근거**: `RecipeCandidates.java:17-32` — 메서드 1개, 호출자 1곳(`RecipeRecommendService:118`). LLM 응답 루트 래퍼 `RecipeCandidateDrafts`는 Gemini 스키마 루트가 객체여야 하므로 그대로 둔다(제거 대상 아님)
- **왜 문제인가**: "후보 여러 개 중 고른다"는 사라진 정책의 흔적이 클래스 이름으로 남아, 읽는 사람이 아직 후보 비교가 있는 줄 안다(STOP §4 레거시)
- **개선 방법**: `RecipeCandidateGenerator.generateRecipeCandidates`가 `Optional<CandidateRecipePlan>`을 직접 돌려준다(이름 빈 후보 필터는 생성기 안 한 줄). 메서드 이름도 `generateRecipe`로 바꿔 "후보들"이라는 흔적을 지운다. `RecipeCandidates` 삭제
- **API 영향**: No Impact
- **예상 변경 파일**: `recommend/domain/RecipeCandidates.java`(삭제), `recommend/infrastructure/llm/RecipeCandidateGenerator.java`, `recommend/application/RecipeRecommendService.java`
- **예상 변경 라인 수**: −35 / +10
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 파일 1개 감소, "후보 여러 개"라는 사라진 정책의 흔적 제거(STOP §4)

### B-2. 순수 함수 단위 테스트 도입 — `IngredientProcessingUtil`·`CandidateRecipePlan.from`

- **Priority**: High (이 라운드 전체의 전제)
- **Category**: Architecture (테스트성)
- **문제**: 재료 정규화·차집합·교집합은 "이걸 더 사세요" 카드의 정확성을 결정하는 순수 함수인데 테스트가 없다. 저장소 전체 테스트 0개
- **근거**: `find src/test -type f` 결과 없음. `./gradlew :baro-ai:test`는 `NO-SOURCE`. `IngredientProcessingUtil`(3개 static 메서드)과 `CandidateRecipePlan.from`은 외부 의존이 없다
- **왜 문제인가**: 정규화 규칙(공백·대소문자)이 조금만 어긋나도 보유 재료가 부족 재료로 둔갑해 "이미 담은 걸 사라"는 카드가 나간다. 지금은 그 회귀를 사람 눈으로만 잡는다
- **개선 방법**: `IngredientProcessingUtilTest`(공백·대소문자·null·중복·차집합), `CandidateRecipePlanTest`("돼지 고기" vs "돼지고기" 교집합 인정, 부족 재료 차집합, 빈 목록). 각 5~6개. Mock 없이 순수 JUnit 5
- **API 영향**: No Impact
- **예상 변경 파일**: `src/test/java/com/barofarm/ai/recommend/domain/IngredientProcessingUtilTest.java`, `.../CandidateRecipePlanTest.java`
- **예상 변경 라인 수**: 120줄 내외
- **위험도**: Low
- **테스트 영향도**: 커버리지 0 → 해당 클래스 전부
- **예상 효과**: `{{테스트 명령}}`이 처음으로 무언가를 검증하게 된다

### B-3. 테스트 의존성 추가 (`spring-boot-starter-test`)

- **Priority**: High (B-2 선행)
- **Category**: Spring
- **문제**: `baro-ai/build.gradle`에 테스트 의존성이 없어 테스트 클래스를 컴파일할 수 없다
- **근거**: `build.gradle` — `testImplementation` 선언 0건, `tasks.named('test') { useJUnitPlatform() }`만 있음
- **왜 문제인가**: B-2 이하 모든 테스트의 컴파일 전제다
- **개선 방법**: `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 1줄. JUnit 5·Mockito·AssertJ가 함께 온다. 공유 인프라(`cross-cutting`) 파일이지만 이 단위가 첫 소비자라 여기서 넣는다
- **API 영향**: No Impact
- **예상 변경 파일**: `baro-ai/build.gradle`
- **예상 변경 라인 수**: 1줄
- **위험도**: Low
- **테스트 영향도**: 전제 조건
- **예상 효과**: 테스트 컴파일 가능

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. `RecipeCandidateGenerator` 테스트

`ChatClient` 플루언트 API를 mock하려면 체인 4단계를 전부 stub해야 해 테스트가 구현에 붙는다. 프롬프트 품질은 이미 골든셋(`002`~`004`)이 담당한다. 지금 하지 않는 이유: 비용 대비 잡을 결함이 없다. 파싱 실패 → 빈 결과 경로만 필요해지면 그때 `ChatClient`를 인터페이스 뒤로 뺀다

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 빈 응답 5종을 예외로 바꾸는 것

장바구니 비었음·유효 상품 없음·재료명 없음·후보 실패는 사용자 상태이지 오류가 아니다. 예외로 던지면 화면이 빈 카드 대신 에러를 보게 된다. 이유 구분은 예외가 아니라 응답 필드로 해야 한다 — 판단 항목 1
### D-2. `searchProductsByIngredientName`의 내부 catch 유지

A-2로 한 번의 `multiSearch`가 되더라도 ES 실패 시 레시피 본문은 살리고 상품 카드만 비우는 현재 동작이 맞다. 격리 지점이 호출 1곳으로 줄어들 뿐이다

## 판단이 서지 않는 항목

1. **실패 이유를 응답에 담을 것인가.** 지금은 장바구니 비었음 / 유효 상품 없음 / 재료명 미색인 / 후보 생성 실패 / **장바구니 서비스 장애**가 전부 `("", [], [], [], "")` 하나다. 앞의 넷은 정상 상태, 마지막은 장애인데 클라이언트가 구분하지 못한다. `RecipeRecommendResponse`에 `reason`(enum: `EMPTY_CART`·`NO_ELIGIBLE_ITEMS`·`INGREDIENTS_NOT_INDEXED`·`GENERATION_FAILED`·`UPSTREAM_ERROR`·`OK`) 한 필드를 추가하면 프론트가 "장바구니에 재료를 담아 보세요"와 "잠시 후 다시 시도"를 나눌 수 있다. baro-ai는 계약 변경이 허용되지만(`baro-ai-performance-refactor.md`), **빈 응답이 정책**이라고 `RecommendErrorCode` Javadoc에 적혀 있어 정책 축에 걸린다. 사용자 결정. 넣기로 하면 STOP §2에 따라 스펙 응답 표를 같은 턴에 갱신한다

## 승인 결과 (2026-09-06)

사용자가 A·B 전 항목을 승인했고, 판단 항목 1은 **`reason` 필드를 추가한다**로 정했다(6종: `OK`·`EMPTY_CART`·`NO_ELIGIBLE_ITEMS`·`INGREDIENTS_NOT_INDEXED`·`GENERATION_FAILED`·`UPSTREAM_ERROR`). 같은 날 4개 단위 감사(이 문서·`taste-vector/002`·`keyword-search/002`·`vector-similarity/002`)를 한 번에 승인하고 구현 순서를 장바구니 → 취향 → 검색 → 벡터로 정했다.

### 구현 결과 (2026-09-06)

A 2건·B 3건·판단 1 전부 반영. 무엇을 바꿨고 어떻게 검증했는지는 [`refactor-log.md`](refactor-log.md) 2026-09-06 "요청 경로 결함 수정" 항목에 있다. 테스트 20건 통과, 빌드 통과. A-2의 로컬 ES 검증(`_msearch` 1건 확인)은 인프라 묶음(`open-items.md` A절)으로 남겼다.
