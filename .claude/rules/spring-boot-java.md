---
paths:
  - "**/*.java"
---

# Spring Boot / Java

OpenAPI 어노테이션(`@Schema`·`@Operation`·`@Parameter`·`@ApiResponses`) 규칙은 [`openapi-conventions.md`](openapi-conventions.md), 주석(`//`·Javadoc) 작성 스타일은 [`java-comments.md`](java-comments.md) — 이 파일은 레이어·Entity·Enum·SOLID/OOP/ACID·스타일·테스트를 다룬다.

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
- **필드 설명:** Entity·enum·베이스 클래스의 **클래스·필드·enum 상수**마다 `@Schema(description = "...")` (springdoc). nullable·example·requiredMode는 ERD·스펙과 맞출 것. 상세: `openapi-conventions.md` **OpenAPI @Schema** 절
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

## SOLID / OOP 원칙 (실용적 적용)

**전제 — 이 저장소는 풀 DDD·포트/어댑터 인터페이스 레이어를 채택하지 않는다** (`docs/decisions/003-architecture-guide.md` 결정 11 — 크로스 도메인 포트 인터페이스는 2026-08-26에 폐기됐다: "구현체가 항상 1개뿐이라 실질적 이득이 없었다"). 아래 규칙은 이 결정과 **충돌하지 않는 범위**에서만 SOLID/OOP를 적용한다 — 추상화를 위한 추상화(불필요한 인터페이스·레이어 추가)는 여기서도 금지다.

### SRP — Service 책임 분리

- `*Service` 클래스는 **하나의 응집된 유스케이스 그룹**만 담당한다. 서로 다른 책임(예: 멤버십 관리 + 추천 로직)이 한 클래스에 섞이면 분리 신호다.
- 이미 확립된 분리 패턴: `TripService`(facade) + `TripCommandService` / `TripQueryService` / `TripMemberQueryService`(`docs/decisions/003` 결정 10) — 다른 도메인의 Service가 커지면 같은 패턴(facade + Command/Query 분리)을 따른다.
- 참고용 정량 신호(기계적 강제 아님): 한 Service가 300~400줄을 넘거나 서로 다른 도메인 개념을 다루면 분리를 검토한다. 줄 수만으로 쪼개지 않는다 — Controller처럼 실제로는 얇은 위임 + Swagger 어노테이션이 대부분이면 분리 대상이 아니다.

### OCP / ISP — 인터페이스는 다형성이 실제로 필요할 때만

- 기본은 **concrete 클래스 직접 주입**(`@RequiredArgsConstructor` + `private final`)이다. 구현체가 하나뿐인 의존성에 "나중에 갈아끼울 수도 있으니" 인터페이스를 미리 만들지 않는다 — 위 포트 폐기 사유와 동일한 이유다.
- 인터페이스가 정당화되는 경우: **실제로 다형성이 있는** 의존성만. 예: `SocialTokenVerifier`(GOOGLE/KAKAO/APPLE 등 여러 concrete 구현이 실제로 존재하고 provider별로 동작이 다름), `JpaRepository`(Spring Data가 구현체를 생성).
- 새 인터페이스를 추가하기 전 "지금 구현체가 2개 이상 존재하거나, 곧 그렇게 될 근거가 있는가?"를 먼저 답한다 — 아니면 concrete 클래스로 시작한다.

### 캡슐화 — Entity는 의도가 드러나는 메서드로 상태를 바꾼다

