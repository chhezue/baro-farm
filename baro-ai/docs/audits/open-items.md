# 미결 항목 색인

리팩터 라운드를 끝낼 때마다 **아직 닫히지 않은 것들이 어느 문서에 남아 있는지**를 모으는 색인이다. 2026-09-05 4라운드 종료 시점에 만들었고, 2026-09-06 장바구니 레시피 라운드까지 반영했다. 실행하지 못한 검증, 답을 기다리는 질문, 자료가 없어 판단을 미룬 항목이 각각 어디에 있는지 한 번에 찾기 위한 것이다.

**이 문서는 SSOT가 아니다.** 각 항목의 내용·근거·값은 "어디에" 칸이 가리키는 원본 문서가 그대로 소유한다. 여기에는 제목과 위치만 둔다 — 같은 값을 두 곳에 적으면 한쪽만 고쳐져 어긋나기 때문이고, 그래서 폐지된 중앙 트래커(`#2`)와 같은 실수를 반복하지 않기 위해서다(`core-scope.md` `[미정]` 처리 절). 원본이 닫히면 여기 행도 같이 지운다.

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

### A-2. 기동해야만 드러나는 확인 (12건)

컴파일이 통과했다고 서버가 뜬다는 뜻은 아니다. 특히 공유 인프라 라운드는 프로필 제거·의존성 6종 삭제·빈 덮어쓰기 허용 해제처럼 **기동 시점에만 결과가 나오는** 변경이 몰려 있다.

| 묶음 | 건수 | 어디에 |
|------|------|--------|
| 공유 인프라 (프로필 없이 기동, 빈 이름 충돌, 지표 노출, 로그 손잡이, 컨슈머 4개, 응답 형식) | 6 | [`audits/cross-cutting/refactor-log.md`](cross-cutting/refactor-log.md) "기동 확인" |
| 장바구니 레시피 (장바구니 연동, LLM 응답 파싱, 읽기 상한, 네임스페이스) | 4 | [`audits/cart-recipe/refactor-log.md`](cart-recipe/refactor-log.md) 2026-09-05 "기동 확인" |
| 장바구니 레시피 · 취향 벡터 (수신 칸을 줄인 뒤 장바구니·이벤트가 정상 파싱되는지) | 3 | [`audits/cart-recipe/refactor-log.md`](cart-recipe/refactor-log.md) 2026-09-06 "기동 확인" |
| 임베딩 유사도 검색 (ES 설정 클래스 삭제 후 빈 등록, 덮어쓰기 허용 해제) | 2 | [`audits/vector-similarity/refactor-log.md`](vector-similarity/refactor-log.md) "기동 확인" |

### A-3. 인덱스 재생성 (5개) — 되돌릴 수 없어 사람이 직접 실행

저장 형식이 바뀐 인덱스다. 재생성 전까지는 해당 기능이 **의도대로 동작하지 않는다.**

| 인덱스 | 어디에 (재생성 이유·절차) |
|--------|---------------------------|
| `cart_event_logs`·`order_event_logs`·`search_logs`·`user_profile_embeddings` | [`audits/taste-vector/refactor-log.md`](taste-vector/refactor-log.md) "인덱스 재생성" |
| `product` | [`audits/keyword-search/refactor-log.md`](keyword-search/refactor-log.md) "인덱스 재생성" — 재생성만으로 끝나지 않고 **상품 이벤트 재발행까지** 필요하다 |

### A-4. 단위 테스트 도입

사용자 결정으로 4라운드 전체에서 제외했다. 저장소에 테스트 클래스가 0개라 `./gradlew :baro-ai:test`는 계속 `NO-SOURCE`다 — "테스트 통과"라고 말할 근거가 없다는 뜻이다. 기준은 [`.claude/rules/testing.md`](../../../.claude/rules/testing.md)("유의미한 테스트만").

쓰이지 않던 `src/test/resources/application-test.yml`은 2026-09-05에 지웠으므로, 테스트를 들일 때 새로 만든다.

## B. 답을 기다리는 항목 (사람에게 물어야 닫힌다)

