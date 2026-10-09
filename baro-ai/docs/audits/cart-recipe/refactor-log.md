# Cart-recipe Refactor Log

장바구니 레시피 작업 단위에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다. 감사 결과 원본은 같은 폴더의 `001-*.md`에 있고, 이전 라운드는 [`audits/recommend/refactor-log.md`](../recommend/refactor-log.md)에 있다.

## 2026-09-05 — 장바구니 연동·요청 경로 전면 반영 (감사 001 A-1·A-2·B-1~B-5 + 채점식 삭제)

**반영 항목**: [`001-cart-integration-and-request-path.md`](001-cart-integration-and-request-path.md)의 A 2건·B 5건 전부, 그리고 [`recommend/002`](../recommend/002-recipe-scoring.md) C-1의 채점식 삭제(사용자 결정). C·D 항목은 감사 판정대로 손대지 않았다. 착수 시점 결정은 감사 문서의 "구현 결과 (2026-09-05)" 절에 있다.

### 왜 문제였나 (쉬운 말로)

**레시피 추천이 실제 경로에서 한 번도 동작한 적이 없을 가능성이 높다.** 두 가지가 겹쳐 있었습니다.

첫째, 장바구니 서비스의 주소를 찾지 못했습니다. 우리 코드는 `shopping-service`라는 **이름**만 적어 두고 "이름을 주소로 바꿔 주는 장치"에 맡기고 있었는데, 운영 환경에서는 그 장치가 꺼져 있고 대신 쓸 설정도 없었습니다. 게다가 실제 서비스 이름은 `baro-shopping`이라 이름조차 달랐습니다. 그래서 호출이 "서비스를 찾을 수 없음"으로 끝나고, 그 실패가 예외 → 빈 응답으로 두 번 포장되면서 로그에는 "통신 오류입니다" 한 문장만 남았습니다.

둘째, 주소를 고쳐도 응답을 못 읽었습니다. 장바구니 서비스는 답을 `{상태, 데이터, 메시지}`라는 **봉투**에 담아 보내는데, 우리 코드는 봉투를 벗기지 않고 알맹이 형식으로 바로 받으려 했습니다. 이러면 봉투 속 실제 데이터를 못 찾아 모든 항목이 비어 버리고, 결과적으로 "장바구니가 비어있습니다"라는 **사실과 다른 로그**를 남기며 빈 화면이 나갔습니다. 지금까지 이 기능이 된다고 보였다면, 그건 장바구니 서비스를 거치지 않고 내용을 직접 넣는 테스트용 통로로만 확인했기 때문일 수 있습니다.

**장바구니 서비스가 멈추면 최대 1분을 붙잡고 있었다.** 이 호출에는 시간 제한이 없어 기본값인 1분까지 기다립니다. 그런데 앞단의 관문은 5초면 사용자에게 오류를 돌려주므로, 사용자가 이미 실패 화면을 본 뒤에도 우리 서버는 55초를 더 기다렸다가 뒤늦게 AI까지 호출하는 구조였습니다. 연결 1초·응답 3초로 상한을 뒀습니다.

**AI에게 "빈 배열로 두라"고 시켜 놓고 매번 받아 버렸다.** AI에게 응답 형식을 강제할 때 쓰는 장치가 **모든 필드를 필수**로 요구합니다. 그런데 그 형식 안에는 서버가 어차피 다시 계산해 덮어쓰는 필드가 두 개 있었고, 그래서 프롬프트에 "이 둘은 빈 배열로 두세요"라고 따로 부탁하는 문장까지 들어가 있었습니다. AI가 만드는 글자 수는 비용과 대기 시간에 그대로 반영되는데, 후보 3개마다 버릴 값을 만들게 한 셈입니다. 응답 전용 형식을 따로 만들어 그 두 필드를 아예 빼고, 부탁 문장도 지웠습니다.

