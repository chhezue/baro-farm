# baro-ai 감사 ① 진단 — 무엇이 문제인가

> 3부작 중 1부. [2부 — 부족한 백엔드 설계 판단](002-missing-backend-decisions.md) ·
> [3부 — 목표 구조와 우선순위](003-target-architecture-and-priority.md)로 이어진다.
>
> **범위:** `baro-ai/` main 83파일 5,484줄 / test 10파일 1,163줄, 설정·k8s, 기존 감사 문서 40여 개,
> 그리고 `baro-shopping`·`baro-common` 연동 지점. 사용자 요청으로 모듈 전체를 한 번에 훑은 감사다.

> **반영 상태 (2026-09-09):** 이 감사에서 나온 **P0~P3을 review 패키지 몫과 아래 예외를 빼고 전부 반영했다**
> (P0·P1은 2026-09-08, P2·P3은 2026-09-09). 무엇을 어떻게 고쳤는지는 각 작업 단위의 반영 이력에 있다 —
> [`cross-cutting`](../cross-cutting/refactor-log.md) · [`keyword-search`](../keyword-search/refactor-log.md) ·
> [`taste-vector`](../taste-vector/refactor-log.md) · [`vector-similarity`](../vector-similarity/refactor-log.md).
>
> **반영하지 않은 것:** review 패키지 소유 항목(P0-1·3·4·5·6·8, P2-3·4, P3-5)은 팀원 몫이라 손대지 않았다.
> 측정이 필요했던 셋(P2-9·P2-10·P2-11)은 2026-09-09에 실제로 재고 [`004`](004-measured-open-questions.md)에
> 판정을 남겼다 — **P2-9·P2-11은 기각**(P2-11은 감사 쪽 지적이 틀린 것으로 확인됐다), P2-10은 실사용자
> 데이터가 없어 측정 불가.
> P3-2·P3-3은 `java-comments.md`가 "되돌리지 않는다"고 명시한 항목이라 **감사 쪽이 틀렸다**. P3-4도 springdoc 제약으로 불가능하다.
>
> **아래 본문은 반영 전 시점의 진단이므로 현행 코드와 다르게 읽히는 곳이 있다** — 현행 동작의 SSOT는
> `docs/specs/`와 위 반영 이력이다.

---

## 1. Executive Summary

### 먼저, 예상과 다른 점

**이 프로젝트는 "기술 스택 나열형 과잉 설계"가 아닙니다.** Redis 없음, 분산락 없음, 별도 벡터 DB 없음, CQRS·헥사고날 흉내 없음, 불필요한 인터페이스 남발 없음. `script_score` vs `knn`을 실측으로 비교하고 "안 바꾼다"고 결론 낸 문서(`docs/decisions/004-script-score-over-knn.md`)까지 있습니다. 이 축에서는 상위 수준입니다.

**진짜 문제는 정반대 방향입니다: 실제로 터질 위험은 방치돼 있고, 안 터질 곳에 정성이 몰려 있습니다.**

### 세 줄 요약

1. **동기 HTTP 요청 하나가 Tomcat 스레드를 최대 90초 이상 붙잡습니다.**
   `GET /recommendations/recipes/{userId}`는 요청 스레드 위에서 Gemini를 호출하고, `LlmClientConfig`가 호출당 30초 × 3회 재시도 + 백오프를 겁니다. 격벽(bulkhead)도, 서킷브레이커도, 타임아웃 예산도 없습니다. 이 서비스에서 **실제로 일어날 유일한 장애**가 여기인데 대비가 하나도 없습니다.

2. **취향 벡터 서브시스템 전체가, "최근 로그 10건의 가중 평균"을 얻으려고 존재합니다.**
   `AsyncConfig`(전용 스레드풀 + 폐기 정책) + `UserProfileRefreshTrigger` + `UserProfileEmbeddingDocument` + 전용 ES 인덱스 + 갱신 경쟁 조건 + 읽기 지연 문제 — 전부 사전 계산(materialized)을 택한 대가입니다. 읽기 시점 계산으로 바꾸면 클래스 4개와 문제 3개가 통째로 사라집니다.

