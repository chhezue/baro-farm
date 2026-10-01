# Recommend Refactor Log

`recommend` 도메인에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다.

## 2026-09-04 — 레시피 추천의 LLM 호출을 3회에서 1회로

**반영 항목**: 장바구니 기반 레시피 추천이 요청 1건마다 OpenAI를 3번 연달아 호출하던 구조. 결정문은 [`decisions/002-ingredient-name-at-index-time.md`](../../decisions/002-ingredient-name-at-index-time.md).

### 왜 문제였나 (쉬운 말로)

장바구니를 열어 레시피 추천을 받을 때마다, 서버가 외부 AI 서비스에 **세 번 연달아** 물어보고 있었습니다. 첫 번째는 "이 상품 이름에서 재료 이름만 뽑아줘"(예: "곰곰 무항생제 신선한 대란, 30구, 1개" → "계란"), 두 번째는 "그 재료 목록이 맞는지 다시 확인해줘", 세 번째가 진짜 목적인 "이 재료로 만들 수 있는 레시피 알려줘"였습니다.

문제는 앞의 두 번이 **매번 같은 답이 나오는 질문**이었다는 점입니다. "대란 30구는 계란이다"라는 건 그 상품의 성질이지, 누가 언제 장바구니에 담았는지와 무관합니다. 같은 계란을 100명이 담으면 똑같은 질문을 100번 한 셈입니다.

게다가 이 세 번의 요청이 **차례대로** 실행돼서, 앞이 끝나야 뒤가 시작됩니다. AI 서비스 응답이 느려지면 그만큼 사용자가 그대로 기다립니다. 시간 제한(타임아웃) 설정도 없어서 응답이 아예 안 오면 무한정 기다릴 수도 있었습니다. 첫 번째 호출이 실패하면 상품을 하나씩 따로 물어보는 예비 경로로 넘어가는데, 장바구니에 상품이 20개면 요청 한 건이 **23번**의 외부 호출로 불어났습니다.

### 무엇을 바꿨나

상품의 재료 이름을 **상품이 등록·수정될 때 미리 한 번만 계산해서 저장**해 두고, 추천할 때는 저장된 값을 읽기만 하도록 바꿨습니다. 상품 등록은 가끔 일어나고 추천 조회는 자주 일어나므로, 비용을 드물게 일어나는 쪽으로 옮긴 것입니다.

> 아래 표는 개발자 참조용이라 코드 식별자를 그대로 쓴다. 쉬운 설명은 바로 위 절에 있다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| 추천 1회당 LLM(외부 AI 서비스) 챗 호출 (정상) | **3회** (정규화 → 재료추출 → 레시피생성) | **1회** (레시피생성) |
| 추천 1회당 LLM 챗 호출 (최악) | **N+3회** — 배치 정규화 실패 시 상품별 개별 호출 | **1회** — 개별 폴백 루프 자체를 제거 |
| 재료명 계산 시점 | 추천 요청마다 | 상품 색인 시점 1회 (`ProductDocument.ingredientName`) |
| 장바구니 → 재료 조회 | LLM 2회 | ES 문서 조회 1회 (`findAllById`) |
| LLM이 원본 상품명을 그대로 되읊어야 재료 인정 | 있음 (`originalProductNames.contains`) — 공백 하나 어긋나면 재료가 조용히 사라짐 | **없음** — 재료명이 상품 문서에서 직접 오므로 대조 불필요 |
| `missingIngredientsCore` 계산 | **2번** 계산, 비교 기준이 서로 달랐고 첫 결과는 로그에만 쓰이고 버려짐 | **1번** — `RecipePromptService`가 계산한 값을 호출부가 그대로 사용 |
| API 응답 계약 | — | **변경 없음** (`RecipeRecommendResponse` 동일) |

### 변경 파일

**신규**
- `src/main/java/com/barofarm/ai/search/infrastructure/llm/ProductIngredientNormalizer.java` — 색인 시점 재료명 추출. LLM 실패 시 정규식 폴백. 프롬프트 주입은 `.param()`으로 넘겨 상품명에 포함된 중괄호가 템플릿 문법으로 해석되지 않게 했다
- `docs/decisions/002-ingredient-name-at-index-time.md`

