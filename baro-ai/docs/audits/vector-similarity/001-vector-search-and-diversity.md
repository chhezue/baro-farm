# Vector-similarity Architecture Audit 001 — 벡터 검색·다양화 (2026-09-04)

임베딩 유사도 검색 작업 단위 — 상품 벡터 생성(`ProductEmbeddingService`) → `script_score` 벡터 검색(`VectorProductSearchService`) → max-min 다양화(`MedoidDiversityService`) → 유사 상품·개인화 추천 호출부 — 와 그 아래 깔린 Elasticsearch(ES) 클라이언트 설정(`ElasticsearchConfig`)을 감사한 결과다. 반드시 고칠 A 항목 2건(벡터 없는 상품이 섞이면 검색 전체가 실패하는 스크립트, 적용되지 않는 ES 타임아웃 설정), 유지보수성 B 항목 5건이 나왔고, 전부 **2026-09-05 승인 완료**(문서 끝 "승인 결과" 절). 메인 세션이 넘긴 가설 10개(H1~H10)는 각 항목의 근거 안에 어느 가설을 확인한 것인지 표시했다. 상품 수·트래픽 규모는 `[미정]`이라 `script_score` → native `knn` 전환은 변경이 아니라 측정 계획(C-1)으로만 적었다.

## 범위

- 패키지·파일: `search.application.VectorProductSearchService`, `recommend.application.MedoidDiversityService`·`SimilarProductRecommendService`·`PersonalizedRecommendService`(공유 코드 호출부만)·`recommend.application.dto.*`·`recommend.exception.RecommendErrorCode`, `embedding.application.ProductEmbeddingService`, `search.domain.ProductDocument`·`embedding.domain.UserProfileEmbeddingDocument`, `config.ElasticsearchConfig`
- 인프라 실물: `baro-ai/build.gradle`(ES 클라이언트 버전), `docker-compose.data.yml`(로컬 ES 8.12.0), `docker/elasticsearch/Dockerfile`(운영 ES 9.1.2), `k8s/infra/elasticsearch/values-elasticsearch.yml`·`templates/es-init-indices-job.yaml`
- **범위 밖**: `UserProfileEmbeddingService`(취향 벡터 단위 — B-4가 5줄 건드리는 지점만 명시), `ProductIndexService`(키워드 검색 단위 — A-1 근거로만 인용), `review` 패키지(팀원 소유 — A-2의 공유 설정 변경이 review의 ES 접속에도 영향을 주므로 사용자 확인 대상으로 표시)
- 감사자: 메인 세션이 넘긴 가설 10개를 신선한 컨텍스트에서 코드로 독립 검증했다 — safe-refactor 1단계
- 기준: `audit-checklist.md` 1~16항목(16번은 이 단위에서 임베딩 호출 1곳뿐이라 해당 부분만), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- 저장 스키마(ES 매핑)는 동결 대상이 아니다 — `baro-ai-performance-refactor.md` "데이터 마이그레이션 방침"
- 외부 사실은 로컬 jar 바이트코드(`spring-data-elasticsearch-5.3.6`, `elasticsearch-java-8.13.3`, `elasticsearch-rest-client-8.18.8`, `spring-boot-autoconfigure-3.5.8`, `spring-ai-model-1.1.8`)와 버전 고정 공식 문서(ES 8.12 `dense-vector`·`query-dsl-script-score-query`·`knn-search`, ES 9.x `dense-vector`, Java 클라이언트 8.13 `introduction`)로 확인했다. 감사 시점에 로컬 ES 컨테이너가 꺼져 있어 실측 재현은 못 했고, 각 항목의 검증 방법에 재현 절차를 적었다
- 단위 테스트 추가는 사용자 결정으로 이번 라운드 제외 — 검증 방법은 전부 로컬 수동 절차다

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 벡터 없는 상품이 하나라도 판매 중이면 벡터 검색 전체가 실패함

- **Priority**: High
- **Category**: Architecture (잠재적 버그)
- **문제**: `script_score`의 스크립트가 `cosineSimilarity(params.query_vector, 'vector')`를 아무 방어 없이 호출한다. ES는 벡터 필드 값이 없는 문서에서 이 함수를 실행하면 **문서를 건너뛰지 않고 오류를 던진다** — 즉 `ON_SALE`·`DISCOUNTED` 상품 중 벡터가 `null`인 문서가 1건만 있어도 그 검색 요청 전체가 실패한다. 그런데 벡터가 `null`인 상품은 색인 경로가 **정상 상황으로 허용**하는 상태다(임베딩 실패 시 `null`로 색인 계속). 실패는 `catch (Exception e)`가 빈 배열로 흡수하므로 "추천이 빈 배열만 준다"로만 드러난다. (H9의 "벡터 없는 상품은 추천에 안 나온다"는 실제로는 "벡터 없는 상품이 생기는 순간 아무 상품도 안 나온다"다)
- **근거**:
  - `VectorProductSearchService.java:103-105` — 카테고리 없을 때 스크립트 `"cosineSimilarity(params.query_vector, 'vector') + 1.0"`, `:219-228` — 카테고리 있을 때 스크립트. 둘 다 `doc['vector'].size()` 검사가 없다. `:133-157`의 `buildBoolQuery`에도 `exists` 필터가 없다
  - `ProductIndexService.java:43-51` — 임베딩 실패 시 `vector = null` 유지, `:70` — 그 `null`을 `ProductDocument`에 담아 저장. 스펙 [`vector-similarity-search.md`](../../specs/vector-similarity/vector-similarity-search.md) 흐름 1단계 "실패하면 벡터 `null`로 색인 계속"
  - ES 8.12 공식 문서 `query-dsl-script-score-query` "Functions for vector fields": *"If a document doesn't have a value for a vector field on which a vector function is executed, an error will be thrown."* 권장 방어는 `doc['my_vector'].size() == 0 ? 0 : cosineSimilarity(...)`
  - `VectorProductSearchService.java:88-91` — 예외를 `log.error` 후 `List.of()`로 흡수. 호출부 2곳(`SimilarProductRecommendService.java:74-76`, `PersonalizedRecommendService.java:87-89`)은 빈 후보를 빈 응답으로 돌린다
  - `MedoidDiversityService.java:34-40`의 "벡터 없는 상품 제외" 필터는 이 오류 뒤에 있어 도달하지 못한다 — 방어가 잘못된 층에 있다
