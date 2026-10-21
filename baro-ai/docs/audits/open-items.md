# 미결 항목 색인

리팩터 라운드를 끝낼 때마다 **아직 닫히지 않은 것들이 어느 문서에 남아 있는지**를 모으는 색인이다. 2026-09-05 4라운드 종료 시점에 만들었고, 2026-09-06 장바구니 레시피 라운드, 같은 날의 비판 평가 후속 라운드(4개 단위), 그리고 같은 날 저녁 로컬 통합 확인까지 반영했다. 실행하지 못한 검증, 답을 기다리는 질문, 자료가 없어 판단을 미룬 항목이 각각 어디에 있는지 한 번에 찾기 위한 것이다.

**이 문서는 SSOT가 아니다.** 각 항목의 내용·근거·값은 "어디에" 칸이 가리키는 원본 문서가 그대로 소유한다. 여기에는 제목과 위치만 둔다 — 같은 값을 두 곳에 적으면 한쪽만 고쳐져 어긋나기 때문이고, 그래서 폐지된 중앙 트래커(`#2`)와 같은 실수를 반복하지 않기 위해서다(`core-scope.md` `[미정]` 처리 절). 원본이 닫히면 여기 행은 취소선과 닫힘 사유로 남긴다 — 무엇이 왜 닫혔는지가 다음 라운드의 판단 재료가 되기 때문이다(지우면 같은 질문을 다시 하게 된다).

## 언제 이 문서를 보는가

- 로컬 인프라를 갖춘 뒤 "그래서 뭘 확인해야 하지"를 찾을 때
- 팀원·인프라 담당자에게 물어볼 것을 한 번에 정리할 때
- 다음 리팩터 라운드를 시작하기 전에 남은 판단이 무엇인지 훑을 때

이 문서에 "예시" 절은 두지 않는다 — 아래 표 자체가 항목 목록이고, 각 행의 "어디에" 칸을 따라가면 그 항목의 전체 서술이 나온다. 쓰는 법은 그 한 단계뿐이다.

## A. 로컬 인프라를 갖춘 뒤 한꺼번에 (보류 묶음)

**사용자 결정(2026-09-05): 아래는 지금 하지 않는다.** 로컬에 Elasticsearch·Kafka·`baro-shopping`·Gemini 키를 모두 갖춘 뒤 한 번에 진행한다. 그전까지 코드 변경은 "컴파일·정적 검사까지만 확인된 상태"로 본다.

### A-1. 실행하지 못한 수동 확인 (26건)

리팩터 4라운드에서 각 스펙의 "수동 확인 (로컬)" 절에 남긴 항목이다. **하나도 실행하지 못했다.**

| 작업 단위 | 건수 | 어디에 |
|-----------|------|--------|
| 취향 벡터 | 8 | [`specs/taste-vector/taste-vector-pipeline.md`](../specs/taste-vector/taste-vector-pipeline.md) |
| 임베딩 유사도 검색 | 6 | [`specs/vector-similarity/vector-similarity-search.md`](../specs/vector-similarity/vector-similarity-search.md) |
| 키워드 검색 | 6 | [`specs/keyword-search/product-search-and-autocomplete.md`](../specs/keyword-search/product-search-and-autocomplete.md) |
| 장바구니 레시피 | 6 | [`specs/cart-recipe/recipe-recommendation.md`](../specs/cart-recipe/recipe-recommendation.md) |

### A-2. 기동해야만 드러나는 확인 (남은 5건 — 2026-09-06에 6건, 2026-09-07에 11건 닫힘)

컴파일이 통과했다고 서버가 뜬다는 뜻은 아니다. 특히 공유 인프라 라운드는 프로필 제거·의존성 6종 삭제·빈 덮어쓰기 허용 해제처럼 **기동 시점에만 결과가 나오는** 변경이 몰려 있다.

**2026-09-06 저녁, nori를 넣은 로컬 ES + Kafka로 앱을 띄워 6건을 닫았다**(환경: `baro-ai/docker/local/`). 닫힌 것과 그때 새로 드러난 것은 [`audits/keyword-search/003`](keyword-search/003-local-integration-verification.md)에 있다 — 통과 6건, 새 한계 1건, 그리고 **이 확인이 아니었으면 못 봤을 A 항목 2건**([`taste-vector/003`](taste-vector/003-shared-executor-saturation.md) A-1, [`cart-recipe/006`](cart-recipe/006-missing-ingredient-product-match.md) A-1).

