# Keyword-search Refactor Log

키워드 검색 작업 단위에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다. 감사 결과 원본은 같은 폴더의 `001-*.md`에 있다.

## 2026-09-05 — 검색·자동완성 조회 경로 전면 반영 (감사 001 A-1~A-4·B-1·B-3·B-4)

**반영 항목**: [`001-query-path.md`](001-query-path.md)의 A 4건·B 3건. B-2(`ElasticsearchConfig` 정리)는 [`vector-similarity/refactor-log.md`](../vector-similarity/refactor-log.md)에서 이미 반영했다. C·D 항목은 감사 판정대로 손대지 않았다. 구현 라운드에서 정해진 것은 같은 문서의 "구현 결과 (2026-09-05)" 절에 있다.

### 왜 문제였나 (쉬운 말로)

**오탈자 검색이 사실상 꺼져 있었다.** "청송사고"라고 잘못 쳐도 "청송사과 프리미엄 1kg"이 나오지 않았습니다. 오탈자를 비교하는 대상이 **상품명 전체를 통짜 한 덩어리로 저장한 필드**였기 때문입니다. 오타 허용은 "몇 글자까지 다른 걸 봐줄까"로 동작하는데, 검색어("청송사고", 4자)와 상품명 전체(13자)의 길이 차이만 9자라 애초에 허용 범위를 한참 넘습니다. 결국 이 기능이 실제로 걸리는 경우는 상품명이 검색어와 길이가 비슷한 짧은 단어("토마토"에 "토마투")뿐이었습니다.

이번에 상품명을 **띄어쓰기 단위로 쪼개 저장하는 필드**를 따로 만들어 거기에 오타 허용을 겁니다. "청송사과"(4자) 하나와 "청송사고"(4자)를 비교하니 한 글자 차이로 걸립니다. 덧붙여 두 가지를 조정했습니다. 원래는 단어 길이에 따라 허용 범위가 자동으로 정해졌는데, 그 방식은 2글자 단어에 오타를 아예 허용하지 않습니다. 사과·감자·양파처럼 농산물 이름은 2~4자가 대부분이라 가장 흔한 경우를 비켜 가고 있었습니다. 그래서 **길이와 상관없이 한 글자까지** 허용하도록 고정하고, 오타 검사를 시작하는 최소 검색어 길이도 3자에서 2자로 낮췄습니다.

**자동완성이 두 번째 단어를 다 치는 순간 사라졌다.** "청송 사"까지는 후보가 뜨는데 "청송 사과"를 완성하면 결과가 0건이 됐습니다. 저장할 때 이미 "청", "청송", "사", "사과" 식으로 앞글자 조각을 전부 만들어 두는데, 찾을 때도 "마지막 단어를 앞글자로 확장"하는 처리를 한 번 더 하고 있었기 때문입니다. 이 이중 처리가 단어 사이의 위치 계산을 어긋나게 만듭니다. 조회 쪽 확장을 없애고 입력한 말 그대로 찾도록 바꿨습니다 — 한 단어만 칠 때는 결과가 전과 같습니다.

**검색 한 번에 필요한 것의 57배를 받아 왔다.** 검색 결과로 화면에 쓰는 건 상품 ID·이름·카테고리·가격 네 가지뿐인데, 검색 엔진에서 상품 하나를 받아 올 때마다 추천용으로 저장해 둔 **숫자 1,536개짜리 묶음**까지 같이 딸려 왔습니다. 상품 하나가 21KB, 기본 10개면 210KB가 매 검색마다 오간 셈입니다(같은 문서를 벡터 없이 받으면 371바이트입니다). 화면에 나가는 응답 크기는 그대로였지만, 검색 엔진에서 서버로 오는 구간과 그걸 다시 자바 객체로 푸는 비용이 그만큼 낭비였습니다. 필요 없는 벡터를 빼고 받도록 했습니다.

**주소창에 숫자를 잘못 넣으면 서버 오류가 났다.** 검색 API는 로그인 없이 누구나 부를 수 있는데, 페이지 번호와 개수에 아무 제한이 없었습니다. `size=0`을 넣으면 스프링이 만든 영문 메시지가 그대로 나갔고, `page=1000`처럼 검색 엔진이 허용하는 위치를 넘기면 **500 서버 내부 오류**가 스택 추적과 함께 로그에 쌓였습니다. `size=10000`은 아예 성공해서 수백 MB를 받아 오고, 자동완성에서는 그 결과가 5분간 캐시에 남기까지 했습니다. 지금은 트래픽이 없어 사고가 없었을 뿐입니다. 범위를 벗어난 값은 한글 안내와 함께 400으로 끊도록 했습니다.

**"최신순 정렬"이 사실은 "우리가 저장한 순"이었다.** 상품 이벤트에는 상품 서비스가 그 이벤트를 내보낸 시각이 함께 실려 오는데, 그 값을 버리고 우리 서버가 저장하는 순간의 시각을 대신 넣고 있었습니다. 두 시각 사이에는 외부 AI 서버 호출이 두 번(벡터 만들기, 재료명 뽑기) 끼어 있어서, 상품마다 몇 초씩 제각각 밀립니다. 처리가 밀렸다가 몰아서 따라잡을 때는 이 차이가 더 벌어져 실제 발행 순서와 다르게 뒤집힐 수 있습니다. 이벤트가 준 값을 그대로 쓰도록 바꿔 이 흔들림을 없앴습니다.