**수정**
- `search/domain/ProductDocument.java` — `ingredientName`(`keyword`) 필드 추가
- `search/application/ProductIndexService.java` — 색인 시 재료명 계산. 실패해도 색인은 계속
- `recommend/application/RecipeRecommendService.java` — LLM 재료 추출을 ES 조회로 교체, 중복 계산 제거
- `recommend/infrastructure/llm/RecipePromptService.java` — `extractOwnedIngredients` 제거(288줄 → 198줄), `enhanceWithServerValidation`의 비교 기준을 `normalizeForCompare`로 통일
- `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` — `product` 매핑·색인 순서 갱신
- `docs/specs/recommend/recipe-recommendation.md` — 구현 반영

**삭제** (STOP §4 — 교체된 구 경로는 같은 변경에서 제거)
- `recommend/infrastructure/llm/ProductNameNormalizer.java` — 역할이 `ProductIngredientNormalizer`로 이동
- `recommend/domain/OwnedIngredient.java` — LLM 재료 추출 응답 전용 타입이라 호출부와 함께 소멸

### 검증

- `./gradlew :baro-ai:compileJava` 통과
- `./gradlew :baro-ai:spotlessApply :baro-ai:checkstyleMain` 통과
- 자동화 테스트는 여전히 **0개**(모듈 전체) — 호출 횟수 감소는 `spring.ai` DEBUG 로그로 요청당 LLM 호출이 1건인지 확인하는 수동 절차로 대체한다. 프롬프트 컴포저 단위 테스트는 후속 과제

### 남긴 것 · 알려진 제약

- **재색인 필요** — 이 변경 이전에 색인된 상품 문서에는 `ingredientName`이 없다. 그런 상품은 보유 재료로 집계되지 않으며, `RecipeRecommendService`가 건너뛴 건수를 WARN으로 남긴다. dev 모드라 전체 재색인으로 해소한다
- **색인 경로에 LLM 호출이 1건 추가됨** — 상품 CRUD 이벤트마다 발생한다. 요청 경로가 아니라 Kafka 컨슈머에서 처리되고 상품당 1회뿐이라 감수했다. 대량 초기 적재 시에는 호출량이 상품 수에 비례하므로, 벌크 적재 경로가 생기면 그때 배치화를 재검토한다
- **LLM 호출 타임아웃 없음** — 이번 변경 범위 밖. 아래 Gemini 전환 항목에도 그대로 남아 있다
- **`ProductSearchService.search`의 `_source` 미필터** — 부족 재료 검색 시 쓰지 않는 1536차원 벡터까지 가져온다. 공개 검색 API와 공유하는 코드라 이번 범위에서 제외했다

## 2026-09-04 — Spring AI 1.0.0-M5 → 1.1.8 업그레이드, LLM 제공자를 Gemini로 전환

**반영 항목**: 운영 비용을 0으로 만들기 위한 제공자 전환과, 그 전제가 되는 프레임워크 업그레이드. 결정문은 [`decisions/003-gemini-migration.md`](../../decisions/003-gemini-migration.md).

### 왜 했나 (쉬운 말로)

OpenAI는 쓴 만큼 돈을 내야 하는데, 구글 Gemini에는 무료로 쓸 수 있는 구간이 있습니다. 아직 수익이 없는 단계라 외부 AI 서비스 비용을 0으로 만드는 게 목적이었습니다.

그런데 그냥 모델만 바꿔 끼울 수는 없었습니다. 무료로 쓸 수 있는 Gemini는 "API 키 한 줄"로 붙는 방식인데, 이 프로젝트가 쓰던 Spring AI 버전(1.0.0-M5, 정식 출시 전 시험판)에는 그 연결 방식 자체가 들어 있지 않았습니다. 들어 있던 건 구글 클라우드 계정과 결제 정보를 등록해야 하는 유료 경로뿐이었습니다. 그래서 프레임워크를 먼저 올려야 했습니다.

