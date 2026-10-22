# Keyword-search Refactor Log

키워드 검색 작업 단위에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다. 감사 결과 원본은 같은 폴더의 `001-*.md`에 있다.

## 2026-09-07 — 검색 API에서 기록 경로 제거 (감사 taste-vector/005 연동)

### 왜 문제였나 (쉬운 말로)

전날 검색 기록 저장을 응답 밖으로 빼서 검색을 빠르게 만들었는데, 실제로 재보니 그렇게 넘긴 일감의 37~65%가 조용히 버려지고 있었다. 그런데 더 따져 보니 그 기록으로 만드는 취향 신호 자체가 미덥지 않았다 — 사용자가 고른 상품이 아니라 검색 결과 상위에 뜬 상품이었다. 그래서 저장 방식을 고치는 대신 **기록을 없앴다.**

### 무엇을 바꿨나

검색 API는 이제 조회만 한다. 사용자 헤더(`X-User-Id`)도 읽지 않는다. 응답 형식은 그대로다.

### Before / After

| 항목 | Before | After |
|---|---|---|
| 검색 1회의 부수 작업 | 로그 적재 + 프로필 갱신 제출 | 없음 |
| 비동기 폐기(963회 기준) | 693건 | 0건 |
| p95 (동시 30) | 75.5ms | 48.2ms |

### 남긴 것

오탈자 매칭의 합성어 한계(`003` C-1)는 그대로다.

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

## 2026-09-06 — 상품 묶음 색인 · 검색 로그 비동기화 · 단위 테스트 (감사 002 A-1·A-2·B-1 + 측정 M-3)

**반영 항목**: [`002-index-throughput-and-search-log.md`](002-index-throughput-and-search-log.md)의 A 2건·B 1건 전부. 판단 항목 1(묶음 크기 N)은 M-3 수치와 최악 지연 계산으로 **5건·15분**으로 정했다. C·D 항목은 손대지 않았다.

### 왜 문제였나 (쉬운 말로)

**하나. 상품을 한 번에 하나씩만 색인했다.** 상품이 들어올 때마다 외부 AI에 두 번(벡터 만들기, 재료명 뽑기) 물어보고 검색 엔진에 두 번 저장했다. 벡터 만들기는 한 번에 여러 개를 물어볼 수 있는데도 하나씩 물어봤다. 게다가 무료 요금제에서는 **하나씩 13번 물어보면 그다음부터 거절(429)**당한다는 걸 이번에 실측으로 확인했다 — 상품 수백 건을 다시 색인하려면 하나씩 방식으로는 아예 끝까지 못 간다.

**둘. 검색 응답을 돌려주기 전에 검색 기록을 저장했다.** 회원이 검색하면 취향 분석용 기록을 남기는데, 그 저장이 끝나야 검색 결과가 나갔다. 기록은 부가 데이터라 조금 늦어도 되는데 본 응답을 붙잡고 있었다.

**셋. 페이징 한도 같은 방어 규칙이 테스트 없이 있었다.** 지난 라운드에 넣은 "페이지 크기는 100까지" 같은 규칙이 다시 깨져도 알 방법이 없었다.

### 무엇을 바꿨나

- **상품을 5개씩 묶어 색인한다** — 벡터 만들기는 묶음당 한 번, 검색 엔진 저장도 묶음당 한 번이다. 삭제가 섞여 있으면 그 앞까지 모인 것을 먼저 저장한 뒤 지워서 순서가 뒤집히지 않는다.
- **묶음 안에서 하나가 실패해도 나머지를 잃지 않는다** — 리뷰(`spring-reviewer`)가 잡은 결함이다. 처음 구현은 실패하면 묶음 전체를 다시 시도하고, 그래도 안 되면 묶음 전체를 버렸다. 지금은 실패한 자리를 알려서 그 앞은 확정하고 그 자리부터만 다시 받는다.
- **검색 기록 저장이 검색 응답을 붙잡지 않는다** — 기록 저장과 취향 갱신을 한 덩어리로 뒤에서 돌리고, 검색 응답은 바로 나간다.
- **방어 규칙에 테스트를 붙였다** — 페이지 크기 한도, 자동완성 글자 수, 묶음 색인, 실패 시 폴백이 전부 테스트로 고정됐다.