3. **review 도메인은 상당 부분이 동작하지 않거나 아무도 안 읽습니다.**
   `@Scheduled` 배치는 `@EnableScheduling`이 없어 **한 번도 실행된 적이 없고**(다른 5개 모듈에는 전부 있습니다), `product_best_review_index`는 어떤 모듈도 읽지 않으며, 감정 분류 설정 파일에는 substring 충돌 버그가 실재합니다.

### 축별 평가

| 축 | 평가 | 근거 |
|---|---|---|
| 전체 복잡도 | **규모 대비 적정~약간 높음** | 5,484줄에 클래스 83개. 레이어링 일관, 순환 의존 없음 |
| 과도한 인프라 | **낮음 (잘한 것)** | Redis·분산락·메시지큐 남용 없음. Kafka는 상류가 이미 쓰는 것 |
| 과도한 추상화 | **낮음~중간** | `SentimentClassifier` 인터페이스가 구현체 1개뿐인 정도. `VectorRecommendPipeline` 공통화는 타당 |
| 과도한 **알고리즘·상수** | **높음 ← 문제 지점** | MaxMin 다양화, 감정 임계값 3종, 프로필 가중치, 컷오프 10 — 검증 근거 없는 숫자 15개 이상 |
| 불필요한 기능 | **중간~높음** | 베스트 리뷰(소비처 없음), MIXED 감정, 제외 카테고리 필터(빈 목록), 별도 자동완성 인덱스 |
| 백엔드 설계 고민 | **부족 ← 문제 지점** | 타임아웃 예산·DLT·이중 쓰기 원자성·낙관적 동시성·재색인 경로·rate limit 전부 없음 |
| 포트폴리오 적합성 | **소재는 최상급, 코드가 못 따라감** | 골든셋 A/B, 차원 축소 실측, LLM 환각 방어 설계는 훌륭. 정작 "백엔드"를 보여줄 지점이 비어 있음 |

---

## 2. 지키고 발전시킬 것

지우면 안 되는 것들입니다. 오히려 이걸 중심으로 재편하는 게 맞습니다.

| 무엇 | 왜 좋은가 |
|---|---|
| **`CandidateRecipePlan.from()`의 서버 측 재료 대조** | LLM이 "사용한 재료 / 부족한 재료"를 스스로 말하게 두지 않고, 서버가 보유 재료와 집합 연산으로 계산합니다. **AI와 결정론적 로직의 책임 경계**를 코드로 그은 유일한 지점이고, 면접에서 그대로 설명 가능한 최고의 소재입니다 |
| **Kafka `partition-offset`을 ES 문서 ID로 쓰는 멱등성** | 재시도 시 덮어쓰기로 중복 적재를 막습니다. 상용 패턴이고 설명 가치가 높습니다 |
| **`BatchListenerFailedException`에 실패 위치를 담는 처리** | 묶음 리스너에서 위치 없이 던지면 뒤의 멀쩡한 레코드까지 폐기됩니다. 이걸 알고 짠 코드는 드뭅니다 |
| **골든셋 기반 프롬프트 A/B (실행 합격률 69.0% → 90.5%)** | "LLM 품질을 어떻게 검증하는가"에 대한 실제 답입니다. 다만 cart-recipe에만 있습니다 |
| **1536 → 768 차원 축소 실측** (응답 507KB→255KB, 동시 30 p95 183ms→53ms, 상위 5개 이웃 94.5% 유지) | 근거 있는 최적화의 교과서적 사례 |
| **`script_score`를 유지하기로 한 결정** | "HNSW가 최신이라서" 안 바꾼 것 — 정확히 반대 방향의 사고 |
| **RDB·Redis를 안 쓴 것** | 이 도메인에 없어도 되는 걸 안 넣었습니다 |

---

## 3. Complexity Hotspots

우선순위 순입니다. 판단: `REMOVE` / `SIMPLIFY` / `KEEP` / `REDESIGN` / `INVESTIGATE`