여기서 한 번 더 갈림길이 있었습니다. 최신인 2.0 계열로 올리면 **저장소 전체의 Spring Boot를 4로 올려야** 하는데, 이건 5명이 함께 쓰는 저장소라 baro-ai 혼자 정할 수 없는 일입니다. 확인해 보니 **1.1.8이면 지금 쓰는 Spring Boot 3.5 그대로 두고도** 무료 Gemini 연결 방식을 쓸 수 있었습니다. 그래서 1.1.8을 골랐고, 결과적으로 다른 모듈은 한 줄도 건드리지 않았습니다.

> 아래 표는 개발자 참조용이라 코드 식별자를 그대로 쓴다. 쉬운 설명은 바로 위 절에 있다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| Spring AI | `1.0.0-M5` (정식 출시 전 마일스톤) | `1.1.8` (정식 릴리스) |
| Spring Boot | 3.5.8 | **3.5.8 (변경 없음)** — 다른 모듈 영향 0 |
| 챗 모델 | OpenAI `gpt-4o-mini` | Gemini `gemini-2.5-flash-lite` |
| 임베딩 모델 | OpenAI `text-embedding-3-small` | Gemini `gemini-embedding-001` |
| 벡터 차원 | 1536 | **1536 (유지)** — 출력 차원을 설정으로 고정해 ES 매핑·유사도 코드 그대로 |
| 챗 빈 주입 | `@Qualifier("openAiChatModel")` | `@Qualifier("googleGenAiChatModel")` |
| 임베딩 빈 주입 | `@Qualifier("openAiEmbeddingModel")` | `@Qualifier("googleGenAiTextEmbedding")` |
| API 키 환경변수 | `BARO_AI_OPENAI_KEY` | `BARO_AI_GEMINI_KEY` |
| 모델명 오버라이드 환경변수 | `SPRING_AI_OPENAI_MODEL`·`SPRING_AI_OPENAI_EMBEDDING_MODEL` | `SPRING_AI_GEMINI_MODEL`·`SPRING_AI_GEMINI_EMBEDDING_MODEL` |
| 재료명 추출 temperature | 0.7 (전역값이 그대로 적용) | **0.0** (호출부에서 명시) |
| 설정 파일의 죽은 항목 | 존재하지 않게 될 벡터스토어 자동설정 제외 | 제거 |

### 변경 파일

**신규**
- `docs/decisions/003-gemini-migration.md`
- `src/main/java/com/barofarm/ai/config/ChatClientConfig.java` — `OpenAiConfig`를 대체. 더 이상 OpenAI를 설정하지 않으므로 이름을 바꿨다

**수정**
- `build.gradle` — Spring AI `1.1.8`, OpenAI 스타터를 Google GenAI 챗·임베딩 스타터로 교체, 미사용 `spring-ai-elasticsearch-store` 제거
- `src/main/resources/application-prod.yml` — `spring.ai.openai.*`를 `spring.ai.google.genai.*`로 교체, 임베딩 출력 차원 1536 고정
- `embedding/application/ProductEmbeddingService.java`·`UserProfileEmbeddingService.java` — 임베딩 빈 `@Qualifier` 교체
- `search/infrastructure/llm/ProductIngredientNormalizer.java` — 재료명 추출 호출의 temperature를 0으로 고정
- `search/domain/ProductDocument.java`·`embedding/domain/UserProfileEmbeddingDocument.java` — OpenAI를 가리키던 주석 갱신

**삭제**
- `config/OpenAiConfig.java` — `ChatClientConfig`로 대체 (STOP §4)

### 왜 temperature를 따로 고정했나

`temperature`는 "같은 질문에 얼마나 다른 답을 내놓게 할지"를 정하는 값입니다. 레시피를 새로 지어내는 일에는 다양성이 필요하지만, "이 상품명에서 재료 이름을 뽑아라"는 매번 같은 답이 나와야 하는 일입니다. 전에는 설정 파일에 하나의 값(0.7)만 있어서 두 작업이 같은 값을 썼는데, 같은 상품에 대해 재료명이 매번 달라질 수 있는 상태였습니다. 재료명 추출 호출에만 0을 지정해 이 문제를 없앴습니다.

### 검증