아래 표는 개발자 참조용이다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| Kafka 상품 이벤트 소비 | poll당 1건 | 묶음 리스너, poll당 최대 5건, 묶음 처리 허용 15분 |
| 묶음 내 부분 실패 | (해당 없음 — 1건) | `BatchListenerFailedException`에 실패 위치를 담아 던진다 → 앞 레코드 커밋, 실패 레코드부터 재시도 |
| 임베딩 호출 | 상품당 1회 | 묶음당 1회(`batchEmbedContents`), 실패 시 상품별 폴백 |
| ES 저장 | 상품당 `save` 2회 | 묶음당 `saveAll` 2회(인덱스당 bulk 1회) |
| 삭제 순서 | 건별 | 삭제를 만나면 모인 색인을 먼저 내보낸 뒤 삭제 |
| 검색 로그 적재 | 응답 스레드에서 동기 | 로그 적재 + 프로필 갱신을 한 비동기 작업으로 제출 |
| 테스트 | 2건(`multiSearch`) | 24건 (검색 10 · 색인 5 · 임베딩 4 · 컨슈머 5) |

### 측정 (M-3) — 앱 없이 잰 것

| 측정 | 결과 | 원본 |
|------|------|------|
| ES 저장: 문서 1,000건(벡터 포함 약 34KB) | 1건씩 4.95초(202건/초) vs bulk 100건씩 **1.16초(860건/초)** — 4.3배 | `vectorbench/bulk-vs-single-20260906-144659.json` |
| Gemini 임베딩: 상품명 50개 | 1건씩: **13회 성공 뒤 14번째 호출에서 429**(Resource exhausted) · 10건 묶음: 5회 전부 성공, 호출당 p50 1.45초(총 7.4초) · 50건·25건 묶음: 분당 한도(quota exceeded) | `vectorbench/embed-batch-20260906-144722.json`·`-144943.json` |

임베딩 묶음 상한은 공식 문서에 명시가 없어(`ai.google.dev/gemini-api/docs/embeddings`·`/api/embeddings` 2026-09-06 확인) 실측으로 10건은 안전, 25건 이상은 무료 티어 분당 한도에 걸린다고 기록한다. **묶음의 가치는 속도보다 한도다** — 1건씩은 13건 뒤에 막히고, 10건 묶음은 50건을 통과했다.

### 묶음 크기 5를 고른 계산

상품 1건의 최악 지연 = 임베딩(30초 × 3회 재시도) + 재료명(30초 × 3회) = 180초. `max.poll.interval.ms`를 넘기면 브로커가 컨슈머를 죽은 것으로 보고 리밸런싱해 같은 묶음을 다시 받는 악순환이 된다. 1건일 때는 180초 < 기본 300초라 최악에도 안전했다. 이 보장을 유지하려면 N × 180 ≤ interval이어야 하고, interval 15분(900초)에서 N = 5가 최대다. 정상 지연(임베딩 묶음 1.5초 + 재료명 건당 약 1.5초)에서는 5건이 10초 안팎이라 여유가 크다. N을 더 키우려면 interval을 같이 키워야 하고, 그러면 실제로 멈춘 컨슈머를 알아채는 데 그만큼 오래 걸린다 — 그 트레이드오프를 5·15분에서 끊었다.

**솔직한 한계**: 재색인 시간의 대부분은 상품당 1회인 재료명 LLM 호출이다(C-1, 프롬프트 변경이라 별도 라운드). 묶음 색인이 줄이는 건 임베딩 왕복과 ES 왕복이고, 임베딩은 시간(건당 약 0.15초 절감)보다 429 회피가 실익이다.

### 변경 파일

- `config/KafkaConsumerConfig.java` — product 팩토리 `max.poll.records` 1→5, `max.poll.interval.ms` 15분 명시, `setBatchListener(true)`
- `search/infrastructure/event/ProductEventConsumer.java` — `onMessages(List<ProductEvent>)`, 색인 모으기·삭제 전 flush, 실패 위치를 담은 `BatchListenerFailedException`(리뷰 반영)
- `search/application/ProductIndexService.java` — `indexProducts(List)`(`indexProduct` 삭제), `saveAll`
- `embedding/application/ProductEmbeddingService.java` — `embedProducts(List)` 배치 + 상품별 폴백
- `embedding/application/UserProfileRefreshTrigger.java` — `recordSearchAndRefresh` (`LogWriteService` 의존 추가)
- `search/application/ProductSearchService.java` — `LogWriteService` 의존 제거, 부가 작업을 트리거 제출 1회로
- `search/presentation/SearchController.java`·`search/application/dto/product/ProductIndexRequest.java` — stale Javadoc 정정
- 테스트(신규): `ProductIndexServiceTest`(5)·`ProductEmbeddingServiceTest`(4)·`ProductEventConsumerTest`(5), `ProductSearchServiceTest`에 10건 추가
- 측정 도구(신규): `scripts/vectorbench/common.py`·`bulk_vs_single.py`·`embed_batch.py`
- 문서: `specs/keyword-search/product-search-and-autocomplete.md`(흐름 2·3, 규칙 표, 리스크 2행, 변경 이력)