| # | 무엇을 | 누구에게 | 막고 있는 것 | 어디에 |
|---|--------|----------|--------------|--------|
| B-1 | 운영 ES 서버와 클라이언트 버전의 호환 | 인프라 담당자 | **ES 클라이언트 버전 고정 해제(감사 B-8)** — 풀면 코드 몇 곳에 마이그레이션이 필요하다(원본에 목록). 헬스 인디케이터 재활성화도 같이 막혀 있다 | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) B-8·판단 항목 8 |
| B-2 | 게이트웨이 prod 라우트에 인증 필터가 없는 것이 의도인지 | 게이트웨이 담당자 | 검색·추천 API가 `X-User-Id`를 그대로 신뢰한다. 남의 장바구니 재료를 조회할 수 있다 | [`product/platform.md`](../product/platform.md) · 각 스펙 리스크 표 |
| B-3 | 게이트웨이 응답 상한 vs LLM 최악 지연 | 게이트웨이 담당자 | 정상 요청도 끊길 수 있다. 어느 쪽에 맞출지 결정 전 | [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) 판단 항목 1 |
| B-4 | 배포 시 `baro-shopping`이 다른 네임스페이스인지 | 배포 담당자 | 다르면 `SHOPPING_SERVICE_URL`에 FQDN이 필요하다 | [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) 판단 항목 2 |
| B-5 | `ORDER_CANCELLED` 이벤트를 실제로 발행하는지 | 발행 모듈 담당자 | 취소 로그가 오지 않으면 취향 벡터의 취소 처리 분기가 죽은 코드다 | [`specs/taste-vector/taste-vector-pipeline.md`](../specs/taste-vector/taste-vector-pipeline.md) 리스크 표 |
| B-6 | 상품 이벤트가 키 없이 발행돼 순서가 보장되지 않는 것 | 발행 모듈 담당자 | 늦게 온 옛 이벤트가 새 상태를 덮어쓸 수 있다 | [`specs/vector-similarity/vector-similarity-search.md`](../specs/vector-similarity/vector-similarity-search.md) 리스크 표 |
| B-7 | review 소유 코드 6건에 같은 개선을 적용할지 | review 담당 팀원 | 컨슈머 팩토리 공통화·중복 `@EnableKafka`·`spring.kafka.producer` 설정·`ReviewBatchController` 응답 형식. 여기에 2026-09-05 추가: `reviewEventConsumerFactory`만 **역직렬화 실패 방어(`ErrorHandlingDeserializer`)와 에러 핸들러가 둘 다 없어**, 깨진 리뷰 이벤트 1건이 그 파티션을 멈출 수 있다. `ReviewConsumer`의 `@KafkaListener(groupId = "ai-service-review")`도 팩토리 값과 중복이라 한쪽만 고치면 어긋난다 | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) 판단 항목 3·4·7 · `config/KafkaConsumerConfig.java` |
| B-8 | `baro-common`이 JPA·AOP·S3를 전부 노출하는 구조 | 팀 | baro-ai가 JPA를 지워도 Hibernate가 남는 원인 | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) 판단 항목 9 |
| B-9 | k8s의 무의미해진 환경변수 3개 정리 | 배포 담당자 | `SPRING_CLOUD_CONFIG_ENABLED`·`SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED`(Config 클라이언트를 지워 효과 없음, `deployment.yml:33-36`) + `SPRING_PROFILES_ACTIVE: "prod"`(`configmap.yml:6` — `application-prod.yml`을 지워 가리킬 파일이 없다. 기동은 정상이지만 읽는 사람이 프로필별 설정이 있다고 오해한다) | [`audits/cross-cutting/002`](cross-cutting/002-dependencies-config-and-dead-code.md) 판단 항목 10 · `k8s/apps/baro-ai/` |
| B-10 | `baro-order`의 `cloud.aws.*` 접두사가 올바른지 | baro-order 담당자 | 이번에 고친 Feign 키와 같은 종류의 문제일 수 있다. 라이브러리 버전에 따라 정상일 수도 있어 확인만 요청 | 이 문서(2026-09-05 관찰) |
| B-11 | 상품 이벤트의 `updatedAt`을 발행 시각이 아니라 상품이 바뀐 시각으로 보낼 수 있는지 | 발행 모듈 담당자 | 검색 최신순 정렬의 기준값이다. 지금은 `Instant.now()`라, 재색인용 재발행에서 전 상품이 한 시점에 뭉쳐 최신순이 무의미해진다 | [`specs/keyword-search/product-search-and-autocomplete.md`](../specs/keyword-search/product-search-and-autocomplete.md) 리스크 표 · [`audits/keyword-search/001`](keyword-search/001-query-path.md) B-3 근거 정정 |
| B-12 | `baro-opa`의 ai 공개 라우트 정리 | 게이트웨이·인가 담당자 | `POST /api/v1/recommendations/recipes/test`가 public 라우트로 남아 있다(엔드포인트는 2026-09-05 삭제). 같은 파일에 `/search/product`·`/search/experience`·`/datagen/*` 등 **존재한 적 없는 경로 6개**가 더 있다 — 없는 경로라 지금 뚫리는 건 아니지만, 이 파일이 실제 API 목록과 어긋난 상태로 굳으면 나중에 진짜 위험한 항목을 못 알아본다. baro-ai 쓰기 범위 밖이라 훅이 차단했다 | `baro-opa/policy/data/ai/public.json` · [`audits/cart-recipe/001`](cart-recipe/001-cart-integration-and-request-path.md) B-2 |
| B-13 | 새 카테고리 4행을 `02-init-categories.sql`에 넣고 상품을 시드하는 것 | 배포·`baro-shopping` 담당자 | **판매 여부는 2026-09-06 결정났다**(김치·두부·장류 — 살 게 없는 추천 50%→10%). 남은 건 실행뿐인데 저장소 루트라 이 하네스의 쓰기 범위 밖이다. 카테고리가 생기기 전까지는 부족 재료로 검색해도 상품이 0건이라 카드가 안 붙는다 | [`specs/cart-recipe/pantry-staples-and-catalog-coverage.md`](../specs/cart-recipe/pantry-staples-and-catalog-coverage.md) 갈래 B "적용할 때 같이 고쳐야 하는 것" |