**서버가 AI의 답 3개 중 하나를 점수로 골랐는데, 그 점수의 근거가 없었다.** 보유한 핵심 재료 수에 10을 곱하고, 추가 재료에 3을 곱하고, 부족한 재료가 한두 개면 15점을 더하는 식이었습니다. 이 숫자들이 어디서 나왔는지 설명하는 문서가 저장소 어디에도 없고, 더 중요한 건 **AI에게 주는 지시가 이미 같은 정책을 강제하고 있었다는 점**입니다("핵심 재료의 30~50%는 없는 재료로 구성하라"). 즉 후보 3개가 애초에 비슷한 점수로 나오게 되어 있었고, 실제로 코드에는 동점일 때 무작위로 고르는 처리가 따로 들어 있었습니다 — 동점이 흔했다는 신호입니다. 사용자 결정으로 이 점수 로직을 걷어내고, AI가 낸 순서의 첫 번째 후보를 쓰도록 했습니다.

**존재하지 않는 실패를 위한 코드가 로그를 지우고 있었다.** 장바구니 조회에서 "장바구니 없음(404)"은 나올 수 없습니다 — 그쪽 서비스가 빈 장바구니를 정상 응답으로 돌려주기 때문입니다. 그런데 그 경우를 처리하는 코드가 있었고, 나머지 모든 실패를 "통신 오류입니다" 한 문장으로 바꿔 버려 **상태 코드와 주소가 로그에서 사라졌습니다**. 위의 주소 문제를 늦게 발견한 이유이기도 합니다. 이 처리기를 지우니 원래 오류 메시지가 그대로 남습니다.

**로그 문구가 원인을 가리키지 않았다.** 실패를 감싸는 범위는 장바구니 조회·재료 조회·AI 호출·상품 검색 전부인데, 실패 메시지는 "장바구니 조회 실패"였습니다. 검색 엔진이 죽어도 "장바구니 조회 실패"로 기록되던 셈입니다. 실제 범위대로 문구를 고쳤습니다.

### 무엇을 바꿨나

- **레시피 추천이 실제 경로에서 동작할 수 있게 했다** — 장바구니 서비스 주소를 설정으로 주고, 응답 봉투를 벗겨 읽는다.
- **장바구니 서비스가 느려도 오래 붙잡지 않는다** — 연결 1초·응답 3초 상한을 뒀다.
- **AI가 버릴 값을 만들지 않게 했다** — 응답 전용 형식을 분리해 필드 10개를 8개로 줄이고, 관련 프롬프트 문장도 지웠다.
- **근거 없는 채점 로직을 걷어냈다** — 서버 점수 대신 AI가 낸 순서의 첫 후보를 쓴다(사용자 결정, 정책 변경).
- **죽은 코드와 부정확한 로그를 정리했다** — 도달할 수 없는 오류 처리기, 운영에 노출돼 있던 테스트용 통로, 실제 범위와 다른 실패 문구.

아래 표는 개발자 참조용이다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| A-1 Feign 응답 타입 | `CartInfo` — 봉투를 못 벗겨 전 필드 `null` | `ResponseDto<CartInfo>` → `data()` |
| A-2 대상 주소 | `@FeignClient(name = "shopping-service")`만, Eureka 꺼짐 → 503 | `spring.cloud.openfeign.client.config.shopping-service.url`(기본 `http://baro-shopping:8080`) |
| B-5 호출 상한 | 없음(기본 연결 10초·읽기 60초) | 연결 1초·읽기 3초 |
| B-1 오류 처리기 | `CartFeignErrorDecoder`·`CartFeignConfig` — 404 분기는 도달 불가, 나머지는 상태 코드·URL을 지움 | 삭제. `RecommendErrorCode`는 상수가 0개가 되어 파일째 삭제 |
| B-2 테스트 엔드포인트 | `POST /recipes/test`(인증 없이 임의 장바구니로 LLM 호출) | 삭제 |
| B-3 LLM 응답 타입 | 도메인 타입 `CandidateRecipePlan`(10필드, 전부 필수) 겸용 + setter 8개 복사 | `RecipeCandidateDraft`(8필드) 분리, 도메인은 record + `from` 팩토리 |
| B-3 프롬프트 | "usedOwnedIngredients, missingIngredientsCore: 빈 배열로 두세요" 한 줄 | 삭제(스키마에서 빠졌으므로) |
| 채점식 | 점수 = 보유 핵심 ×10 + 보유 추가 ×3 + 부족 보너스(−10/+15/−5), 동점 시 난이도 → 셔플 | 삭제. `chooseFirst` — 이름이 있는 첫 후보 |
| B-4 실패 로그 | "장바구니 조회 실패로 레시피 추천을 건너뜁니다" | "레시피 추천 실패(장바구니 조회·재료 조회·후보 생성 중 예외)" |
| B-4 죽은 코드 | `CartInfo.empty()`(호출 0건), `userId != null ? … : "null"`, 중복 `.limit(size)` | 삭제 |