| # | 위치 | 문제 | 현재 복잡성의 명목 이유 | 실제 필요성 | 판단 | 추천 방향 |
|---|---|---|---|---|---|---|
| H1 | `RecipeRecommendService` → `RecipeCandidateGenerator` → `LlmClientConfig` | 동기 요청 스레드가 LLM에 최대 ~93초(30s×3 + 백오프) 묶임. 게이트웨이 상한은 5초로 알려짐 | "재시도로 429·5xx 흡수" | 재시도는 필요, **요청 경로에서는 불가** | **REDESIGN** | 호출 지점별 타임아웃 예산 분리(요청 경로 ≤3s·재시도 0~1회), 격벽 스레드풀, 실패 시 `GENERATION_FAILED` 즉시 반환 |
| H2 | `AsyncConfig` + `UserProfileRefreshTrigger` + `UserProfileEmbeddingDocument` + `UserProfileEmbeddingRepository` | 최근 로그 10건 평균을 위해 사전계산 파이프라인 유지. 쓰기 증폭(이벤트 1건 = ES 4왕복), 동시 갱신 시 lost update, 읽기 지연 | "추천 응답을 빠르게" | 추천 경로에 ES 2~3왕복(수 ms) 추가면 끝 | **SIMPLIFY** | 읽기 시점 계산으로 전환 → 클래스 4개·인덱스 1개·경쟁조건·폐기 정책 동시 소멸 |
| H3 | `ProductAutocompleteDocument` + `ProductAutocompleteRepository` + 이중 쓰기 | 상품명·상태를 두 인덱스에 복제. 원자성 없음(하나만 성공 가능). `PURCHASABLE_STATUSES` 상수를 한쪽이 못 씀(주석이 "같이 고쳐야 함"이라 경고) | "edge_ngram 전용 분석기 필요" | `ProductDocument.productName`에 `@InnerField(suffix="ac")` 하나면 동일 | **REMOVE** | 인덱스 통합 → 문서·레포지토리·이중쓰기·상수 이원화 전부 제거 |
| H4 | `MaxMinDiversityService` (201줄, 코사인 직접 구현) | **유사 상품 추천**에서 서로 가장 먼 것을 고름. 관련도 항이 아예 없는 Farthest-first | "같은 카테고리 쌍 45%→11.5%" | 쏠림은 실측했으나 **쏠림이 나쁘다는 근거는 없음** | **REDESIGN** | MMR(λ로 관련도/다양성 가중) 또는 카테고리 단위 상한 1~2개. 후자면 클래스 삭제 가능 |
| H5 | `review/application/bestreview/*` 3클래스 + `ReviewCandidateQueryRepository`(126줄) + 배치 | `@EnableScheduling` 없어 배치 미동작. `product_best_review_index`를 **읽는 코드가 전 저장소에 없음** | "베스트 리뷰 선정" | 소비처 부재 | **REMOVE 또는 소비처 확정** | 팀원 소유이므로 먼저 확인 필요. 소비처 없으면 삭제 |
| H6 | `KeywordSentimentClassifier`(204줄) + `review-sentiment.yml`(~110줄) | 임계값 3종(tolerance 2, strongDiff 3, mixedMin 4) 근거 없음. substring 매칭 충돌 버그 실재 | "LLM 없이 감정 분류" | 별점 1~2 / 3 / 4~5 매핑만으로 대부분 결정됨 | **REDESIGN** | 별점 기준으로 축소하거나, 리뷰 저장 시 LLM 1회 분류(이미 Gemini 있음) |
| H7 | `ProductIndexService.indexProducts` — 상품당 LLM 1회 | 색인 처리량이 LLM 지연에 고정(상품당 ~1.5s). 가격·재고만 바뀐 `PRODUCT_UPDATED`에도 임베딩+LLM 재호출 | "재료명 사전 계산" | 사전 계산은 옳음, **무조건 재계산이 문제** | **SIMPLIFY** | `productName` 해시 비교로 변경 감지 → 이름 안 바뀌면 기존 `ingredientName`·`vector` 재사용 |
| H8 | `ProductSearchService` (406줄, 4역할) | 키워드 검색·자동완성·msearch·`findByIds`가 한 클래스 | "상품 인덱스 질의를 한 곳에" | 응집이 아니라 결합 | **SIMPLIFY** | `ProductQueryRepository`(ES 조립) / `ProductSearchService`(검색) / `AutocompleteService` 분리 |
| H9 | `CartLogDocument` + `OrderLogDocument` + 레포지토리 2개 | 필드가 **완전히 동일**(id/userId/productId/productName/eventType/occurredAt). 읽을 때 `Stream.concat`으로 합침. "유형별 최근 5건"이라 최근성 정렬이 유형 경계에서 깨짐 | "도메인 분리" | 스키마·질의·소비처가 전부 같음 | **SIMPLIFY** | `user_behavior_logs` 단일 인덱스 + `domain` 필드 → 레포 1개, 질의 1회, 정렬 왜곡 해소 |
| H10 | ~~`PersonalizedRecommendService`의 제외 목록~~ **사실 정정(2026-09-09)** — "영구 제외"가 아니다. 제외 목록은 취향 벡터를 만든 것과 **같은 최근 10건·30일 창**에서 나오므로 그 창을 벗어나면 다시 추천된다. 아래 서술은 정정 전 것이다 | ~~담아봤거나 산 상품을 추천에서 영구 제외~~ | "중복 노출 방지" | **식료품은 재구매가 지배적**. 계란·우유를 다시 추천 안 하는 게 맞나? | **INVESTIGATE** | 주문 상품은 N일 후 재노출, 장바구니에 현재 담긴 것만 제외 |
| H11 | `LlmClientConfig` 전역 단일 정책 | 색인(배치)과 추천(대화형)이 같은 30s×3을 씀 | "SDK 레벨 일괄 방어" | 두 경로의 허용 지연이 30배 다름 | **SIMPLIFY** | 경로별 `Client` 2개 또는 호출부 옵션 |
| H12 | `VectorRecommendPipeline.MAX_TOP_K = 100/3 = 33` | 내부 후보 상한이 **공개 API 제약(1~33)으로 누출**. 에러 메시지에도 33 하드코딩 | "실효 배수 유지" | 클라이언트가 알 이유 없는 숫자 | **SIMPLIFY** | topK 상한은 50 등 의미 있는 값, 후보 수는 내부에서 흡수 |
| H13 | `ProductSearchResponse` ↔ `ProductRecommendResponse` | 필드 4개가 완전히 동일한 DTO 2개 + 필드별 수동 매핑 | "도메인 분리" | 순수 의식(ceremony) | **SIMPLIFY** | 하나로 통합 |