다만 **"상품이 실제로 바뀐 시각"까지 간 것은 아닙니다.** 상품 서비스는 이벤트를 만드는 그 순간의 시각을 찍어 보내지(`ProductEventPublisher`의 `Instant.now()`), 판매자가 상품 정보를 고친 시각을 보내지 않습니다. 그래서 나중에 전체 상품을 다시 색인하려고 이벤트를 재발행하면 전 상품이 다시 비슷한 시각으로 뭉쳐 최신순이 무의미해집니다 — 이 부분은 발행하는 쪽이 값을 바꿔 줘야 풀립니다([`open-items.md`](../open-items.md) B-11).

**"색인 서비스"에 조회 기능이 들어 있었고, 로그가 세 배로 찍혔다.** 이름은 색인(저장) 서비스인데 자동완성 **조회**까지 들어 있어서, 조회 경로를 읽으려는 사람이 저장 코드를 열어야 했습니다. 자동완성을 조회 담당 서비스로 옮겼습니다. 또 상품 이벤트 한 건마다 "받았다·처리 시작·완료" 세 줄을 찍으면서 상품명을 세 번 반복했는데, 완료 한 줄만 남겼습니다. 저장 형식을 지정하는 것처럼 보이지만 실제로는 아무 효과가 없던 표시(`@JsonFormat`)도 지웠습니다.

### 무엇을 바꿨나

- **오탈자 검색이 실제로 동작하게 했다** — 비교 대상을 띄어쓰기 단위 필드로 바꾸고, 허용 범위를 한 글자로 고정하고, 최소 검색어 길이를 2자로 낮췄다.
- **자동완성이 두 단어에서도 결과를 준다** — 조회 쪽의 중복 접두어 확장을 없앴다.
- **검색 한 번에 오가는 데이터를 크게 줄였다** — 화면에 쓰지 않는 벡터를 받지 않는다.
- **잘못된 입력을 400으로 끊고, 최신순 정렬의 흔들림을 줄였다** — 페이징 상한을 두고, `updatedAt`에 색인 시각 대신 이벤트 발행 시각을 쓴다.
- **조회 코드를 한 파일로 모으고 로그를 줄였다** — 자동완성 조회가 조회 서비스로 옮겨졌고, 이벤트 1건당 INFO가 3줄에서 1줄이 됐다.

아래 표는 개발자 참조용이다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| A-1 오탈자 필드 | `productName.raw`(keyword) — 상품명 전체가 토큰 1개 | `productName.word`(text, standard) — 띄어쓰기 단위 토큰 |
| A-1 허용 범위 | `fuzziness=AUTO`(2자 단어는 0회 편집) | `fuzziness=1` 고정 |
| A-1 최소 길이 | 검색어 3자 이상 | 검색어 2자 이상 |
| A-2 자동완성 조회 | `match_phrase_prefix`(`max_expansions=10`) — 두 단어 입력 시 0건 | `match`(`operator=and`) |
| A-3 검색 응답 조회 | `_source` 전체(문서당 약 21KB) | `_source`에서 `vector` 제외(약 371바이트) |
| A-4 페이징 검증 | 없음 — `size=0`은 영문 400, `page=1000`은 500, `size=10000`은 성공 | `SearchErrorCode` 4종으로 400. `size` 1~100, 자동완성 `size` 1~20, `(page+1)×size ≤ 10,000` |
| B-1 자동완성 조회 위치 | `ProductIndexService`(색인 서비스) | `ProductSearchService`(조회 서비스) |
| B-3 `updatedAt` | 색인 시각(`Instant.now()`) — 임베딩·LLM 지연만큼 상품마다 밀림 | 이벤트가 실어 보낸 **발행 시각**(없으면 현재 시각). "상품이 바뀐 시각"은 발행 측이 보내지 않아 여전히 아니다 |
| B-4 컨슈머 로그 | 이벤트 1건당 INFO 3줄, 상품명 3회 반복 | 완료 INFO 1줄. 수신은 DEBUG |
| B-4 죽은 어노테이션 | `ProductDocument.updatedAt`에 효과 없는 `@JsonFormat` | 삭제 |

### 변경 파일

**코드 (키워드 검색 8개 · 신규 1 + 영향받은 다른 단위 1개)**