**2026-09-07, 차원을 바꾼 코드로 인덱스를 지우고 다시 만든 뒤 같은 환경에서 다시 띄워 11건을 더 닫았다** — 상세는 [`audits/vector-similarity/003`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) M-3. 남은 것은 아래 표에서 취소선이 없는 행이다.

| 묶음 | 건수 | 어디에 |
|------|------|--------|
| ~~공유 인프라 (프로필 없이 기동, 빈 이름 충돌, 지표 노출, 로그 손잡이, 컨슈머 4개, 응답 형식)~~ **닫힘(2026-09-07)** — 프로필 지정 없이 기동, `/actuator`에 health·metrics·prometheus·loggers만 노출(prometheus 157줄), `executor.completed{name=profileUpdateExecutor}` 수집, 로그 레벨 손잡이 동작, 컨슈머 그룹 4개(`ai-service`·`-cart`·`-order`·`-review`) 등록, 응답이 `ResponseDto` 형식 | 0 | [`audits/vector-similarity/003`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) M-3 |
| 장바구니 레시피 — **장바구니 연동과 LLM 응답 파싱은 닫힘**(2026-09-07, `scripts/localstub/`의 장바구니 스텁으로 경로 전체 통과). 남은 것은 읽기 상한(3초)이 실제로 걸리는지와 배포 네임스페이스 확인 | 2 | [`audits/cart-recipe/007`](cart-recipe/007-fruit-exclusion-and-prompt-example.md) M-4 |
| 장바구니 레시피 · 취향 벡터 (수신 칸을 줄인 뒤 정상 파싱되는지) — **장바구니 이벤트는 닫힘**(상류가 보낸 `quantity`·`categoryCode`·`cartId`·`cartItemId`가 있는 메시지를 정상 소비하고 축소된 칸만 저장). 남은 것은 주문 이벤트와 장바구니 Feign 응답(`baro-shopping` 필요) | 2 | [`audits/vector-similarity/003`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) M-3 |
| ~~임베딩 유사도 검색 (ES 설정 클래스 삭제 후 빈 등록, 덮어쓰기 허용 해제)~~ **닫힘(2026-09-07)** — 기동 성공 + 추천 API 정상 응답 | 0 | [`audits/vector-similarity/003`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) M-3 |
| 키워드 검색 (`DefaultErrorHandler`의 묶음 **부분 실패** 재시도 동작) — 묶음 리스너 실제 소비와 색인 시간은 닫힘 | 1 | [`audits/keyword-search/003`](keyword-search/003-local-integration-verification.md) |
| ~~장바구니 레시피 (`_msearch` 1건) · 취향 벡터 (프로필 생성)~~ **둘 다 닫힘(2026-09-06)** | 0 | [`audits/keyword-search/003`](keyword-search/003-local-integration-verification.md) |

### A-3. 인덱스 재생성·삭제 — 되돌릴 수 없어 사람이 직접 실행

**할 일이 세 가지다.** ① 매핑이 바뀐 인덱스는 **지우고 다시 만든다**(`product`). ② 코드가 더 이상 쓰지 않는 인덱스는 **지우기만 한다**(`search_logs` 2026-09-07, `product-autocomplete`·`user_profile_embeddings` 2026-09-08, `cart_event_logs`·`order_event_logs` 2026-09-09). ③ 새로 생긴 인덱스는 **만들기만 하면 된다**(`user_behavior_logs` 2026-09-09 — 앱이 뜰 때 자동 생성된다). 아래 표의 각 행이 어느 쪽인지 밝혀 둔다.

**2026-09-09 — `product` 채우는 방법이 생겼다.** 그전에는 인덱스를 새로 만든 뒤 채우려면 발행 모듈 담당자에게 전체 재발행을 부탁해야 했다. 이제 `POST /api/v1/search/admin/reindex`가 `baro-shopping`에서 상품을 페이지 단위로 다시 읽어 색인한다(관리 토큰 필요). **인덱스 삭제·생성은 여전히 사람 몫이고**, 그다음 채우는 일만 이 API가 대신한다.

**2026-09-07 추가(2) — 로컬은 닫힘.** 로컬 ES는 같은 날 지우고 다시 만든 뒤 앱을 띄워 전 경로를 확인했다([`audits/vector-similarity/003`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) M-3). 아래는 **배포 환경**에 남은 몫이다. 임베딩 차원을 1536에서 768로 낮췄다. `product` 인덱스의 `dense_vector` 매핑이 바뀌므로 **재생성 + 전체 재색인 전까지 벡터 추천이 정상 동작하지 않는다** — 옛 1536차원 문서는 후보에서 걸러지기만 한다(오류는 나지 않는다). 같은 날 장바구니·주문 로그에서 쓰기 전용 필드 2개(`quantity`·`categoryCode`)를 지워 `cart_event_logs`·`order_event_logs`도 재생성 대상에 남는다.