- `./gradlew :baro-ai:build -x test` 통과
- `./gradlew :baro-ai:dependencies --configuration runtimeClasspath`로 실제 해석 결과 확인 — `spring-ai-openai`가 사라지고 `spring-ai-google-genai:1.1.8`·`spring-ai-google-genai-embedding:1.1.8`·`com.google.genai:google-genai:1.37.0`이 올라온 것을 확인
- 빈 이름·설정 키는 추측하지 않고 `spring-ai-autoconfigure-model-google-genai:1.1.8` 실물을 열어 확인 (`decisions/003` "확인한 실물 값" 표)
- 코드·설정에 남은 OpenAI 참조 0건 (grep 확인)

### 리뷰에서 잡혀 함께 고친 것

구현 후 `spring-reviewer`·`doc-reviewer` 기준으로 리뷰를 돌려 나온 지적을 같은 작업 안에서 반영했다.

| 지적 | 무엇이 문제였나 | 조치 |
|------|-----------------|------|
| 재료가 아닌 상품이 억지 재료명으로 색인됨 | 프롬프트는 "요리 재료가 아니면 빈 문자열"을 지시하는데, 코드가 빈 응답을 **실패로 오인해** 정규식 폴백을 태웠다. 그러면 후라이팬이 `"스테인리스 후라이팬"`으로 색인돼 보유 재료로 잡힌다. **모델이 지시를 잘 따를수록 더 나쁜 값이 저장되는 구조**였다 | 호출 실패(예외)와 "재료 아님"(빈 응답)을 구분. 후자는 빈 값 유지 |
| 누락 건수 과소 보고 | 재색인 필요 여부를 판단할 WARN 로그가 **ES에서 문서가 돌아온 상품만** 셌다. 인덱스에 문서가 아예 없는 상품(가장 흔한 케이스)이 집계에서 빠졌다 | 요청한 상품 수와 집계된 재료 수를 비교하도록 변경 |
| 배포 시 전부 조용히 실패 | `k8s/apps/baro-ai/deployment.yml`이 구 `BARO_AI_OPENAI_KEY`를 주입해, 그대로 배포하면 api-key가 빈 값이 된다. 색인은 예외를 삼키므로 **모든 상품이 빈 `ingredientName`으로 색인되고 추천은 빈 응답만 돌려준다** | 매니페스트의 키 이름을 `BARO_AI_GEMINI_KEY`로 교체 (클러스터 시크릿 항목 추가는 배포 담당자 몫) |
| LLM 호출 타임아웃 없음 | `spring.ai.google.genai.*`에는 타임아웃 속성이 아예 없다(1.1.8 설정 메타데이터로 확인). 상한이 없으면 Gemini가 응답하지 않을 때 추천 요청 스레드와 Kafka 리스너 스레드가 무기한 붙잡힌다 | `ChatClientConfig`에서 `com.google.genai.Client` 빈을 직접 만들어 **30초 상한** 지정 |
| 문서에 OpenAI가 현행처럼 남음 | `IMPLEMENTATION.md`의 로컬 실행 절차가 `export BARO_AI_OPENAI_KEY`를 안내해 **그대로 따라 하면 앱이 뜨지 않는** 상태였다. `README`·ES 아키텍처·`AGENTS.md`·`glossary.md` 등 9개 문서가 같은 문제 | 전부 Gemini 기준으로 갱신. 용어집에 `ingredientName`·"보유 재료" 등록 |

### 아직 하지 않은 것 · 남은 위험