- `search/domain/ProductDocument.java` — `raw` → `word` 서브필드, `@JsonFormat` 삭제
- `search/application/ProductSearchService.java` — 오탈자 필드·허용 범위·게이트, `_source` 필터, 페이징 검증, 자동완성 조회 이관
- `search/application/ProductIndexService.java` — 자동완성 조회 제거, `updatedAt` 이벤트 값 사용
- `search/exception/SearchErrorCode.java` — 신규(400 4종)
- `search/infrastructure/elasticsearch/ProductAutocompleteRepository.java` — `match`(`operator=and`)
- `search/infrastructure/event/ProductEventConsumer.java` — 로그 축약, `updatedAt` 전달
- `search/application/dto/product/ProductIndexRequest.java` — `updatedAt` 필드 추가
- `search/presentation/SearchController.java` — 조회 서비스 하나만 주입, `page`·`size` 전달
- `recommend/application/RecipeRecommendService.java` — 바뀐 `search` 시그니처에 맞춤(부족 재료 상품 검색, `size=2`라 상한에 걸리지 않는다)

**문서 (5개)**

- `specs/keyword-search/product-search-and-autocomplete.md` — 용어·흐름·API 에러 표·데이터 모델·비즈니스 규칙·검증·리스크
- `ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` — §1 핵심 특징, §3.1 매핑, §4.2 오탈자 절, §5.2·§5.3 자동완성, §7.2 색인 절차
- `IMPLEMENTATION.md` — 색인 서비스 코드 발췌
- `product/glossary.md` — 오탈자 매칭·자동완성 접두어
- `specs/README.md` — 작업 단위 ↔ 클래스 매핑

### 검증

- `./gradlew :baro-ai:build` 통과 (컴파일 · spotless · checkstyle)
- `./gradlew :baro-ai:test`는 `NO-SOURCE` — 이 저장소에 자동화 테스트가 없다. "테스트 통과"라고 말할 근거가 없다는 뜻이다
- `grep`으로 `productName.raw`·`match_phrase_prefix`·`ProductIndexService.autocomplete` 잔존 0건 확인
- **수동 확인은 하지 못했다** — 로컬 Elasticsearch를 띄우지 못해 스펙 "수동 확인 (로컬)" 절의 6개 항목이 전부 미체크로 남아 있다
- `spring-reviewer` — Critical·High·Medium 0건. Low 2건 중 메서드 삭제 흔적으로 남은 빈 줄은 고쳤고, `SearchErrorCode`가 `spring-boot-java.md` 예시(`ErrorCode` 인터페이스 + `@Schema`)가 아니라 `BaseErrorCode`를 구현한 점은 `EmbeddingErrorCode`·`RecommendErrorCode`와 같은 이 저장소의 기존 관례라 그대로 뒀다(규칙 문서와 실제 관례의 차이는 하네스 회고 후보)
- `doc-reviewer` — 반드시 고칠 것 0건. 지적받은 변경 파일 수 표기와 용어집 변경 이력 순서를 고쳤다

### 인덱스 재생성이 필요하다 (사람이 직접 실행)

`productName`의 서브필드가 `raw`(keyword)에서 `word`(text)로 바뀌었다. Elasticsearch는 이미 만들어진 인덱스의 필드 타입을 바꾸지 못하므로 **`product` 인덱스를 지우고 다시 만든 뒤 상품 이벤트를 재발행**해야 오탈자 검색이 동작한다. 되돌릴 수 없는 삭제라 사람이 직접 실행한다(`core-guardrails.md` STOP §3).

`product-autocomplete`·`user_profile_embeddings` 등 다른 인덱스는 이번 변경의 대상이 아니다. 다만 [`taste-vector/refactor-log.md`](../taste-vector/refactor-log.md)가 요청한 인덱스 4개 재생성은 여전히 남아 있다.

### 남긴 것 · 알려진 제약

- **오탈자 매칭이 넓어진 만큼 결과 집합도 넓어진다** — 같은 검색 경로를 쓰는 레시피 추천의 부족 재료 상품 목록도 영향을 받는다. 계약 변경은 아니다
- **A-4는 계약이 바뀌는 변경이다** — 지금까지 200 또는 500이던 범위 밖 값이 400 + 새 에러 코드로 나간다. 프론트가 `size`를 100 초과로 쓰는 곳이 있는지는 확인하지 못했다(프론트 저장소가 이 하네스 범위 밖)
- **`updatedAt`은 "발행 시각"이지 "상품이 바뀐 시각"이 아니다** — 색인 시각을 쓰던 흔들림은 없앴지만, `baro-shopping`이 `Instant.now()`를 찍어 보내므로 재색인용 재발행에서는 여전히 전 상품이 뭉친다. 발행 측 변경이 필요해 [`open-items.md`](../open-items.md) B-11로 넘겼다
- **`ingredientName`은 `_source`에서 빼지 않았다** — 수십 바이트라 벡터 하나만 제외해도 충분하다(감사 A-3 판정)
- **C 항목은 손대지 않았다** — 상태만 바뀐 수정에도 임베딩·LLM을 다시 부르는 문제(C-1)는 운영 재개 후 빈도 측정이 먼저다. 응답 envelope 불일치(C-2), 운영 DEBUG 로깅(C-3), yml 컨슈머 설정 미적용(C-4), 자동완성 캐시 키 정규화(C-5)도 그대로다
- **`VectorProductSearchService.processSearchResults`(C-6)는 이미 없다** — `vector-similarity/001` B-1에서 삭제했다