---

## 4. Overengineering Candidates

### O1. 취향 벡터의 사전 계산 + 전용 비동기 실행자

**① 지금 무엇을 하는가**

장바구니·주문 Kafka 이벤트마다 → ES 로그 저장 → `@Async("profileUpdateExecutor")`로 프로필 재계산 요청 → 전용 스레드풀(core 2 / max 5 / queue 100 / **큐 넘치면 조용히 폐기**)에서 → 장바구니 로그 5건 + 주문 로그 5건 조회(ES 2회) → 상품 벡터 mget(ES 1회) → 가중 평균 + L2 정규화 → `user_profile_embeddings`에 저장(ES 1회).

**② 왜 과도한가**

- 얻는 것은 **최대 10개 상품 벡터의 가중 평균**입니다. 계산 비용은 무시할 수준(768차원 × 10)인데, 그걸 미리 만들어 두려고 스레드풀·트리거 컴포넌트·전용 인덱스·폐기 정책·읽기 지연 문제를 전부 떠안았습니다.
- 이벤트 1건당 ES 4왕복. 장바구니에 5개를 담으면 이벤트 5건 → **ES 20왕복 + 프로필 5회 재계산**, 그런데 최종 결과는 마지막 1회와 사실상 같습니다.
- 같은 사용자의 이벤트 2건이 동시에 처리되면 두 스레드가 각자 계산 후 각자 저장합니다. **낙관적 동시성 제어(`seq_no`/`primary_term`)가 없어 lost update가 가능**하고, 이 위험은 기존 감사 문서에도 없습니다.
- 읽기 지연: 방금 담은 상품이 추천에 반영되기까지 비동기 지연 + ES refresh(1s)가 겹칩니다(open-items C-23으로 인지는 돼 있음).
- 부하 시 **유실을 정책으로 허용**합니다. 실측 폐기율이 37~65%였던 시기가 있었습니다(C-14). "개인화가 부하 때 절반 날아가도 됨"은 근거 없이 서 있는 결정입니다.

**③ 실제 요구사항**

"이 사용자의 최근 관심 상품 방향 벡터를 추천 시점에 안다."

**④ 제거하면 생기는 문제**

