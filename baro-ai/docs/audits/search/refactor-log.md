# Search Refactor Log

`search` 도메인에서 근거 기반 성능 리팩토링([`baro-ai-performance-refactor.md`](../../../../.claude/rules/baro-ai-performance-refactor.md)) 이니셔티브로 실제 반영한 변경 이력이다. 무엇을 고쳤고 어떻게 검증했는지 날짜순으로 남긴다.

## 2026-09-04 — 자동완성 캐시 활성화

**반영 항목**: `docs/specs/search/product-search-and-autocomplete.md` 리스크 항목 — `ProductIndexService.autocomplete()`의 `@Cacheable`이 `@EnableCaching` 부재로 죽어있던 것.

**근거** (`baro-ai-performance-refactor.md` 기준):
- 현재 동작의 증거: `@EnableCaching` 부재를 grep으로 확인, Spring Boot 3.5 공식 문서 + 로컬 jar 바이트코드(`CacheAutoConfiguration`, `SimpleCacheConfiguration`)로 "`@EnableCaching` 없으면 캐시 인프라 자체가 조건 불충족으로 비활성화됨"을 교차 확인
- Spring Boot 기본 폴백(`ConcurrentMapCacheManager`)은 TTL·최대 크기 제한이 없어(공식 문서 명시) 그대로 켜면 무제한 증가 — 그래서 기본값 대신 Caffeine을 명시 채택

**변경 파일**:
- `src/main/java/com/barofarm/ai/config/CacheConfig.java` (신규) — `@EnableCaching` + Caffeine `CacheManager`(최대 1,000개, TTL 5분)
- `src/main/java/com/barofarm/ai/search/application/ProductIndexService.java` — stale `// TODO Redis 캐시 추가` 주석 제거, 실제 근거를 가리키는 주석으로 교체
- `build.gradle` — `com.github.ben-manes.caffeine:caffeine` 추가
- `docs/decisions/001-autocomplete-caching.md` (신규) — 캐싱 범위 결정(자동완성만, 개인화/추천 결과는 안 함) 근거 기록. 기존 `docs/CACHE_STRATEGY.md`(개인화·추천까지 포괄한 제안 문서) 대체 — 삭제

**검증 방법**: 자동화 테스트가 없는 도메인이라(`baro-ai` 전체 테스트 0개) `{{테스트 명령}}`은 컴파일 통과만 확인한다. 캐시 hit/miss를 검증하는 단위 테스트는 아직 없음 — 후속 과제로 남김.

**남긴 것**: 개인화·유사상품·레시피 추천 결과 캐싱은 `001-autocomplete-caching.md`에서 근거를 들어 Never로 판단 — 트래픽이 실제로 몰리면 그때 재검토.

## 2026-09-04 — 색인 경로: 컨슈머 재시도·poll 건수, 정규식 폴백 제거

**반영 항목**: [`001-index-path.md`](001-index-path.md) A-1(Kafka product 컨슈머)·B-1(죽은 catch)·B-2(정규식 폴백 제거). 폴백 출처 필드 추가안은 사용자 결정으로 폐기하고 "LLM만 + 실패 시 빈 값"으로 바꿨다.

### 왜 문제였나 (쉬운 말로)

**컨슈머.** 상품이 등록되면 Kafka 메시지가 와서 검색 인덱스에 저장됩니다. 저장이 실패하면 코드 주석은 "Kafka가 다시 시도하도록 예외를 던진다"고 적혀 있었지만, 실제 설정은 "재시도 없이 바로 건너뛰기"였습니다. Elasticsearch가 잠깐 죽어 있는 사이에 온 상품은 검색·추천에서 영구히 빠졌습니다. 또 한 번에 가져오는 메시지 수(기본 500건)를 손대지 않았는데, 상품 1건마다 외부 호출이 2번(각 최대 30초) 있어 여러 건이 느려지면 Kafka의 처리 제한(5분)을 넘겨 같은 묶음을 계속 다시 받는 반복에 빠질 수 있었습니다. 지금은 상품 등록이 드물어 안 보이지만, 나중에 "전체 다시 색인" 기능을 만들면 바로 터지는 조건입니다.

**정규식 폴백.** Gemini가 재료명을 못 뽑으면 정규식으로 브랜드·단위를 지우고 **남은 글자**를 그대로 저장했습니다. "곰곰 무항생제 신선한 대란, 30구, 1개"에서 "대란"이 나오는 식인데, 이건 재료명처럼 보이지만 레시피 추천이 쓰는 "계란"과 달라 매칭에서 빠집니다. 더 나쁜 건 이 값이 정상 값처럼 저장돼 나중에 골라낼 수 없다는 점입니다. 반면 추천 코드는 이미 "재료명이 비어 있으면 건너뛰고 재색인 필요라고 세는" 처리를 갖고 있어, 실패를 빈 값으로 두는 편이 더 정직합니다. 상품명이 판매자 자유 입력(제약은 "비어 있으면 안 됨"뿐)이라 규칙으로 뽑는 방식은 근본 대안이 못 됩니다.