- Entity 클래스에 **클래스 레벨 `@Setter` 금지**(저장소 전역에 이미 확립된 컨벤션 — 아래 **Style** 절). 여러 필드를 함께 바꾸는 상태 전이(예: 멤버십 활성화, 알림 읽음 처리)는 Service에서 setter를 여러 번 호출하지 않고, Entity에 `activate()`·`markRead(LocalDateTime now)`처럼 **의도가 드러나는 메서드**를 두고 그 안에서 처리한다 (`TripMember.activate()`, `NotificationHistory.markRead()` 참고). 무결성 조건(예: A 상태일 때만 B로 전이 가능)이 있으면 그 검증도 메서드 안에 둔다.
- **Law of Demeter — 과도한 getter 체이닝 자제.** `@ManyToOne` 연관관계 자유 탐색은 이미 결정된 방침이라 1~2단계 탐색(`tripMember.getTrip()`, `trip.getOwner()`)은 문제 없다. 다만 **3단계 이상** 이어지거나(`a.getB().getC().getD()`), 같은 체이닝을 여러 Service 메서드에서 반복해야 한다면 — 자주 쓰는 값이라는 신호이니 중간 객체에 조회 메서드를 추가하거나 Repository/Support에서 필요한 값만 직접 가져오는 방식으로 승격한다.
- 이 규칙은 풀 DDD(애그리거트 불변식·행위 중심 모델)를 의미하지 않는다 — JPA 연관관계 자유 탐색은 그대로 허용이고(`docs/decisions/003`), 여기서 막는 건 "여러 필드를 흩어진 setter 호출로 바꾸는" 절차적 패턴과 과도한 getter 체이닝 두 가지뿐이다.

## ACID / 트랜잭션 경계

`@Transactional` 자체는 위 **레이어** 절에서 다룬다 — 여기서는 트랜잭션 **안에서** 지켜야 할 것을 다룬다.

### Atomicity — 외부 I/O는 트랜잭션 밖에서 먼저 끝낸다

소셜 로그인 토큰 검증·FCM 발송·Google Calendar 호출 같은 **외부 API 호출**은 가능하면 `@Transactional` 시작 **전에** 끝내고, DB 쓰기만 짧게 트랜잭션 안에 넣는다. 외부 provider가 느려지거나 장애가 나도 DB 커넥션 풀을 오래 붙잡지 않기 위함이다 — provider 지연이 그 기능과 무관한 다른 요청까지 함께 느려지게 만들 수 있다.

### Consistency — 무결성은 가능하면 DB 제약으로

FK·UNIQUE 제약으로 표현 가능한 무결성은 DB 제약을 우선한다. Service 레벨 검증은 DB 제약으로 표현할 수 없는 비즈니스 규칙(BR-*)에만 쓴다.

### Isolation — 동시 쓰기 충돌 지점은 스펙에 명시

같은 row를 여러 요청이 동시에 갱신할 수 있는 유스케이스(예: 정원 체크 후 멤버 추가, 멤버 상태 동시 전이)는 lost update 위험을 인지하고 스펙에 동시성 처리 방식을 명시한다 — 낙관적 락(`@Version`) 또는 재조회 후 조건부 갱신 중 하나를 스펙 단계에서 선택한다(현재 저장소에 `@Version` 사용 사례는 아직 없다 — 필요해지면 그때 도입). 기본 격리수준(MySQL InnoDB `REPEATABLE READ`)을 벗어나는 요구가 있으면 그 이유를 스펙에 남긴다.

### Durability — 롤백돼야 하는 것과 커밋 후에만 나가야 하는 것을 분리

트랜잭션이 롤백되면 그 안에서 준비한 부수 효과(알림 발행 등)도 함께 취소돼야 자연스러운데, 실제 발송처럼 **커밋 이후에만** 실행돼야 하는 부수 효과를 트랜잭션 내부에서 직접 호출하면 롤백된 트랜잭션의 알림이 이미 나가버리는 사고로 이어진다. 이런 부수 효과는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 분리한다 — `NotificationEventListener`(여행방·리마인드 이벤트를 트랜잭션 커밋 후 받아 FCM 발송·이력 저장)가 이미 이 패턴을 쓰고 있다.

## Style