추천 요청당 ES 2~3왕복(로그 조회 + 벡터 mget)이 늘어납니다. 상품 벡터 mget은 최대 10건이라 수 ms 수준이고, 그 뒤에 이어지는 `script_score` 전수 스캔이 훨씬 무겁습니다. 개인화 추천이 초당 수백 건 들어오는 상황이 아니면 체감 차이가 없습니다.

**⑤ 더 단순한 대안**

```
PersonalizedRecommendService.recommendProducts(userId, topK)
  ├─ behaviorLogRepository.findRecent(userId, since=30d, limit=10)   // ES 1회
  ├─ productRepository.findAllById(productIds)                       // ES 1회 (벡터 포함)
  ├─ blendIntoProfileVector(...)                                     // 순수 계산, 그대로 재사용
  └─ vectorRecommendPipeline.recommend(vector, exclude, topK)
```

삭제 대상: `AsyncConfig`, `UserProfileRefreshTrigger`, `UserProfileEmbeddingDocument`, `UserProfileEmbeddingRepository`, `user_profile_embeddings` 인덱스, 컨슈머의 `refresh()` 호출 2곳.
`blendIntoProfileVector`와 `calculateEventWeight`는 **그대로 유지**(이미 static이고 테스트도 있음).

**⑥ 포트폴리오 관점**

지금 구조는 "비동기 처리를 해봤다"를 보여주지만, 면접에서 *"왜 비동기여야 했나요? 동기로 하면 얼마나 느려지나요?"* 를 물으면 답할 측정치가 없습니다. 반대로 **"사전 계산을 검토했다가 읽기 시점 계산으로 되돌렸고, 그 이유는 쓰기 증폭·lost update·읽기 지연 대비 이득이 없어서"** 는 훨씬 강한 답변입니다. 그리고 진짜 비동기가 **필요한** 곳(H1의 LLM)에 그 노력을 옮길 수 있습니다.

---

### O2. `product-autocomplete` 별도 인덱스

**① 지금**

상품 색인 시 `product`와 `product-autocomplete` 두 인덱스에 각각 씁니다. 후자는 `productId`·`productName`·`status`만 담고 edge_ngram 분석기를 씁니다.

**② 왜 과도한가**

- ES는 **하나의 필드에 여러 분석기를 붙이는 멀티필드**를 제공하고, 이 코드는 이미 `productName`에 `@MultiField`로 `word` 서브필드를 쓰고 있습니다. 즉 같은 문제를 이미 다른 방식으로 풀어 놓고, 자동완성만 별도 인덱스로 갔습니다.
- 대가가 큽니다. **이중 쓰기의 원자성이 없습니다.** `indexProducts`는 autocomplete → main 순으로 저장하는데, 두 번째가 실패하면 자동완성에는 뜨지만 검색에는 없는 유령 상품이 남습니다. `deleteProduct`도 main → autocomplete 순이라 반대 방향 고아가 생깁니다. 재시도 2회 후에는 **조용히 건너뜁니다**.
- `PURCHASABLE_STATUSES` 상수를 자동완성 레포지토리가 쓸 수 없어(`@Query` 문자열) 상태 목록이 **두 곳에 하드코딩**돼 있습니다. 코드 주석이 "같이 고쳐야 한다"고 경고하는데, 이건 설계로 없앨 수 있는 위험을 주석으로 막고 있는 것입니다.

**③ 실제 요구사항**

상품명 접두어 자동완성.

**④ 제거하면**

`product` 인덱스 매핑 변경 + 전체 재색인이 필요합니다. 그런데 **어차피 지금도 재색인 대기 상태**입니다(open-items A-3: 차원 변경으로 `product` 재생성 필요). 같은 라운드에 묶으면 추가 비용이 0에 가깝습니다.

**⑤ 대안**

```java
@MultiField(
    mainField = @Field(type = Text, analyzer = "nori"),
    otherFields = {
        @InnerField(suffix = "word", type = Text, analyzer = "standard"),
        @InnerField(suffix = "ac", type = Text,
                    analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    })
private String productName;
```

자동완성은 `productName.ac` + `PURCHASABLE_STATUSES` 필터로 `NativeQuery` 한 번.
삭제 대상: `ProductAutocompleteDocument`, `ProductAutocompleteRepository`, 이중 쓰기 2곳, 상태 목록 이원화.

**⑥ 포트폴리오 관점**