### 변경 파일

**코드 (10개 · 신규 2 · 삭제 3)**

- `recommend/infrastructure/client/CartClient.java` — 반환 타입 `ResponseDto<CartInfo>`, `configuration` 제거
- `recommend/infrastructure/client/dto/CartInfo.java`·`CartItemInfo.java` — `empty()` 삭제, 모듈명 주석 정정
- `recommend/infrastructure/llm/RecipeCandidateDraft.java`·`RecipeCandidateDrafts.java` — 신규(LLM 응답 전용)
- `recommend/infrastructure/llm/RecipeCandidateGenerator.java` — 응답 타입 교체, 프롬프트 한 줄·setter 복사 삭제, stale 주석 정정
- `recommend/domain/CandidateRecipePlan.java` — record 전환, `from` 팩토리, `calculateScore` 삭제
- `recommend/domain/RecipeCandidates.java` — record 전환, `chooseBest` → `chooseFirst`
- `recommend/application/RecipeRecommendService.java` — 봉투 언래핑, 테스트 메서드·죽은 코드 삭제, 실패 로그 문구
- `recommend/presentation/RecommendationController.java` — 테스트 엔드포인트 삭제
- 삭제: `recommend/infrastructure/client/CartFeignConfig.java`·`CartFeignErrorDecoder.java`, `recommend/exception/RecommendErrorCode.java`
- `src/main/resources/application-prod.yml` — Feign `url`·타임아웃

**문서 (5개)**

- `specs/cart-recipe/recipe-recommendation.md` — 배경·용어·흐름·API·데이터 모델·비즈니스 규칙·검증·리스크
- `FEATURES.md` — 테스트 엔드포인트 절 삭제, 응답 예시를 실제 경로 아래로
- `README.md` — API 목록, 패키지 트리의 추천 에러 코드 줄
- `IMPLEMENTATION.md` — 레시피 서비스 코드 발췌
- `product/glossary.md` — "판매 기회 보너스"·"후보 점수" 삭제, "후보 원본" 추가

### 검증

- `./gradlew :baro-ai:build` 통과 (컴파일 · spotless · checkstyle)
- `./gradlew :baro-ai:test`는 `NO-SOURCE` — 이 저장소에 자동화 테스트가 없다. "테스트 통과"라고 말할 근거가 없다는 뜻이다
- `grep`으로 `recipes/test`·`chooseBest`·`calculateScore`·`RecommendErrorCode`·`CartFeign` 잔존 0건 확인(문서의 "삭제했다" 서술 제외)
- **수동 확인은 하나도 하지 못했다** — `baro-shopping`과 Gemini 실호출이 모두 필요해 스펙 "수동 확인 (로컬)" 절의 6개 항목이 전부 미체크다. 이 단위는 특히 검증 공백이 크다: A-1·A-2가 실제로 동작하는지는 실행으로만 확인할 수 있다
- `spring-reviewer` — 지적 0건. 위험하다고 본 네 가지를 실물 라이브러리로 직접 확인해 줬다: `ResponseDto`가 record이고 보조 생성자가 있어도 Jackson이 정식 생성자로 역직렬화하는 것, `spring.cloud.openfeign.client.config.*`가 이 버전(4.3.0)의 실제 바인딩 경로인 것, `jsonschema-generator` 4.38.0이 record로 스키마를 만들고 되돌리는 것, `RecommendErrorCode` 삭제가 STOP §2 위반이 아닌 것
- `doc-reviewer` — 반드시 고칠 것 1건(용어집이 삭제된 메서드를 가리킴) 포함 3건 전부 반영