- **앱 기동 검증을 못 했다.** Elasticsearch·Kafka가 로컬에 떠 있어야 하고 Gemini API 키도 필요하다. 컴파일·의존성 해석·정적검사까지만 확인한 상태다 — **키를 발급해 실제로 한 번 띄워봐야 완료다**
- **전체 재색인이 필요하다.** 임베딩 모델이 바뀌어 기존 벡터는 못 쓴다. `ingredientName`(`002`)도 함께 채워지므로 어차피 재색인이 필요하다
- **대량 재색인 시 Kafka 컨슈머가 버티는지 미검증** — 색인 1건당 원격 호출이 임베딩 1회에서 임베딩 + 재료명 2회로 늘었는데 `KafkaConsumerConfig`의 `max.poll.interval.ms`·`max.poll.records`는 기본값(5분 / 500건) 그대로다. 한 번에 수백 건이 들어오면 5분 안에 못 끝내 리밸런스가 반복될 수 있다. 게다가 에러 핸들러가 재시도 없이(`FixedBackOff(0, 0)`) 넘기고 색인 코드가 LLM 예외를 삼키므로, **무료 티어 호출 한도(429)에 걸린 상품은 빈 `ingredientName`으로 영구히 남는다.** 재색인은 이 경로를 반드시 대량으로 밟으므로 **재색인 전에 컨슈머 설정 조정이나 재료명 계산 분리를 결정해야 한다**
- **`review` 도메인의 요약 모델이 함께 바뀐다.** 공용 `ChatClient`를 주입받기 때문이다. 코드는 건드리지 않았지만 결과물이 달라진다 — 팀원 소유 도메인이라 알린다
- **`local` 프로필에 Gemini 설정이 없다** — `application-local.yml`이 없어 기본 프로필로 띄우면 api-key가 없다. 전환 이전에도 같은 조건이었을 수 있어 회귀인지는 기동해 봐야 안다
- **임베딩 출력 차원 1536이 실제로 나오는지 미확인** — 설정 키가 존재하는 것은 확인했지만, 실제 응답 길이가 다르면 ES `dims = 1536` 매핑에서 저장이 거부된다
- **`recommend.application`이 `search.infrastructure`의 리포지터리를 직접 주입한다** — 다른 도메인의 레이어를 건너뛰는 접근이다. 다만 `SimilarProductRecommendService`·`PersonalizedRecommendService`가 이미 같은 방식이라 이번에 생긴 패턴은 아니다. `ProductSearchService`에 조회 메서드를 두어 한 경로로 모으는 정리는 후속 과제


## 2026-09-04 — 프롬프트에서 잘못된 전제 제거, 클래스명 정리

**반영 항목**: 위 두 작업을 마친 뒤 사용자가 "상품에 카테고리가 이미 있는데 재료 필터링 호출이 필요했나"라고 물어 카탈로그를 다시 확인한 결과, **없는 상황을 가정한 지시가 프롬프트에 남아 있던 것**을 발견해 정리했다.

### 무엇이 문제였나 (쉬운 말로)

원래 있던 두 번째 AI 호출은 "장바구니에 프라이팬이나 세제 같은 요리 재료가 아닌 물건이 섞여 있을 수 있으니 걸러내자"는 목적이었습니다. 그런데 이 쇼핑몰의 카테고리를 전부 세어 보니 **22개가 모두 농산물이었습니다.** 최상위 분류부터가 "신선직송 농산물"이라, 애초에 걸러낼 물건이 존재할 수 없는 구조였습니다.

문제는 그 두 번째 호출을 없애면서 **같은 목적을 프롬프트 지시문으로 옮겨 심어 뒀다**는 점입니다. "요리 재료가 아니면 빈 문자열을 반환하라"는 지시와 후라이팬 예시가 그것인데, 존재하지 않는 상황을 위한 지시였습니다. 앞선 리뷰에서 잡힌 버그(빈 응답을 잘못 처리한 것)도 결국 이 없는 전제에서 파생된 것이었습니다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| "재료가 아니면 빈 문자열" 지시 | 프롬프트에 존재 (후라이팬 예시 포함) | **제거** — 이 카탈로그에는 해당 상품이 없다 |
| 빈 응답 해석 | "재료 아님" 판정으로 보고 빈 값 저장 | **추출 실패**로 보고 정규식 폴백 (분기 자체가 단순해짐) |
| 프롬프트 입력 | 상품명만 | 상품명 + **카테고리명** (`ProductIndexRequest`가 이미 들고 있어 추가 조회 없음) |
| 예시 | 2개 (하나는 존재하지 않는 상품 유형) | 3개 전부 실제 카테고리 기반 |
| 설정 클래스명 | `ChatClientConfig` | **`LlmClientConfig`** |
| 레시피 생성 클래스명 | `RecipePromptService` | **`RecipeCandidateGenerator`** |

### 왜 이름을 바꿨나