저장 형식이 바뀐 인덱스다. 재생성 전까지는 해당 기능이 **의도대로 동작하지 않는다.**

| 인덱스 | 어디에 (재생성 이유·절차) |
|--------|---------------------------|
| `cart_event_logs`·`order_event_logs` **(삭제만)** · `search_logs` **(삭제만)** · `user_behavior_logs` **(신규 — 자동 생성)** | 2026-09-09에 행동 로그 인덱스 둘을 하나로 합치면서 옛 둘은 삭제 대상이 됐다. 옛 기록은 옮기지 않는다(30일 창이라 며칠이면 새 기록으로 찬다) — [`audits/taste-vector/refactor-log.md`](taste-vector/refactor-log.md) 2026-09-09 |
| `product` **(재생성)** | [`audits/keyword-search/refactor-log.md`](keyword-search/refactor-log.md) "인덱스 재생성" — 재생성만으로 끝나지 않고 **상품 이벤트 재발행까지** 필요하다. **2026-09-08 추가:** 상품명에 자동완성용 접두어 서브필드(`productName.ac`)가 생겨 매핑이 또 바뀌었다. 재생성 전까지 자동완성이 결과를 내지 못한다 |
| `product-autocomplete` **(삭제 대상)** | 2026-09-08 자동완성을 `product` 인덱스 서브필드로 합치면서 코드가 더 이상 만들지도 읽지도 않는다. 이미 만들어진 인덱스 삭제는 사람이 실행한다 — [`audits/keyword-search/refactor-log.md`](keyword-search/refactor-log.md) 2026-09-08 |
| `user_profile_embeddings` **(삭제 대상)** | 2026-09-08 취향 벡터를 추천 시점 계산으로 바꾸면서 코드가 더 이상 만들지도 읽지도 않는다. 삭제는 사람이 실행한다 — [`audits/taste-vector/refactor-log.md`](taste-vector/refactor-log.md) 2026-09-08 |
| `product` **(재생성)** — 벡터 차원 1536 → 768 | [`audits/vector-similarity/003`](vector-similarity/003-embedding-dims-and-candidate-multiplier.md) A-1 — 재색인이 곧 전 상품 임베딩 재호출이라 Gemini 한도(C-12)와 함께 계획해야 한다. `user_profile_embeddings`는 2026-09-08부터 삭제 대상이라 이 행에서 뺐다(재색인할 이유가 없다) |

### A-4. 단위 테스트 도입

**2026-09-06 해소.** 순수 계산·서비스 경계를 대상으로 테스트를 들였다(`./gradlew :baro-ai:test` 통과).

**2026-09-08 추가 — Elasticsearch 통합 테스트 도입.** Testcontainers로 실물 ES를 띄우는 `ProductIndexAndSearchIntegrationTest`(3건)를 넣어, 상품 매핑 생성·형태소 검색·자동완성 접두어·벡터 후보 조회를 자동으로 확인한다. 이미지는 `docker/local/Dockerfile.es-nori`를 그대로 빌드해 쓰므로 로컬 확인 환경과 어긋나지 않는다. **A-1의 검색 경로 몫이 이것으로 대체된다.** 남은 것은 Kafka·Gemini가 필요한 확인이다. 기준은 [`.claude/rules/testing.md`](../../../.claude/rules/testing.md) — 그 문서의 "테스트를 처음 들일 때 그때 정한다" 문구는 2026-09-08에 실제 방식으로 대체해 해소했다.

쓰이지 않던 `src/test/resources/application-test.yml`은 2026-09-05에 지웠으므로, 테스트를 들일 때 새로 만든다.

## B. 답을 기다리는 항목 (사람에게 물어야 닫힌다)

