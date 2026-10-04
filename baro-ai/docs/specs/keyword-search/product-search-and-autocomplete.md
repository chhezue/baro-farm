# 상품 검색 · 자동완성

> 상태: Implemented
> MVP: In scope
> 관련 BR: N/A

## 목표

키워드로 상품을 빠르고 정확하게 찾고, 입력 중에는 자동완성으로 후보를 보여준다.

## 배경

Elasticsearch 인덱스(`product`, `product-autocomplete`) 기반. 검색은 다른 모듈이 발행하는 `product-events`로 인덱스를 채우고, 검색 자체가 다시 추천의 입력(검색 로그 → 프로필 임베딩)이 되는 순환 구조를 가진다.

## 요구사항

### Must Have (구현 완료)

- [x] 키워드 검색 — `match_phrase`(정확, boost 2.0) + `match`(OR, boost 1.0) + `fuzzy`(3글자 이상만, boost 0.3) 조합
- [x] 카테고리 복수 선택 필터, 가격 범위 필터
- [x] 판매 상태 필터 — `ON_SALE`·`DISCOUNTED`만 노출(고정, 옵션 아님)
- [x] 자동완성 — `edge_ngram` analyzer + `match_phrase_prefix`, 2글자 미만이면 빈 리스트
- [x] `product-events`(PRODUCT_CREATED/UPDATED/DELETED) 구독 → 인덱싱/삭제, 인덱싱 시 상품명 임베딩 동시 생성
- [x] userId + keyword 있는 검색은 검색 로그 저장 + 사용자 프로필 벡터 비동기 재계산 트리거
- [x] 자동완성 로컬 캐시(Caffeine, 최대 1,000개·TTL 5분) — `CacheConfig.java`, 근거: [`docs/decisions/001-autocomplete-caching.md`](../../decisions/001-autocomplete-caching.md)

### Out of Scope (이번 스펙에서 하지 않음)

- 정렬 옵션 다양화 — 현재 `score desc → updatedAt desc` 고정, 가격순 등 없음
- 검색 결과 캐싱 — 아래 리스크 참고

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/api/v1/search` | `X-User-Id`(선택) | `q`·`categories`·`priceMin`·`priceMax`·`page`·`size` |
| GET | `/api/v1/search/autocomplete` | 없음 | `q`(필수, 2글자↑)·`size`(기본 5) |

구현: [`SearchController.java`](../../../src/main/java/com/barofarm/ai/search/presentation/SearchController.java)

## 데이터 모델

- ES 인덱스: `product`(`ProductDocument` — productId·productName·categoryId/Code/Name·price·status·updatedAt·`dense_vector(1536)`), `product-autocomplete`(`ProductAutocompleteDocument`)
- 상세 매핑: [`ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](../../ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)

## 비즈니스 규칙

| 규칙 | 값 | 구현 위치 |
|------|-----|-----------|
| 정확 매칭 boost | 2.0 | `ProductSearchService.applyExactMatch` |
| 일반 매칭 boost | 1.0 | `applyNormalMatch` |
| 오탈자 매칭 boost | 0.3, 3글자 이상만 | `applyFuzzyMatch` |
| 자동완성 최소 길이 | 2글자 | `ProductIndexService.autocomplete` |
| 상품 임베딩 실패 시 | 인덱싱은 계속 진행(벡터만 null) | `ProductIndexService.indexProduct` |

## 검증 시나리오

### 정상

- [x] 키워드+카테고리+가격 조합 검색
- [x] 키워드 없이 필터만으로 검색
- [x] 자동완성 2글자 이상

### 엣지 · 실패

- [x] 자동완성 1글자 → 빈 리스트
- [x] 임베딩 실패해도 상품 인덱싱은 유지
- [x] 검색 로그 저장 실패해도 검색 응답에는 영향 없음(catch 후 warn 로그만)

## 완료 기준

- [x] 기능 구현 및 수동 확인
- [ ] `./gradlew :baro-ai:test` — **자동화 테스트 없음** (아래 리스크 참고)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| ~~`ProductIndexService.autocomplete()`의 `@Cacheable`이 동작하지 않음~~ | **해결 (2026-09-04)** | Caffeine 기반 `CacheConfig` 추가로 실제 캐싱 동작. 근거·트레이드오프: `docs/decisions/001-autocomplete-caching.md` |
| 자동화 테스트 0개 | 확정 | 검색/자동완성 모두 수동 확인만 거침 — 캐시 hit/miss를 검증하는 테스트도 아직 없음 |
| 정렬 커스터마이징 없음 | [미정] | 가격순·최신순 등 요구가 생기면 스펙 amend |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 기존 구현을 코드 기준으로 역작성(retro-spec) |
