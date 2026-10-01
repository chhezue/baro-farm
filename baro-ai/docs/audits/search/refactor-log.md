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