### 기동 확인이 필요하다 (사람이 직접 실행)

인덱스 재생성은 이 단위에 없다. 대신 아래를 확인해야 안전하다.

1. `baro-shopping`을 띄우고 `SHOPPING_SERVICE_URL`을 로컬 주소로 준 뒤 추천 API를 1회 호출 — `ownedIngredients`가 채워지면 A-1·A-2가 함께 동작한 것이다
2. 같은 호출에서 레시피가 나오는지 — 스키마 생성·파싱 자체는 같은 라이브러리 버전으로 로컬 검증했지만(`spring-reviewer`), 실제 Gemini 응답까지 이어지는 경로는 아직 확인하지 못했다
3. 담은 상품이 많은 장바구니로 호출 — 정상 조회가 읽기 상한 3초를 넘지 않는지. 넘으면 상한을 올린다
4. 배포 시 `baro-shopping`이 다른 네임스페이스면 `SHOPPING_SERVICE_URL`에 FQDN(`baro-shopping.<ns>.svc.cluster.local`)이 필요하다 — 배포 담당자 확인(감사 판단 항목 2)

### 남긴 것 · 알려진 제약

- **채점식 삭제는 비즈니스 정책 변경이고, 되돌릴 근거가 얇다** — 삭제 전에도 후보별 점수를 로그로 남기지 않아 삭제 전후 추천 품질을 비교할 자료가 없다. 복원이 필요하면 [`recommend/002`](../recommend/002-recipe-scoring.md) C-1의 가중치를 쓴다
- **`POST /recipes/test` 삭제는 계약 변경이다** — Before: 존재 / After: 404. 저장소 전체에서 호출부는 0건이었다
- **읽기 3초는 실측 없이 정한 값이다** — `baro-shopping`의 정상 장바구니 응답 시간을 재지 못했다. 그쪽 조회가 항목마다 상품·재고를 다시 부르는 구조라 담은 상품이 많으면 3초를 넘길 수 있다
- **C 항목은 손대지 않았다** — 추천이 검색 인프라 레포지토리를 직접 주입하는 것(C-1), 카테고리 폴백 문자열(C-2), 응답의 `ownedIngredients`가 비교용 정규화 형태로 나가는 것(C-3), 의존성 정리(C-4), 설정 프로필(C-6)
- **게이트웨이 응답 상한(5초) vs LLM 최악 지연은 그대로다** — 판단 항목 1. 정상 케이스의 LLM 지연을 먼저 재야 하는데 운영이 내려가 있다

## 2026-09-06 — 프롬프트 재설계 반영 · 수신 DTO 정리 · 가설 2건 기각

**반영 항목**: [`recipe-quality-redesign.md`](../../specs/cart-recipe/recipe-quality-redesign.md)(Approved)의 프롬프트·응답 필드 재설계, 그리고 쓰지 않는 수신 필드 정리. 같은 날 측정한 두 가설([`003`](003-pantry-staples-and-variants.md))은 **둘 다 기각돼 코드를 바꾸지 않았다.**

### 왜 문제였나 (쉬운 말로)

**하나. 추천이 지어낸 요리를 내놨다.** 프롬프트가 모델에게 "후보를 3개 내라"·"조리 방식을 서로 다르게 하라"고 시켜서, 마땅한 요리가 하나뿐인 장바구니에서도 세 번째 칸을 채우려고 "오이 파스타" 같은 걸 만들어냈다. 정작 서버는 첫 번째 후보만 쓰고 나머지 둘은 버렸다 — 이상한 답을 만들 압력만 주고 결과는 버리고 있었던 셈이다.

**둘. 화면에서 앞뒤가 안 맞았다.** "보유 재료"로 장바구니 전체를 그대로 내보내서, 11가지가 담긴 장바구니에서 김치찌개를 추천하면 우유·사과까지 김치찌개 재료인 것처럼 보였다.

**셋. 옆 서비스가 보내는 값을 쓰지도 않으면서 그대로 베껴 두고 있었다.** 장바구니를 받아오는 자리에 16개 칸이 있는데 그중 4개만 읽고 있었다. 지금 당장 고장 나는 건 아니지만, 읽는 사람이 "이 값들이 다 쓰이는구나" 하고 오해하고, 옆 서비스가 바뀔 때 여기까지 같이 고쳐야 하는 것처럼 보인다.