"인덱스를 둘로 나눴다"보다 **"이중 쓰기의 원자성 문제를 인지하고 단일 인덱스 멀티필드로 없앴다"** 가 정확히 백엔드 답변입니다.

---

### O3. `MaxMinDiversityService` — 관련도 항이 없는 다양화

**① 지금**

topK×3 후보를 유사도 순으로 가져와, 1위를 고정한 뒤 **이미 고른 것들과 가장 먼 것**을 반복 선택합니다(Farthest-first). 마지막에 쿼리 벡터 유사도로 재정렬.

**② 왜 과도한가**

- 2번째 선택부터 **쿼리 벡터를 전혀 보지 않습니다.** 정의상 후보 중 "서로 가장 안 닮은 것"을 뽑으므로, 유사 상품 추천에서 *가장 덜 유사한 것들*을 고르는 알고리즘입니다.
- 측정된 것은 "같은 카테고리 쌍 45% → 11.5%"인데, **쏠림이 나쁘다는 근거는 어디에도 없습니다.** 사과 상세페이지에서 다른 사과 5종을 보여주는 게 나쁜가요? 실제 클릭·전환 데이터가 없으니 이 45%가 문제인지 아무도 모릅니다. "그럴듯해 보이기 위해 추가된 임의의 로직"의 정확한 사례입니다.
- 파생 비용이 큽니다. 후보 배수 3배 → ES에서 벡터를 **후보 수만큼 통째로 받아야 함**(`_source`에서 벡터를 못 뺌) → 그래서 응답 크기 측정이 필요했고 → 그래서 차원 축소 라운드가 필요했고 → 그래서 topK 상한 33이라는 이상한 API 제약이 생겼습니다. **한 알고리즘이 4개의 후속 결정을 만들어냈습니다.**

**③ 실제 요구사항**

"추천 5개가 사실상 같은 상품이면 곤란하다."

**④ 제거하면**

같은 상품의 용량 변형(1kg/2kg/3kg)이 나란히 뜰 수 있습니다. 실재하는 문제입니다.

**⑤ 대안 두 가지**

- **(a) MMR**: `score = λ·sim(q,d) − (1−λ)·max sim(d, selected)`. 20줄이면 되고, λ가 관련도/다양성 트레이드오프를 **명시적 파라미터로** 드러냅니다. 근거 없는 45%보다 설명이 쉽습니다.
- **(b) 카테고리 상한**: 후보를 유사도 순으로 훑되 같은 `productCategoryCode`는 최대 2개까지. 코사인 계산 없음, 클래스 삭제 가능, 후보 배수 3 불필요 → **후보에서 벡터를 안 받아도 됨** → 응답 크기 문제·topK 33 제약까지 동시 소멸.

규모(상품 수백~수천 건)를 보면 **(b)를 권합니다.** (a)는 "MMR을 안다"를 보여주지만 (b)는 "필요한 만큼만 푼다"를 보여줍니다.

**⑥ 포트폴리오 관점**

지금은 *"Greedy Max-Min을 구현했습니다"* 인데, 면접관이 *"관련도는 어디서 반영되죠?"* 라고 물으면 답이 없습니다. **"다양화를 넣었다가 관련도 항이 없다는 걸 발견하고, 카테고리 상한으로 대체해 코드 200줄과 후보 배수를 없앴다"** 가 훨씬 강합니다.

---

### O4. 키워드 기반 감정 분류기 + 임계값 3종

**① 지금**

리뷰 본문 정규화 → 긍정/부정 키워드 `contains` 매칭 개수 → 전환어 유무 → 별점 기반 base와 조합해 4단계 규칙으로 POSITIVE/NEGATIVE/MIXED 결정.

**② 왜 과도한가 — 그리고 실제로 틀립니다**

`contains` 부분 문자열 매칭이라 **긍정 키워드가 부정 키워드를 포함하는 쌍이 설정 파일에 실재합니다**:

| 긍정 키워드 | 포함하는 부정 키워드 |
|---|---|
| `냄새없` | `냄새` |
| `비린내없` | `비린` |
| `파손없` | `파손` |

