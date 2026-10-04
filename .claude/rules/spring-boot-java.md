---
paths:
  - "**/*.java"
---

# Spring Boot / Java

OpenAPI 어노테이션(`@Schema`·`@Operation`·`@Parameter`·`@ApiResponses`) 규칙은 [`openapi-conventions.md`](openapi-conventions.md), 주석(`//`·Javadoc) 작성 스타일은 [`java-comments.md`](java-comments.md) — 이 파일은 패키지 구조·레이어·Entity·Enum·표기규칙·네이밍 원칙을 다룬다. SOLID/OOP·ACID·스타일·테스트는 [`spring-boot-java-design.md`](spring-boot-java-design.md).

## Package Layout (Domain-Driven Layered Architecture)

최상위는 **도메인 단위**(`auth`, `trip`, `user`, `notification`, `common`)로 분리하고, 각 도메인 내부는 **계층형 레이어**(`controller/dto/service/domain/repository`, 필요 시 `client/exception/config`)를 일관되게 유지한다. **전체 패키지 트리 SSOT는 [`docs/architecture.md`](../../baro-ai/docs/architecture.md)** — 여기서 중복 유지하지 않는다.

가이드: `docs/decisions/003-architecture-guide.md`.
**풀 DDD 미적용** — JPA 연관관계·객체 그래프 탐색 허용.

**feature 하위 패키지:** 도메인 안 기능이 커지면 `{domain}/{feature}/`에 **동일 레이어 세트**를 둘 수 있다 (예: `user/schedule/`, `user/googlecalendar/`, `trip/membership/`, `trip/recommendation/`, `trip/schedule/`). 최상위 도메인으로 승격하지 않는 한 소유 도메인 안에 둔다. `controller/dto/`처럼 **레이어만 중첩**하는 것은 금지. 여러 feature가 공유하는 코드(`TripServiceSupport` 등)나 크로스 도메인 조회 포트(`{domain}/port/out/`)는 도메인 루트에 둔다 — 상세: `docs/specs/trip/package-structure-refactor.md`(Implemented), `docs/decisions/003-architecture-guide.md` 결정 11·12.

**자동 검증(ArchUnit):** 아래 규칙 중 일부는 prose가 아니라 `src/test/java/**/architecture/ArchitectureTest.java`가 `./gradlew test`마다 실제로 검증한다 — domain이 controller/service에 의존하지 않음, controller가 repository에 직접 의존하지 않음, repository는 인터페이스만, `@Autowired` 필드 주입 금지(생성자 주입만), `@RestController`에 `@Transactional` 금지, `@Id` 필드는 UUID 타입, `*ErrorCode`는 `ErrorCode` 구현. 새 아키텍처 규칙을 추가할 때 이 테스트에도 반영을 검토할 것.

## 레이어 (최소 규칙)

- **controller**: HTTP·DTO만. `@Transactional`·비즈니스 로직 금지.
- **dto**: API 요청·응답 record/class. `controller/dto/` 중첩 금지.
- **service**: 유스케이스 조율, `@Transactional` (읽기만이면 `readOnly = true`).
- **domain**: JPA Entity·enum·도메인 값 타입.
- **repository**: `JpaRepository`만. Entity는 `domain/`에 둔다.
- **client**: 외부 OAuth·HTTP 연동, 토큰 검증 adapter. service에서 호출.
- 예외 → `GlobalExceptionHandler`가 `ErrorCode` 인터페이스로 envelope 변환. 공통은 `CommonErrorCode`, 도메인별은 `{domain}/exception/{Domain}ErrorCode`, feature별은 `{domain}/{feature}/exception/{Feature}ErrorCode` (예: `ScheduleErrorCode`).
- **Support 헬퍼 재사용:** `{Domain}ServiceSupport`에 이미 있는 조회·검증(`requireActiveTrip`·`requireMembership`·`requireOwner` 등)을 다른 Service 메서드에서 `repository.findBy...().orElseThrow(...)`로 **인라인 재구현하지 않는다** — 같은 예외·조건이면 Support 메서드를 호출. 새 조회가 필요하면 Support에 메서드를 추가하고 호출부를 그쪽으로 통일.
- **User 조회 SSOT:** `userId`로 `User`를 로드하고 없으면 `AUTH_FORBIDDEN`을 던지는 로직은 `user/service/UserLookupService.requireUser(userId)`가 SSOT다. `auth`·`user`·`user/schedule`·`user/googlecalendar` 등 다른 도메인 Service에서 `userRepository.findById(userId).orElseThrow(...)`를 각자 private 메서드로 재구현하지 않는다.

