# Vector-similarity Refactor Log

임베딩 유사도 검색 작업 단위에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다. 감사 결과 원본은 같은 폴더의 `001-*.md`에 있다.

## 2026-09-05 — 벡터 검색·다양화 전면 반영 (감사 001 A-1·A-2·B-1~B-5)

**반영 항목**: [`001-vector-search-and-diversity.md`](001-vector-search-and-diversity.md)의 A 2건·B 5건 전부. C·D 항목은 감사 판정대로 손대지 않았다. 구현 라운드에서 정해진 세 가지(유사 상품 경로의 카테고리 보너스 삭제, 음수 유사도 처리, 타임아웃 값)는 같은 문서의 "구현 결과 (2026-09-05)" 절에 있다.

### 왜 문제였나 (쉬운 말로)

**상품 하나에 벡터가 없으면 추천이 통째로 죽었다.** 상품이 새로 들어올 때 그 이름을 외부 AI 서버에 보내 "의미를 담은 숫자 묶음"(벡터)을 받아 함께 저장합니다. 이 호출은 무료 사용량 제한이 있어서 실패할 수 있고, 실패하면 벡터 없이 상품만 저장하도록 되어 있었습니다 — 여기까지는 의도한 대로입니다.

문제는 추천을 찾을 때였습니다. 검색 엔진에게 "이 벡터와 비슷한 상품을 찾아줘"라고 시키는데, 벡터가 없는 상품을 만나면 검색 엔진은 그 상품만 건너뛰는 게 아니라 **요청 전체를 실패로 처리**합니다. 즉 판매 중인 상품 중 벡터 없는 게 딱 한 건만 있어도 그때부터 유사 상품 추천과 개인화 추천이 모두 빈 화면이 됩니다. 게다가 실패를 조용히 빈 목록으로 바꿔 돌려주고 있어서, 겉으로는 "추천할 게 없나 보다"로만 보였습니다. 이번에 검색 조건에 "벡터가 있는 상품만"을 넣어, 벡터 없는 상품은 후보에서 처음부터 빠지게 했습니다.

**설정 파일을 고쳐도 아무 일도 일어나지 않았다.** 검색 엔진에 접속할 때의 대기 시간을 정해 둔 설정 클래스가 있었는데, 그 값을 실제로 읽어 쓰는 코드가 어디에도 없었습니다. 진짜로 적용되던 값은 라이브러리 기본값이었고, 문서에는 설정 클래스에 적힌 값(연결 10초)이 사실처럼 적혀 있었습니다. 지금 당장 장애는 아니지만, 나중에 누가 접속 정보나 인증을 붙이려고 이 파일을 고쳐도 효과가 없는 구조입니다. 죽은 설정 클래스를 지우고 스프링이 원래 읽는 설정 파일 쪽으로 옮겼습니다. **실제로 돌던 값(연결 1초)을 그대로 명시했기 때문에 동작은 달라지지 않습니다** — 검색 엔진을 함께 쓰는 리뷰 기능에도 영향이 없습니다.

**이름이 하는 일과 달랐다.** 추천 결과가 "청송사과·부사사과·홍로사과"처럼 사실상 같은 상품으로 도배되지 않게, 서로 멀리 떨어진 상품을 골라내는 부분이 있습니다. 코드는 이걸 "메도이드"라고 불렀는데, 메도이드는 원래 무리의 중심을 반복해서 다시 계산하는 방법을 가리킵니다. 실제 코드는 그런 반복이 없고 "지금까지 고른 것들에서 가장 먼 것"을 하나씩 더하는 훨씬 단순한 방식이었습니다. 발표나 인수인계에서 잘못 설명하게 되고, 코드를 읽는 사람이 있지도 않은 반복 계산을 찾게 됩니다. 실제 동작대로 이름을 바꿨습니다.

**"정반대"를 "고장"으로 오해하고 있었다.** 두 상품이 얼마나 비슷한지는 −1(정반대)에서 1(같음) 사이 숫자로 나옵니다. 그런데 코드는 이 값이 음수면 "계산 실패"로 보고 그 상품을 후보에서 빼 버렸습니다. 다양성을 확보하는 게 목적인 자리에서 **가장 다른 상품을 골라 버린** 셈입니다. 진짜 오류(벡터가 없거나 길이가 안 맞는 경우)와 정상적인 음수를 같은 값으로 표시한 게 원인이라, 둘을 구분하도록 고쳤습니다.