- **왜 문제인가**: 임베딩은 외부 API(Gemini 무료 티어, 한도 있음 — [`decisions/003`](../../decisions/003-gemini-migration.md) 트레이드오프)라 실패가 예정된 경로인데, 그 실패 1건이 유사 상품·개인화 추천 API 전체를 무력화한다. 재색인 경로도 아직 없어([`search/001`](../search/001-index-path.md) 개선 제안 1) 한 번 생긴 `null` 문서는 사람이 지우기 전까지 남는다
- **개선 방법**: `buildBoolQuery`의 `filter`에 `exists(field = "vector")`를 추가한다. 스펙의 비즈니스 규칙 "벡터 없는 상품은 후보에서 제외"를 ES 쿼리 단계에서 그대로 구현하는 것이라 정책 변경이 아니다. 스크립트 쪽 `size() == 0 ? 0 : ...` 가드는 `exists` 필터가 있으면 중복이므로 넣지 않는다(YAGNI). `MedoidDiversityService`의 `validCandidates` 필터는 값싼 방어라 그대로 둔다(D-2). native `knn`으로 바꾸더라도 벡터 없는 문서는 자연히 제외되므로 이 필터는 C-1 결정과 무관하게 지금 넣는다
- **검증 방법** (수동, 로컬 ES 8.12.0 기동 후):
  1. Before — 벡터 없는 판매 상품 1건을 직접 색인한다: `curl -XPUT 'localhost:9200/product/_doc/00000000-0000-0000-0000-000000000001' -H 'Content-Type: application/json' -d '{"productName":"벡터없음","status":"ON_SALE","productCategoryCode":"AGRI_X"}'`. 벡터가 있는 다른 상품으로 `GET /api/v1/recommendations/similar/{productId}`를 호출하면 응답은 `[]`이고 로그에 `벡터 유사도 후보 검색 실패: ... doesn't have a value for a vector field`가 찍힌다
  2. After — 같은 호출이 정상 목록을 돌려주고 ERROR 로그가 없다. `org.elasticsearch: DEBUG`(이미 켜져 있음, `application-prod.yml:70`) 요청 본문에 `"exists":{"field":"vector"}`가 들어 있다
  3. 확인 후 테스트 문서는 `curl -XDELETE 'localhost:9200/product/_doc/00000000-0000-0000-0000-000000000001'`로 지운다
- **API 영향**: No Impact (응답 형식 동일. 벡터 없는 상품이 있을 때의 결과가 `[]`에서 정상 목록으로 바뀌는 것은 결함 수정)
- **예상 변경 파일**: `search/application/VectorProductSearchService.java`
- **예상 변경 라인 수**: 5줄 내외
- **위험도**: Low
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 추천 API 가용성(임베딩 실패 1건이 전체 실패로 번지지 않음) · 스펙 규칙과 구현 위치 일치

### A-2. `ElasticsearchConfig`의 타임아웃 설정은 아무도 읽지 않는 죽은 빈이고, 문서는 그 값을 현행으로 적고 있음

- **Priority**: Medium
- **Category**: Spring (죽은 설정 · 문서-구현 불일치)
- **문제**: `ElasticsearchConfig`는 빈을 두 개 만든다. (가) `RestClient elasticsearchRestClient()`는 Spring Boot 자동설정이 그대로 받아 `RestClientTransport` → `ElasticsearchClient` → `ElasticsearchTemplate`으로 이어지는 **실제 클라이언트**다. (나) `ClientConfiguration elasticsearchClientConfiguration()`은 connect 10초·socket 30초를 담고 있지만 **이 컨텍스트에서 그것을 소비하는 코드가 없다.** 그래서 실제 타임아웃은 (가)가 `RestClient.builder(hosts).build()`로 만든 기본값(connect 1초·socket 30초)이고, `application-prod.yml`의 `spring.elasticsearch.*`(connection-timeout·socket-timeout·인증·sniffer) 속성도 사용자 정의 `RestClient` 빈 때문에 우회된다. 아키텍처 문서는 10초/30초를 현행으로 서술한다. 클래스 Javadoc은 "ElasticsearchVectorStore"·"k-NN/ANN"·"O(log N)"이라고 적혀 있는데 셋 다 사실이 아니다(VectorStore 의존성은 2026-09-04 제거, 검색은 `script_score` 선형 스캔). (H6 검증 — "RestClient 빈이 미사용"이라는 가설은 **반대**였다: 미사용은 `ClientConfiguration` 쪽이다)
- **근거**:
  - `ElasticsearchConfig.java:62-75` — `ClientConfiguration` 빈, `.withConnectTimeout(10s).withSocketTimeout(30s)`. `grep -rn ClientConfiguration baro-ai/src/main/java` 결과는 이 파일뿐이다
  - `spring-data-elasticsearch-5.3.6.jar` 바이트코드: `ClientConfiguration`을 받는 클래스는 `ElasticsearchConfiguration`(abstract — `clientConfiguration()`을 **상속해서 구현**해야 쓰인다), `ElasticsearchClients`(정적 팩토리), `ElasticsearchClientFactoryBean`(XML용)뿐이다. baro-ai는 셋 다 안 쓴다(`grep -rn "extends ElasticsearchConfiguration\|ElasticsearchClients\|ElasticsearchClientFactoryBean"` 0건)
  - `spring-boot-autoconfigure-3.5.8.jar` 바이트코드: `ElasticsearchClientConfigurations$ElasticsearchTransportConfiguration.restClientTransport(RestClient, JsonpMapper, ...)`가 `RestClient` 빈을 받는다. Boot 자동설정 클래스 중 `org/springframework/data/elasticsearch/client/ClientConfiguration`을 참조하는 클래스는 0개다. `ElasticsearchRestClientConfigurations$RestClientConfiguration.elasticsearchRestClient(RestClientBuilder)`는 `@ConditionalOnMissingBean`이라 사용자 빈이 있으면 건너뛴다 → Boot의 `RestClientBuilder`(속성 반영본)는 만들어지되 쓰이지 않는다
  - `elasticsearch-rest-client-8.18.8.jar` `RestClientBuilder` 상수: `DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000`, `DEFAULT_SOCKET_TIMEOUT_MILLIS = 30000`, `DEFAULT_MAX_CONN_PER_ROUTE = 10`, `DEFAULT_MAX_CONN_TOTAL = 30`
  - `ElasticsearchConfig.java:11-19` Javadoc의 "ElasticsearchVectorStore"·"O(log N)" — [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md) §2.2가 VectorStore 미사용을, §6.2가 `script_score` 사용을 명시한다. 같은 문서 §2.1(32-36줄)은 "connect timeout: 10초, socket timeout: 30초"를 현행으로 적는다 → STOP §1 문서·구현 불일치