- Java 21 (records, pattern matching) 사용 가능
- Lombok — Entity(JPA)·`@ConfigurationProperties` 클래스·**Service**만 사용. Controller/DTO(record)는 미사용(2026-08-14 개정 — 이전엔 Service도 미사용이었으나 생성자 보일러플레이트 제거를 위해 허용으로 변경).
  - Entity: **클래스 레벨 `@Getter`만.** `@Setter`는 클래스 레벨에 두지 않는다 — 상태 전이는 도메인 메서드로 한다(위 **SOLID / OOP 원칙 — 캡슐화** 절). 예외: PK(`id`) 필드에 한해 테스트 픽스처용 **필드 레벨** `@Setter`를 허용한다(`updatable = false`라 런타임 갱신 경로가 없음 — `Trip.id`/`TripMember.id` 참고). `@Data`/`@EqualsAndHashCode`/`@ToString`은 Entity에 금지(JPA 프록시·지연로딩 함정)
  - Service: 생성자 주입 필드는 `@RequiredArgsConstructor` + `private final` — 수동 생성자 작성 금지(ArchitectureTest가 필드 주입 금지는 이미 검증, 생성자 스타일은 리뷰로 확인)
    - **예외 — 수동 생성자 유지:** 생성자 바디에 필드 대입 외 로직(검증·파생값 계산 — `JwtService`의 secret 길이 검증 등)이 있거나, `@Lazy`처럼 **생성자 파라미터에만** 걸려야 의미가 살아나는 스프링 애너테이션이 있는 경우(필드에 붙이면 Lombok이 생성자로 복사하지 않음 — `FcmService`의 `@Lazy FirebaseMessaging` 사고 사례, 2026-08-14). 이런 경우 수동 생성자를 유지하고 이유를 `//` 한 줄로 남긴다
- 공통 설정: `common/config/`, 도메인 전용: `{domain}/config/`

### DTO record 가독성

- API DTO record 컴포넌트(필드)는 **필드마다 빈 줄로 구분**, `@Schema` / validation / 타입을 **각각 별도 줄** (`UpdateProfileRequest` 스타일)
- Eclipse Spotless는 record 컴포넌트를 **method parameter**와 동일 규칙으로 포맷한다 → 컨트롤러 한 줄 파라미터 설정과 충돌
- 따라서 다필드 DTO record는 `// @formatter:off` … `// @formatter:on`으로 가독성 레이아웃을 고정한다

### Controller 메서드 파라미터

- 파라미터 어노테이션은 **같은 줄**에 붙인다. 어노테이션만 단독 줄로 쪼개지 않음
- 기준: `config/{프로젝트}-java-format.xml` — `insert_new_line_after_annotation_on_parameter=do not insert`

```java
// ✅
ResponseEntity<SuccessResponse<UserSummaryResponse>> updateProfile(
		@AuthorizedUser UUID userId,
		@Valid @RequestBody UpdateProfileRequest request) { ... }

// ❌ 어노테이션·타입·이름을 한 줄씩 분리
ResponseEntity<...> updateProfile(
		@AuthorizedUser
		UUID userId,
		@Valid
		@RequestBody
		UpdateProfileRequest request) { ... }
```

## API (추가 시)

- `@RestController` + `@RequestMapping("/api/v1/...")`
- 응답 envelope: `docs/architecture/api-response.md` (확정)
- 요청 검증: Jakarta Validation (`@Valid`)
- 예외: `@RestControllerAdvice` + `{ code, message }`
- 문서: springdoc — `@Tag`·`@Operation`·`@Schema` 작성 규칙은 `openapi-conventions.md`, enum 목록은 위 **Enum** 절
- Controller 파라미터 스타일: 위 **Controller 메서드 파라미터** 절 준수
- **계약 변경(필드 추가·삭제·이름변경·타입변경·필수화, enum 값, `ErrorCode`, 경로·메서드):** optional 필드 추가라도 커밋 본문에 `Breaking-Change-Reason:` 트레일러 필수 — `core-guardrails.md` STOP §5 · `docs/api/README.md`

## Configuration

- 프로필: `application-{local|dev|test}.yml` — `dev`가 유일한 실제 배포 환경(별도 `prod` 없음). 상세: `docs/architecture.md` Configuration 절
- 민감 정보: 환경 변수 — `application*.yml`에 하드코딩 금지
- `spring.jpa.open-in-view: false` 유지

## Tests

- JUnit 5, `{ClassName}Test`
- `src/test/java/` 패키지는 main과 **동일한 도메인·레이어 구조** 유지
- 통합: `@SpringBootTest` + `test` 프로필 + `@Import(TestcontainersConfig.class)` (실제 MySQL 8, Testcontainers `@ServiceConnection`)
- 단위: service·domain — Mockito로 Repository mock