### 무엇을 바꿨나

| 무엇 | Before | After |
|------|--------|-------|
| 모델이 만드는 후보 수 | 3개 (2개는 버림) | 1개 |
| 모델이 채우는 칸 수 | 8개 | 3개 |
| 응답의 "보유 재료" | 장바구니 전체 | 이 요리가 실제로 쓰는 것만 |
| 장바구니 수신 칸 | 16개 (4개만 읽음) | 4개 |
| 장바구니 이벤트 수신 칸 | 6개 (4개만 읽음) | 4개 |
| 운영 모델 | `gemini-2.5-flash-lite` (신규 키로 404) | `gemini-3.5-flash-lite` |

품질은 골든셋 42건 × 2팔로 쟀다 — 실행 단위 합격이 69.0%에서 **90.5%**로 올랐고 출력 토큰은 **72.6% 줄었다**([`002`](002-goldenset-before-after.md)).

### 수신 칸을 지워도 되는 근거

읽지 않는 칸을 지우면 옆 서비스가 보낸 값이 들어올 자리가 없어진다. 그래도 되는지 실물로 확인했다.

- **Feign(장바구니 조회)** — 모르는 값을 무시하도록 돼 있다(Spring Boot 기본값, `application.yml`에 `spring.jackson` 설정 없음을 확인)
- **Kafka(장바구니 이벤트)** — `spring-kafka 3.3.11` jar의 `JacksonUtils`를 직접 뜯어 `FAIL_ON_UNKNOWN_PROPERTIES`를 끄는 것을 확인했다
- 두 레코드를 코드에서 직접 만드는 곳은 0건이다(Jackson만 만든다) — 생성자 인자 수가 바뀌어도 깨질 호출부가 없다

### 변경 파일

- `recommend/infrastructure/llm/RecipeCandidateDraft.java` — 8칸 → 3칸
- `recommend/infrastructure/llm/RecipeCandidateGenerator.java` — 프롬프트 전면 교체
- `recommend/domain/CandidateRecipePlan.java` — 10칸 → 5칸, 보유 재료 계산을 핵심 재료 기준으로
- `recommend/application/RecipeRecommendService.java` — 응답의 보유 재료를 이 요리가 쓰는 것으로
- `recommend/infrastructure/client/dto/CartInfo.java` — 6칸 → 1칸
- `recommend/infrastructure/client/dto/CartItemInfo.java` — 10칸 → 3칸
- `resources/application.yml` — 운영 모델 교체

### 검증

- `./gradlew :baro-ai:build` 통과
- `./gradlew :baro-ai:test`는 `NO-SOURCE` — 자동화 테스트가 0개다. "테스트 통과"라고 말할 근거가 없다
- 골든셋 42건 × 2팔, 사람 채점(축1·2·4) + 기계 계산(축3·5)
- **`baro-shopping` 실호출은 여전히 못 했다** — 수신 칸을 줄인 변경이라 실제 왕복 확인이 특히 필요하다

### 바꾸지 않은 것 (근거가 안 나와서)

| 가설 | 결과 |
|------|------|
| 기본 양념 목록을 넓히면 살 수 있는 재료가 는다 | **기각** — 목표 지표 50%→45%(42건 중 2건, 신호 아님). 목록에 넣어도 모델이 20건이나 어긴다 |
| 대파·쪽파·실파 같은 변종을 합치면 추천이 나아진다 | **기각** — 합쳐도 나아지지 않았고, "이미 가진 걸 사라"는 상황은 141건에서 0건 |

### 기동 확인이 필요하다 (사람이 직접 실행)

1. `baro-shopping`을 띄우고 추천 API 1회 호출 — 칸을 줄인 뒤에도 장바구니가 정상 파싱되는지
2. 같은 호출에서 `ownedIngredients`가 **이 요리가 쓰는 재료만** 담고 있는지
3. 장바구니 이벤트를 1건 흘려보내 로그 적재가 되는지 — 이벤트 수신 칸도 2개 줄였다