- **왜 문제인가**: 지금 당장 장애는 아니다 — 클러스터 안 ES에 1초 connect는 대개 충분하다. 문제는 "설정을 바꿨는데 아무 일도 안 일어나는" 구조다. 누군가 타임아웃을 조정하려고 이 파일을 고쳐도 효과가 없고, 문서는 그 값을 사실처럼 보여준다. `spring.elasticsearch.*`로 인증·SSL을 붙여야 하는 날에도 같은 이유로 무시된다
- **개선 방법**:
  1. `ElasticsearchConfig`의 빈 두 개와 Javadoc을 삭제하고 Spring Boot 자동설정에 맡긴다. `spring.elasticsearch.uris`는 이미 있으므로(`application-prod.yml:23-24`) 다중 URI 파싱 코드도 필요 없다(Boot가 쉼표 구분을 처리한다)
  2. 문서가 현행으로 적은 값(connect 10초·socket 30초)을 유지하려면 `application-prod.yml`에 `spring.elasticsearch.connection-timeout: 10s`·`socket-timeout: 30s`를 명시한다 — 값 자체를 바꾸는 건 STOP §1.3(임의 개선)이므로 **문서 값을 그대로** 옮기고, 다른 값이 낫다고 보면 사용자 확인
  3. `@EnableElasticsearchRepositories(basePackageClasses = AiApplication.class)`는 Boot의 `ElasticsearchRepositoriesAutoConfiguration`이 메인 클래스 패키지 기준으로 동일하게 등록하므로 중복 — 클래스를 통째로 지울 수 있다. 지운 뒤 리포지토리 빈이 뜨는지는 아래 검증 2로 확인
  4. `ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` §2.1을 같은 변경에서 amend한다(STOP §4 — 교체된 '현행' 문구 삭제)
  5. 이 설정은 `review` 도메인의 ES 접속에도 같이 적용된다 — 코드는 안 건드리지만 connect 타임아웃 값이 1초→10초로 **동작이 바뀌므로** 구현 전 사용자에게 알린다(`baro-ai-performance-refactor.md` 범위 표)
- **검증 방법** (수동): `ELASTICSEARCH_URIS=http://10.255.255.1:9200`(라우팅되지 않는 주소)로 기동한 뒤 `GET /api/v1/recommendations/similar/{아무 UUID}`를 호출하고 응답까지 걸린 시간을 `curl -w '%{time_total}'`로 잰다. Before — yml 값과 무관하게 약 1초 뒤 `[]`(RestClient 기본 connect 1초). After — `spring.elasticsearch.connection-timeout: 10s`에서 약 10초, `2s`로 바꾸면 약 2초(설정이 살아 있다는 증거). 검증 2 — 정상 ES로 기동해 유사 상품 API가 응답하는지(리포지토리·템플릿 빈이 자동설정으로 떴는지) 확인
- **API 영향**: No Impact
- **예상 변경 파일**: `config/ElasticsearchConfig.java`(삭제), `src/main/resources/application-prod.yml`, `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`
- **예상 변경 라인 수**: Java −77줄, yml +2줄, 문서 3줄
- **위험도**: Medium — 공유 인프라라 ES를 쓰는 모든 도메인(review 포함)에 적용된다. `allow-bean-definition-overriding`(판단 항목 3)과 함께 기동 확인이 필요하다
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 코드 감소 · 설정이 실제로 적용됨 · 문서-구현 정합

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. Dead code — 호출되지 않는 변환 메서드·빈 DTO·던지지 않는 ErrorCode·도달 불가 기본값

