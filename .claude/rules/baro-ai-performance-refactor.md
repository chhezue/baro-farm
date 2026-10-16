# baro-ai — 현재 이니셔티브: 근거 기반 성능 리팩토링 (2026-09-04~)

이 저장소(`baro-ai`) 고유의 **지금 진행 중인 작업 방향**을 담는 always-load 규칙이다. `harness-map.md`(슬롯)·스택 옵션과 달리 이 파일은 **한시적**이다 — 이니셔티브가 끝나거나 방향이 바뀌면 이 파일을 갱신하거나 지운다(`core-workflow.md` G4 "문서 갱신 점검"에서 매번 재확인).

## 무엇을 하는 중인가

**기능 추가가 아니라 전면 리팩토링이다.** 다만 `safe-refactor`가 원래 상정하는 "도메인 1개씩 좁게" 감사가 아니라, **아래 범위 전체**를 대상으로 성능·전문성 위주 개선을 진행한다. 캐싱, 프롬프트 튜닝(LLM 호출 비용·지연·품질), 비동기 처리 같은 **백엔드 성능·설계 품질** 축이 중심이다.

**목표는 성능만이 아니라 "백엔드 로직의 전문성"이다** — 더 견고하고 정확한 설계로 바꾸는 것도 이 이니셔티브에 포함된다. 단, **오버엔지니어링은 금지** — 주니어 수준보다는 위여야 하지만, 이 프로젝트 규모에 안 맞는 과도한 추상화·패턴 남용은 `safe-refactor`의 YAGNI 원칙 위반이다. "시니어스럽게 보이려고" 넣는 복잡도는 오히려 감점 요인.

**트랙: 전부 B(감사·리팩터)다.** `core-workflow.md`의 Track A(기능·`specify`)가 아니라 Track B(`safe-refactor`)를 쓴다. 단 `safe-refactor`의 절대 원칙 중 **API Contract 동결 원칙은 baro-ai에서 해제**한다 — 아래 "API 계약 변경 방침" 참고. 비즈니스 로직·정책 불변, 성능 악화 금지, YAGNI는 그대로 적용된다.

## API 계약 변경 방침 (2026-09-04 사용자 결정 — safe-refactor 원 원칙에서 벗어남)

이 프로젝트는 **아직 릴리즈되지 않았고 실제 서버도 내려간 상태**라 하위 호환을 지킬 대상(기존 클라이언트·트래픽)이 없다. 그래서 API 계약(요청/응답 필드, 엔드포인트, ErrorCode)이 **더 정확하고 명확해지는 방향이면 바꿔도 된다** — `safe-refactor` 원 스킬의 "API Contract 100% 동일" 원칙을 그대로 따르지 않는다.

- 여전히 지킬 것: **비즈니스 로직·정책은 그대로**(계약 표현 방식이 바뀌는 것과 로직이 바뀌는 것은 다르다), `core-guardrails.md` STOP §2(ErrorCode 동시갱신)는 계약이 바뀔 때도 그대로 적용
- 실제로 다른 모듈(팀원 담당)이나 프론트가 호출하는 엔드포인트를 바꾸면, 막지는 않되 **refactor-log.md에 Before/After를 분명히 남긴다** — 5인 공용 저장소라 나중에 팀원이 놀라지 않게
- OpenAPI 계약 보호 스택 옵션은 이미 꺼져 있으므로(`harness-map.md`) `oasdiff` 검증은 애초에 안 쓴다

### 데이터 마이그레이션 방침 (dev 모드 전제)

같은 이유로 **저장 스키마도 동결 대상이 아니다.** 운영 서버가 내려가 있어 보존할 실데이터가 없으므로, ES 매핑 변경·인덱스 재생성·전체 재색인이 필요한 개선안을 "마이그레이션 비용" 때문에 기각하지 않는다. 임베딩 모델·차원 교체(`ProductDocument.vector`, `UserProfileEmbeddingDocument` 모두 현재 `dims = 1536`)도 여기 포함된다. 단 실행은 사용자 승인 후.

