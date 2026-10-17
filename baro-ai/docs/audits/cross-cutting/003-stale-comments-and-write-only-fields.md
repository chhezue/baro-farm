# Cross-cutting Architecture Audit 003 — 사실이 아닌 주석·쓰기 전용 필드·도달 불가 코드 (2026-09-07)

사용자의 "리팩터가 다 됐는지, 과도한 로직과 오버엔지니어링이 남았는지 비판적으로 봐 달라"는 요청에서 나온 감사다. 성능이 아니라 **정합성**이 주제다 — 지난 라운드들이 기능을 지우면서 그 기능을 설명하던 문장을 남겨 둬, 코드를 읽는 사람에게 사실과 다른 말을 하고 있었다.

`.claude/rules/java-comments.md`는 baro-ai의 촘촘한 Javadoc 방식을 **의도적으로 채택**하되 "주석은 사실이어야 한다"를 예외 없는 조건으로 못박고 있다. 이 감사는 그 조건을 어긴 것만 골랐다 — 문체·분량은 손대지 않았다.

## 범위

- 코드: `log.*`, `event.*`, `embedding.*`, `recommend.application.*`, `search.application.*`, `config.*`, `common.config.SwaggerConfig`
- **제외**: `com.barofarm.ai.review` 전체(팀원 소유, 2026-09-07 사용자 재확인). `config.SentimentPropertiesConfig`·`NegativeReviewPropertiesConfig`·`KafkaProducerConfig`는 패키지는 `ai.config`지만 review 전용이라 함께 제외했다
- 감사자: 메인 세션 + 결론을 주지 않은 읽기 전용 서브에이전트 1회(독립 확인)
- 기준: `core-guardrails.md` ⛔ STOP §1·§4, `java-comments.md` "주석은 사실이어야 한다"

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 삭제된 기능을 현행처럼 설명하는 주석·문서 12곳

- **Priority**: Medium
- **Category**: Correctness (문서–코드 불일치)
- **문제**: 2026-09-07에 검색을 취향 신호에서 빼고 수량 가중치 축을 지웠는데, 그 기능을 설명하던 문장이 다른 파일에 남았다. 지금 코드를 처음 읽는 사람은 있지도 않은 경로를 찾게 된다. 스펙 계약 표 한 곳도 같은 성격이라 함께 넣었다(2026-09-06 라운드의 잔여분)
- **근거 (grep으로 확인)**:

  | 위치 | 적혀 있던 것 | 실제 |
  |---|---|---|
  | `CartLogDocument`·`OrderLogDocument`의 `quantity` | "취향 벡터 가중치 산출 시 관심 강도로 반영, **최대 5로 제한됨**" | 가중치는 행동 종류 하나로만 정해지고, **5로 제한하는 코드가 모듈 전체에 없다**(`grep -rn "min(.*5)"` 0건) |
  | `CartLogEvent`의 `@param quantity` | "이벤트 종류별 가중치에 **곱해진다**" | 곱하지 않는다 |
  | `LogWriteService` 클래스 | "Kafka Consumer **및 상품 검색 서비스**로부터… 장바구니, 주문, **검색**" | 호출자는 `CartEventConsumer`·`OrderEventConsumer` 둘뿐 |
  | `LogWriteService`의 `@param quantity` | "수량 (관심 강도)" | 읽는 곳 없음 |
  | `UserProfileEmbeddingDocument`·`PersonalizedRecommendService`·`RecommendationController` | 행동 로그가 "장바구니/주문/**검색**" | 두 가지뿐 |
  | `AsyncConfig` | "메인 비즈니스 흐름(이벤트 소비, **검색 응답**)과 분리" | 검색은 이 실행자에 아무것도 제출하지 않는다 |
  | `CartEventConsumer` | "수량, 발생 일시는 가중치 계산을 위해 원본 그대로 저장" | 수량은 저장하지 않게 됐다 |
  | `specs/cart-recipe/recipe-recommendation.md` 계약 표 | Feign 수신 DTO가 상류의 전체 칸(`cartId`·`quantity`·`unitPrice` …)을 그대로 갖는 것처럼 적혀 있었다 | 2026-09-06에 **실제로 읽는 3칸**으로 줄였는데 표가 따라가지 않았다 |
  | `CartLogEvent`의 `@param ts` | "최근 행동에 더 큰 **가중치**를 주는 계산의 기준값" | 시간 감쇠 축은 2026-09-06에 빠졌다. 지금 `ts`가 정하는 것은 30일 조회 창과 최신순 정렬뿐이다 |
  | `RecipeRecommendService`(2곳)·`RecommendProperties` | 제외 카테고리가 "**가공식품·생필품·주방용품·가전**" | 설정값은 **과일 4종**뿐이고, 이 쇼핑몰은 농산물만 팔아 주방용품 카테고리가 존재하지 않는다. 같은 저장소의 `RecipeRecommendReason.NO_ELIGIBLE_ITEMS`는 "과일류"라고 맞게 적혀 있어 **한 저장소 안에서 서로 다른 말**을 했다 |