### 무엇을 바꿨나

- product 컨슈머는 한 번에 **1건**만 받고, 실패하면 1초 간격으로 **2번 더** 시도한 뒤 로그를 남기고 건너뜁니다. 주석을 실제 동작에 맞췄고, 역직렬화에 실패해 내용이 없는 메시지는 재시도 없이 바로 건너뛰는 방어를 넣었습니다.
- 정규식 폴백을 삭제했습니다. Gemini가 실패하거나 빈 답을 주면 재료명을 빈 값으로 저장하고 "재색인 대상" 경고를 남깁니다. 실패 빈도 자체는 [`cross-cutting/refactor-log.md`](../cross-cutting/refactor-log.md)의 재시도로 낮췄습니다.
- 그에 맞춰 죽은 예외 처리 블록을 지우고, 결정문 002·ES 아키텍처 문서·IMPLEMENTATION.md의 "정규식 폴백" 서술을 고쳤습니다.

> 아래 표는 개발자 참조용이라 코드 식별자를 그대로 쓴다. 쉬운 설명은 바로 위 절에 있다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| product 컨슈머 재시도 | `FixedBackOff(0, 0)` — 0회 | `FixedBackOff(1000, 2)` — 1초 간격 2회 |
| 한 번의 poll 건수 | Kafka 기본 500건 | `max.poll.records = 1` |
| 최악 처리 시간 / poll | 500 × 60초 (5분 제한 초과) | 3 × 60초 + 2초 (제한 안) |
| 리스너 `throw e` 주석 | "Kafka가 재시도하도록" (동작과 반대) | 재시도 정책은 컨테이너 팩토리가 정한다고 명시 |
| 내용 없는 이벤트(역직렬화 실패) | `event.getData()`에서 NPE 가능 | WARN 후 건너뜀 |
| Gemini 실패 시 `ingredientName` | 정규식 결과(`"대란"` 등) | `""` + "재색인 대상" WARN |
| `ProductIndexService`의 재료명 `try/catch` | 도달 불가 코드 5줄 | 삭제 |
| ES 매핑 · API 응답 계약 | — | **변경 없음** |

### 변경 파일

- `config/KafkaConsumerConfig.java` — product 팩토리에 `MAX_POLL_RECORDS`, 에러 핸들러 백오프, 클래스 주석의 TODO를 DLQ 결론(D-1)으로 교체
- `search/infrastructure/event/ProductEventConsumer.java` — null 이벤트 방어, `throw e` 주석 정정
- `search/infrastructure/llm/ProductIngredientNormalizer.java` — `applyBasicNormalization` 삭제, 실패 시 `""`, Javadoc 갱신
- `search/application/ProductIndexService.java` — 죽은 `try/catch` 삭제, 주석 정정
- `docs/decisions/002-ingredient-name-at-index-time.md` — amend(폴백 제거 근거를 "고려한 대안"에 추가, 검증 3번 교체)
- `docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` · `docs/IMPLEMENTATION.md` — 폴백 서술 교체

### 검증

- `./gradlew :baro-ai:compileJava :baro-ai:spotlessApply :baro-ai:checkstyleMain` 통과
- 자동화 테스트 없음(사용자 결정). 수동 절차는 로컬 Kafka·ES 기동이 필요해 **이 세션에서는 실행하지 못했다**:
  1. 컨슈머: Kafka만 띄우고 ES를 내린 채 상품 이벤트 1건 발행 → 같은 레코드 실패 로그가 3번 찍힌 뒤 건너뛰는지, 기동 로그 `ConsumerConfig values`에 `max.poll.records = 1`인지
  2. 폴백 제거: 잘못된 Gemini 키로 기동 → 상품 이벤트 1건 → `curl localhost:9200/product/_doc/{productId}`에서 `ingredientName`이 `""`이고 로그에 "재색인 대상" WARN

### 남긴 것 · 알려진 제약

- **컨슈머 값(1건·2회·1초)은 잠정치** — 이벤트 유입량 추정치가 없어 "재시도 포함 최악 시간이 5분 제한 안에 드는" 계산으로만 정했다. 커밋 전 사용자 확인 대상
- **빈 값으로 남은 상품을 다시 계산하는 경로가 없다** — 폴백을 없앤 만큼 이 경로의 필요가 커졌다. 새 기능이라 `specify`로 스펙을 쓴 뒤 진행한다([`001-index-path.md`](001-index-path.md) 개선 제안 1)
- 검색·자동완성 조회 경로는 이번 범위 밖 — 다음 search 라운드