| # | 무엇을 | 누구에게 | 막고 있는 것 | 어디에 |
|---|--------|----------|--------------|--------|
| B-1 | 운영 ES 서버와 클라이언트 버전의 호환 | 인프라 담당자 | **ES 클라이언트 버전 고정 해제(감사 B-8)** — 풀면 코드 몇 곳에 마이그레이션이 필요하다(원본에 목록). 헬스 인디케이터 재활성화도 같이 막혀 있다 | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) B-8·판단 항목 8 |
| B-2 | 게이트웨이가 `X-User-Id` 헤더를 실제로 넣어 주는지, prod 라우트에 인증 필터가 없는 것이 의도인지 | 게이트웨이 담당자 | **2026-09-08 부분 완화:** 컨트롤러가 헤더와 경로 변수를 대조해 다르면 403으로 끊는다. 다만 **헤더가 없으면 막지 않는다** — 게이트웨이가 넣어 주는지 확인되지 않아 지금 필수로 만들면 두 API가 통째로 막히기 때문이다. 확인되면 헤더만 신뢰하도록 바꾸고 경로 변수를 지운다 | [`product/platform.md`](../product/platform.md) · 각 스펙 리스크 표 |
| B-3 | 게이트웨이 응답 상한(5초) vs 레시피 경로 예산 합 | 게이트웨이 담당자 | LLM 최악 지연은 2026-09-09에 해소됐다(요청 경로 3초 상한 + SDK 기본 재시도 차단, [`audits/cart-recipe/010`](cart-recipe/010-unit-wide-audit.md) A-1). 남은 것은 코드 값을 더한 최악 합(장바구니 4초 + ES 3초 + LLM 3초 + ES 3초 = 13초)이 5초를 넘는다는 점 — 정상 경로는 그에 한참 못 미치지만 어느 쪽에 맞출지 결정 전(같은 문서 판단 항목 2) | [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) 판단 항목 1 · [`010`](cart-recipe/010-unit-wide-audit.md) 판단 항목 2 |
| B-4 | 배포 시 `baro-shopping`이 다른 네임스페이스인지 | 배포 담당자 | 다르면 `SHOPPING_SERVICE_URL`에 FQDN이 필요하다 | [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) 판단 항목 2 |
| B-5 | `ORDER_CANCELLED` 이벤트를 실제로 발행하는지 | 발행 모듈 담당자 | 취소 로그가 오지 않으면 취향 벡터의 취소 처리 분기가 죽은 코드다 | [`specs/taste-vector/taste-vector-pipeline.md`](../specs/taste-vector/taste-vector-pipeline.md) 리스크 표 |
| B-6 | 상품 이벤트가 키 없이 발행돼 순서가 보장되지 않는 것 | 발행 모듈 담당자 | 늦게 온 옛 이벤트가 새 상태를 덮어쓸 수 있다 | [`specs/vector-similarity/vector-similarity-search.md`](../specs/vector-similarity/vector-similarity-search.md) 리스크 표 |
| ~~B-14 공유 실행자 포화 방향~~ | **닫힘(2026-09-07)** | 검색을 신호에서 빼면서 유입이 사라져 코드 수정 없이 해소. 폐기 693건 → **0건** | [`audits/taste-vector/005`](taste-vector/005-signal-and-weight-simplification.md) |
| ~~B-15 부족 재료 `ingredientName` 매칭~~ | **닫힘(2026-09-07)** | A-1·A-2 반영 완료. 최초 감사가 원인 하나를 놓치고 처방도 틀렸던 것을 fresh-eye가 잡아 정정한 뒤 구현했다 | [`audits/cart-recipe/006`](cart-recipe/006-missing-ingredient-product-match.md) |
| B-7 | review 소유 코드 6건 + 2026-09-07 추가 3건에 같은 개선을 적용할지 | review 담당 팀원 | 컨슈머 팩토리 공통화·중복 `@EnableKafka`·`spring.kafka.producer` 설정·`ReviewBatchController` 응답 형식. 여기에 2026-09-05 추가: `reviewEventConsumerFactory`만 **역직렬화 실패 방어(`ErrorHandlingDeserializer`)와 에러 핸들러가 둘 다 없어**, 깨진 리뷰 이벤트 1건이 그 파티션을 멈출 수 있다. `ReviewConsumer`의 `@KafkaListener(groupId = "ai-service-review")`도 팩토리 값과 중복이라 한쪽만 고치면 어긋난다. 여기에 2026-09-07 추가: `KafkaProducerConfig`의 `@EnableKafka`가 `KafkaConsumerConfig`와 중복이고, `SentimentPropertiesConfig`·`NegativeReviewPropertiesConfig`는 본문이 빈 클래스다(셋 다 review 전용이라 손대지 않았다 — [`audits/cross-cutting/003`](cross-cutting/003-stale-comments-and-write-only-fields.md) C-3) | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) 판단 항목 3·4·7 · `config/KafkaConsumerConfig.java` |
| B-8 | `baro-common`이 JPA·AOP·S3를 전부 노출하는 구조 | 팀 | baro-ai가 JPA를 지워도 Hibernate가 남는 원인 | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) 판단 항목 9 |
| B-9 | k8s의 무의미해진 환경변수 3개 정리 | 배포 담당자 | `SPRING_CLOUD_CONFIG_ENABLED`·`SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED`(Config 클라이언트를 지워 효과 없음, `deployment.yml:33-36`) + `SPRING_PROFILES_ACTIVE: "prod"`(`configmap.yml:6` — `application-prod.yml`을 지워 가리킬 파일이 없다. 기동은 정상이지만 읽는 사람이 프로필별 설정이 있다고 오해한다) | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) 판단 항목 10 · `k8s/apps/baro-ai/` |
| B-10 | `baro-order`의 `cloud.aws.*` 접두사가 올바른지 | baro-order 담당자 | 이번에 고친 Feign 키와 같은 종류의 문제일 수 있다. 라이브러리 버전에 따라 정상일 수도 있어 확인만 요청 | 이 문서(2026-09-05 관찰) |
| B-11 | 상품 이벤트의 `updatedAt`을 발행 시각이 아니라 상품이 바뀐 시각으로 보낼 수 있는지 | 발행 모듈 담당자 | 검색 최신순 정렬의 기준값이다. 지금은 `Instant.now()`라, 재색인용 재발행에서 전 상품이 한 시점에 뭉쳐 최신순이 무의미해진다 | [`specs/keyword-search/product-search-and-autocomplete.md`](../specs/keyword-search/product-search-and-autocomplete.md) 리스크 표 · [`audits/keyword-search/001`](keyword-search/001-query-path.md) B-3 근거 정정 |
| B-16 | 저장소 루트 `docs/`의 사실 오류 3건 | 문서 담당자 | **2026-09-09 사용자 결정: baro-ai에서 고치지 않는다**(5인 공용 영역). 발견만 남긴다 — ① `CICD_GUIDE.md`가 CI 단계에 "단위 테스트 실행"이라 적었으나 9개 워크플로 전부 `bootJar`만 돌린다 ② `BARO_DEPLOYMENT_GUIDE.md`가 존재하지 않는 워크플로 4개(`redis`·`mysql`·`kafka`·`elasticsearch-deploy.yml`)를 나열하고 실재하는 `baro-gateway-deploy.yml`은 빠뜨렸다 ③ `BARO_FARM_STRUCTURE.md`가 "모듈 간 통신은 Gateway + Eureka + Feign"이라 적었으나 baro-ai는 Eureka 설정이 없고 k8s Service 주소를 직접 쓴다 | 저장소 루트 `docs/` |
| B-12 | `baro-opa`의 ai 공개 라우트 정리 | 게이트웨이·인가 담당자 | `POST /api/v1/recommendations/recipes/test`가 public 라우트로 남아 있다(엔드포인트는 2026-09-05 삭제). 같은 파일에 `/search/product(/autocomplete)`·`/search/experience(/autocomplete)`·`/datagen/*` 3종 등 **이미 삭제된 경로 7개**가 더 있다(각각 `ProductSearchController`·`DataGenerationController`의 것으로, 두 컨트롤러 모두 삭제됐다) — 지금 뚫리는 건 아니지만, 이 파일이 실제 API 목록과 어긋난 상태로 굳으면 나중에 진짜 위험한 항목을 못 알아본다. baro-ai 쓰기 범위 밖이라 훅이 차단했다 | `baro-opa/policy/data/ai/public.json` · [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) B-2 |
| B-13 | 새 카테고리 4행을 `02-init-categories.sql`에 넣고 상품을 시드하는 것 | 배포·`baro-shopping` 담당자 | **판매 여부는 2026-09-06 사용자가 결정했다**(김치·두부·장류). 당시 근거였던 "살 게 없는 추천 50%→10%"는 손 목록 기준이라 2026-09-09에 근거 무효 처리했다가, 같은 날 측정 기준을 카테고리 트리로 바꾼 뒤 **종류 수준에서 거의 같은 값(52%→10%)이 나왔다**([`008`](cart-recipe/008-open-problems-handoff.md) §3) — 결정 유지. 남은 건 실행뿐인데 저장소 루트라 이 하네스의 쓰기 범위 밖이다. 카테고리가 생기기 전까지는 부족 재료로 검색해도 상품이 0건이라 카드가 안 붙는다 | [`specs/cart-recipe/pantry-staples-and-catalog-coverage.md`](../specs/cart-recipe/pantry-staples-and-catalog-coverage.md) 갈래 B "적용할 때 같이 고쳐야 하는 것" |