- **Priority**: Medium
- **Category**: Dead Code
- **문제**: (H5 검증 — 전부 사실)
  - `VectorProductSearchService.java:176-215` `processSearchResults` — private, 호출 0건(`grep -rn processSearchResults` 결과 정의뿐). 이 메서드 때문에 남아 있는 import `HashSet`(7줄)·`Set`(10줄)·`ProductRecommendResponse`(4줄)도 함께 죽는다
  - `recommend/application/dto/SimilarProductResponse.java:4` — 필드 없는 빈 record, 참조 0건
  - `RecommendErrorCode.java:12-17` — `USER_PROFILE_NOT_FOUND`·`PRODUCT_NOT_FOUND`·`INVALID_TOP_K`·`RECOMMENDATION_FAILED`·`VECTOR_SEARCH_FAILED`·`INVALID_VECTOR_DATA` 6개는 어디서도 `throw`되지 않는다(`grep -rn RecommendErrorCode` — 사용처는 `CartFeignErrorDecoder.java:15`의 `CART_NOT_FOUND`뿐). 스펙대로 이 단위는 실패를 전부 빈 배열로 돌리므로 앞으로도 쓸 자리가 없다
  - `VectorProductSearchService.java:56` `categoryMatchBonus != null ? categoryMatchBonus : 0.2` — 호출부 2곳(`SimilarProductRecommendService.java:56`, `PersonalizedRecommendService.java:74`)이 카테고리가 있으면 `0.3`, 없으면 `null`을 넘기고, `null`일 때는 `hasTargetCategory=false`라 `bonus`가 스크립트에 들어가지도 않는다. `0.2`는 도달 불가이고 `0.3`은 두 호출부에 리터럴로 중복돼 있다
  - `VectorProductSearchService.java:77-79` `if (results.size() >= candidateSize) break;` — `:128`에서 `PageRequest.of(0, fetchSize)`로 이미 ES가 그 수만큼만 돌려주므로 도달 불가
  - 참고(이 단위 밖): `EmbeddingErrorCode.INSUFFICIENT_LOGS`·`USER_PROFILE_NOT_FOUND`도 `throw` 0건(`grep -rn "EmbeddingErrorCode\."` — `EMBEDDING_GENERATION_FAILED`만 사용). 취향 벡터 단위 감사에서 다룬다
- **근거**: 위 grep 결과. `spring-boot-java.md`·STOP §4(호출되지 않는 구 경로 방치 금지)
- **왜 문제인가**: `processSearchResults`의 Javadoc(33-35줄)은 존재하지 않는 "기존 `findSimilarProductsByVector`"를 언급해 읽는 사람을 헤매게 한다. `RecommendErrorCode`의 미사용 상수는 "이 API가 404/400을 돌려준다"는 오해를 준다(실제는 빈 배열)
- **개선 방법**: 나열한 것을 전부 삭제한다. `categoryMatchBonus` 파라미터는 없애고 `VectorProductSearchService`에 `private static final double CATEGORY_MATCH_BONUS = 0.3`을 두어 `targetCategoryCode != null`일 때만 적용한다 — 값(0.3)·조건은 그대로라 정책 변경이 아니다. ErrorCode 삭제는 STOP §2 대상이지만 스펙에 이 단위의 에러 표가 없고(전부 빈 배열) 생성 문서(Swagger)에도 노출되지 않으므로 enum 삭제로 끝난다
- **검증 방법**: `./gradlew :baro-ai:compileJava` 통과 + 유사 상품·개인화 API 각 1회 호출 결과가 Before와 동일(같은 상품 ID·같은 순서)
- **API 영향**: No Impact
- **예상 변경 파일**: `VectorProductSearchService.java`, `SimilarProductRecommendService.java`, `PersonalizedRecommendService.java`, `RecommendErrorCode.java`, `dto/SimilarProductResponse.java`(삭제)
- **예상 변경 라인 수**: −60줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 코드 감소 · 오독 방지

### B-2. "메도이드" 이름이 실제 알고리즘(max-min 다양화)과 다름

- **Priority**: Low
- **Category**: Readability (Naming)
- **문제**: 클래스 `MedoidDiversityService`, 메서드 `selectDiverseMedoids`, 변수 `medoids`, Javadoc(9-12·17-23줄) "메도이드 알고리즘", 54줄 주석 "k-medoids 방식으로" — 그러나 구현은 1순위를 고정하고 "이미 고른 것들과의 최소 거리가 가장 큰" 후보를 순차 선택하는 greedy max-min(farthest-first)이다. 중심점 재계산·반복(k-medoids)이 없다. (H4 검증 — 사실. 스펙·용어집은 2026-09-04에 이미 정정됐고 코드만 남았다)
- **근거**: `MedoidDiversityService.java:57`(첫 선택 고정) · `:65-97`(루프마다 마지막 선택과의 거리만 갱신, 최소 거리 최대인 후보 선택, 재배정 없음). `medoids.contains(candidate)`(71줄)는 `ProductDocument`에 `equals` 재정의가 없어 동일성 비교인데(`grep -n "equals\|hashCode" ProductDocument.java` 0건) 같은 인스턴스를 넣고 찾으므로 문제없다. 복잡도는 후보 n ≤ 100, k ≤ 33에서 무시할 수준
- **왜 문제인가**: 포트폴리오·발표에서 "k-medoids"로 설명하면 틀린 설명이 되고, 코드를 읽는 사람이 "재계산 루프가 어디 있지"를 찾게 된다
- **개선 방법**: `MaxMinDiversityService.selectDiverse(...)` 정도로 개명하고 Javadoc·54줄 주석을 실제 동작으로 고친다. 호출부 2곳의 필드명·주석("메도이드 알고리즘으로")도 같이 바꾼다. 용어집·스펙의 "코드명 메도이드" 표기와 [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md) §6.3 "메도이드 다양성"을 같은 변경에서 amend한다(STOP §4). B-3과 같은 변경에서 하면 파일을 한 번만 건드린다
- **검증 방법**: 컴파일 통과 + API 응답 Before/After 동일
- **API 영향**: No Impact
- **예상 변경 파일**: `MedoidDiversityService.java`(개명), `SimilarProductRecommendService.java`, `PersonalizedRecommendService.java`, `docs/product/glossary.md`, `docs/specs/vector-similarity/vector-similarity-search.md`, `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`
- **예상 변경 라인 수**: 30줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 문서-코드 용어 일치

### B-3. 유사 상품·개인화 추천이 같은 5단계 파이프라인을 각자 복사해 갖고 있음