"냄새없어요"는 긍정 1 + 부정 1로 동시 집계됩니다. 지금은 `tolerance: 2` 덕분에 결과가 살아나지만, **버그가 아니라 우연히 안 터지는 상태**입니다. 여기에 부정 목록의 `늦`·`부족`·`개선`·`문의`·`응대` 같은 단어는 "배송이 **늦**지 않았어요", "**부족**함이 없어요"를 부정으로 집계합니다.

임계값 `tolerance=2`, `strongDiff=3`, `mixedMin=4`는 어떤 라벨링 데이터로도 검증된 적이 없습니다. 정확도를 측정할 방법 자체가 코드에 없습니다.

그리고 **`MIXED`는 계산·저장되지만 소비처가 없습니다** — `BestReviewListService`는 POSITIVE/NEGATIVE만 처리합니다.

**③ 실제 요구사항**

상품 상세에 "긍정 요약 / 부정 요약" 두 줄 보여주기.

**④ 제거하면**

별점 3점 리뷰의 텍스트 뉘앙스를 못 잡습니다.

**⑤ 대안**

- **(a) 별점만**: 4~5 POSITIVE, 1~2 NEGATIVE, 3 제외. 코드 204줄 + 설정 110줄 → **0줄**. 요약 대상은 어차피 상위 10건이라 3점 리뷰가 빠져도 결과가 거의 같습니다.
- **(b) LLM 1회**: 리뷰 저장 시점에 Gemini로 분류. 이미 `ChatClient`가 있고 `responseSchema` 패턴도 있습니다. 비용은 리뷰당 1회(수십 토큰).

**(a)를 권합니다.** "단순한 구현이 더 좋은 경우"에 해당합니다.

**⑥ 포트폴리오 관점**

키워드 사전은 "규칙 기반 NLP를 짰다"로 보이길 기대하지만, 실제로는 **검증 불가능한 휴리스틱 + 실재하는 매칭 버그**로 읽힙니다. 감점 요소입니다.

---

### O5. `BestReviewReplacementPolicy` + `CANDIDATE_SIZE=10` 이중 용도

**① 지금**

별도 `@Component`가 "기존 베스트 리뷰 중 50% 이상이 바뀌면 교체"를 판정합니다. 그리고 `CANDIDATE_SIZE=10`이 (a) 상위 몇 건을 뽑을지 (b) **최소 몇 건이 있어야 베스트 리뷰를 만들지** 두 의미로 동시에 쓰입니다.

**② 왜 과도한가**

- 교체 임계값 0.5의 목적은 "안 바뀌었으면 LLM 요약을 다시 안 부른다"는 **비용 통제**입니다. 이건 정당한 의도인데, 그 의도가 코드 어디에도 안 적혀 있고 임계값도 임의입니다.
- `count < CANDIDATE_SIZE`면 조기 반환 → **긍정 리뷰가 9개인 상품은 요약을 영원히 못 받습니다.** 신상품 대부분이 여기 걸립니다. 근거 없는 하드 컷오프입니다.
- 상수 하나가 두 개념을 겸하면 한쪽만 바꿀 수 없습니다.

**③ 실제 요구사항**

"리뷰가 안 바뀌었는데 LLM을 또 부르지 말자."

**⑤ 대안**

`updatedAt` + 마지막 요약 이후 신규 리뷰 수로 판단(예: 신규 3건 이상 또는 7일 경과). 클래스 삭제, 컷오프는 `MIN_REVIEWS_FOR_SUMMARY = 3` 정도로 분리.

**⑥ 포트폴리오 관점**

"LLM 호출 비용을 억제하려고 변경률 기반 갱신 게이트를 뒀다"는 좋은 소재인데, **지금은 그 의도가 코드에 없어서 그냥 이상한 정책 객체로 보입니다.**

---

### O6. `cart_event_logs` / `order_event_logs` 이중 인덱스 + "유형별 5건"

**② 왜 과도한가**

두 문서 클래스의 필드가 완전히 동일하고, 레포지토리 메서드 시그니처도 동일하며, 읽는 쪽은 `Stream.concat`으로 즉시 합칩니다. 분리로 얻는 것이 없습니다.

게다가 **"유형별 최근 5건"이 최근성을 깨뜨립니다.** 장바구니에 오늘 20개를 담고 주문은 25일 전 1건인 사용자는, 오늘 담은 것 5건 + 25일 전 주문 1건으로 프로필이 만들어집니다. 25일 전 주문이 오늘 담은 15개보다 우선합니다. 단일 인덱스에서 `occurredAt desc limit 10`이면 이 왜곡이 사라집니다.