**같은 코드가 두 벌 있었고, 안 쓰는 코드가 남아 있었다.** 유사 상품 추천과 개인화 추천은 "기준이 되는 숫자 묶음을 어디서 가져오는가"만 다르고 나머지는 줄 단위로 같았습니다. 후보 개수나 정책을 바꿀 때 두 곳을 같이 고쳐야 하고, 한쪽만 고치면 두 추천이 조용히 달라집니다. 공통 부분을 한 곳으로 모았습니다. 또 아무도 호출하지 않는 메서드, 필드가 하나도 없는 빈 응답 형식, 실제로는 절대 발생하지 않는 여섯 종류의 에러 코드가 남아 있어 함께 지웠습니다 — 특히 에러 코드는 "이 API가 404나 400을 돌려준다"는 오해를 주는데, 실제로는 모든 실패가 빈 목록으로 나갑니다.

**같은 실패가 로그에 두 번 찍혔다.** 벡터를 만들다 실패하면 만드는 쪽에서 한 번, 그걸 받는 쪽에서 또 한 번 기록해서 실패 1건이 2건처럼 보였습니다. 또 결과가 비어 있을 때만 예외적으로 "빈 숫자 묶음"을 돌려주고 있어서, "벡터 없음"을 나타내는 방법이 두 가지가 되어 검사가 여기저기 겹쳤습니다. 표현을 하나로 통일했습니다.

### 무엇을 바꿨나

- **벡터 없는 상품이 추천을 통째로 죽이지 못하게 했다** — 검색 조건에 "벡터가 있는 상품만"을 넣어, 임베딩에 실패한 상품이 있어도 나머지 추천은 정상으로 나온다.
- **설정을 고치면 실제로 적용되게 했다** — 아무도 읽지 않던 설정 클래스를 지우고 스프링이 원래 읽는 설정 파일로 옮겼다. 값은 지금 돌던 것 그대로라 동작 변화는 없다.
- **이름과 하는 일을 맞추고, 같은 코드 두 벌을 하나로 모았다** — 다양화 서비스를 실제 알고리즘 이름으로 바꾸고, 두 추천이 복사해 갖고 있던 5단계 경로를 공통 경로 하나로 합쳤다.
- **"정반대 상품"을 "계산 실패"로 오해하던 것을 고치고, 안 쓰는 코드를 지웠다** — 호출되지 않는 메서드·빈 응답 형식·발생하지 않는 에러 코드 6종이 사라졌다.

아래 표는 개발자 참조용이다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| A-1 벡터 없는 문서 | 검색 조건에 없음 → `cosineSimilarity`가 오류를 던져 요청 전체 실패 | bool `filter`에 `exists(vector)` 추가 |
| A-2 ES 접속 설정 | `ElasticsearchConfig`의 `RestClient`·`ClientConfiguration` 두 빈. 후자는 아무도 읽지 않는 죽은 설정 | 클래스 삭제, `spring.elasticsearch.*`(연결 1초·응답 30초)만 사용 |
| B-1 죽은 코드 | `processSearchResults`(호출 0건), 빈 record `SimilarProductResponse`, 던지지 않는 `RecommendErrorCode` 6개, 도달 불가 기본값 `0.2`·조기 `break` | 전부 삭제 |
| 카테고리 보너스 | 두 경로 모두 +0.3을 ES 점수에만 더하고 최종 정렬에서는 사라짐 | 삭제 — 스크립트 분기·파라미터 두 벌이 하나로 |
| B-2 이름 | `MedoidDiversityService.selectDiverseMedoids` | `MaxMinDiversityService.selectDiverse` |
| B-3 중복 | 두 추천 서비스가 5단계 파이프라인을 각자 복사 | `VectorRecommendPipeline` 하나 + `ProductRecommendResponse.from` |
| B-4 벡터 타입 | 사용자 `List<Double>` ↔ 상품 `float[]` 양방향 변환 2곳 | 사용자도 `float[]` — 변환 삭제 |
| B-5 임베딩 실패 | 빈 결과는 `float[0]`, 예외는 던짐. 실패 1건이 ERROR 스택 2회 | "벡터 없음"은 `null` 하나로. 만드는 쪽은 WARN 한 줄, 스택은 호출부만 |
| 음수 유사도 | 오류와 같은 `-1`로 뭉뚱그려 후보에서 제외 | 잴 수 없을 때만 `NaN`. 음수는 거리 `1 − cos`(최대 2)로 정상 반영 |

### 변경 파일

**코드 (10개, 신규 2 · 삭제 3)**