### ErrorCode enum

`*ErrorCode` 상수는 **한 상수 = 한 줄** (`NAME(HttpStatus, "CODE", "메시지"),`). 여러 상수를 한 줄에 이어서 쓰지 않고, 생성자 인자도 같은 줄에 둔다. **enum·각 상수에 `@Schema(description)` 필수** (`CommonErrorCode` 기준).

```java
// ✅
@Schema(description = "인증 도메인 에러 코드")
public enum AuthErrorCode implements ErrorCode {
  @Schema(description = "소셜 토큰·액세스 JWT 무효")
  AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 소셜 로그인 토큰입니다."),
  @Schema(description = "액세스 JWT 만료")
  AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED", "액세스 토큰이 만료되었습니다.");
  // ...
}

// ❌ 상수·인자를 줄바꿈해 이어서 붙이기 · @Schema 누락
AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN",
    "…"), AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED",
        "…");
```

Spotless(Eclipse): `alignment_for_enum_constants=48`, enum 상수 인자는 wrap 안 함 (`config/{프로젝트}-java-format.xml`).

**새 실패 분기 추가 시 (같은 턴):** enum 상수 → Service/Interceptor throw → 스펙 에러 표 → (필요 시) `api-response.md` 예시. Harness: `.claude/rules/core-guardrails.md` ⛔ ErrorCode 절.

### 교차관심사 (AOP · Interceptor)

| 메커니즘 | 어노테이션 | 역할 |
|----------|------------|------|
| Spring AOP | `@TripActivity` | L1 성공 후 `last_activity_at` touch (`TripActivityAspect`) |
| Interceptor | `@TripMemberOnly` / `@TripOwnerOnly` | 멤버·방장·ACTIVE (`TripAuthorizationInterceptor`) |
| ArgumentResolver | `@AuthorizedUser` | JWT → `UUID userId` |