- **왜 문제인가**: 지난 라운드가 "검색 신호를 뺐다"는 사실 자체는 여러 문서에 잘 남겼는데, 그 사실이 **코드 안에서는 절반만 반영**됐다. 이런 상태가 굳으면 다음 사람이 주석을 근거로 판단하게 되고, 그게 정확히 이 저장소가 `core-guardrails.md` STOP §1로 막으려던 상황이다
- **개선 방법**: 각 문장을 현재 동작으로 고치고, 지운 이유를 한 줄로 남긴다(왜를 지우지 않는다 — `java-comments.md` 두 번째 조건)
- **검증 방법**: `grep -rn "검색" --include=*.java`로 남은 언급이 전부 사실인지 확인 + `./gradlew :baro-ai:test`
- **API 영향**: No Impact
- **반영**: 2026-09-07 완료

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. 모든 이벤트마다 저장하지만 아무도 읽지 않는 필드 2개

- **문제**: `quantity`와 `categoryCode`가 장바구니·주문 로그 문서에 저장되는데 읽는 코드가 main에 없다(`grep -rn "getQuantity\|getCategoryCode" src/main` → review 제외 0건). 수량은 2026-09-07에 가중치에서 빠졌고, 카테고리는 처음부터 "언젠가 카테고리 선호를 쓸지도 모른다"는 이유로 저장만 하고 있었다([`open-items.md`](../open-items.md) C-6·C-10)
- **왜 문제인가**: 필드가 남아 있으면 주석도 남고, 위 A-1처럼 "가중치에 쓰인다"는 설명이 따라다닌다. 실제로 그 설명이 사실과 반대가 된 원인이 이 필드다
- **개선 방법**: 두 문서와 `LogWriteService` 시그니처, 그리고 수신 record(`CartLogEvent.CartEventData`·`OrderLogEvent.OrderItemData`)에서 모두 뺀다. 상류(`baro-shopping`)는 계속 보내지만 spring-kafka의 기본 매퍼가 모르는 필드를 무시하므로 수신에 지장이 없다 — 이미 `cartId`·`cartItemId`를 그렇게 흘려보내고 있다
- **인덱스 영향**: `cart_event_logs`·`order_event_logs`를 재생성해야 옛 문서의 필드까지 사라진다. 두 인덱스는 이미 재생성 목록에 있다([`open-items.md`](../open-items.md) A-3)
- **반영**: 2026-09-07 완료 — `open-items.md` C-6·C-10을 닫는다

### B-2. 상품 인덱스 질의를 두 클래스가 각자 조립한다

- **문제**: `RecipeRecommendService`가 `ElasticsearchOperations`를 직접 주입받아 상품 ID 조회 쿼리를 만들었다. 그래서 `EXCLUDED_SOURCE_FIELDS = {"vector"}` 상수가 `ProductSearchService`와 이 클래스 **두 곳에 똑같이** 선언돼 있었고, 응답에서 벡터를 빼는 규칙을 한쪽만 고치면 조용히 어긋난다
- **개선 방법**: `ProductSearchService.findByIds(List<UUID>)`를 추가하고 레시피 추천은 그것을 부른다. 상품 인덱스에 질의하는 코드가 한 클래스로 모인다
- **부수 효과**: `RecipeRecommendServiceTest`가 `SearchHit`·`SearchHits`를 목으로 조립하던 12줄이 한 줄(`when(productSearchService.findByIds(anyList()))`)로 줄었다
- **반영**: 2026-09-07 완료

### B-3. 판매 상태 목록이 세 곳에 문자열로 박혀 있다

- **문제**: `ON_SALE`/`DISCOUNTED`가 `ProductSearchService`·`VectorProductSearchService`의 쿼리 빌더와 `ProductAutocompleteRepository`의 `@Query` JSON에 각각 있었다. 상태를 하나 늘리면 세 곳을 같이 고쳐야 하는데 그 사실이 어디에도 적혀 있지 않았다
- **개선 방법**: `ProductDocument.PURCHASABLE_STATUSES`에 값을 두고 두 쿼리 빌더가 그것을 읽는다. `@Query`는 어노테이션이라 상수를 끼워 넣을 수 없으므로 세 번째 자리는 문자열로 남기되, **왜 여기만 다른지와 같이 고쳐야 한다는 것**을 Javadoc에 적었다
- **반영**: 2026-09-07 완료