## C. 자료가 없어 판단을 미룬 항목

측정값이나 실데이터가 있어야 결론이 나는 것들이다. 운영이 내려가 있어 지금은 잴 수 없다.

| # | 무엇이 | 무엇이 있어야 닫히는지 | 어디에 |
|---|--------|------------------------|--------|
| C-1 | ~~`script_score` 전수 계산을 native `knn`으로 바꿀지~~ **닫힘(2026-09-06)** — 1천·1만·10만 건 실측으로 "전환 안 함" 결정. 상품이 수만 건을 넘으면 재개 | [`decisions/004`](../decisions/004-script-score-over-knn.md) |
| ~~C-2 상태·가격만 바뀐 수정에도 임베딩·LLM을 다시 부르는 것~~ | **닫힘(2026-09-08)** — 빈도를 재지 않고도 닫혔다. 색인 전에 기존 문서를 읽어, 무거운 값의 **입력이 그대로면** 다시 만들지 않고 옮겨 쓴다. 판정 조건은 값마다 다르다 — 임베딩은 상품명만 보므로 이름이 같으면 되고, 재료명은 상품명과 카테고리를 함께 보므로 카테고리까지 같아야 한다(리뷰에서 잡힌 구분이다) | [`audits/keyword-search/refactor-log.md`](keyword-search/refactor-log.md) 2026-09-08 |
| C-3 | 검색 히트가 10,000건을 넘을 때 총계가 잘리는 것 | 상품 총수 | [`audits/keyword-search/001`](keyword-search/001-query-path.md) 판단 항목 3 |
| C-4 | 장바구니 조회 읽기 상한이 적절한지 | `baro-shopping` 정상 응답 시간 | [`audits/cart-recipe/refactor-log.md`](cart-recipe/refactor-log.md) "남긴 것" |
| C-5 | 레시피 후보 채점식 삭제가 추천 품질에 미친 영향 | 삭제 전후 비교 자료 — **없다.** 삭제 전에도 후보별 점수를 로그로 남기지 않았다. 복원이 필요하면 [`audits/recommend/002`](recommend/002-recipe-scoring.md) C-1의 가중치를 쓴다 | [`specs/cart-recipe/recipe-recommendation.md`](../specs/cart-recipe/recipe-recommendation.md) 리스크 표 |
| ~~C-6 `quantity` 의미 차이~~ | **닫힘(2026-09-07)** — 취향 계산이 읽지 않게 된 데 이어, 같은 날 필드 자체를 로그 문서·수신 모델에서 지웠다 | [`audits/cross-cutting/003`](cross-cutting/003-stale-comments-and-write-only-fields.md) B-1 |
| C-7 | `JsonData.of(float[])`가 스크립트 파라미터로 숫자 배열이 되는지 | 요청 본문 로그(로컬 ES 필요) — 확인되면 변환 코드를 지울 수 있다 | [`audits/vector-similarity/refactor-log.md`](vector-similarity/refactor-log.md) "남긴 것" |
| C-8 | 챗봇의 목적·사용처 | 기획 결정 | [`specs/chat/chatbot.md`](../specs/chat/chatbot.md) |
| C-9 | 부족 재료로 상품이 실제로 몇 건 걸리는지 | **로컬 상품 시드는 2026-09-07에 갖췄다**(`scripts/localstub/seed_catalog.py`, 70건). 남은 것은 부족 재료 목록을 모아 걸리는 건수를 세는 것이다. 원래 적어 둔 조건은 이렇다 — 검색이 nori 형태소 분석과 오탈자 교정을 쓰므로 "다진마늘"로도 마늘 상품이 걸릴 수 있다. 그러면 [`003`](cart-recipe/003-pantry-staples-and-variants.md)의 "살 게 없는 추천 45%"가 과대평가가 된다 | [`audits/cart-recipe/003`](cart-recipe/003-pantry-staples-and-variants.md) 곁가지 |
| ~~C-10 `categoryCode`를 계속 받을지~~ | **닫힘(2026-09-07)** — 사용자 승인으로 받지 않기로 했다. 저장·수신 양쪽에서 지웠고, 카테고리 선호가 필요해지면 그때 다시 넣는다(상류는 계속 보내고 있어 수신 모델에 한 줄 되살리면 된다) | [`audits/cross-cutting/003`](cross-cutting/003-stale-comments-and-write-only-fields.md) B-1 |
| C-12 | Gemini 무료 티어 한도에서 전체 재색인이 가능한지 | 상품 총수·티어 결정. 실측: 임베딩 1건씩은 13회 뒤 429, 10건 묶음은 통과, 25건 이상 묶음은 분당 한도 초과 | [`audits/keyword-search/refactor-log.md`](keyword-search/refactor-log.md) 2026-09-06 M-3 |
| C-13 | ~~다양화 실측을 실제 상품 벡터로 다시 볼지~~ → **카테고리 상한 2가 만드는 쏠림 비율**을 실제 상품 벡터로 볼지(2026-09-09 다양화 삭제 후 의미 변경, `vector-similarity/refactor-log.md` 2026-09-09) | 원래 결론(×3 유지)은 벤치 상품명 154개 기준이었다. **2026-09-07에 실제 카테고리 구조를 따르는 상품 70건을 로컬에 심어 두었으므로**(`scripts/localstub/seed_catalog.py`) 이제 `diversity.py`를 그 데이터로 다시 돌릴 수 있다 — 남은 것은 실행뿐이다. 다만 상품명은 여전히 사람이 지은 것이라 실제 카탈로그와 완전히 같지는 않다 | [`audits/vector-similarity/002`](vector-similarity/002-vector-transfer-knn-and-diversity.md) M-2 |
| C-14 | 비동기 부가 작업의 유실을 어디까지 허용할지 | 상정 검색 트래픽(초당 몇 건). 실측 유실률은 37%(동시 1)~65%(동시 30) | [`audits/taste-vector/003`](taste-vector/003-shared-executor-saturation.md) 판단 항목 1 |
| C-16 | nori 사용자 사전이 필요한지 (`꿀사과`가 문맥에 따라 `꾸`+`사과`가 된다) | 상품명 표본. 20건으로는 사전 필요 여부를 알 수 없다 | [`audits/keyword-search/003`](keyword-search/003-local-integration-verification.md) 판단 항목 1 |
| C-17 | nori 주 필드에 고정 `fuzziness=1`을 얹으면 오탐이 얼마나 느는지 | 오탐 측정. `AUTO`는 2자 형태소에서 0회 편집이라 효과 없음이 실측으로 확인됐다(`001` 판단 항목 6 닫힘) | [`audits/keyword-search/003`](keyword-search/003-local-integration-verification.md) C-1·"덤으로 닫은 것" |
| ~~C-18 검색 신호 유지 여부~~ · ~~C-19 수량 축 제거~~ · ~~C-11 15배 범위~~ | **셋 다 닫힘(2026-09-07)** — 사용자 결정으로 검색 신호와 수량 축을 제거. 비중 범위가 15배 → 3배가 되어 C-11도 함께 사라졌다 | [`audits/taste-vector/005`](taste-vector/005-signal-and-weight-simplification.md) |
| C-20 | C안(연속 갱신 합치기)의 실제 효과 | 사용자 수를 분산시키는 벤치. 지금 스크립트는 한 명으로 300회라 C안에 유리하게 치우친다 | [`audits/taste-vector/004`](taste-vector/004-executor-fix-measurement.md) "답할 수 없는 것" |
| C-21 | 신규 사용자의 프로필 생성 경로 | 사용자 유입 패턴. 검색을 빼서 주문·장바구니 3건이 쌓여야 프로필이 생긴다. 상품 조회 이벤트를 받으면 풀리지만 발행 모듈 변경 필요 | [`audits/taste-vector/005`](taste-vector/005-signal-and-weight-simplification.md) 판단 항목 1 |
| C-22 | 부족 재료 카드의 오탐을 허용할지 | 카탈로그가 커진 뒤 오탐률 측정. 실측 2건: 카탈로그에 없는 `새우젓`에 오탈자 절이 `손질 새우살`을 붙였고(2026-09-06), `설탕`에 `논산 딸기 설향 500g`이 붙었다(2026-09-07, nori가 "설"을 잡음). **과일 허용으로 디저트 레시피가 늘면 설탕·레몬즙 같은 미판매 재료 요구가 잦아져 이 오탐이 더 자주 보일 수 있다.** 2026-09-09 규칙 4 교체 뒤 남은 미판매 부족 재료는 마요네즈·식빵·설탕·버터·스팸 — 시드가 오면 이 다섯으로 먼저 확인한다([`009`](cart-recipe/009-category-rule-in-prompt.md)) | [`audits/cart-recipe/006`](cart-recipe/006-missing-ingredient-product-match.md) 판단 항목 1 · [`007`](cart-recipe/007-fruit-exclusion-and-prompt-example.md) M-4 |
| C-23 | 갱신 태스크가 방금 쓴 로그를 못 보는 경합 | 재현 조건. 2026-09-07 두 번 재봤으나 재현 안 됨(비동기 훅 지연이 refresh 간격을 덮는다). 갱신이 빨라지거나 refresh 간격이 늘면 재검토 | [`audits/taste-vector/003`](taste-vector/003-shared-executor-saturation.md) 후속 |
| C-24 | `term(ingredientName)`의 우위가 카탈로그 규모에서도 유지되는지 | 카탈로그가 커진 뒤 재측정. BM25 IDF 때문에 흔한 재료명일수록 원점수가 낮아진다. 밀리면 `constant_score`로 감싼다 | [`audits/cart-recipe/006`](cart-recipe/006-missing-ingredient-product-match.md) 판단 항목 2 |
| ~~C-26 과일을 계속 제외할지~~ | **닫힘(2026-09-07)** — 제외를 풀었다. 원인이 과일이 아니라 옛 프롬프트의 후보 3개 압력이었음을 M-2가, 과일을 넣어도 할루시네이션이 늘지 않고 오히려 보유 재료 사용률이 79.4% vs 63.9%로 높다는 것을 M-3이 보였다. 남아 있던 범위 질문(디저트·음료를 집밥 레시피로 볼 것인가)은 사용자가 **포함**으로 결정 | [`audits/cart-recipe/007`](cart-recipe/007-fruit-exclusion-and-prompt-example.md) A-2 |
| C-25 | 마지막 어절 규칙이 3어절 이상 재료명에서도 맞는지 | 실제 부족 재료 문자열 분포. 골든셋 실행 로그를 모으면 볼 수 있다 | 같은 문서 판단 항목 3 |

