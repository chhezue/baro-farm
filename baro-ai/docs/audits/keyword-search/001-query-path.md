# Keyword-search Architecture Audit 001 — 검색·자동완성 조회 경로 (2026-09-04)

키워드 검색 작업 단위의 **조회 경로**(`GET /api/v1/search`·`GET /api/v1/search/autocomplete` → `ProductSearchService`·`ProductIndexService.autocomplete` → Elasticsearch(ES))와, 이전 라운드가 다루지 않은 색인 경로의 나머지(`updatedAt` 처리·로그·ES 클라이언트 설정)를 감사한 결과다. 반드시 고칠 A 항목 4건(오탈자 매칭이 실질적으로 죽어 있음, 두 단어 자동완성이 빈 결과, 검색 응답이 벡터까지 로드, page·size 무검증), 유지보수성 B 항목 4건이 나왔고 **2026-09-05 전부 승인 완료**(문서 끝 "승인 결과" 절). A-1·A-2·A-3은 로컬 ES 8.12.0 컨테이너에서 실제로 재현해 수치를 남겼다.

## 범위

- 패키지: `com.barofarm.ai.search.application`(`ProductSearchService`·`ProductIndexService`), `search.infrastructure.elasticsearch`, `search.domain`, `search.presentation`, `search.application.dto`, `src/main/resources/settings/autocomplete-analyzer.json`
- 공유 설정 중 검색에 영향을 주는 것만: `config/CacheConfig.java`·`config/ElasticsearchConfig.java`·`application-prod.yml`의 로깅 레벨
- 호출자로서만 읽은 코드: `recommend.application.RecipeRecommendService.searchProductsByIngredientName`·`loadOwnedIngredientNames` (수정 대상 아님 — cart-recipe 단위)
- 판단 항목용으로 읽기만 한 인프라: `baro-gateway/src/main/resources/application-prod.yml`, `k8s/apps/baro-ai/hpa.yml`
- 감사자: 메인 세션이 넘긴 가설 10개를 신선한 컨텍스트에서 코드로 독립 검증했다 — safe-refactor 1단계. 가설 H1~H10의 판정 결과는 아래 표
- 기준: `audit-checklist.md` 1~16항목(16번 LLM·프롬프트는 이번 범위에 해당 코드 없음), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- 저장 스키마(ES 매핑)는 동결 대상이 아니다 — `baro-ai-performance-refactor.md` "데이터 마이그레이션 방침". 단 **재색인 경로가 없어**([`search/001`](../search/001-index-path.md) 개선 제안 1) 매핑을 바꾸면 `product` 인덱스를 지우고 상품 이벤트를 다시 흘려야 한다
- 재현 환경: `docker run elasticsearch:8.12.0`(nori 없음 — `productName` 주 필드는 `standard`로 대체해 실험), 실험 스크립트는 세션 스크래치에만 두고 저장소에 넣지 않았다. 각 항목의 "검증 방법"에 같은 절차를 다시 적었다

**이전 라운드가 이미 다뤄서 이번에 다시 보지 않은 것**

| 이미 다룬 문서 | 제외한 내용 |
|---------------|-------------|
| [`search/001-index-path.md`](../search/001-index-path.md) | product 컨슈머 재시도·poll 건수(A-1), 죽은 `catch`(B-1), 정규식 폴백 제거(B-2), `spring.kafka.consumer.*` 미적용(C-1), DLQ·병렬화·사전 대체(D-1~3), 재색인 경로(개선 제안 1) |
| [`search/002-llm-prompt-tuning.md`](../search/002-llm-prompt-tuning.md) | 재료명 추출 프롬프트·모델 선택·배치화 |
| [`search/refactor-log.md`](../search/refactor-log.md) · [`decisions/001`](../../decisions/001-autocomplete-caching.md) | 자동완성 캐시 활성화(Caffeine 1,000개·5분), 캐싱 범위 결정 |
| [`cross-cutting/001-llm-call-infra.md`](../cross-cutting/001-llm-call-infra.md) | Gemini 호출 타임아웃·재시도·지표 |

**가설 판정 요약**

| 가설 | 판정 | 어디에 |
|------|------|--------|
| H1 오탈자 매칭이 keyword 전체 문자열 대상 | 재현 확인, **A-1** | 아래 |
| H2 자동완성 이중 접두어 | 단순화를 넘어 **두 단어 입력 시 빈 결과**까지 재현, **A-2** | 아래 |
| H3 검색 응답이 벡터까지 `_source` 로드 | 실측 57배, **A-3** | 아래 |
| H4 `ProductIndexService` 책임 혼재 | **B-1** | 아래 |
| H5 응답 봉투 불일치 | **C-2** (cross-cutting 소관) | 아래 |
| H6 이벤트 `updatedAt` 무시 | **B-3** | 아래 |
| H7 상태만 바뀐 수정에도 원격 호출 2회 | **C-1** (빈도 `[미정]`, 측정 계획 첨부) | 아래 |
| H8 게이트웨이 무인증·5초·인스턴스별 캐시 | 판단 항목 1 | 아래 |
| H9 prod DEBUG 로깅·컨슈머 INFO 3줄 | 컨슈머 쪽 **B-4**, yml 쪽 **C-3** | 아래 |
| H10 `spring.kafka.consumer.*` 죽은 설정 | 재감사 안 함, **C-4**에서 교차 참조만 | 아래 |

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 오탈자 매칭이 `productName.raw`(keyword) 전체 문자열을 비교해 두 단어 이상 상품명에는 절대 걸리지 않음