### B-4. HTTP로 나갈 수 없는 에러 코드

- **문제**: `EmbeddingErrorCode.EMBEDDING_GENERATION_FAILED`(HTTP 500)의 유일한 throw는 `ProductEmbeddingService.embedProduct`이고, 그 메서드의 유일한 호출자는 같은 클래스의 묶음 폴백이며 거기서 곧바로 잡아 `null`로 바꾼다. `GlobalExceptionHandler`까지 갈 경로가 없고, embedding 패키지에는 컨트롤러도 없다
- **왜 문제인가**: 에러 코드 목록은 "이 API가 이런 실패를 돌려준다"는 계약으로 읽힌다. 나갈 수 없는 코드가 섞여 있으면 그 목록 전체를 믿기 어려워진다. 같은 이유로 [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md)에서 이미 6종을 지운 전례가 있다
- **개선 방법**: `embedProduct`를 private으로 낮추고 실패를 `null` 반환으로 표현한다("벡터 없음"을 이미 `null`로 나타내고 있어 표현이 하나로 합쳐진다). `EmbeddingErrorCode`는 상수가 그것 하나뿐이라 파일째 지운다
- **파급**: `.claude/rules/core-guardrails.md`·`harness-map.md`가 이 enum을 "에러코드 패턴이 있다"는 근거로 인용하고 있어 같은 턴에 `SearchErrorCode`로 바꿔 적었다
- **반영**: 2026-09-07 완료

### B-5. 아무것도 담기지 않은 `Components()`

- **문제**: `SwaggerConfig.openAPI()`가 `.components(new Components())`를 부르는데 빈 객체라 생성되는 스펙에 영향이 없다
- **반영**: 2026-09-07 완료 (import까지 제거)

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 레시피 후보를 감싸는 배열 래퍼(`RecipeCandidateDrafts`) 없애기

- **판단**: 하지 않는다. 프롬프트는 후보 1개만 요구하는데 응답 스키마는 배열이고, 자바 쪽은 `stream().filter(이름 있음).findFirst()`로 받는다. 래퍼 record 하나와 3단 스트림을 단일 객체 + null 검사로 줄일 수 있다
- **왜 지금 안 하나**: 이건 **LLM 응답 스키마 변경**이다. 현행 프롬프트의 품질은 이 스키마로 측정해 확정한 값이고([`cart-recipe/002`](../cart-recipe/002-goldenset-before-after.md) 실행 합격률 69.0%→90.5%), 스키마를 바꾸면 그 측정이 무효가 된다. 아끼는 것은 약 15줄인데 치러야 할 것은 골든셋 재측정이라 거래가 맞지 않는다
- **다시 열 조건**: 다른 이유로 프롬프트·스키마를 손대 어차피 골든셋을 다시 돌릴 때 같이 한다

### C-2. review 소유 파일의 중복 설정

- **판단**: 손대지 않는다(2026-09-07 사용자 지시). `KafkaProducerConfig`의 `@EnableKafka`가 `KafkaConsumerConfig`와 중복이고, `SentimentPropertiesConfig`·`NegativeReviewPropertiesConfig`는 본문이 빈 클래스다. 셋 다 review 기능만을 위한 파일이라 [`open-items.md`](../open-items.md) B-7(review 담당 팀원에게 물을 목록)에 남긴다

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. `ProductIndexService.indexProducts`의 반환값 없애기

- **판단**: 하지 않는다. 프로덕션 유일 호출자(`ProductEventConsumer`)는 반환값을 쓰지 않지만 `ProductIndexServiceTest`가 색인 결과를 검증하는 데 쓴다. void로 바꾸면 그 테스트가 목 검증만 남아 약해진다

## 판단이 서지 않는 항목

없음. 이 감사의 항목은 전부 "사실이 아닌 설명"이거나 "읽는 곳이 없는 코드"라 판정에 추가 자료가 필요하지 않았다.

## 승인 결과

2026-09-07 사용자 승인("문서로 남기고 전부 진행. review 쪽은 냅두고"). A-1과 B-1~B-5 반영 완료, C-1·C-2와 D-1은 위 사유로 보류. 반영 이력은 [`refactor-log.md`](refactor-log.md)에 있다.