## D. 의도적으로 하지 않기로 한 것

각 감사의 C·D 항목이다. **닫힌 판단**이라 여기서는 위치만 남긴다 — 상황이 바뀌면 해당 감사 문서를 다시 연다.

| 작업 단위 | 어디에 |
|-----------|--------|
| 취향 벡터 | [`audits/taste-vector/001`](taste-vector/001-profile-refresh-path.md) C·D |
| 임베딩 유사도 검색 | [`audits/vector-similarity/001`](vector-similarity/001-vector-search-and-diversity.md) C·D |
| 키워드 검색 | [`audits/keyword-search/001`](keyword-search/001-query-path.md) C·D |
| 장바구니 레시피 | [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) C·D |
| 공유 인프라 | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) C·D |

## 관련 문서

- [`audits/README.md`](README.md) — 감사 문서 규칙·번호 체계
- [`audits/harness-retro.md`](harness-retro.md) — 하네스(규칙·스킬·훅) 개선 후보. 코드가 아니라 작업 방식의 결함을 다룬다
- [`.claude/rules/core-scope.md`](../../../.claude/rules/core-scope.md) — `[미정]` 처리 원칙(원본 문서에 표기, 중앙 트래커 금지)

## E. 모듈 전체 감사(2026-09-08~09)에서 나온 미결 — 측정으로 닫음