- `search/application/VectorProductSearchService.java` — `exists` 필터, 카테고리 분기·죽은 메서드 삭제, 시그니처 5개 → 3개 인자
- `recommend/application/MaxMinDiversityService.java` — 신규(개명 + 음수 유사도 처리)
- `recommend/application/VectorRecommendPipeline.java` — 신규(공통 경로)
- `recommend/application/SimilarProductRecommendService.java`·`PersonalizedRecommendService.java` — 공통 경로 위임
- `recommend/application/dto/ProductRecommendResponse.java` — `from(ProductDocument)` 추가
- `embedding/domain/UserProfileEmbeddingDocument.java` — `float[]`
- `embedding/application/UserProfileEmbeddingService.java` — 정규화 결과를 `float[]`로
- `embedding/application/ProductEmbeddingService.java` — 빈 결과 분기 삭제, 로그 한 줄
- `recommend/exception/RecommendErrorCode.java` — 미사용 6개 삭제
- 삭제: `recommend/application/MedoidDiversityService.java`, `recommend/application/dto/SimilarProductResponse.java`, `config/ElasticsearchConfig.java`
- `src/main/resources/application-prod.yml` — `connection-timeout: 1s`·`socket-timeout: 30s`

**문서 (7개)**

- `specs/vector-similarity/vector-similarity-search.md` — 흐름·용어·데이터 모델·비즈니스 규칙·수동 확인·리스크
- `specs/taste-vector/taste-vector-pipeline.md` — 카테고리 보너스·다양화 클래스명
- `ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` — §2.1 접속 설정, §3.3 벡터 타입, §6.2 쿼리, §6.3 추천 연계, §11 코드 위치
- `IMPLEMENTATION.md` — 세 서비스와 문서 모델의 코드 발췌
- `EMBEDDING_EXAMPLE.md` — "어떻게 쓰이는가" 발췌, 저장 타입 각주
- `product/glossary.md` — 카테고리 보너스 삭제, max-min 다양화·추천 공통 경로
- `specs/README.md` — 작업 단위 ↔ 클래스 매핑

### 검증

- `./gradlew :baro-ai:build` 통과 (컴파일 · spotless · checkstyle)
- `./gradlew :baro-ai:test`는 `NO-SOURCE` — 이 저장소에 자동화 테스트가 없다. "테스트 통과"라고 말할 근거가 없다는 뜻이다
- `grep`으로 삭제 대상(`MedoidDiversityService`·`SimilarProductResponse`·`selectDiverseMedoids`·`categoryMatchBonus`·`convertToFloatArray`) 잔존 0건 확인
- **수동 확인은 하지 못했다** — 로컬 Elasticsearch를 띄우지 못해 스펙 "수동 확인 (로컬)" 절의 5개 항목이 전부 미체크로 남아 있다
- `spring-reviewer` — 지적 0건. `exists` 필터가 기존 상태 필터와 AND로 누적되는지, NaN 기반 "잴 수 없음" 처리가 루프·정렬에서 맞는지를 클라이언트 바이트코드까지 확인받았다
- `doc-reviewer` — 반드시 고칠 것 3건(코드 펜스 누락으로 `IMPLEMENTATION.md` 뒷부분 렌더링 깨짐, refactor-log 절 구조, 감사 문서의 새 절 레벨) 전부 반영

### 기동 확인이 필요하다 (사람이 직접 실행)

인덱스 재생성은 이번 라운드에서 필요 없다 — `float[]` 전환은 ES에 저장되는 JSON을 바꾸지 않으므로 기존 `user_profile_embeddings` 문서가 그대로 읽힌다. 다만 [`taste-vector/refactor-log.md`](../taste-vector/refactor-log.md)가 요청한 인덱스 4개 재생성은 여전히 남아 있다.

기동 후 아래를 확인해야 안전하다.

1. `ElasticsearchConfig` 삭제 후 리포지토리·템플릿 빈이 자동설정으로 뜨는지 (유사 상품 API가 응답하면 확인된 것)
2. `spring.main.allow-bean-definition-overriding: true`(`application-prod.yml`)를 지우고 기동해 `BeanDefinitionOverrideException`이 없는지 — 감사 판단 항목 3. 없으면 그 줄도 지운다

### 남긴 것 · 알려진 제약

- **`convertToDoubleList`는 남아 있다** — `JsonData.of(float[])`가 스크립트 파라미터로 숫자 배열이 되는지 요청 본문 로그로 확인해야 하는데 로컬 ES를 띄우지 못했다. 확인되면 지울 수 있다
- **C-1(native `knn` 전환)·C-2(클라이언트 버전 고정)는 손대지 않았다** — 상품 수·초당 요청이 `[미정]`이고, ES 서버 9.1.2 ↔ 클라이언트 8.13.3 호환은 인프라 담당자 답변 대기 중이다
- **D 항목은 감사 판정대로 유지** — 후보 조회의 `_source` 전체 조회, 다양화의 벡터 없음 필터, `contains` 동일성 비교, 두 서비스 통합·인터페이스 도입, 실패를 빈 배열로 흡수하는 정책