### 검증

- `./gradlew :baro-ai:test` — **61건 통과, 0 실패**(누적)
- `./gradlew :baro-ai:build` — 통과
- A-1 단위: `ProductIndexServiceTest` — 상품 3개에 임베딩 1회·`saveAll` 인덱스당 1회, 벡터 실패 상품은 `null`로 색인. `ProductEmbeddingServiceTest` — 묶음 실패 시 상품별 폴백, 실패한 것만 `null`. `ProductEventConsumerTest` — 생성→삭제 순서 보존, 색인 실패 시 묶음 첫 레코드 위치·삭제 실패 시 그 레코드 위치를 담은 예외
- 리뷰에서 잡힌 결함: 처음 구현은 실패 시 일반 예외를 던졌는데, spring-kafka 3.3 `FallbackBatchErrorHandler`는 그 경우 묶음 전체를 재시도한 뒤 **전부 폐기**한다(리뷰어가 jar 소스로 확인). 실패 지점 뒤의 멀쩡한 이벤트가 사라지는 구조였다 — 1건씩 처리하던 때보다 퇴행. `BatchListenerFailedException(index)`로 바꿔 닫았다
- A-2 단위: `ProductSearchServiceTest` — 회원 검색에 `recordSearchAndRefresh` 1회(상위 상품 3개), `LogWriteService` 직접 호출 없음, 제출 실패해도 결과 반환
- 통합(로컬 ES + Kafka, `open-items.md` A절 묶음): 상품 이벤트 100건 재발행 시 색인 완료 시간 Before/After — **미실행**. 로컬 앱 기동 자체가 아직 안 된 상태다

### 남긴 것

- `taste-vector/002` C-1(저장 직후 조회가 1초 refresh 안이면 최신 로그를 놓침): 로그 저장과 갱신이 같은 스레드에서 순서대로 도니 순서 문제는 없어졌고, refresh 지연은 그대로다 — 다음 행동에서 따라잡는다
- `DefaultErrorHandler`의 위치 기반 재시도(앞 레코드 커밋 → 실패 레코드부터 재시도 → 소진 시 그 레코드만 건너뜀)는 spring-kafka 소스 기준이며 로컬 Kafka로 재현하지 않았다 — 기동 확인 항목

## 2026-09-08 — 자동완성 인덱스 통합 · 바뀐 상품만 다시 만들기 · Elasticsearch 통합 테스트

**근거:** 사용자 요청으로 2026-09-08에 `baro-ai` 전체를 다시 감사한 결과다. 기존 감사(`00N-*.md`)처럼 작업 단위 하나를 좁게 본 것이 아니라 모듈 전체를 훑었고, 결과 문서는 [`full-module/`](../full-module/001-complexity-and-overengineering.md) 세 편이다. 여기서 "P0"는 동작하지 않거나 잘못된 설계로 판정해 반드시 고치기로 한 것, "P1"은 백엔드 설계 판단을 코드에 드러내기 위해 보강하기로 한 것을 가리킨다 — `core-scope.md`의 `priority: must/could` 라벨과는 다른 축이다. review 패키지는 팀원 소유라 이번 범위에서 제외했다.


### 왜 문제였나 (쉬운 말로)

**첫째, 상품 하나를 저장할 때 두 군데에 나눠 썼다.** 검색용 상품 창고와 자동완성 전용 창고에 같은 상품명을 각각 넣고 있었는데, Elasticsearch에는 "둘 다 되거나 둘 다 안 되게" 묶는 수단이 없다. 앞의 쓰기만 성공하면 자동완성에는 뜨는데 검색에는 없는 상품이 남는다. 같은 이유로 "지금 살 수 있는 상태" 목록도 두 곳에 따로 적혀 있어, 한쪽만 고치면 어긋나는 상태였다(코드 주석이 그걸 경고하고 있었다).