| # | 무엇을 | 결과 | 어디에 |
|---|--------|------|--------|
| ~~E-1 임베딩 입력에 카테고리명을 붙일지~~ | **닫힘(2026-09-09)** — 기각. 목표한 효과(+0.0378)가 미리 정한 기준(+0.05)에 못 미쳤고, 같은 날 카테고리 상한을 도입해 얻을 것도 줄었다 | [`full-module/004`](full-module/004-measured-open-questions.md) P2-9 |
| ~~E-2 "부족 재료 최소 1개" 규칙을 완화할지~~ | **닫힘(2026-09-09)** — 규칙 유지. 빼면 32.5%가 살 것 없는 추천이 되고 품질 대가는 관측되지 않았다. **감사 쪽 지적이 틀렸다** | [`full-module/004`](full-module/004-measured-open-questions.md) P2-11 |
| ~~E-3 재구매 상품 제외 정책~~ | **닫힘(2026-09-09)** — 현행 유지. 감사가 "영구 제외"라고 적은 것이 사실이 아니었다(취향 벡터와 같은 30일·10건 창에서 나오고 창을 벗어나면 풀린다). 전제가 틀렸으므로 지적을 철회했다 | [`full-module/004`](full-module/004-measured-open-questions.md) P2-10 |
| E-4 골든셋 5축 사람 채점 | 두 팔의 채점 시트를 만들어 두었으나 **미채점**. 객관 지표만으로는 E-2 결론이 뒤집힐 여지가 낮아 보인다 | `cart-recipe/goldenset/grading-*-20260909-*.md` |
| E-5 취향 벡터의 근거 창(30일·10건)이 충분한가 | **미정** — E-3을 파다 드러났다. 취향 벡터와 제외 목록이 같은 10건에서 나와, 개인화 추천이 사실상 "최근 장바구니와 비슷한 것 − 그 장바구니"다. 실사용자 자료 없이는 답할 수 없다 | [`full-module/004`](full-module/004-measured-open-questions.md) P2-10 |

