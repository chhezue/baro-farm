---
paths:
  - "**/*Test.java"
  - "**/src/test/**"
---

# Testing

## 실행

```bash
./gradlew :baro-ai:test                                  # 모듈 전체
./gradlew :baro-ai:test --tests '*ProductSearchServiceTest'
```

## 프로필·외부 의존

**baro-ai에는 RDB가 없다.** 검색·추천·리뷰가 전부 Elasticsearch 문서 기반이고, JPA 엔티티도 `application-test.yml`도 없다(쓰이지 않던 파일을 2026-09-05에 삭제했다 — [`audits/open-items.md`](../../baro-ai/docs/audits/open-items.md) A-4). 따라서 `ddl-auto`·MySQL Testcontainers·`@DataJpaTest`는 **이 모듈에 해당하지 않는다.**

- 단위 테스트는 외부 의존이 없다. `MaxMinDiversityService`·`CandidateRecipePlan.from`·`IngredientProcessingUtil`처럼 순수 계산 로직이 우선 대상이다
- **Elasticsearch가 필요한 테스트는 Testcontainers로 실물을 띄운다**(2026-09-08 확정). 이미지는 `baro-ai/docker/local/Dockerfile.es-nori`를 그대로 빌드해 쓴다 — 로컬 확인 환경과 같은 파일이라 둘이 어긋나지 않고, 한국어 형태소 분석기(nori)가 들어 있어야 상품 매핑이 만들어진다. 예시: `ProductIndexAndSearchIntegrationTest`
- Spring 컨텍스트는 띄우지 않는다. 검증 대상이 빈 배선이 아니라 매핑·쿼리라, `ElasticsearchTemplate`을 직접 조립해 서비스에 넣는다. 그래서 `application-test.yml`도 아직 필요 없다
- Kafka·Gemini는 아직 실물로 확인하지 않는다. 필요해지면 그때 방식을 정한다
- 다른 모듈(`baro-order` 등)에 테스트를 붙일 때는 이 절을 그대로 적용하지 말고 그 모듈의 실제 스택을 코드에서 확인한다 — [`core-guardrails.md`](core-guardrails.md) STOP §0 4번

## 구조

- `src/test/java/` — main과 동일 패키지 경로
- 단위: service·유틸 — mock repository, 트랜잭션 없음
- 슬라이스: `@WebMvcTest` — 필요한 것만 로드
- 통합: `@SpringBootTest` — API·전체 컨텍스트 (최소화)

## 네이밍·패턴

```java
@Test
void search_whenSizeExceedsLimit_throwsInvalidPageSize() { ... }
```

- `given_when_then` 또는 `method_condition_expected` 스타일
- BR-* 규칙이 있으면 테스트 메서드명·`@DisplayName`에 BR 번호 참조 가능

## 완료 기준

- 새 service public 메서드 → 단위 테스트 1개 이상 권장
- 새 REST endpoint → `@WebMvcTest` 또는 MockMvc 통합 테스트
- `./gradlew :baro-ai:test` 통과 후 PR/완료 보고
- Elasticsearch 통합 테스트가 들어 있어 **첫 실행은 컨테이너 기동에 1분 남짓 걸린다.** Docker가 없으면 그 클래스만 실패한다 — 나머지 단위 테스트는 그대로 돈다