- **Priority**: Low
- **Category**: Cleanup (중복)
- **문제**: `SimilarProductRecommendService.findSimilarProducts`(49-91줄)와 `PersonalizedRecommendService.findSimilarProductsByVector`(62-104줄)는 "보너스 결정 → `min(topK×3, 100)` → 벡터 검색 → 다양화 → `ProductRecommendResponse` 변환"이 줄 단위로 같다. `ProductDocument → ProductRecommendResponse` 변환도 두 곳(83-90줄·96-103줄)에 같은 4줄이 있다(+ B-1의 죽은 메서드까지 세 곳)
- **근거**: 위 줄 번호. `grep -rn "new ProductRecommendResponse("` 4건 중 3건이 `ProductDocument` 기반 동일 변환(`RecipeRecommendService.java:140`은 검색 DTO 기반이라 별개)
- **왜 문제인가**: 후보 규모나 보너스 정책을 바꿀 때 두 곳을 같이 고쳐야 하고, 한 곳만 고치면 유사 상품과 개인화가 조용히 달라진다
- **개선 방법**: `ProductRecommendResponse.from(ProductDocument)` 정적 팩토리 1개 + 공유 파이프라인 메서드 1개(예: B-2로 개명한 다양화 서비스에 `recommend(float[] queryVector, List<UUID> excludeIds, String categoryCode, int topK)`를 두거나 `VectorProductSearchService`에 두는 것 중 하나 — 구현 시 결정). 두 호출 서비스는 "기준 벡터를 어디서 얻는가"(상품 문서 vs 프로필)만 남긴다. 새 인터페이스·추상 클래스는 만들지 않는다(D-4)
- **검증 방법**: 컴파일 통과 + 두 API 응답 Before/After 동일
- **API 영향**: No Impact
- **예상 변경 파일**: `SimilarProductRecommendService.java`, `PersonalizedRecommendService.java`, 다양화 서비스(또는 `VectorProductSearchService.java`), `dto/ProductRecommendResponse.java`
- **예상 변경 라인 수**: −40줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 정책 변경 지점 1곳

### B-4. 사용자 벡터는 `List<Double>`, 상품 벡터는 `float[]` — 양방향 변환 코드가 두 곳

- **Priority**: Low
- **Category**: Cleanup
- **문제**: 임베딩 모델은 `float[]`를 준다. 프로필 저장 시 `float[] → List<Double>`(`UserProfileEmbeddingService.java:299-303`), 추천 시 다시 `List<Double> → float[]`(`PersonalizedRecommendService.java:108-114`)로 되돌린다. 상품 벡터는 처음부터 `float[]`다(`ProductDocument.java:56-57`). 정밀도는 float에서 시작하므로 double로 넓혀 저장해도 얻는 게 없다. (H7 검증 — 사실)
- **근거**: `UserProfileEmbeddingDocument.java:24-25` `@Field(type = Dense_Vector, dims = 1536) private List<Double> userProfileVector;` · 위 변환 두 곳. ES에 저장되는 JSON은 두 타입 모두 숫자 배열이라 **매핑·저장 문서는 바뀌지 않고 Java 역직렬화만 바뀐다** — 재색인 불필요(`ProductDocument.vector`가 이미 `float[]`로 같은 매핑을 읽고 있다)
- **왜 문제인가**: 변환 메서드 2개와 "왜 타입이 다르지"라는 질문이 남는다
- **개선 방법**: `UserProfileEmbeddingDocument.userProfileVector`를 `float[]`로 바꾸고 변환 두 곳을 지운다. `UserProfileEmbeddingService.embedText`(취향 벡터 단위)의 5줄이 함께 바뀌므로 그 단위 감사와 순서를 맞춘다. `VectorProductSearchService.java:231-239`의 `convertToDoubleList`(스크립트 파라미터용)도 `JsonData.of(float[])`가 숫자 배열로 직렬화되면 지울 수 있다 — 구현 시 요청 본문 로그로 확인
- **검증 방법**: 기존 프로필 문서가 있는 사용자로 개인화 API 호출 결과가 Before와 동일(재색인 없이 읽힘) + `org.elasticsearch: DEBUG` 요청 본문의 `query_vector`가 숫자 배열
- **API 영향**: No Impact
- **예상 변경 파일**: `UserProfileEmbeddingDocument.java`, `PersonalizedRecommendService.java`, `VectorProductSearchService.java`, (`UserProfileEmbeddingService.java` 5줄 — 취향 벡터 단위)
- **예상 변경 라인 수**: −20줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 코드 감소 · 타입 일관성

### B-5. 임베딩 결과가 비면 `null` 대신 `float[0]`을 돌려주고, 실패는 두 번 로그됨

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `ProductEmbeddingService.embedProduct`는 응답이 비면 `new float[0]`(28-31줄), 예외면 `CustomException`(39-43줄)을 던진다. 호출부 `ProductIndexService.java:43-51`은 예외를 잡아 `null`로 두므로 "실패 = `null`"이 계약인데, 빈 응답 경로만 `float[0]`이 문서에 그대로 들어간다. 또 예외 경로는 `embedProduct`가 ERROR+스택을 찍고 던진 것을 `ProductIndexService`가 다시 ERROR+스택으로 찍어 같은 실패가 두 번 남는다. (H10 검증 — 코드 사실은 맞음. `float[0]`이 ES에 저장될 때 무슨 일이 생기는지는 판단 항목 5)
- **근거**: 위 줄 번호. `spring-ai-model-1.1.8.jar` `EmbeddingModel.embed(List)` 기본 구현은 `call(request).getResults().stream().map(getOutput).toList()`라 결과가 0건일 때만 빈 리스트가 된다 — 입력 1건에 결과 0건은 실질적으로 오류 응답이라 예외 경로와 같게 다루는 게 맞다. `MedoidDiversityService.java:37`이 `length > 0`을 따로 검사하는 것도 이 `float[0]` 때문에 생긴 방어다
- **왜 문제인가**: "벡터 없음"의 표현이 `null`·빈 배열 두 가지라 검사가 두 겹이 되고, 로그를 보는 사람은 실패 1건을 2건으로 센다
- **개선 방법**: 빈 결과 분기를 지우고(`embeddings.get(0)`이 던지는 예외가 기존 `catch`로 들어가 `null`이 된다) `embedProduct`의 로그는 WARN 한 줄(스택 없이)로 줄인다 — 스택은 호출부가 남긴다. `UserProfileEmbeddingService.embedText`(312줄)도 같은 모양이지만 취향 벡터 단위라 여기서 고치지 않는다
- **검증 방법**: 잘못된 `BARO_AI_GEMINI_KEY`로 기동해 상품 이벤트 1건을 발행하면 ERROR 스택이 1회만 찍히고 문서는 `vector` 없이 색인된다(`GET product/_doc/{id}`)
- **API 영향**: No Impact
- **예상 변경 파일**: `embedding/application/ProductEmbeddingService.java`
- **예상 변경 라인 수**: −6줄
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: "벡터 없음" 표현 단일화 · 로그 중복 제거

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. `script_score` 전수 계산 vs native `knn` — 상품 수가 `[미정]`이라 변경이 아니라 측정 계획