- L1 touch 대상 public 메서드에 `@TripActivity` — create는 엔티티 초기값. 수동 `touchLastActivity()` 호출 금지.
- 방 입장 완료(`activate`)가 touch — `join`은 초대 링크를 연 시점이라 touch하지 않는다 (`TripCommandService` 주석).
- Draft(#13) 추천 API는 stub에 `@TripActivity`만 두고, ErrorCode는 **구현 착수 시** 추가.

## Entity Conventions

- 공통 베이스: `common/domain/BaseTimeEntity`, `SoftDeleteEntity`
- 비즈니스 Entity: `{domain}/domain/`
- Enum: `{domain}/domain/`, `@Enumerated(EnumType.STRING)`
- JPA `@ManyToOne` 등 연관관계 사용 가능 — 기본 `LAZY`
- 테이블·컬럼: snake_case. 예약어 컬럼은 `@Column(name = "...")` 명시 (`rank` → `recommendation_rank`). 테이블명: **`users`**(구 `user` — MySQL 예약어 회피, Java 엔티티는 `User`)
- `globally_quoted_identifiers: true` **사용 금지** (TEXT quoting 등과 조합 시 DDL 실패 유발). 스키마 drift 원인은 보통 **단일 설정이 아니라** TEXT quoting + 예약어 + dialect + naming strategy **조합** — Docker/배포 설정은 `deployment.md` SSOT
- **PK / FK:** 모든 테이블 PK·FK는 **UUID v4**. Java `java.util.UUID`, DB `CHAR(36)`. `@GeneratedValue` + `@UuidGenerator` + `@JdbcTypeCode(SqlTypes.CHAR)` (`length = 36`). **`Long` / `IDENTITY` / `bigint` PK 금지.** SSOT: `docs/architecture/erd.md`, `docs/specs/cross-cutting/uuid-primary-key.md`
- **필드 설명:** Entity·enum·베이스 클래스의 **클래스·필드·enum 상수**마다 `@Schema(description = "...")` (springdoc). nullable·example·requiredMode는 ERD·스펙과 맞출 것. 상세: `openapi-conventions-operation.md` **OpenAPI @Schema** 절
- **캡슐화(상태 전이):** 클래스 레벨 `@Setter` 금지, 상태 전이는 도메인 메서드로 한다 — 상세: 아래 **SOLID / OOP 원칙** 절

```java
// ✅ PK
@Id
@GeneratedValue
@UuidGenerator
@JdbcTypeCode(SqlTypes.CHAR)
@Column(length = 36, nullable = false, updatable = false)
private UUID id;

// ✅ Controller는 DTO만 반환
return TripResponse.from(trip);

// ❌ Entity 직접 반환 금지
// ❌ @GeneratedValue(strategy = GenerationType.IDENTITY) / Long id
```

## Enum

Entity·DTO·도메인 값에 **닫힌 집합**(상태·모드·제공자·기간 등)이 생기면 `String` magic value 대신 **`{domain}/domain/`(또는 feature `domain/`) Java enum**으로 분리한다.

| 분리 ✓ | 분리하지 않음 |
|--------|----------------|
| API/DB에 노출되는 고정 상수 (`POSSIBLE`, `BASIC`, `GOOGLE`) | 자유 입력 문구, 표시명 |
| 분기·검증·동등 비교가 필요한 코드 | 콤마 목록 등 **계약이 String인** 필드 (`daysOfWeek`) |
| 새 상수 추가가 스펙·마이그레이션으로 관리되는 값 | 외부 시스템 opaque 토큰·임의 ID |

**프론트에 enum을 전하는 SSOT (enum 전용 md 파일은 만들지 않음)**

- **계약(값 목록):** OpenAPI `/v3/api-docs` · Swagger UI — enum + `@Schema`가 스키마에 노출됨
- **의미·정책:** 해당 `docs/specs/`, 필요 시 `docs/product/glossary.md`(한글 UI 라벨)
- ❌ `docs/enums/*.md` 또는 enum마다 md — 코드·스펙과 **이중 관리·드리프트** 유발. 만들지 않음

```java
@Schema(description = "연차 신청 가능 시점")
public enum VacationApplyPeriod {
  @Schema(description = "상관없음")
  ANY,
  // ...
}
```

## 표기 규칙 (casing)

| 계층 | 규칙 | 예 |
|------|------|-----|
| DB·JPA | snake_case, 단수 테이블 | `trip`, `trip_member` |
| Java | PascalCase / camelCase | `Trip`, `tripMember` |
| API path | kebab-case 또는 snake | `/api/v1/trip-rooms` |
| UI 한글 | `docs/product/glossary.md` 기준 | "여행방" ≠ 임의 영문 |

## 네이밍 우선 원칙 (프론트 · 신규 개발자 가독성)

`@Schema`·`@Operation`·`//`로 "왜 이런 이름인지"부터 설명해야 한다면, 설명을 늘리기 전에 **이름 자체를 바꿀 수 있는지 먼저 검토한다.** 좋은 이름은 설명 없이도 오해를 안 만든다 — 문서는 이름이 못 담는 맥락(언제·왜·전제)만 보강하는 역할이다.

- 새 enum 상수·필드명을 짓거나 리뷰할 때: "이 이름만 보고 신규 개발자·프론트가 오해하지 않을까?"를 먼저 묻는다. 오해 소지가 있으면 `@Schema`로 땜질하지 말고 **이름을 먼저 교체**한다.
- **같은 개념 = 같은 필드명.** 같은 enum을 가리키는데 DTO마다 `status`/`memberStatus`/`myMemberStatus`처럼 이름이 흩어지면 안 된다 — "내 것" vs "타인 것" 구분만 접두사(`my`)로 통일하고 나머지는 동일한 이름을 쓴다.
- 이름을 바꾸면 **같은 턴에** 전부 최신화한다: enum·DTO·테스트 · `docs/specs/` · `docs/architecture/erd.md` · `docs/product/glossary.md`. 한 곳이라도 구 이름이 남으면 "구 이름 방치"로 `core-guardrails.md` STOP §4(레거시)와 동일하게 취급한다.
- `@Schema`/`@Operation` 설명이 **3문단 넘게** 길어지거나 값별로 "의미"를 장황하게 반복해야 한다면, 우선 이름부터 다시 의심할 것 — 설명으로 이름의 결함을 메우지 않는다.
- 예: `TripMemberStatus`의 구 `JOINED`→`SCHEDULE_PENDING`, 구 `RESPONDED`→`ACTIVE` 개명 — "방에 참여했다"로 오독되던 이름을 "일정 확인 대기중 / 방 활동 가능"으로 이름만으로 뜻이 드러나게 바꾼 사례 (`docs/specs/trip/trip-member-status-derive.md` 변경 이력).

## 설계 원칙 · 스타일 · 테스트

SOLID/OOP 원칙(SRP·OCP/ISP·캡슐화)·ACID/트랜잭션 경계·Style(Lombok·DTO record·Controller 파라미터)·API 추가 시 규칙·Configuration·Tests → [`spring-boot-java-design.md`](spring-boot-java-design.md) 참고