## 범위

**작업 단위는 코드 패키지가 아니라 비즈니스 기능 4개다** (2026-09-04 사용자 결정). 패키지(`search`·`recommend`·`embedding`·`event`·`log`)는 한 기능이 여러 패키지에 걸쳐 있어 감사·문서·포트폴리오 단위로 쓰기 어려웠다. 단위 ↔ 패키지·클래스 매핑과 각 단위의 용어·흐름·규칙은 [`docs/specs/README.md`](../../baro-ai/docs/specs/README.md)가 SSOT다.

| 작업 단위 (폴더명) | 무엇 | 주요 코드 | 상태 |
|--------------------|------|-----------|------|
| 취향 벡터 (`taste-vector`) | 검색·장바구니·주문 행동 로그 → 프로필 벡터 → 개인화 추천 | `event.consumer.*`, `log.*`, `embedding.application.UserProfileEmbeddingService`, `embedding.domain.UserProfileEmbeddingDocument`, `recommend.application.PersonalizedRecommendService`, `config.AsyncConfig` | 대상 |
| 장바구니 레시피 (`cart-recipe`) | 장바구니 → 보유 재료 → LLM 레시피 후보 → 서버 검증·스코어링 → 부족 재료 상품 | `recommend.application.RecipeRecommendService`, `recommend.domain.*`, `recommend.infrastructure.llm.*`, `recommend.infrastructure.client.*`, `search.infrastructure.llm.ProductIngredientNormalizer`(색인 시점 재료명) | 대상 |
| 임베딩 유사도 검색 (`vector-similarity`) | 상품 임베딩 → 벡터 유사도 검색 → 다양화 → 유사 상품 추천 | `embedding.application.ProductEmbeddingService`, `search.application.VectorProductSearchService`, `recommend.application.MedoidDiversityService`, `recommend.application.SimilarProductRecommendService`, `ProductDocument.vector` | 대상 |
| 키워드 검색 (`keyword-search`) | 상품 이벤트 색인 경로 → 키워드 검색 → 자동완성 | `search.infrastructure.event.*`, `search.application.ProductIndexService`·`ProductSearchService`, `search.infrastructure.elasticsearch.*`, `config.ElasticsearchConfig`·`CacheConfig`·`KafkaConsumerConfig`(product 팩토리) | 대상 |
| 공유 인프라 (`cross-cutting`) | `com.barofarm.ai.config`(`KafkaConsumerConfig`·`KafkaProducerConfig`·`LlmClientConfig` 등), `build.gradle`, `application-*.yml` | 대상 — 위 4개 단위에 영향이 있을 때만. **review 전용 설정**(`NegativeReviewPropertiesConfig`·`SentimentPropertiesConfig`)은 제외 | 대상 |
| chat | `com.barofarm.ai.chat` | 스텁만 존재, 목적 미정([`docs/specs/chat/chatbot.md`](../../baro-ai/docs/specs/chat/chatbot.md)). 감사 대상 코드 없음 | 보류 |
| review | `com.barofarm.ai.review` | **범위 밖 — 코드 변경 안 함.** 팀원 소유 도메인 | 제외 |

**review 패키지를 건드리게 되는 상황이 생기면** (예: 공유 이벤트 모델 변경, 공통 config 개편) — STOP하고 사용자에게 먼저 확인한다. 하드 블로킹 훅은 없다 — 기술적 차단이 아니라 `core-guardrails.md` STOP 패턴(멈추고 질문)으로 다룬다.

## 근거 기준 (이 이니셔티브의 핵심 제약)