`ChatClientConfig`는 Spring AI가 제공하는 타입 이름을 그대로 따온 것이라, 이 클래스가 무엇을 설정하는지 드러나지 않았습니다. `RecipePromptService`도 마찬가지로 "프롬프트"라는 **수단**을 가리키는 이름이었는데, 재료 추출을 떼어낸 지금은 레시피 후보를 만드는 일만 남았으므로 역할대로 이름을 바꿨습니다.

**도메인 포트(인터페이스)는 만들지 않았습니다.** 처음에는 ssoss처럼 `RecipeGenerator` 같은 인터페이스를 두자고 제안했으나, 실제로 OpenAI에서 Gemini로 갈아끼워 보니 설정과 `@Qualifier` 3곳으로 끝났습니다. `ChatClient` 자체가 이미 벤더 추상화 역할을 하고 있어, 그 위에 구현체 1개짜리 인터페이스를 얹는 것은 `spring-boot-java.md`의 "다형성이 실제로 필요할 때만 인터페이스" 원칙에 어긋납니다.

### 변경 파일

- `search/infrastructure/llm/ProductIngredientNormalizer.java` — 프롬프트 정리, `normalize(productName, categoryName)`로 시그니처 변경
- `search/application/ProductIndexService.java` — 카테고리명 전달
- `config/ChatClientConfig.java` → `config/LlmClientConfig.java` (이름 변경)
- `recommend/infrastructure/llm/RecipePromptService.java` → `RecipeCandidateGenerator.java` (이름 변경)
- `recommend/application/RecipeRecommendService.java` — 참조 갱신
- 문서: `decisions/002`(카테고리 근거·대안 보강), `README.md`, `IMPLEMENTATION.md`, `specs/chat/chatbot.md`, `.claude/rules/baro-ai-performance-refactor.md`

### 검증

- `./gradlew :baro-ai:build -x test` 통과 (컴파일·spotless·checkstyle)
- 문서에 남은 구 클래스명 0건 (grep 확인)

### 함께 고친 것 — 재시도 정책

작업 중 `docs/audits/ssoss-llm-comparison.md`가 지적한 리스크를 확인해 반영했다. **Spring AI 1.1.8은 재시도 기본값이 최대 10회, 백오프 상한 3분**이고 baro-ai에는 override가 없었다(`spring-ai-retry-1.1.8.jar`의 `RetryUtils` 바이트코드로 직접 확인: `maxAttempts(10)`, `exponentialBackoff(2000ms, ×5.0, max 180000ms)`).

즉 호출 1회에 30초 상한을 걸어도 **요청 하나가 수십 분까지 늘어질 수 있는** 상태였다. 무료 티어의 호출 한도(429)에 걸리면 실제로 이 경로를 밟고, Kafka 리스너의 기본 `max.poll.interval.ms`(5분)를 훌쩍 넘긴다. `LlmClientConfig`의 타임아웃 주석이 "상한이 없으면 무기한"이라고만 적어 **그 30초가 시도 1회에만 걸린다는 사실을 가리고 있었다** — 주석도 함께 정정했다.

| 항목 | Before | After |
|------|--------|-------|
| 최대 재시도 | 10회 (기본값, 미지정) | **3회** |
| 백오프 | 2초 시작, 배수 5.0, 상한 3분 | **1초 시작, 배수 2, 상한 10초** |
| 최악 지연(추정) | 시도 10회 × 30초 + 백오프 누적 → 수십 분 | 시도 3회 × 30초 + 백오프 ~11초 → **약 1분 30초** |

### 남긴 것

- **카테고리를 결정적 가드로 쓰지는 않았다.** 지금은 프롬프트 힌트로만 넘긴다. LLM이 뽑은 재료명이 카테고리와 명백히 어긋날 때 폴백하는 검증은 넣지 않았다 — 실제로 어긋나는 사례를 본 뒤에 넣는 게 맞다고 판단했다
- **`AGRI_LIVESTOCK_EGG`처럼 리프 카테고리가 곧 재료명인 경우 LLM 호출을 생략하는 최적화**는 하지 않았다. 22개 중 해당하는 건 계란 하나뿐이라 지금은 이득이 작다