## C. 자료가 없어 판단을 미룬 항목

측정값이나 실데이터가 있어야 결론이 나는 것들이다. 운영이 내려가 있어 지금은 잴 수 없다.

| # | 무엇이 | 무엇이 있어야 닫히는지 | 어디에 |
|---|--------|------------------------|--------|
| C-1 | `script_score` 전수 계산을 native `knn`으로 바꿀지 | 상품 수·초당 요청 추정치 | [`audits/vector-similarity/001`](vector-similarity/001-vector-search-and-diversity.md) C-1 |
| C-2 | 상태·가격만 바뀐 수정에도 임베딩·LLM을 다시 부르는 것 | 수정 이벤트 빈도 | [`audits/keyword-search/001`](keyword-search/001-query-path.md) C-1 |
| C-3 | 검색 히트가 10,000건을 넘을 때 총계가 잘리는 것 | 상품 총수 | [`audits/keyword-search/001`](keyword-search/001-query-path.md) 판단 항목 3 |
| C-4 | 장바구니 조회 읽기 상한이 적절한지 | `baro-shopping` 정상 응답 시간 | [`audits/cart-recipe/refactor-log.md`](cart-recipe/refactor-log.md) "남긴 것" |
| C-5 | 레시피 후보 채점식 삭제가 추천 품질에 미친 영향 | 삭제 전후 비교 자료 — **없다.** 삭제 전에도 후보별 점수를 로그로 남기지 않았다. 복원이 필요하면 [`audits/recommend/002`](recommend/002-recipe-scoring.md) C-1의 가중치를 쓴다 | [`specs/cart-recipe/recipe-recommendation.md`](../specs/cart-recipe/recipe-recommendation.md) 리스크 표 |
| C-6 | `quantity`의 의미가 이벤트마다 다른 것 | 발행 모듈의 의도 | [`specs/taste-vector/taste-vector-pipeline.md`](../specs/taste-vector/taste-vector-pipeline.md) 리스크 표 |
| C-7 | `JsonData.of(float[])`가 스크립트 파라미터로 숫자 배열이 되는지 | 요청 본문 로그(로컬 ES 필요) — 확인되면 변환 코드를 지울 수 있다 | [`audits/vector-similarity/refactor-log.md`](vector-similarity/refactor-log.md) "남긴 것" |
| C-8 | 챗봇의 목적·사용처 | 기획 결정 | [`specs/chat/chatbot.md`](../specs/chat/chatbot.md) |
| C-9 | 부족 재료로 상품이 실제로 몇 건 걸리는지 | 로컬 ES + 상품 시드 — 검색이 nori 형태소 분석과 오탈자 교정을 쓰므로 "다진마늘"로도 마늘 상품이 걸릴 수 있다. 그러면 [`003`](cart-recipe/003-pantry-staples-and-variants.md)의 "살 게 없는 추천 45%"가 과대평가가 된다 | [`audits/cart-recipe/003`](cart-recipe/003-pantry-staples-and-variants.md) 곁가지 |
| C-10 | 장바구니 이벤트의 `categoryCode`를 계속 받을지 | 취향 벡터가 카테고리 선호를 쓸 계획이 있는지 — 지금은 행동 로그에 저장만 되고 읽는 곳이 없다 | [`audits/taste-vector/refactor-log.md`](taste-vector/refactor-log.md) 2026-09-06 "남긴 것" |

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