**둘째, 가격만 바뀌어도 외부 AI를 두 번씩 다시 불렀다.** 상품이 바뀌었다는 알림이 오면 무조건 임베딩과 재료명 추출을 새로 했는데, 둘 다 상품명만 보고 만들어지는 값이라 이름이 그대로면 결과도 같다. 재고나 가격 변경이 잦을수록 순수한 낭비였고, 무료 사용 한도까지 함께 갉아먹고 있었다.

**셋째, 매핑이 실제로 만들어지는지 확인할 방법이 사람 손밖에 없었다.** 손으로 확인해야 할 항목이 26건 쌓여 있었고 하나도 실행되지 않은 상태였다.

### 무엇을 바꿨나

- 자동완성 전용 창고를 없애고, 상품명 한 필드에 분석 방식 세 가지(형태소·띄어쓰기·접두어)를 함께 붙였다. 이제 저장은 한 번이고 상태 목록도 한 곳이다.
- 색인 전에 이미 저장된 상품을 읽어 **이름이 그대로면** 기존 임베딩·재료명을 그대로 옮겨 쓴다. 지난번에 만들지 못해 비어 있으면 이번에 다시 시도한다.
- Elasticsearch를 실제로 띄워 색인→검색 왕복, 자동완성 접두어, 벡터 후보 조회를 확인하는 테스트를 넣었다.

### Before / After

| 항목 | Before | After |
|---|---|---|
| 상품 1건 색인 시 인덱스 쓰기 | 2회 (product, product-autocomplete) | 1회 |
| 한쪽만 성공해 어긋날 가능성 | 있음 | 없음 (쓰기가 하나) |
| 구매 가능 상태 목록이 적힌 곳 | 2곳 (상수 + `@Query` 문자열) | 1곳 (`PURCHASABLE_STATUSES`) |
| 이름이 그대로인 수정 이벤트의 외부 호출 | 임베딩 1회 + 재료명 1회 | 0회 |
| ES 매핑·쿼리 자동 검증 | 없음 (사람이 로컬에서 확인) | 통합 테스트 3건 |

### 바꾼 파일

- `search/domain/ProductDocument.java` — `productName`에 `ac` 서브필드(Edge N-gram) 추가, `@Setting`으로 분석기 설정 연결
- 삭제: `search/domain/ProductAutocompleteDocument.java`, `search/infrastructure/elasticsearch/ProductAutocompleteRepository.java`
- `search/application/ProductSearchService.java` — 자동완성을 상품 인덱스 질의로, 자동완성 레포지토리 의존 제거
- `search/application/ProductIndexService.java` — 이중 쓰기 제거, 변경 감지, 재사용·재계산 지표
- `build.gradle` — Testcontainers(junit-jupiter, elasticsearch) 테스트 의존성
- 테스트: `ProductIndexServiceTest` 재작성(9건, 변경 감지 5건 포함), `ProductSearchServiceTest` 자동완성 검증 갱신, `ProductIndexAndSearchIntegrationTest`(신규 3건)

### 검증

- `./gradlew :baro-ai:test` — **전건 통과** (통합 테스트 포함, 컨테이너 기동 포함 약 50초)
- 통합 테스트가 실제로 확인한 것: 상품 매핑 생성, 한국어 형태소 검색이 "방울토마토"를 "토마토"로 찾는 것, 품절 상품이 상태 필터에서 빠지는 것, 통합된 인덱스에서 접두어 자동완성이 걸리는 것, 벡터 후보 조회의 Painless 스크립트가 도는 것
- 이미지는 저장소에 이미 있던 `docker/local/Dockerfile.es-nori`를 그대로 빌드해 쓴다 — 로컬 확인 환경과 같은 파일이라 둘이 어긋날 일이 없다

### 남긴 것

- **`product` 인덱스 재생성이 필요하다.** 상품명 매핑이 바뀌었으므로 기존 인덱스에는 접두어 서브필드가 없다. 재생성 + 상품 이벤트 재발행 전까지 자동완성이 결과를 내지 못한다 — `open-items.md` A-3
- **`product-autocomplete` 인덱스는 이제 아무도 쓰지 않는다.** 삭제는 되돌릴 수 없어 사람이 실행한다
- 재료명 LLM 호출은 여전히 상품마다 1회다. 여러 상품을 한 프롬프트로 묶는 것은 골든셋 재측정이 필요해 별도 라운드로 남겼다

## 2026-09-09 — 검색 서비스 셋으로 분리 · 재색인 API 신설

**근거:** [`full-module/003-target-architecture-and-priority.md`](../full-module/003-target-architecture-and-priority.md)의 P2·P3 항목이다. P0·P1은 2026-09-08에 반영했고, 이번에 나머지를 처리했다. review 패키지 소유 항목(P2-3·P2-4·P3-5)은 팀원 몫이라 손대지 않았다.

