---
paths:
  - "**/*Test.java"
  - "src/test/**"
---

# Testing

## 실행

```bash
./gradlew test          # 전체
./gradlew test --tests {루트패키지}.auth.service.FooServiceTest
```

## 프로필

- 테스트는 `application-test.yml` — `ddl-auto: create-drop`
- `@SpringBootTest`(DB 붙는 통합 테스트)는 `@ActiveProfiles("test")` + `@Import(TestcontainersConfig.class)`로 실제 MySQL 8 컨테이너(Testcontainers `@ServiceConnection`)를 붙인다 — 로컬 Docker 필요, CI(GitHub Actions ubuntu-latest)는 기본 내장 Docker로 별도 설정 없이 동작
- MySQL·운영 DB에 테스트가 붙지 않도록 `@DataJpaTest` 등은 test 프로필 확인

## 구조

- `src/test/java/` — main과 동일 패키지 경로
- 단위: service·유틸 — mock repository, 트랜잭션 없음
- 슬라이스: `@WebMvcTest`, `@DataJpaTest` — 필요한 것만 로드
- 통합: `@SpringBootTest` — API·전체 컨텍스트 (최소화)

## 네이밍·패턴

```java
@Test
void createTrip_whenInvalidDates_throwsBadRequest() { ... }
```

- `given_when_then` 또는 `method_condition_expected` 스타일
- BR-* 규칙이 있으면 테스트 메서드명·`@DisplayName`에 BR 번호 참조 가능

## 완료 기준

- 새 service public 메서드 → 단위 테스트 1개 이상 권장
- 새 REST endpoint → `@WebMvcTest` 또는 MockMvc 통합 테스트
- `./gradlew test` 통과 후 PR/완료 보고