- **Priority**: High
- **Category**: Architecture (검색 품질 결함)
- **문제**: 오탈자 허용 절(`applyFuzzyMatch`)이 `match` 쿼리를 `productName.raw`에 건다. `raw`는 `keyword` 타입이라 상품명 전체가 토큰 하나이고, `match`는 검색어 전체를 그 토큰 하나와 편집 거리로 비교한다. 편집 거리는 두 문자열 길이 차 이상이므로 상품명이 검색어보다 3자 이상 길면 `AUTO`의 최대 허용치(2)를 넘어 **수학적으로 매칭이 불가능**하다. "청송사과 프리미엄 1kg"(13자)에 "청송사고"(4자)를 치면 절대 걸리지 않는다. 이 절이 실제로 걸리는 경우는 상품명 자체가 검색어 길이 ±2 이내인 짧은 단일어("토마토"에 "토마투")뿐이다
- **근거**:
  - `ProductSearchService.java:134-144` — `mm.field("productName.raw").query(keyword).fuzziness("AUTO").prefixLength(1)`; `ProductDocument.java:23-31` — `raw`는 `FieldType.Keyword`; `productName.raw`를 쓰는 곳은 이 한 곳뿐(`grep -rn 'productName.raw' baro-ai/src/main/java` 1건)
  - ES 공식 문서 (Elasticsearch Guide **8.13**, 클라이언트 `elasticsearch-java:8.13.3` 기준. 운영 서버 9.1.2 문서는 따로 확인하지 않았으나 fuzziness·keyword 의미는 주 버전 간 변경 이력이 없다):
    - [`common-options.html#fuzziness`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/common-options.html#fuzziness) — `AUTO`는 기본 `AUTO:3,6`: 0~2자 정확 일치, 3~5자 1회 편집, 6자 이상 2회 편집
    - [`analysis-keyword-analyzer.html`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/analysis-keyword-analyzer.html) — "The `keyword` analyzer is a 'noop' analyzer which returns the entire input string as a single token"
    - [`query-dsl-match-query.html`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/query-dsl-match-query.html) — `match`는 검색어를 필드의 분석기로 분석한 뒤 검색하고, `fuzziness`는 토큰 단위로 적용된다
  - **로컬 재현** (ES 8.12.0, `productName`은 `standard` text + `raw` keyword 서브필드로 매핑, 상품 3건 색인):

    | 대상 필드 | 검색어 | 결과 |
    |-----------|--------|------|
    | `productName.raw` (현재) | `청송사고` | **0건** |
    | `productName.raw` (현재) | `토마투` | 1건 (`토마토` — 상품명이 3자라 걸림) |
    | `productName.raw` (현재) | `청송사과 프리미엄 1kh` | 1건 (전체를 거의 다 쳐야 걸림) |
    | `productName` (standard text) | `청송사고` | **1건** (`청송사과 프리미엄 1kg`) |
    | `productName` (standard text) | `감차` (2자) | 0건 — `AUTO`가 2자 토큰에 0회 편집 |
    | `productName` (standard text), `fuzziness=1` | `감차` | 1건 (`국내산 감자 2kg`) |

    `standard` 분석기는 한글 연속 음절을 공백 단위 한 토큰으로 낸다(`_analyze` 결과 `['청송사과'(0), '프리미엄'(1), '1kg'(2)]`).
- **왜 문제인가**: 스펙이 "정확·일반·오탈자 매칭 세 절"을 검색 품질의 정의로 두는데(`product-search-and-autocomplete.md` "용어"), 셋 중 하나가 실질적으로 꺼져 있다. 오탈자 입력은 상품이 있어도 빈 결과가 되고, 레시피 추천의 부족 재료 상품 검색(`RecipeRecommendService.java:133`)도 같은 경로를 타므로 거기서도 놓친다. 스펙 검증 시나리오에 이미 "`청송사고` 검색 → 거의 매칭되지 않는다"로 미체크 상태로 남아 있다
- **개선 방법** (둘 중 하나 — 사용자 선택, 판단 항목 2번):
  1. **권장 — `raw` keyword 서브필드를 `standard` 분석 text 서브필드로 교체**(예: `productName.word`). 공백 단위 단어 하나가 토큰이 되므로 "청송사과"(4자)는 `AUTO`에서 1회 편집을 허용받아 "청송사고"가 걸린다. `applyFuzzyMatch`의 필드명만 바꾸고, `raw`는 다른 사용처가 없으므로 같은 변경에서 삭제한다(STOP §4). 매핑 변경이라 `product` 인덱스 재생성 + 상품 이벤트 재발행이 필요하다
  2. 대안 — 매핑을 그대로 두고 `applyNormalMatch`의 `match`(nori 주 필드)에 `fuzziness`를 얹는다. 코드 2줄이지만 nori가 "청송사과"를 "청송"+"사과"처럼 2자 형태소로 쪼개면 `AUTO`가 0회 편집이라 "사고→사과"를 못 잡는다. 로컬 ES에 nori가 없어 이 감사에서는 확인하지 못했다(판단 항목 6번)
  - 둘 다 `keyword.length() >= 3` 게이트·`prefixLength(1)`·boost 0.3은 그대로 둔다(정책 불변). 다만 게이트가 "검색어 전체 길이"인 채로 토큰 단위 fuzzy가 되면 의미가 달라지므로 판단 항목 2번에서 함께 확인
- **검증 방법** (로컬 수동): `docker run -p 9200:9200 -e discovery.type=single-node -e xpack.security.enabled=false elasticsearch:8.12.0`으로 ES를 띄우고, 앱을 local 프로필로 기동해 상품 이벤트 3건(`청송사과 프리미엄 1kg`·`토마토`·`국내산 감자 2kg`)을 색인한다. `curl 'localhost:8092/api/v1/search?q=청송사고'`의 `totalElements`를 Before(0)/After(1)로 비교한다. nori가 필요하면 `docker/elasticsearch/Dockerfile`(9.1.2 + nori)을 로컬에서 빌드해 쓴다
- **API 영향**: 응답 스키마 변화 없음. **결과 집합이 넓어진다**(오탈자 히트 추가) — 같은 검색 경로를 쓰는 레시피 추천의 부족 재료 상품 목록도 영향을 받는다. 계약 변경은 아니다
- **예상 변경 파일**: `search/domain/ProductDocument.java`, `search/application/ProductSearchService.java`, `docs/specs/keyword-search/product-search-and-autocomplete.md`(비즈니스 규칙 "오탈자 매칭" 행·검증 시나리오), `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` §3.1·§4.2, `docs/product/glossary.md` "오탈자 매칭"
- **예상 변경 라인 수**: 코드 ±10, 문서 ±15
- **위험도**: Medium — 매핑 변경은 인덱스 재생성이 따른다(운영 데이터 없음 전제)
- **테스트 영향도**: 자동화 테스트 없음. 위 수동 절차가 회귀 기준
- **예상 효과**: 오탈자 검색이 실제로 동작. 죽은 서브필드(`raw`) 제거로 색인 크기 소폭 감소

### A-2. 자동완성이 `edge_ngram` 색인 + `match_phrase_prefix` 조회를 겹쳐 써서 두 단어를 다 치면 결과가 사라짐

- **Priority**: High
- **Category**: Architecture (기능 결함 + 불필요한 이중 처리)
- **문제**: 색인 쪽은 `edge_ngram`(1~20자)으로 이미 모든 접두어 조각을 토큰으로 넣어 두는데, 조회 쪽은 `match_phrase_prefix`로 "마지막 단어를 접두어로 확장"을 한 번 더 한다. 단일어에서는 결과가 같지만, `edge_ngram` 토크나이저는 조각마다 **position을 하나씩 올려** 넣기 때문에 `match_phrase_prefix`가 요구하는 "앞 단어 바로 다음 위치"가 깨진다. "청송 사과"를 치면 색인엔 `청송`(pos 1)·`사과`(pos 3)만 있고 pos 2는 `사`라서 구문 매칭이 실패해 **0건**이 된다. 사용자가 두 번째 단어를 완성하는 순간 자동완성 후보가 사라지는 동작이다
- **근거**:
  - `ProductAutocompleteRepository.java:15-33` — `match_phrase_prefix`, `max_expansions: 10`; `ProductAutocompleteDocument.java:19` — `analyzer = "autocomplete_analyzer", searchAnalyzer = "standard"`; `settings/autocomplete-analyzer.json` — `edge_ngram` 1~20, `token_chars: letter, digit`
  - ES 공식 문서 (8.13):
    - [`analysis-edgengram-tokenizer.html`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/analysis-edgengram-tokenizer.html) — "It only makes sense to use the `edge_ngram` tokenizer at index time … At search time, just search for the terms the user has typed in." 예시는 검색 시 `match`(`operator: and`)를 쓴다
    - [`query-dsl-match-query-phrase-prefix.html`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/query-dsl-match-query-phrase-prefix.html) — 단어를 **같은 순서·인접 위치**로 요구하며 마지막 단어만 접두어 확장. `max_expansions` 기본 50. "can sometimes produce confusing results"
  - **로컬 재현** (ES 8.12.0, `autocomplete-analyzer.json`을 그대로 인덱스 설정으로 사용, 상품 4건 색인):

    `_analyze`(`autocomplete_analyzer`, "청송 사과") → `[('청',0), ('청송',1), ('사',2), ('사과',3)]`

    | 조회 | 검색어 | 결과 |
    |------|--------|------|
    | `match_phrase_prefix`(현재) | `토마` | 2건 (`토마토`, `토마토 주스`) |
    | `match` | `토마` | 2건 — 같은 집합, 같은 순서 |
    | `match_phrase_prefix`(현재) | `청송 사` | 1건 (`사`가 pos 2에 있어서 우연히 걸림) |
    | `match_phrase_prefix`(현재) | `청송 사과` | **0건** |
    | `match`(`operator: and`) | `청송 사과` | **1건** (`청송 사과 프리미엄 1kg`) |
    | `match_phrase_prefix`(현재) / `match` | `청송사` | 1건 / 1건 (`청송사과`) |

- **왜 문제인가**: 두 단어 상품명(스펙 예시 "청송 사과 …")에서 자동완성이 입력 도중 사라졌다 나타났다 한다. 게다가 이중 접두어 처리는 결과가 같은 단일어 경우에도 `match_phrase_prefix`의 term 확장(최대 10개)을 헛되이 수행한다. `token_chars: letter, digit` 때문에 공백에서 잘리는 것 자체는 의도대로다(단어별 접두어)
- **개선 방법**: `@Query`의 `must`를 `match`(`operator: and`)로 바꾸고 `max_expansions`를 지운다. 매핑·설정 파일은 그대로. `and`를 쓰는 이유는 "청송 사과"를 쳤을 때 "사과"만 있는 상품이 섞이지 않게 하려는 것으로, 현재 `match_phrase_prefix`의 "모든 단어 포함" 의미를 유지한다(정책 불변)
- **검증 방법** (로컬 수동): A-1과 같은 환경에서 `청송 사과 프리미엄 1kg`·`청송사과`·`토마토`·`토마토 주스`를 색인하고 `curl 'localhost:8092/api/v1/search/autocomplete?q=청송 사과'`를 Before(빈 배열)/After(1건)로 비교한다. 회귀 확인은 `q=토마`·`q=청송사`가 Before/After 같은 목록·순서인지 본다. 캐시 TTL 5분이 있으니 Before/After 사이에 앱을 재기동한다
- **API 영향**: 응답 스키마 변화 없음. 두 단어 이상 입력에서 결과가 **생긴다**
- **예상 변경 파일**: `search/infrastructure/elasticsearch/ProductAutocompleteRepository.java`, `docs/specs/keyword-search/product-search-and-autocomplete.md`(비즈니스 규칙 "자동완성 쿼리" 행·리스크 표), `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` §5.2, `docs/product/glossary.md` "자동완성 접두어"
- **예상 변경 라인 수**: 코드 ±6, 문서 ±8
- **위험도**: Low — 쿼리 문자열만 바뀌고 매핑은 그대로
- **테스트 영향도**: 자동화 테스트 없음. 위 수동 절차가 회귀 기준
- **예상 효과**: 두 단어 자동완성 동작. 단일어에서는 결과 동일하고 term 확장 단계 하나가 빠진다

### A-3. 검색 응답 조회가 1536차원 벡터를 포함한 `_source` 전체를 받아 오는데 응답에는 4개 필드만 쓴다

- **Priority**: Medium
- **Category**: Performance
- **문제**: `ProductSearchService.search`가 `NativeQuery`에 `_source` 필터 없이 `ProductDocument` 전체를 받는다. 응답 DTO(`ProductSearchResponse`)는 `productId`·`productName`·`productCategoryName`·`price` 4개만 쓰는데, 문서에는 `vector`(float 1536개)와 `ingredientName`이 같이 실려 온다. ES → baro-ai 구간에서 히트당 약 21KB, 기본 페이지(10건)면 약 210KB가 매 검색마다 오간다. 프론트로 나가는 응답 크기는 그대로다
- **근거**:
  - `ProductSearchService.java:42-72` — `withSourceFilter` 없음(`grep -rn 'SourceFilter' baro-ai/src/main/java` 0건); `:74-83` — 4개 필드만 사용; `ProductDocument.java:56-57` — `dense_vector dims = 1536`
  - Spring Data Elasticsearch 5.3.6(로컬 jar `javap`로 확인)에 `FetchSourceFilter(String[] includes, String[] excludes)` 생성자와 `SourceFilter.of(...)`가 있고 `NativeQueryBuilder.withSourceFilter(SourceFilter)`로 넘길 수 있다
  - **로컬 실측** (ES 8.12.0, 소수 8자리 float 1536개를 가진 상품 문서 1건):

    | 조회 | 바이트 |
    |------|--------|
    | `GET product/_doc/{id}` 전체 | **21,160** |
    | `GET product/_doc/{id}?_source_excludes=vector` | **371** (57분의 1) |
    | `_search?size=10` 2건 | 55,062 |
    | `_search?size=10&_source_excludes=vector` 2건 | 816 |

- **왜 문제인가**: 검색은 이 모듈에서 가장 자주 불리는 API인데, 요청마다 필요한 데이터의 57배를 ES에서 받아 역직렬화(`float[1536]` 생성)한 뒤 버린다. 페이지 크기를 키우면 비례해 커진다(A-4의 `size` 무제한과 겹치면 수십 MB). ES 노드 입장에서도 `_source` 읽기·직렬화 비용이 그대로 든다
- **개선 방법**: `NativeQuery.builder()`에 `.withSourceFilter(new FetchSourceFilter(null, new String[] {"vector"}))`를 추가한다. `ingredientName`도 안 쓰지만 수십 바이트라 벡터 하나만 제외해도 충분하다. `@Id`(`productId`)는 `_source`가 아니라 `_id`에서 읽으므로 영향 없다. 같은 `ProductDocument`를 벡터 연산에 쓰는 `VectorProductSearchService`(vector-similarity 단위)는 벡터가 필요하므로 손대지 않는다
- **검증 방법** (로컬 수동): 앱을 local 프로필로 기동하되 `logging.level.tracer=TRACE`(ES 저수준 클라이언트가 요청·응답 본문을 통째로 찍는 로거)를 켜고 `GET /api/v1/search?q=사과`를 한 번 호출해 로그에 찍힌 응답 본문 길이를 Before/After로 비교한다. 앱 없이도 `curl 'localhost:9200/product/_search?q=productName:사과' | wc -c`와 `…&_source_excludes=vector | wc -c`로 같은 비율을 볼 수 있다
- **API 영향**: No Impact (프론트 응답 동일)
- **예상 변경 파일**: `search/application/ProductSearchService.java`, `docs/specs/keyword-search/product-search-and-autocomplete.md`(데이터 모델 비고·리스크 표·수동 확인 항목), `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` §4.2
- **예상 변경 라인 수**: 코드 +2, 문서 ±5
- **위험도**: Low
- **테스트 영향도**: 자동화 테스트 없음
- **예상 효과**: 검색당 ES→앱 전송량 약 98% 감소, `float[1536]` 역직렬화 제거

**cross-unit 메모**: `RecipeRecommendService.loadOwnedIngredientNames`(`:180`, `findAllById`)도 장바구니 상품 수만큼 벡터를 같이 받는다. `CrudRepository`에는 `_source` 필터가 없어 `ElasticsearchOperations.multiGet(Query, Class)` + `SourceFilter`로 바꿔야 한다 — cart-recipe 단위 감사에서 다룬다. `SimilarProductRecommendService.findById`(`:25`)는 벡터가 필요하므로 대상 아님

### A-4. `page`·`size`·자동완성 `size`에 검증이 없어 500과 무제한 응답이 가능함

- **Priority**: Medium
- **Category**: Security (입력 검증 누락)
- **문제**: `SearchController`의 `page`·`size`(검색)와 `size`(자동완성)를 그대로 `PageRequest.of`와 ES에 넘긴다. 게이트웨이 prod 라우트에 인증 필터가 없어(판단 항목 1번) 누구나 호출할 수 있는 API인데, 값 범위에 따라 다음이 일어난다:
  - `size=0`·`size=-1`·`page=-1` → `PageRequest.of`가 `IllegalArgumentException` → 400이지만 메시지는 Spring 영문 문구("Page size must not be less than one")
  - `page=1000&size=10` 등 `from + size > 10,000` → ES가 400을 돌려주고 Spring Data가 `UncategorizedElasticsearchException`으로 감싸 **500** "서버 내부 오류"
  - `size=10000` → ES 상한 안이라 성공하고 히트 10,000건 × 약 21KB(A-3 적용 전) ≈ 210MB를 ES에서 받아 온다. 자동완성 `size=10000`은 그 결과가 Caffeine 캐시에 5분간 남는다
- **근거**:
  - `SearchController.java:41-42, 53` — `@RequestParam(defaultValue = "0") int page`, `"10" int size`, `"5" int size`에 범위 제약 없음; `grep -rl '@Validated\|@Min\|@Max' baro-ai/src/main/java` 0건(review 제외)
  - `baro-common/.../BaseExceptionHandler.java:74-83` — `IllegalArgumentException` → 400, `:208-220` — 그 외 `Exception` → 500. `ConstraintViolationException`·`HandlerMethodValidationException` 핸들러는 없음(Bean Validation 어노테이션을 붙여도 500이 된다)
  - ES 공식 문서 (8.13) [`index-modules.html`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/index-modules.html) — `index.max_result_window` "The maximum value of `from + size` for searches to this index. Defaults to 10000"
  - **로컬 재현**: `GET /audit-vec/_search?from=9995&size=10` → `400 Result window is too large, from + size must be less than or equal to: [10000] but was [10005]`
- **왜 문제인가**: 무인증 공개 API에서 파라미터 하나로 500 에러 로그(스택 트레이스 포함, `BaseExceptionHandler:211`)와 수백 MB 전송을 일으킬 수 있다. 지금 트래픽이 없어 사고는 없었지만, 검증이 없다는 사실 자체가 코드로 확인된다
- **개선 방법**: 검색 단위 `ErrorCode`를 신설(`search/exception/SearchErrorCode` — `RecommendErrorCode`와 같은 `BaseErrorCode` 패턴)하고 서비스 진입부에서 `CustomException`을 던진다(`BaseExceptionHandler:193`이 400으로 변환). 값은 사용자 결정(판단 항목 4번): 검색 `size` 1~`[미정]`(예 100), `page ≥ 0`, `(page + 1) × size ≤ 10,000`; 자동완성 `size` 1~`[미정]`(예 20). Bean Validation(`@Min/@Max`)은 위 핸들러 부재로 500이 되므로 쓰지 않는다. STOP §2에 따라 enum·throw·스펙 에러 표를 같은 턴에 채운다
- **검증 방법** (로컬 수동): `curl -i 'localhost:8092/api/v1/search?page=1000&size=10'` Before(500)/After(400 + 한글 메시지), `curl -i '…/search?size=0'` Before(400 영문)/After(400 한글), `curl -i '…/search/autocomplete?q=토마&size=0'` Before(400 영문)/After(400 한글)
- **API 영향**: **Breaking** — 지금까지 200 또는 500이던 범위 밖 값이 400 + 새 에러 코드로 바뀐다. 정상 범위 호출은 그대로. 프론트가 `size`를 100 초과로 쓰는 곳이 있는지는 확인 필요(판단 항목 4번)
- **예상 변경 파일**: `search/exception/SearchErrorCode.java`(신규), `search/application/ProductSearchService.java`, `search/application/ProductIndexService.java`(또는 B-1 반영 후 자동완성이 옮겨 간 곳), `docs/specs/keyword-search/product-search-and-autocomplete.md`(API 표에 에러 표 추가)
- **예상 변경 라인 수**: 코드 +30, 문서 +10
- **위험도**: Low
- **테스트 영향도**: 자동화 테스트 없음
- **예상 효과**: 잘못된 입력이 400으로 정리되고, 최대 응답 크기에 상한이 생긴다

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `ProductIndexService`가 색인·삭제·자동완성 조회·프로필 갱신 트리거 네 가지를 함께 가짐

- **Priority**: Medium
- **Category**: Architecture (책임 분리)
- **문제**: 이름은 "색인 서비스"인데 조회 API(`autocomplete`, `@Cacheable`)와 취향 벡터 갱신 트리거(`updateUserProfileAsync`, `@Async`)까지 들어 있다. 그 결과 `SearchController`가 조회를 위해 색인 서비스를 주입받고(`:31, :55`), `ProductSearchService`는 프로필 갱신 때문에 색인 서비스를 주입받는다(`:30, :99`). 조회 경로를 읽는 사람이 색인 코드까지 열어야 하고, 색인 서비스의 의존성(`ProductEmbeddingService`·`ProductIngredientNormalizer`·`UserProfileEmbeddingService`)이 조회 API 빈 그래프에 딸려 들어온다
- **근거**: `ProductIndexService.java:32-36`(의존성 5개), `:42`(색인), `:83`(삭제), `:93-102`(자동완성 조회), `:108-119`(프로필 비동기 갱신); 호출자 `SearchController.java:55`, `ProductSearchService.java:99`, `ProductEventConsumer.java:43,52,58`
- **왜 문제인가**: 단일 책임 신호(줄 수가 아니라 호출자 종류가 셋 — 컨트롤러·검색 서비스·Kafka 컨슈머). A-4의 자동완성 검증 코드를 넣을 자리가 "색인 서비스"가 되면 어색함이 더 커진다
- **개선 방법**: `autocomplete`를 조회 쪽 `ProductSearchService`로 옮긴다(새 클래스를 만들지 않는다 — 조회 메서드 둘뿐이라 YAGNI). `@Cacheable`은 컨트롤러에서 프록시를 거쳐 호출되므로 그대로 동작한다. `updateUserProfileAsync`는 취향 벡터 단위 소관이라 이번에 옮기지 않고, 그쪽 감사(`audits/taste-vector/`, 병행 진행 중)가 전용 빈을 제안하면 그때 따라간다 — 여기서 중복 제안하지 않는다. 같은 파일을 만지는 김에 `ProductSearchService.java:32-35` Javadoc의 "키워드(q) 필수"(실제는 선택 — `:47`)와 `:101-102`의 문자열 연결 로그(`{}` 자리표시자로)도 같이 고친다
- **검증 방법**: 컴파일 + `GET /api/v1/search/autocomplete?q=토마`를 두 번 호출해 두 번째가 ES 요청 없이(`org.elasticsearch.client` DEBUG 로그 미출력) 응답하는지 확인 — 캐시 프록시가 유지됐다는 증거
- **API 영향**: No Impact
- **예상 변경 파일**: `search/application/ProductIndexService.java`, `search/application/ProductSearchService.java`, `search/presentation/SearchController.java`, `docs/specs/keyword-search/product-search-and-autocomplete.md`(흐름 6 행), `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` §5.2·§11
- **예상 변경 라인 수**: 코드 ±35, 문서 ±5
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 조회 경로가 파일 하나에서 읽힌다. 컨트롤러 의존성이 조회 서비스 하나로 준다

### B-2. `ElasticsearchConfig` 정리 — [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) A-2로 이관

같은 클래스의 같은 결함(`ClientConfiguration` 빈이 아무도 읽지 않아 타임아웃 설정이 죽어 있고, 수동 `RestClient` 빈이 Boot의 `spring.elasticsearch.*`를 우회하며, Javadoc이 ANN·VectorStore를 현행처럼 서술)을 두 감사가 독립적으로 발견했다. **SSOT는 `vector-similarity/001` A-2**(등급 A·근거·개선 방법·검증 절차 전부 그쪽)이며, 이 문서에서는 중복 구현을 피하기 위해 항목 번호만 남긴다. 검색 경로에 특유한 영향 한 가지만 여기 적는다: 실효 connect 타임아웃이 1초라 ES가 잠깐 느리면 검색·자동완성이 곧바로 실패하는데, 색인 경로와 달리 검색에는 재시도가 없다(`ProductSearchService.search`에 재시도 없음, `search/001` A-1은 컨슈머만). A-2가 반영되면 `docs/specs/keyword-search/product-search-and-autocomplete.md` 배포 전제 표의 ES 행에 실효 타임아웃 값을 한 줄 추가한다.

### B-3. 색인 시 `updatedAt`을 이벤트 값 대신 `Instant.now()`로 덮어써 정렬 기준이 "색인된 시각"이 됨

- **Priority**: Low
- **Category**: Architecture
- **문제**: 상품 이벤트에는 `updatedAt`(발행 시각)이 실려 오는데 컨슈머가 `ProductIndexRequest`로 옮기며 버리고, 색인 서비스가 `Instant.now()`를 넣는다. 정렬 2순위(`updatedAt desc`)는 "상품이 최근에 바뀐 순"이 의도인데 실제로는 "baro-ai가 최근에 저장한 순"이다. 평소엔 컨슈머 지연(밀리초~초)만큼만 다르지만, 재시도·컨슈머 중단 후 따라잡기, 그리고 앞으로 만들 재색인 경로에서는 전 상품이 같은 시각으로 뭉쳐 최신순이 사라진다
- **근거**: `ProductEvent.java:39`(`updatedAt` 수신), `ProductEventConsumer.java:75-84`(`toRequest`가 `updatedAt`을 옮기지 않음), `ProductIndexRequest.java:5-13`(필드 없음, 주석 "updatedAt은 서버에서 자동 생성"), `ProductIndexService.java:68`(`Instant.now()`), `ProductSearchService.java:68`(정렬 키). 스펙 "흐름 1"은 이벤트 `updatedAt`이 발행 시각임을 확인해 뒀다
- **왜 문제인가**: 값의 뜻이 문서(`ProductDocument.java:51` "최신순으로 정렬")와 다르고, 순서 미보장 토픽(키 없음)에서 늦게 도착한 옛 이벤트가 더 "최신"으로 기록된다. 지금 당장 사용자가 느낄 차이는 작다
- **개선 방법**: `ProductIndexRequest`에 `updatedAt`을 추가해 이벤트 값을 그대로 저장한다. 이벤트 값이 `null`이면 `Instant.now()`로 폴백(방어). 정렬·필터 로직은 그대로(정책 불변). 스펙 "흐름 3" 행과 `ProductIndexRequest` 주석·아키텍처 문서 §7.2 "updatedAt=now"를 같이 고친다
- **검증 방법**: 로컬에서 `updatedAt`을 과거 시각으로 넣은 이벤트 1건을 발행하고 `curl localhost:9200/product/_doc/{id}`의 `updatedAt`이 이벤트 값과 같은지 확인
- **API 영향**: No Impact (정렬 값의 출처만 바뀜)
- **예상 변경 파일**: `search/application/dto/product/ProductIndexRequest.java`, `search/infrastructure/event/ProductEventConsumer.java`, `search/application/ProductIndexService.java`, `docs/specs/keyword-search/product-search-and-autocomplete.md`, `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` §7.2
- **예상 변경 라인 수**: 코드 ±8, 문서 ±4
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 정렬 값이 문서대로 "상품 변경 시각"이 된다. 재색인 경로를 만들 때 최신순이 보존된다

### B-4. 컨슈머가 이벤트 1건에 INFO 3줄(상품명 3회 반복)을 찍고, 문서 클래스에 효과 없는 Jackson 어노테이션이 남아 있음

- **Priority**: Low
- **Category**: Cleanup
- **문제**:
  1. `ProductEventConsumer.onMessage`가 이벤트 1건마다 "수신"·"처리 시작"·"완료" INFO 3줄을 남기고, 세 줄 모두 상품 ID·상품명·카테고리·가격을 반복한다. 실패 시엔 여기에 ERROR 1줄이 더해지고 재시도(최대 3회)마다 반복된다
  2. `ProductDocument.updatedAt`의 `@JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ssX")`은 아무 효과가 없다. Spring Data Elasticsearch 5.3.6은 엔티티를 Jackson이 아니라 `MappingElasticsearchConverter`로 변환하고 `@Field(format = date_time)`이 실제 포맷을 정한다
- **근거**:
  - `ProductEventConsumer.java:31-34, 39-42, 44-45, 48-51, 53-54, 57, 59` — INFO 7곳, 정상 경로 1건당 3줄
  - `ProductDocument.java:49-50`; 로컬 jar 검사 `spring-data-elasticsearch-5.3.6.jar` — `MappingElasticsearchConverter.class`에 `jackson` 문자열 0건, `core/convert`·`core/mapping` 전 클래스에 `JsonFormat` 참조 0건
- **왜 문제인가**: 로그는 `application-prod.yml`이 `com.barofarm.ai: DEBUG`라 전부 출력된다. 재색인 경로가 생겨 수백 건이 흐르면 같은 상품명이 세 번씩 찍혀 로그를 읽기 어렵다. `@JsonFormat`은 읽는 사람이 "이 패턴으로 저장된다"고 오해하게 하는 죽은 어노테이션이다
- **개선 방법**: 컨슈머는 완료 시점 INFO 1줄(타입·ID·상품명)만 남기고 수신·시작 줄은 DEBUG로 내리거나 지운다. 실패 ERROR는 유지. `@JsonFormat`과 `com.fasterxml.jackson` import를 지운다
- **검증 방법**: 로컬에서 이벤트 1건 발행 후 `docker logs` 또는 콘솔에서 `[CONSUMER]` INFO 줄 수 Before(3)/After(1) 비교. `curl localhost:9200/product/_doc/{id}`의 `updatedAt` 문자열이 Before/After 같은지 확인
- **API 영향**: No Impact
- **예상 변경 파일**: `search/infrastructure/event/ProductEventConsumer.java`, `search/domain/ProductDocument.java`
- **예상 변경 라인 수**: −15
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 색인 로그 1/3, 죽은 어노테이션 제거

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 상태·가격만 바뀐 `PRODUCT_UPDATED`에도 임베딩·재료명 LLM을 다시 호출함 (H7)

- **현재 동작**: 발행 측은 diff 없이 항상 UPDATED를 보내고(스펙 "발행 조건"), 컨슈머는 CREATED·UPDATED를 구분 없이 `indexProduct`로 보내며(`ProductEventConsumer.java:52`), `indexProduct`는 무조건 임베딩(`:48`)과 재료명 LLM(`:57`)을 호출한다. 둘 다 입력이 `productName`뿐이므로 상품명이 같으면 결과도 (LLM 비결정성을 빼면) 같다
- **왜 지금 안 하는가**: 비용이 "수정 이벤트 1건당 원격 호출 2회"라는 건 코드로 확인되지만, **수정 이벤트 빈도를 모른다** — 재고 변경은 이벤트를 안 내고(스펙), 판매자가 상태·가격을 얼마나 자주 고치는지 추정치가 없다(`[미정]`). 근거 기준상 빈도 없이 A/B로 올리지 않는다. 또 "기존 문서를 읽어 상품명이 같으면 벡터·재료명을 재사용"은 색인 결과를 바꾸지 않는 최적화이지만, 재료명이 빈 문서(이전 실패)는 다시 계산해야 하는 등 분기가 생겨 정책 확인이 필요하다
- **측정 계획**: 운영 재개 후 `product-events` 토픽의 UPDATED 비율(또는 baro-shopping `updateProduct` 호출 수)과 `gen_ai.client.*` 지표([`cross-cutting/001`](../cross-cutting/001-llm-call-infra.md))의 색인 경로 호출 수를 1주 비교한다. UPDATED가 CREATED보다 많으면 B로 올린다. 구현은 `repository.findById` 1회 + 상품명 비교 + 빈 재료명이면 재계산, 약 15줄

### C-2. `SearchController`는 `ResponseDto`로 감싸고 `RecommendationController`는 날 리스트를 돌려줌 (H5)

- **현재 동작**: `SearchController.java:35, 51` — `ResponseDto<…>`; `RecommendationController.java:38-40, 70-72` — `List<ProductRecommendResponse>`·`RecipeRecommendResponse` 직접 반환. 스펙에도 "추천 API는 감싸지 않는다"로 기록돼 있다
- **왜 지금 안 하는가**: 어느 쪽으로 맞출지는 baro-ai 전체 API 규약이라 키워드 검색 단위가 정할 일이 아니다. `{{API 응답 규격}}`(`baro-common` `ResponseDto`)이 있으므로 추천 쪽이 맞추는 게 자연스럽지만, 그 변경은 vector-similarity·cart-recipe 단위의 계약 변경이다. **cross-cutting 감사 소관**으로 넘긴다

### C-3. `application-prod.yml`이 운영에서 `org.springframework.web`·`DispatcherServlet`·`org.elasticsearch`·`org.springframework.ai`를 DEBUG로 켬 (H9)

- **현재 동작**: `application-prod.yml:66-71`. `DispatcherServlet` DEBUG는 요청당 "GET /api/v1/search…"·"Completed 200 OK" 2줄, `org.elasticsearch.client.RestClient` DEBUG는 ES 호출당 "request […] returned […]" 1줄(jar 문자열로 확인)을 찍는다. 검색 1회 = 최소 3줄 + `com.barofarm.ai` DEBUG 줄. 자동완성은 캐시 적중 시 ES 줄이 빠진다
- **왜 지금 안 하는가**: 이 파일은 review 패키지의 컨트롤러·컨슈머 로그 레벨도 함께 정하는 공유 설정이라 키워드 검색 단위 승인만으로 바꾸지 않는다. `logging.level`을 `INFO`로 내리고 필요한 패키지만 DEBUG로 두는 변경은 **cross-cutting 감사**에서 review 팀 확인과 함께 다룬다. B-4(컨슈머 자체 로그 줄이기)는 search 소유라 여기서 처리한다

### C-4. `spring.kafka.consumer.*` yml 블록이 커스텀 팩토리에는 적용되지 않음 (H10)

[`search/001-index-path.md`](../search/001-index-path.md) C-1과 동일. 이번 라운드에서 재감사하지 않았고 상태도 그대로다(`application-prod.yml:29-33` 유지, `KafkaConsumerConfig`가 별도 `ConsumerFactory` 생성). 교차 참조만 남긴다

### C-5. 자동완성 캐시 키가 검색어를 정규화하지 않고, 2글자 미만 입력도 캐시 슬롯을 차지함

- **현재 동작**: `ProductIndexService.java:93` — 키 `#query + '-' + #size`. "토마"와 "토마 "(뒤 공백)·"Toma"와 "toma"가 다른 슬롯이고, `:95`의 길이 검사가 캐시 뒤에 있어 1글자 입력의 빈 배열도 5분간 저장된다
- **왜 지금 안 하는가**: 적중률에 실제로 얼마나 영향을 주는지 측정치가 없다(트래픽 없음). 최대 1,000개·5분 TTL이라 슬롯 낭비도 상한이 있다. 다음에 `autocomplete`를 옮길 때(B-1) `query.trim()` 후 길이 검사를 캐시 밖(컨트롤러 또는 별도 public 메서드)으로 빼는 정도로 같이 정리할 수 있으나, 캐시 키 의미가 바뀌는 변경이라 따로 승인받는다

### C-6. `VectorProductSearchService.processSearchResults`가 호출되지 않는 private 메서드임

`VectorProductSearchService.java:176-215`, 호출처 0건(`grep -rn processSearchResults baro-ai/src/main/java` 정의 1건). 같은 `search.application` 패키지에 있지만 **vector-similarity 단위** 코드라 이번 라운드에서 손대지 않는다. 그쪽 감사에 Dead Code로 넘긴다

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 상태 필터(`ON_SALE`·`DISCOUNTED`)가 세 곳에 중복돼 있지만 공통화하지 않음

`ProductSearchService.applyStatusFilter`(`:147-159`), `VectorProductSearchService.buildBoolQuery`(`:145-155`), `ProductAutocompleteRepository`의 `@Query` 문자열(`:26-30`). 앞 둘은 Java 빌더, 마지막은 JSON 문자열이라 하나의 상수로 묶으면 JSON 쪽은 어차피 문자열 조립이 필요하고, 두 작업 단위(keyword-search·vector-similarity)가 한 상수를 공유하게 돼 결합만 는다. 값이 바뀌면 `glossary.md` "상태 필터" 항목이 세 곳을 가리키므로 놓치지 않는다

### D-2. `indexProduct`의 두 인덱스 저장(`product-autocomplete` → `product`)을 원자적으로 묶지 않음

`ProductIndexService.java:75-77`. 두 번째 저장이 실패하면 자동완성에만 남는 순간이 있지만, 컨슈머 재시도(`search/001` A-1)가 같은 이벤트를 다시 처리하고 두 저장 모두 ID 기준 덮어쓰기라 재시도로 수렴한다. ES는 인덱스 간 트랜잭션이 없으므로 "원자성"을 흉내 내려면 보상 삭제 같은 코드가 필요한데, 그 코드가 오히려 실패 경로를 늘린다

### D-3. 자동완성을 `completion` suggester·`search_as_you_type`으로 갈아타지 않음

ES 문서(`query-dsl-match-query-phrase-prefix.html`)가 자동완성엔 이 둘을 권하지만, 현재 구조는 상태 필터(`ON_SALE`·`DISCOUNTED`)를 `bool.filter`로 거는 게 핵심이고 `completion` suggester는 컨텍스트 매핑을 따로 설계해야 필터가 된다. A-2로 `match`만 쓰면 ES 문서가 `edge_ngram`에 권하는 바로 그 형태가 되므로 구조를 바꿀 이유가 사라진다. 상품 수가 수만 건을 넘어 `edge_ngram` 색인 크기가 문제가 되면 그때 재검토(수치 없음)

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

### 1. 늦게 도착한 옛 이벤트가 새 상태를 덮어쓰지 않도록 이벤트 `updatedAt`으로 stale 판정 — **Later** (성격이 정책 추가라 Track A `specify` 대상)

- **왜 필요한지**: `product-events`는 키 없이 발행돼 같은 상품의 이벤트 순서가 보장되지 않는다(스펙 "순서"). 컨슈머 재시도(3회)나 파티션이 2개 이상일 때 "ON_SALE로 변경" 뒤에 "HIDDEN으로 변경"이 먼저 처리되면 최종 문서가 잘못된 상태로 남는다
- **근거**: `baro-shopping` `ProductEventProducer.java:26`(키 없음, 스펙 인용), `ProductIndexService.java:77`(무조건 덮어쓰기), `ProductEvent.java:39`(비교에 쓸 시각이 이미 실려 옴). B-3을 반영하면 문서에도 같은 시각이 남아 비교가 가능해진다
- **장단점**: 장점 — 순서 역전 방어를 baro-ai 안에서 해결. 단점 — "옛 이벤트를 버린다"는 정책이 새로 생기고, 시계가 다른 발행 인스턴스 간 비교라는 전제가 붙는다. 구현 난이도 낮음(GET 1회 + 비교)
- **Later인 이유**: 파티션 수가 `[미정]`이고(스펙 "토픽 생성") 파티션이 1개면 순서가 보장돼 지금은 문제가 실재하지 않는다. 파티션을 늘리거나 재색인 경로를 만들 때 함께 결정한다. 이건 리팩터가 아니라 정책이므로 `specify`로 스펙을 먼저 쓴다

## 판단이 서지 않는 항목

1. **게이트웨이·인프라 전제 (H8)** — prod `/ai-service/**` 라우트에 `AuthenticationFilter`가 없어(`baro-gateway/.../application-prod.yml:127-132`, local 프로필에만 있음) `X-User-Id`를 클라이언트가 임의로 넣을 수 있다. 검색 로그·프로필 갱신이 남의 userId로 쌓일 수 있다. 응답 타임아웃 5초(`:29-30`)는 baro-ai의 ES socket 30초·Gemini 30초보다 짧아 느린 검색이면 게이트웨이가 먼저 504를 돌려주고 baro-ai는 계속 일한다. HPA 1~3(`k8s/apps/baro-ai/hpa.yml`)이라 자동완성 캐시는 인스턴스별이다(ADR-001이 이미 수용). 셋 다 baro-ai 쓰기 범위 밖 — 게이트웨이·인프라 소유자에게 "검색 비로그인 허용이 의도인지", "5초가 의도인지" 확인이 필요하다
2. **A-1의 fuzziness 값과 3글자 게이트** — 재현에서 `AUTO`는 2자 토큰("감차"→"감자")을 못 잡고 `fuzziness=1`은 잡는다. 농산물 이름은 2자가 많다(사과·감자·양파·대파). `AUTO` 유지(오탐 적음) vs `1` 고정 또는 `AUTO:2,5`(2자부터 1회 편집) 중 무엇이 의도인지 사용자 결정. 또 `keyword.length() >= 3` 게이트는 "검색어 전체 길이" 기준인데 토큰 단위 fuzzy로 바뀌면 "청송 사과"(5자, 토큰은 2자 둘)가 통과한다 — 게이트를 유지할지, 토큰 길이로 볼지도 같이 결정
3. **`track_total_hits` 10,000 상한** — ES 기본값(8.13 [`search-your-data.html#track-total-hits`](https://www.elastic.co/guide/en/elasticsearch/reference/8.13/search-your-data.html#track-total-hits))이라 검색 히트가 10,000건을 넘으면 `CustomPage.totalElements`·`totalPages`가 10,000 기준으로 잘린다(`hits.total.relation: gte`). 상품 총수를 모르므로(`[미정]`) 문제인지 판단할 수 없다. 상품이 수천 건이면 영원히 해당 없음
4. **A-4의 상한값** — 검색 `size` 최대(예 100), 자동완성 `size` 최대(예 20)를 프론트 사용 범위를 확인해 정해야 한다. 프론트 저장소가 이 하네스 범위 밖이라 사용자 확인
5. **B-2의 타임아웃 의도** — 죽어 있던 값(connect 10초·socket 30초)이 실제 결정이었는지, 아니면 아무도 검토하지 않은 초기값인지 모른다. Boot 기본(1초·30초)을 그대로 둘지, 10초로 올릴지(게이트웨이 5초와 겹치면 의미가 없음) 사용자 결정. review 팀 ES 호출에도 적용되므로 공유 확인
6. **nori 환경에서의 A-1 대안 2 검증** — 로컬 `docker-compose.data.yml`의 ES 8.12.0에는 nori가 없어 `productName` 주 필드(nori)에 fuzziness를 얹는 대안을 이 감사에서 재현하지 못했다. `docker/elasticsearch/Dockerfile`(9.1.2 + nori)을 로컬에서 빌드해 "청송사과"가 어떤 형태소로 나뉘는지(`_analyze`) 보면 판단할 수 있다. 권장안 1은 nori와 무관하다
7. **C-1의 수정 이벤트 빈도** — 위 C-1 측정 계획대로 운영 재개 후 확인

## 승인 결과 (2026-09-05)

사용자가 A·B 전 항목을 승인했고, 판단 항목 4는 **검색 `size` 최대 100·자동완성 `size` 최대 20**으로 정했다(초과 시 400 + `SearchErrorCode`). 판단 항목 1(게이트웨이 인증 필터)은 팀 전달 항목이고, 2·5·6은 구현 세션에서 결정한다.

원래 승인 대기 문구: 사용자 승인 후 A/B 항목만 우선순위 순으로 구현한다 — 제안 순서: A-1 → A-2 → A-3 → A-4 → B-1 → B-2 → B-3 → B-4. A-1(매핑 변경·인덱스 재생성)·A-4(계약 변경)는 구현 전 위 판단 항목 2·4번의 결정이 먼저 필요하고, B-2는 `vector-similarity/001` A-2에서 구현한다(판단 항목 5번도 그쪽 라운드에서 함께 결정). C/D는 이번 라운드에서 수정하지 않는다.

### 구현 결과 (2026-09-05)

A 4건·B 3건(B-2 제외 — `vector-similarity/001` A-2에서 이미 반영)을 전부 반영했다. 무엇을 바꿨고 무엇을 남겼는지, 검증 결과는 [`refactor-log.md`](refactor-log.md) 2026-09-05 항목에 있다. 구현 세션에서 이 감사가 사용자에게 넘긴 판단이 아래와 같이 정해졌다.

| 번호 | 결정 | 반영 위치 |
|------|------|-----------|
| 2 | **개선 방법 1(권장안) 채택** — `productName.raw`(keyword)를 `productName.word`(standard text)로 교체. 오탈자 허용은 `AUTO` 대신 **`fuzziness=1` 고정**, 최소 검색어 길이는 3자 → **2자**로 낮춘다. 농산물 이름이 2~4자에 몰려 있어 `AUTO`가 가장 흔한 경우를 비켜 가고, 게이트를 3자로 두면 그 경우가 그대로 남기 때문이다. 둘 다 비즈니스 규칙 변경이라 스펙에 명시했다 | `ProductDocument`·`applyFuzzyMatch` · 스펙 비즈니스 규칙 |
| 5 | **해소** — `vector-similarity/001` A-2에서 실제 동작 값(connect 1초)을 채택했다. 검색 경로에 재시도가 없다는 이 문서의 지적은 그대로 유효하다 | `application-prod.yml` |
| 6 | **미확인** — 로컬 ES를 띄우지 못해 nori 환경 검증을 못 했다. 다만 채택한 권장안은 nori와 무관한 별도 서브필드를 쓰므로 이 확인에 걸리지 않는다 | — |
| 1·3·7 | **미해결** — 게이트웨이 소유자 확인, 상품 총수 `[미정]`, 운영 재개 후 측정이 각각 필요하다 | 스펙 리스크 표 |

A-4의 검증 위치는 감사가 적은 "서비스 진입부"를 지키기 위해 `search`의 시그니처를 `Pageable` 대신 `page`·`size`로 바꿨다. 컨트롤러에서 `PageRequest.of`를 만들면 검증 전에 `IllegalArgumentException`이 나 영문 메시지가 그대로 나가기 때문이다.