### 왜 문제였나 (쉬운 말로)

**첫째, 한 파일이 426줄이었다.** 그 안에 성격이 다른 셋이 섞여 있었다 — 검색 엔진에 보낼 질의문을 조립하는 코드, 검색 유스케이스를 조율하는 코드, 자동완성. 특히 자동완성은 규칙이 따로다(2글자부터 시작, 개수 상한이 다름, 결과를 잠시 기억함). 검색어 가중치 하나를 고치려 해도 이 셋을 다 지나쳐야 했다.

**둘째, 인덱스를 새로 만들면 채울 방법이 없었다.** 평소 색인은 상품 변경 알림(Kafka)으로 이뤄지는데 이미 지나간 알림은 다시 받을 수 없다. 그래서 저장 형식을 바꿔 인덱스를 새로 만들면 그 안이 텅 비고, 채우려면 **다른 팀 담당자에게 "전 상품을 다시 발행해 달라"고 부탁**하는 수밖에 없었다. 그 부탁이 걸린 채로 인덱스 재생성 항목이 계속 쌓여 있었다.

### 무엇을 바꿨나

- 파일을 셋으로 나눴다 — 질의 조립(`ProductQueryFactory`) · 검색(`ProductSearchService`) · 자동완성(`ProductAutocompleteService`).
- 상품을 원본에서 다시 읽어 색인하는 관리 API를 만들었다. **한 번에 한 페이지씩** 처리한다 — 상품마다 재료명 AI를 부르므로 수천 건을 한 요청에서 하면 끝나지 않고 무료 한도에도 걸린다. 부르는 쪽이 페이지를 올리며 속도를 조절한다.
- 이 API는 **토큰이 맞을 때만** 받는다. 부를 때마다 상품 수만큼 외부 AI 호출이 나가서, 아무나 부를 수 있으면 비용을 그대로 태울 수 있다. 토큰을 설정하지 않은 서버에서는 항상 거절한다.
- **인덱스를 지우지는 않는다.** 저장 형식을 바꿔야 하면 사람이 먼저 지우고 다시 만든 뒤 이 API를 부른다 — 되돌릴 수 없는 삭제를 API 뒤에 숨기지 않았다.

### Before / After

| 항목 | Before | After |
|---|---|---|
| `ProductSearchService` | 426줄 (질의 조립 + 검색 + 자동완성) | 144줄 (검색만) |
| — | | `ProductQueryFactory` 261줄 · `ProductAutocompleteService` 69줄 |
| 전체 재색인 | 다른 팀에 전체 재발행 요청 | `POST /api/v1/search/admin/reindex?page=&size=` |
| Elasticsearch 응답 상한 | 30초 (RestClient 기본값을 그대로 적어 둔 값) | 3초 |

### API 계약 변경

| 엔드포인트 | 변경 |
|---|---|
| `POST /api/v1/search/admin/reindex` | **신설.** `X-Admin-Token` 헤더 필요(불일치·미설정이면 403 `ADMIN_TOKEN_INVALID`). 이미 실행 중이면 409 `REINDEX_ALREADY_RUNNING` |

### 바꾼 파일

- 신규: `search/infrastructure/elasticsearch/ProductQueryFactory.java`, `search/application/ProductAutocompleteService.java`, `search/application/ProductReindexService.java`, `search/presentation/ProductReindexController.java`, `search/infrastructure/client/ProductCatalogClient.java`(+`dto/CatalogProduct`)
- `search/application/ProductSearchService.java` — 426줄 → 144줄
- `search/presentation/SearchController.java` — 자동완성 서비스 주입
- `search/exception/SearchErrorCode.java` — `REINDEX_ALREADY_RUNNING`·`ADMIN_TOKEN_INVALID`
- `application.yml` — ES 응답 상한 3초, 관리 토큰, 커넥션 풀 기본값 주석, 쓰이지 않던 producer 설정 삭제
- 테스트: `ProductAutocompleteServiceTest` 분리 신설

### 검증

- `./gradlew :baro-ai:build` — 전건 통과. 통합 테스트 3건이 분리 후에도 색인→검색·자동완성이 그대로 동작함을 실물 ES로 확인
- **미검증:** 재색인 API 실동작. `baro-shopping`이 떠 있어야 하고 Gemini 호출이 실제로 나가므로 로컬에서 돌리지 않았다