(H1 검증) 확인한 사실은 다음과 같다.

- 매핑이 실제로 무엇을 보내는가: `spring-data-elasticsearch-5.3.6.jar` `MappingParameters` 바이트코드는 `similarity`가 `"default"`가 아닐 때만 `similarity`와 `index`를 쓴다. `@Field.similarity()`의 기본값은 `"default"`이고 `ProductDocument.java:56`은 그 기본값을 쓰므로, 생성되는 매핑은 `{"type":"dense_vector","dims":1536}`뿐이다. `index`·`similarity`·`index_options`는 전부 서버 기본값이다
- 서버 기본값: ES 8.12 공식 `dense-vector` 문서 — `index` "Defaults to `true`", `similarity` "Defaults to `cosine`". ES 9.x 공식 문서 — `index` true, `similarity` cosine, `index_options.type`은 **9.1부터 dims ≥ 384이면 `bbq_hnsw`**(9.0은 `int8_hnsw`). 즉 로컬(8.12.0)·운영(9.1.2) 모두 색인 시 HNSW 그래프(운영은 양자화까지)를 만든다
- 읽기 쪽: ES 8.12 `script-score` 문서 — *"The `script_score` query calculates the score for every matching document"*, *"all matched documents are linearly scanned"*. `VectorProductSearchService.java:110-129`는 `scriptScore` 안에 `bool`(상태 필터·제외 ID)만 있어 그래프를 쓰지 않는다. 8.12 `knn-search` 문서는 정확 kNN을 "필터로 작은 부분집합으로 줄였을 때"만 권한다
- 클라이언트 지원: `spring-data-elasticsearch-5.3.6` `NativeQueryBuilder`에 `withKnnQuery(KnnQuery)`(쿼리 DSL `knn` 절 — 8.12 문서는 "expert case")는 있고, 최상위 `knn` 옵션용 `withKnnSearches`는 **없다**(`getKnnSearches()`만 존재). `elasticsearch-java-8.13.3.jar`에는 `KnnSearch`·`KnnQuery`·`DenseVectorIndexOptions` 클래스가 있다
- 카테고리 보너스: `knn`은 `filter`를 그래프 탐색 중에 적용하지만 스크립트 가산은 못 한다. 보너스를 유지하려면 `bool.should(term categoryCode, boost)`로 옮기거나 `function_score`로 감싸야 하고, 그러면 점수 스케일(`cos+1` 0~2)이 바뀌어 **판단 항목 1(보너스 의도)을 먼저 정해야 한다**

**왜 지금 안 하는가**: `근거 기준` 3번 — 상품 수·초당 요청 추정치가 `[미정]`이다. 상품이 수천 건이면 선형 스캔이 밀리초 단위라 바꿀 이유가 없고, 수십만 건이면 바꿔야 한다. 어느 쪽인지 모른 채 바꾸면 "베스트 프랙티스라서"가 근거가 된다. 측정 계획:

1. 로컬 ES 기동 후 `GET product/_mapping`으로 `vector` 필드에 붙은 실제 기본값 확인(스펙 수동 확인 1번)
2. 상품 문서를 목표 규모(사용자가 정한 값)만큼 시드
3. 같은 기준 벡터로 `script_score`(현행 본문 그대로)와 `knn`(`num_candidates = candidateSize × 10`, 같은 상태 필터)을 각 20회 호출해 `took` 중앙값과 상위 `candidateSize`개 집합의 겹침 비율을 비교
4. `took` 차이가 응답 예산(`[미정]`)에서 의미 있을 때만 전환을 A 항목으로 올린다. 전환 시 Spring Data 5.5.x 업그레이드(C-2) 여부도 같이 본다

### C-2. ES 클라이언트 버전 고정의 근거가 사라졌고, 클라이언트·서버 버전이 세 갈래로 어긋나 있음

(H2 검증) 확인한 사실:

- `baro-ai/build.gradle:31-32` — Spring Data ES를 5.3.6으로, `:44`·`:72` — `elasticsearch-java`를 8.13.3으로 강제. Boot 3.5.8이 관리하는 값은 각각 5.5.6·8.18.8이다(`./gradlew :baro-ai:dependencies`에 `5.5.6 -> 5.3.6`, `8.13.4 -> 8.18.8` 강등·승격이 보인다). 그 결과 **API 클라이언트(`elasticsearch-java`)는 8.13.3, 저수준 클라이언트(`elasticsearch-rest-client`)는 8.18.8**로 같은 클라이언트 안에서도 어긋난다
- 고정 이유: 커밋 `e0b3429e`(#105) 주석 "Spring AI 1.0.0-M5 호환을 위해 elasticsearch-java 8.13.3 사용", `5d5728b6`(#109) "Elasticsearch Java client 8.13.3과 호환되는 Spring Data 버전으로 고정". Spring AI는 [`decisions/003`](../../decisions/003-gemini-migration.md)에서 1.1.8로 올렸고 `spring-ai-elasticsearch-store`도 제거됐다 — **고정의 전제가 없어졌다**
- 서버: 로컬 `docker-compose.data.yml:108` `elasticsearch:8.12.0`, 운영 `docker/elasticsearch/Dockerfile:1` `bitnami/elasticsearch:9.1.2`(`values-elasticsearch.yml:15-19`가 이 이미지를 씀). 운영 헬스 인디케이터는 `application-prod.yml:97-99`에서 "Java client 8.13.3과 호환성 문제"로 꺼져 있다
- Elastic 공식 문서(Java 클라이언트 8.13 `introduction`): *"the client supports communicating with greater or equal minor versions of Elasticsearch"* — 8.13 클라이언트 ↔ 8.12 로컬 서버(클라이언트가 더 새로움)와 8.x 클라이언트 ↔ 9.1 서버(메이저 교차)는 이 문장이 보장하는 범위 밖이다. 9.x 문서에서 8.x 클라이언트의 9.x 서버 호환 문장은 찾지 못했다(판단 항목 7)

**왜 지금 안 하는가**: `build.gradle`은 공유 인프라(cross-cutting 단위)이고, 5.3.6 → 5.5.6 업그레이드는 이 단위 밖 코드(`ProductSearchService`·`ExperienceSearchService`·review의 ES 코드)까지 컴파일 영향이 있다. 이 단위에서 얻는 건 `withKnnSearches`(C-1 전환 시)뿐이다. cross-cutting 라운드에서 "고정 해제 → Boot 관리 버전으로 통일 → 헬스 인디케이터 재활성화" 순으로 다루기를 제안한다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 후보 조회가 벡터를 포함한 `_source` 전체를 가져오는 것 — 유지

(H8) 다양화가 클라이언트에서 후보 간 코사인을 계산하므로 벡터는 반드시 가져와야 한다. 크기 추정: 1536개 float를 JSON으로 보내면 문서당 약 15KB, 기본 `topK=3`이면 후보 9건 ≈ 140KB, 상한 100건 ≈ 1.5MB. `_source` 필터로 `ingredientName`·`updatedAt` 같은 미사용 필드를 빼도 벡터가 지배적이라 이득이 없다. 후보 상한 100(`SimilarProductRecommendService.java:64`)이 이미 상한을 정한다.

### D-2. `MedoidDiversityService`의 벡터 없음 필터(34-40줄) — 유지

A-1의 `exists` 필터가 들어가면 이 필터는 도달하지 않지만, 후보 n ≤ 100의 null 검사는 비용이 없고 이 서비스를 다른 호출부가 재사용할 때의 방어다. `length > 0` 검사만 B-5 이후 `!= null`로 줄인다.

### D-3. `medoids.contains`의 동일성 비교와 O(k·n) 루프 — 유지

`equals`/`hashCode` 재정의나 `Set` 도입은 n ≤ 100, k ≤ 33에서 측정 가능한 이득이 없고, `ProductDocument`에 `equals`를 넣으면 벡터 배열 비교 의미를 정해야 하는 새 문제가 생긴다.

### D-4. 유사 상품·개인화 서비스를 하나로 합치거나 인터페이스를 두는 것 — 안 함

B-3은 공유 파이프라인 1개를 뽑는 것까지다. 두 서비스는 입력(상품 문서 vs 프로필 문서)과 제외 정책(자기 자신 vs 경험 상품)이 달라 컨트롤러 진입점을 유지하는 게 읽기 쉽다. `spring-boot-java.md`의 "구현체 하나뿐인 의존성에 인터페이스 금지"도 같은 결론이다.

### D-5. 실패를 빈 배열로 흡수하는 정책(`catch (Exception e)` → `List.of()`) — 유지

스펙 "ES 예외 → 빈 배열 + ERROR 로그"가 확정 정책이고, 상품 상세 페이지의 다른 부분이 추천 실패에 끌려 죽지 않게 하는 의도가 `SimilarProductRecommendService.java:28-29` 주석에 있다. 예외를 던지는 쪽으로 바꾸는 건 정책 변경이라 이 스킬 범위 밖이다. 다만 A-1이 고쳐지기 전까지는 이 정책이 결함을 가리고 있었다는 점을 남긴다.

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

- **Database (벡터 인덱스 — native `knn` 전환)** — **Later**. 근거·측정 계획은 C-1에 있다. 상품 수 `[미정]`이 채워지고 측정에서 `took` 차이가 나올 때만 올린다.
- **Resilience (벡터 없는 문서 격리)** — **Now**. A-1과 동일하므로 반복하지 않는다.
- **Redis · 응답 캐시 · Circuit Breaker** — 제안하지 않는다. 유사 상품 API의 호출 빈도·동일 상품 반복 비율 자료가 없어(`[미정]`) 캐시 적중률을 추정할 수 없고, 근거 없는 도입은 `audit-checklist.md` 15번 금지 사유에 해당한다. 자동완성 캐시([`decisions/001`](../../decisions/001-autocomplete-caching.md))처럼 반복 호출 관찰이 생기면 그때 본다.

## 판단이 서지 않는 항목

1. **카테고리 보너스가 최종 순서에서 사라지는 것이 의도인가** (H3 검증 — 사실). `VectorProductSearchService.java:219-228`은 ES 점수에 +0.3을 더하고, `MedoidDiversityService.java:100-106`은 최종 목록을 기준 벡터와의 **순수 코사인**으로 다시 정렬한다. 보너스는 (가) 어떤 상품이 후보 `candidateSize`개 안에 드는가, (나) 첫 선택(57줄)이 무엇인가에만 영향을 주고 응답 순서에는 없다. 스펙 용어 "카테고리 일치 보너스로 맥락 보정"과 어긋난다. 선택지 — **A안**: 재정렬을 ES 점수 순(보너스 포함)으로 바꾼다. `SearchHit.getScore()`를 후보와 함께 넘기면 되고 클라이언트 코사인 재계산도 없어진다(코드 −10줄). **B안**: 현행 유지 — 보너스는 "같은 카테고리를 후보에 더 많이 넣기"용이고 최종 순서는 순수 유사도라고 스펙·용어집을 amend한다. 둘 다 비즈니스 규칙이라 감사자가 정하지 않는다
2. **음수 코사인 유사도를 "거리 계산 실패"로 건너뛰는 규칙**. `MedoidDiversityService.java:114-121` `cosineDistance`는 `sim < 0`이면 `-1`을 돌려주고 `:77-80`이 그 후보를 그 라운드에서 건너뛴다(`minDistances[i]`도 갱신 안 됨). 그런데 음수 유사도는 오류가 아니라 "가장 먼" 벡터라 max-min 다양화에서는 오히려 1순위로 골라야 할 후보다. 진짜 오류(null·차원 불일치, `:124-127`)와 정상 음수를 같은 `-1`로 합친 것이 원인이다. 스펙이 이 동작을 규칙으로 적어 두어("음수 유사도(거리 계산 실패)는 후보에서 건너뜀") 결함인지 의도인지 사용자 확인이 필요하다. 결함이면 `cosineDistance = 1 − sim`(0~2)으로 두고 오류만 별도 처리하는 3줄 수정이다
3. **`spring.main.allow-bean-definition-overriding: true`(`application-prod.yml:16`)가 아직 필요한가** (H6 일부). 커밋 `f0cd09dc`(배포)에서 헬스 인디케이터 비활성화와 함께 들어왔다. baro-ai의 `@Bean` 이름과 Boot ES 자동설정의 빈 이름을 대조하면 충돌이 없다 — 이름이 같은 `elasticsearchRestClient`는 Boot 쪽이 `@ConditionalOnMissingBean`이라 애초에 등록되지 않는다. 다른 스타터가 필요로 하는지는 정적으로 못 가린다. 확인 방법: A-2 반영 후 이 줄을 지우고 기동해 `BeanDefinitionOverrideException`이 없으면 삭제(STOP §4)
4. **ES 헬스 인디케이터를 끈 "호환성 문제"의 실체**(`application-prod.yml:97-99`). Boot 3.5.8의 `ElasticsearchRestHealthContributorAutoConfiguration`은 저수준 `RestClient`(8.18.8)로 `_cluster/health`를 호출할 뿐 `elasticsearch-java` 8.13.3을 쓰지 않는다. 주석의 원인 서술이 맞는지 확인 방법: 로컬에서 `management.health.elasticsearch.enabled=true`로 기동해 `/actuator/health` 응답을 본다. C-2 고정 해제와 함께 보는 게 자연스럽다
5. **`float[0]`이 `dense_vector`에 저장될 때 ES가 무엇을 하는가** (H10). 8.12·9.x `dense-vector` 문서 모두 빈 배열을 다루지 않는다. 확인 명령: `curl -XPUT localhost:9200/_tmp_vec -d '{"mappings":{"properties":{"v":{"type":"dense_vector","dims":3}}}}'` 후 `curl -XPUT localhost:9200/_tmp_vec/_doc/1 -d '{"v":[]}'` — 400(차원 불일치)이면 색인 자체가 실패해 컨슈머 재시도 후 레코드가 버려지는 것이고, 200이면 벡터 없는 문서(A-1 대상)가 된다. 어느 쪽이든 B-5로 `float[0]` 경로를 없애면 질문이 사라진다
6. **상품 수·초당 요청 추정치** — C-1·D-1·아키텍처 제안 전부가 이 값에 걸려 있다. `[미정]`
7. **8.x Java 클라이언트 ↔ 9.1.2 서버 호환** — 공식 문서에서 메이저 교차 호환 문장을 찾지 못했다(8.13 `introduction`은 "greater or equal minor versions"만, 9.x `installation`은 호환 문장 없음). 인프라 소유자 확인 또는 9.x 릴리스 노트 재조사(`researcher`) 필요
8. **Spring Data ES 5.5.6에 `withKnnSearches`가 있는가** — 로컬 캐시에 5.5.6 jar가 없어 5.3.6만 확인했다. C-1 전환 결정 시 `researcher` 또는 jar 실물로 확인
9. **ES 8.12에서 `index_options` 생략 시 기본 그래프 타입** — 8.12 문서 fetch 결과에 명시적 기본값 서술이 없었다. `GET product/_mapping`(C-1 측정 1단계)이 답이다

## 승인 결과 (2026-09-05)

사용자가 A·B 전 항목을 승인했다. 구현 순서는 A-1 → A-2 → B-1 → B-2·B-3 → B-4 → B-5, C/D는 수정하지 않는다.

- **판단 항목 1 — 개인화 추천 경로의 카테고리 보너스는 삭제**(A안·B안 모두 아님). 근거와 범위는 [`taste-vector/001`](../taste-vector/001-profile-refresh-path.md) "A-3 재설계" 절의 "선호 카테고리·카테고리 보너스 삭제"가 SSOT다. **유사 상품 추천 경로**(`SimilarProductRecommendService`)의 보너스는 원본 상품과 같은 카테고리를 우대한다는 취지가 있어, 이 단위 구현 라운드에서 유지/삭제를 따로 결정한다 — 삭제하면 B-1의 `CATEGORY_MATCH_BONUS` 상수화도 필요 없어진다
- **판단 항목 7 — ES 서버 9.1.2 ↔ 클라이언트 8.13.3 호환**: 인프라 담당자에게 확인 요청(팀 전달). 답이 오기 전에는 C-2 버전 고정 변경을 하지 않는다
- 판단 항목 2·6·8·9는 구현 세션에서 확인한다