**"일반적으로 좋다"·"베스트 프랙티스다"·"보통 이렇게 한다"는 근거로 변경하지 않는다.** `audit-checklist.md` 15번 항목이 이미 "최신 기술이라서"류 근거를 금지하고 있었는데, 이 이니셔티브에서는 그보다 더 엄격하게 **적극적 증거**를 요구한다 — 지난 세션에서 실제로 문서가 미구현 Redis 캐시를 사실처럼 서술했던 사고([`docs/specs/keyword-search/product-search-and-autocomplete.md`](../../baro-ai/docs/specs/keyword-search/product-search-and-autocomplete.md) 리스크 항목)가 계기다.

**A(반드시 수정) 항목, 특히 Category가 Performance인 항목은 감사 문서에 다음을 추가로 명시해야 한다** (감사 템플릿 A 항목 포맷의 "근거" 필드 — [`docs/templates/how-to-doc.md`](../../baro-ai/docs/templates/how-to-doc.md)):

1. **현재 동작의 증거** — 코드로 확인 가능한 사실(예: "`@EnableCaching`이 없어 `@Cacheable`이 no-op임을 grep으로 확인"), 또는 재현 가능한 관찰(간단한 타이밍 로그·반복 호출 스크립트 등). "느릴 것 같다"·"비효율적으로 보인다"는 증거가 아니다.
2. **개선 후 검증 방법** — 어떻게 효과를 확인할 것인지(테스트로 캐시 hit 확인, 호출 횟수 감소 확인, LLM 토큰 수 비교 등). 프로덕션 서버가 내려가 있어 실측 APM은 못 쓰므로, **로컬에서 재현 가능한 방법**(단위/통합 테스트, 반복 호출 스크립트, 토큰 카운트 비교)이어야 한다.
3. C/D(안 하는 게 낫다) 판단도 마찬가지로 "왜 지금 필요 없는지"를 추측이 아니라 실제 트래픽·데이터 규모(예: 상품 수, 초당 요청 수 추정치)에 근거해 적는다 — 모르면 `[미정]`으로 남기고 사용자에게 묻는다.

**Best Practice·유사 구현 사례는 위 1·2번 증거를 대체하지 않는다** (2026-09-04 사용자 결정). `researcher`가 조사한 업계 사례·유사 구현이 있어도 그 자체로 A 항목의 근거가 되지는 않는다 — 여전히 코드로 확인 가능한 사실이나 재현 가능한 관찰이 있어야 A로 분류한다. 다만 조사했다면 감사 문서에서 조용히 빠지지 않도록 반드시 별도 절(`researcher` 출력 포맷의 "참고: Best Practice·유사 구현 사례")로 옮겨 적어 사용자가 판단할 때 참고하게 한다 — 근거 없이 묻히는 것과 근거 취급되는 것 둘 다 막는 게 목적이다.

**LLM/프롬프트 튜닝은 `audit-checklist.md` 16번 항목**(이 이니셔티브에서 추가)을 따른다 — 토큰 수·모델 선택·응답 파싱 안정성·재시도·배치화 가능성. **이 항목이 대상인 라운드는 Audit 단계에 `researcher`를 기본으로 포함한다**(Best Practice·유사 구현 사례 조사) — 다른 도메인은 지금처럼 필요할 때만 호출한다. 상세 절차는 `safe-refactor` 스킬 1단계.

## ⛔ fresh-eye 재검사 필수 (2026-09-07 사용자 결정)

**변경 하나가 끝날 때마다 신선한 컨텍스트의 서브에이전트로 재검사한다.** 같은 대화에서 방금 짠 코드를 스스로 리뷰하면 자기 판단을 재확인하는 편향이 생긴다 — `core-tools.md`가 이미 그 이유를 적어 두었지만, 이 이니셔티브에서는 규모 게이트("3파일+·API·DB")를 **적용하지 않고 매 변경마다** 돌린다.

계기: 2026-09-07. 이 규칙이 없던 동안 같은 라운드에서 세 가지가 연달아 새어 나갔다.