**⑤ 대안**

`user_behavior_logs` 단일 인덱스 + `eventType`(이미 있음)만으로 충분. 레포지토리 1개, 질의 1회(왕복 절반), `UserProfileEmbeddingService`의 `Stream.concat`·이중 `toWeightedProduct` 오버로드 삭제.

---

## 5. Unnecessary Business Logic

| # | 무엇 | 왜 불필요 / 근거 없음 | 권장 |
|---|---|---|---|
| B1 | ~~**"부족한 핵심 재료가 최소 1개는 반드시 있어야 한다"** (프롬프트 규칙 3)~~ **철회(2026-09-09 측정)** — 규칙을 빼면 32.5%가 살 것 없는 추천이 되고, 보유 재료 사용률은 오히려 떨어졌다(65.8%→62.9%). 판매 유도인 것은 맞지만 품질을 대가로 치른다는 아래 추측은 데이터로 뒷받침되지 않았다. 근거: [`004`](004-measured-open-questions.md) P2-11. 아래는 철회 전 서술이다 | 판매 유도를 위해 **요리 선택 자체를 왜곡**합니다. "보유 재료만으로 완성되는 요리면 다른 요리를 골라라"는 지시입니다. 사용자에게 최적인 레시피가 아니라 상품을 팔 수 있는 레시피를 추천합니다. 이 규칙의 효과(전환율)는 측정된 적이 없습니다 | 규칙 완화 후 A/B. 부족 재료가 없으면 그냥 없는 대로 응답 |
| B2 | `exclude-recipe-category-codes` 필터 경로 전체 | 목록이 **비어 있어 항상 통과**합니다. `isRecipeEligible`, `RecommendProperties`, `recipe-recommend.yml`, `CartItemInfo.productCategoryCode`, `RecipeRecommendReason.NO_ELIGIBLE_ITEMS`가 전부 이 죽은 분기를 위해 존재합니다 | "나중에 필요할 수도"는 YAGNI. 삭제하고 필요해지면 되살리기 |
| B3 | `Sentiment.MIXED` | 계산·저장되지만 읽는 코드 없음 | 별점 기준으로 단순화하며 함께 삭제 |
| B4 | `product_best_review_index` 전체 | **저장소 어디에서도 읽지 않습니다.** baro-shopping은 Kafka로 오는 요약만 받습니다 | 소비처 확인 → 없으면 `BestReviewListDocument`·`Repository`·`ReplacementPolicy` 삭제, 요약만 남김 |
| B5 | `ReviewService.deleteReview`의 `findById` | 삭제 전 조회해서 반환하는데 **호출자(`ReviewConsumer`)가 반환값을 버립니다.** 순수 낭비 ES 왕복 | `deleteById`만 |
| B6 | `saveReview` / `updateReview` | 본문이 **바이트 단위로 동일** | 하나로 통합 |
| B7 | ~~제외 목록이 영구적이다~~ **철회(2026-09-09)** — 코드를 다시 읽으니 영구가 아니라 최근 10건·30일 창이었다. "산 걸 영영 다시 추천 안 함"이라는 전제가 사실이 아니었으므로 지적 자체가 성립하지 않는다 | 근거: [`004`](004-measured-open-questions.md) P2-10 | — |
| B8 | `RecipeRecommendReason` 6종 | `NO_ELIGIBLE_ITEMS`는 B2로 사실상 도달 불가, `INGREDIENTS_NOT_INDEXED`와 `GENERATION_FAILED`를 클라이언트가 다르게 다룰 이유 없음 | 3종(OK / EMPTY_CART / UNAVAILABLE)으로 축소 |
| B9 | 리뷰 10건 미만 컷오프 | 신상품 전부가 요약을 못 받음 | 3건 등으로 낮추고 상수 분리 |
| B10 | `NegativeReviewProperties` + `review-negative-keywords.yml`(90줄) | **읽는 코드가 없습니다.** `@EnableConfigurationProperties`로 빈만 등록됨 | 삭제 (`NegativeReviewPropertiesConfig` 포함) |

---

> 이어서 [**2부 — 부족한 백엔드 설계 판단**](002-missing-backend-decisions.md).