1. 감사 `cart-recipe/006`이 **원인 두 개 중 하나만** 보고 처방까지 틀리게 적었다(정규화 정확 일치 — 오히려 어긋나게 만드는 방법). fresh-eye가 나머지 원인과 반론을 잡았다
2. 검색 신호를 지우면서 **필드는 지우고 Javadoc만 남겨** 고아 주석을 만들었다. fresh-eye가 잡았다
3. `features`·`portfolio` 문서에서 한 줄만 고치고 바로 옆 줄을 그대로 둬 **같은 화면 안에서 앞뒤가 모순**됐다. `doc-reviewer`가 잡았다

**적용 규칙**

| 변경 성격 | 누구에게 |
|-----------|----------|
| Java 코드 (파일 수 무관) | `spring-reviewer` |
| 문서 (줄 수 무관 — 신규·수정 모두) | `doc-reviewer` |
| 감사 문서의 A/B 항목을 새로 세울 때 | `Explore` 등 읽기 전용 에이전트에 **결론을 주지 않고** 같은 질문을 다시 던진다 |

**"변경 하나"의 단위:** 커밋 하나로 묶일 응집된 변경이다. 그 변경 안에서 리뷰 지적을 반영하는 후속 수정은 같은 변경의 일부로 보고 매번 다시 돌리지 않는다 — 다만 반영 과정에서 **원래 리뷰 범위 밖으로 나갔다면** 다시 돌린다. 리뷰가 지적한 것을 고치는 대신 다른 방식으로 우회했을 때가 특히 그렇다.

**호출할 때 결론을 미리 주지 않는다.** "이게 문제인 것 같은데 확인해 달라"고 물으면 확인만 하고 돌아온다. 관찰된 증상만 주고 원인·처방은 스스로 세우게 한다 — 위 1번을 잡은 것이 이 방식이었다.

**⛔ fresh-eye 결과도 그대로 믿지 않는다.** 에이전트는 코드로 추론하므로 실측과 어긋날 수 있다. 2026-09-07 재감사가 "갱신 트리거와 ES refresh의 경합으로 자기 이벤트가 계산에서 빠진다"를 A급으로 올렸는데, 로컬에서 두 번(간격 4초·연속 발행) 재보니 **재현되지 않았다**. 근거 기준(이 문서 "근거 기준" 절)은 fresh-eye 출력에도 똑같이 적용된다 — **재현되지 않는 지적으로 코드를 고치지 않는다.**

## 완료 기준 (작업 단위별)

`safe-refactor` 6단계(Audit → 승인 → Implement → Verify → Report → 다음 도메인) 그대로. 단 이 이니셔티브는:

- **OpenAPI 계약 보호가 꺼진 프로젝트라 `oasdiff` 생략** — 대신 `{{테스트 명령}}` 전체 통과 + (테스트가 없는 도메인은) 최소 수동 확인 절차를 감사 문서에 명시
- 작업 단위 4개를 하나의 큰 라운드로 취급하지 않는다 — **구현은 한 번에 한 단위**. 감사(Audit)는 사용자 요청으로 여러 단위를 같은 시점에 진행할 수 있으나, 승인·구현·검증은 단위별로 따로 돈다. 순서(2026-09-04 확정): 취향 벡터 → 임베딩 유사도 검색 → 키워드 검색 → 장바구니 레시피. 단위 테스트 도입·포트폴리오 재작성은 코드 변경이 끝난 뒤로 미룬다(사용자 결정)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 범위(search/recommend/embedding/event-log/chat, review 제외)·근거 기준 확정 |
| 2026-09-04 | 작업 단위를 패키지 5개에서 **비즈니스 기능 4개**(취향 벡터·장바구니 레시피·임베딩 유사도 검색·키워드 검색)로 재정의. 감사·스펙·refactor-log 폴더도 같은 이름으로 통일 |
| 2026-09-07 | fresh-eye 재검사 필수 절 신설 — 매 변경마다, 규모 게이트 없이. fresh-eye 출력도 실측으로 검증한다는 단서 포함 |
