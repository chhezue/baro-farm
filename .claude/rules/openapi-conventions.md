---
paths:
  - "**/*.java"
---

# OpenAPI Conventions

springdoc OpenAPI 3 + `therapi-runtime-javadoc`(build.gradle). 레이어·Entity·Enum·SOLID/OOP/ACID는 [`spring-boot-java.md`](spring-boot-java.md), 주석(`//`·Javadoc 작성 스타일)은 [`java-comments.md`](java-comments.md) 참고 — 이 파일은 **설명 문자열이 들어가는 어노테이션**(`@Schema`·`@Operation`·`@Parameter`·`@Tag`·`@ApiResponse`)만 다룬다.

**쓰는 원칙 — 완전한 문장으로, 산문체로 쓴다.** 화살표(`→`)나 `의미:`/`언제:`/`불가:`처럼 강제로 라벨을 나열하는 미니 구조 대신, 동료 개발자에게 말로 설명하듯 자연스러운 한국어 문장 2~4개로 쓴다. 영어 전문용어(idempotent 등)를 설명 없이 괄호로 툭 붙이지 않고, 그 용어가 뜻하는 바를 우리말 문장으로 풀어서 적는다. 프론트·신규 개발자가 다른 문서를 찾아보지 않고 한 번에 읽고 이해할 수 있어야 한다는 게 기준이다 — Stripe 개발 문서가 이 방향의 참고 사례다.

## OpenAPI 설명 어노테이션 (전부)

**설명 문자열이 들어가는 어노테이션은 종류와 무관하게 동일 규칙**을 쓴다.

| 어노테이션 | 위치 | 설명 출처 |
|------------|------|-----------|
| `@Schema(description)` | Entity · DTO · enum · ErrorCode · envelope | 어노테이션 문자열 (그대로 유지) |
| `@Operation(summary)` | Controller | `summary`만 어노테이션, 상세 설명은 **메서드 Javadoc** — 아래 **OpenAPI @Operation · JWT** 절 |
| `@Parameter(description)` | Controller 쿼리·path | 어노테이션 문자열 |
| `@Tag(name/description)` | Controller | 어노테이션 문자열 |
| 기타 (`@ApiResponse` description 등) | 있으면 동일 | 어노테이션 문자열 |

**독자:** 프론트·신규 서버 개발자. 구현 메모·이슈 트래커용 문자열이 아니다.

**전 어노테이션 공통 금지**

- GitHub 이슈 번호 (`#39` …)
- BR/스펙 ID (`BR-USER-007`, `D5`, `D-JOIN-ENTRY`, `C1` 단독 등)
- `docs/specs/...` 경로만 나열
- `구 XXX 대체` 같은 레거시 메모
- Bearer/JWT 문구 (`@Operation` — 자물쇠·`security`로만)

**허용:** 도메인 용어의 **의미**, HTTP 상태·`ErrorCode` 상수명, idempotent/정렬/쿼리 의미

## OpenAPI @Schema (Entity · DTO · enum)

| 대상 | `@Schema` 위치 | 필수 |
|------|----------------|------|
| JPA `@Entity` / `@MappedSuperclass` | 클래스 + **모든 필드** | ✓ |
| `{domain}/domain/` enum | enum + **각 상수** | ✓ |
| `{domain}/dto/` record·class | record/class + **각 컴포넌트·필드** | ✓ (API 노출 DTO) |
| `common/api/` envelope | record + 필드 | ✓ |
| verifier 경계 record (`OAuthProfile` 등) | record + 필드 | ✓ |
| Controller | `@Tag`, `@Operation`, `@Parameter` (아래 양식) | API 추가 시 |

**작성 규칙**

- 설명은 **한국어**. Entity는 컬럼 의미·제약(nullable, UNIQUE, FK). API DTO는 **FE가 쓰는 의미** · null 의미 · 단위/범위
- API 필드: `example`, `nullable`, `requiredMode = REQUIRED` (validation `@NotNull`/`@NotBlank`와 일치)
- **클래스 `@Schema`:** 무엇인지 + 주로 쓰이는 API 경로 (이슈 번호 금지)
- Entity는 Swagger UI에 직접 안 나와도 **코드·ERD SSOT**로 동일하게 작성
- Javadoc 대신 `@Schema` 우선 (필드 의미). 메서드·API 흐름 주석은 `java-comments.md` 참고
- 계약 값·정책 SSOT: `docs/architecture/erd.md`, 해당 `docs/specs/` — 불일치 시 문서 먼저. **스펙 경로·스펙 ID는 `@Schema` 문자열에 넣지 않음**

### 상태성 enum `@Schema` (SCHEDULE_PENDING/ACTIVE · TripStatus 등)

상수마다 **① 이 상태가 뭔지 → ② 언제 이 상태가 되는지 → ③ 이 상태에서 할 수 있는 것·없는 것**을 이어지는 문장으로 자연스럽게 설명한다. 억지로 줄을 나누거나 라벨을 달지 않는다 — 문단 하나로 읽혀도 된다. 클래스 `@Schema`는 "이 enum이 통째로 무엇을 나타내는지" 한 문장이면 충분하다.

```java
@Schema(description = """
		여행방 안에서 멤버가 어디까지 진행했는지를 나타내는 상태입니다.
		""")
public enum TripMemberStatus {
	@Schema(description = """
			방에 참여하긴 했지만 아직 이 방의 일정을 확인하지 않은 상태입니다. 방장은 방을 만든 직후,
			참여자는 초대 링크로 들어온 직후 이 상태가 되고, "일정 확인 완료" 버튼을 눌러야 다음 상태(ACTIVE)로 넘어갑니다.

			이 상태에서는 방 상세 화면·멤버 목록·달력·초대 링크 공유를 아직 쓸 수 없습니다. 다만 홈 화면
			목록에는 보이고, 즐겨찾기(Pin)는 개인 설정이라 가능합니다.
			""")
	SCHEDULE_PENDING,
	// ...
}
```

### 파생·조회 시 계산 필드 (DB 컬럼 없음)

Entity·`user` 테이블에 **저장되지 않는** API 필드(예: `hasCompletedPreSchedule` — `users.vacation_apply_period IS NOT NULL` 파생)는 아래를 **반드시** 남긴다.

| 위치 | 필수 내용 |
|------|-----------|
| **DTO `@Schema(description)`** | 어떤 조건인지 · **저장 안 함** · **true/false가 바뀌는 트리거**(어떤 CRUD) · **값이 클라이언트에 실리는 API**(login/me 등). 스펙 ID·이슈 번호 금지 |
| **계산 Service** (`UserSummaryService` 등) | 클래스·public 메서드 `//` — **어디서 호출되는지**, EXISTS/집계 **How**, 정책 **Why(평문)** |
| **Repository** | EXISTS·집계 메서드 — **파생 필드 SSOT 조회**임을 한 줄 (`existsByUserId` 등) |
| **스펙** | `docs/specs/` 해당 정책 ID — **스펙 문서용**, Swagger·역할 `//` 본문 아님 |

- ❌ DTO 필드명만으로 “알아서 파생값” 가정 — OpenAPI·프론트 계약 깨짐
- 일정 CRUD 응답에 `user` 요약이 **없으면** `@Schema` 또는 Service 주석에 **me 재조회 필요** 명시

```java
@Schema(description = "소셜 로그인 요청")
public record LoginRequest(
		@Schema(description = "소셜 제공자", example = "GOOGLE", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull
		SocialProvider provider,

		@Schema(description = "GOOGLE/APPLE: id_token, KAKAO: access_token", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank
		String token
) {}
```

## OpenAPI @Operation · JWT (Controller)

`OpenApiConfig`에 **`bearer-jwt`** HTTP Bearer 스키마 + **전역 `security`** 가 있다. Swagger UI 자물쇠 = JWT 필요.

**독자:** 프론트·신규 서버 개발자. Javadoc은 구현자 메모가 아니라 **호출 가이드**다.

| 엔드포인트 | 코드 | Swagger |
|------------|------|---------|
| JWT 필요 (`@AuthorizedUser` 사용) | `@Operation(summary)`만 — **전역 security 유지** | 자물쇠 ON |
| JWT 불필요 (login/refresh/logout 등) | `security = {}`로 전역 해제 | 자물쇠 OFF |

**`summary`:** `@Operation` 어노테이션에 한국어 한 줄 (동사+대상). **`description` 속성은 쓰지 않는다** — 상세 설명은 메서드 위 Javadoc(`/** ... */`)에 쓰고 `therapi-runtime-javadoc`이 런타임에 읽어 Swagger `description`으로 자동 반영한다(springdoc이 classpath의 therapi를 자동 감지 — 별도 설정 불필요).

**Javadoc 내용 — Stripe/GitHub 스타일: 자유 서술, 고정 헤더 없음.** 이름·시그니처·`@ApiResponse` 예시로 이미 드러나는 건 반복하지 않고, **이름만으로는 모를 것만** 자연스러운 문장으로 쓴다 — 호출 전에 갖춰야 할 조건, 호출하면 어떤 상태가 바뀌는지, 같은 요청을 두 번 보내도 결과가 같은지(= idempotent 여부, 우리말로 먼저 설명하고 필요하면 용어를 덧붙인다), 자주 나는 에러의 원인 정도. 설명할 게 없으면(단순 CRUD 조회 등) Javadoc 자체를 생략해도 된다 — `summary`만으로 충분하면 그걸로 끝.

- 문단 구분은 Javadoc 그대로(빈 줄) — Markdown 헤더(`목적:`, `결과:` 등)를 강제하지 않는다
- `@Tag(name, description)` — 태그 한 줄 목적
- 쿼리 의미가 한눈에 안 들어오면 `@Parameter(description)` 보강
- **인증 여부는 `security`로만** (`"Bearer 필수"` 문구 금지)
- `@AuthorizedUser` 있는 메서드에 `security = {}` 두지 말 것
- `SecurityConfig` `permitAll`과 Controller `security = {}`를 **같이** 맞춤

**Javadoc(`@Operation`/`@Tag`/`@Parameter` 설명 포함) 공통 금지**

- GitHub 이슈 번호 (`#39`, `#17` …)
- BR/스펙 ID (`BR-USER-007`, `D5`, `D-JOIN-ENTRY`, `C1` 단독 등)
- `docs/specs/...` 경로만 나열
- Bearer/JWT 문구 (자물쇠와 중복)

**허용:** 도메인 용어의 **의미** (`SCHEDULE_PENDING` = 멤버이지만 일정 확인 전), HTTP 상태·`ErrorCode` 상수명, idempotent/정렬/쿼리 의미

**예시**

```java
/**
 * 일정 확인을 끝내 여행방 입장을 완료한다. 방장·참여자 모두 이 API를 호출하면 SCHEDULE_PENDING이었던
 * 멤버십이 ACTIVE로 바뀐다. 이미 ACTIVE인 상태에서 다시 호출해도 아무것도 바뀌지 않고 같은 응답을 그대로
 * 돌려주므로 여러 번 호출해도 안전하다. 방 안 API(멤버 목록, 달력 등)는 이 호출 이후에만 쓸 수 있다.
 */
@Operation(summary = "여행방 멤버십 활성화")
@PostMapping("/{tripId}/activate")
ResponseEntity<SuccessResponse<TripDetailResponse>> activateMembership(...) { ... }

// ✅ 설명할 게 없으면 Javadoc 생략 — summary만으로 충분
@Operation(summary = "정기 일정 목록")
@GetMapping("/regular")
ResponseEntity<?> listRegular(@AuthorizedUser UUID userId) { ... }

// ✅ JWT 불필요 — security = {} 필수
/** 소셜 토큰으로 로그인하고 access·refresh를 발급한다. 앱 최초 로그인·재로그인에 사용. */
@Operation(summary = "소셜 로그인", security = {})
@PostMapping("/login")
ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) { ... }
```

### OpenAPI 200 성공 응답 (`@ApiResponse`) — 필수

지금까지 `@ApiResponses`는 **에러 코드만** 문서화해 Swagger UI에서 성공 example을 볼 수 없었다. **모든 API**는 성공 케이스도 에러와 **같은 `@ApiResponses` 배열**에 명시한다.

- 실제 성공 HTTP 상태로 `responseCode`(`"200"`/`"201"`/`"204"`) 지정 — 배열 **맨 앞**(에러보다 먼저)에 둔다.
- **Body 있는 응답: `useReturnTypeSchema = true`만 지정** — `schema`나 `content(examples = ...)`는 명시하지 않는다. Controller 메서드의 실제 반환 타입(`ResponseEntity<SuccessResponse<XxxResponse>>`)에서 springdoc이 `SuccessResponseXxxResponse`라는 파생 스키마를 자동 생성해, 그 안의 필드·enum까지 Swagger UI Schema 탭에 전부 노출된다.
- **`schema = @Schema(implementation = SuccessResponse.class)`로 직접 지정 금지(성공 200/201에서).** 이 raw 타입 지정은 제네릭을 지워버려 springdoc이 실제 `data` 타입(리스트·필드·enum 포함)을 전혀 못 읽는다. 에러 응답(`ErrorResponse.class`)은 제네릭이 아니므로 `schema = @Schema(implementation = ErrorResponse.class)`를 그대로 써도 된다.
- `SuccessResponse`는 `@JsonInclude(NON_NULL)`이라 성공 시 `message`/`code` 키가 실제 응답 바디에 **없다**.
- `ResponseEntity<Void>`(204 No Content): `content` 없이 `@ApiResponse(responseCode = "204", description = "...")`만 기재한다.
- **Controller 내 `@ExampleObject`, `@Parameter(example = "...")` 사용 금지:** Swagger 어노테이션 간결화를 위해 컨트롤러에서는 예시 값 직접 작성을 생략하고, DTO의 `@Schema(example = "...")`에 작성된 예시를 활용한다.

```java
// ✅ 200을 배열 맨 앞에 추가 (me 예시) — useReturnTypeSchema로 SuccessResponseUserSummaryResponse 자동 생성
@ApiResponses({
		@ApiResponse(
				responseCode = "200",
				description = "조회 성공",
				useReturnTypeSchema = true),
		@ApiResponse(
				responseCode = "401",
				description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
// ✅ 반환 타입도 반드시 구체 타입 — ResponseEntity<?>가 아니라 ResponseEntity<SuccessResponse<UserSummaryResponse>>
ResponseEntity<SuccessResponse<UserSummaryResponse>> me(@AuthorizedUser UUID userId) { ... }
```

### OpenAPI 400/401/404/409 등 에러 응답 (`@ApiResponse`) — 예시 자동 주입

컨트롤러에 별도의 커스텀 어노테이션(예: `@ApiErrorCode`)을 추가할 필요가 **전혀 없습니다**.
`@ApiResponse`의 `description` 속성에 에러 코드 상수명(예: `AUTH_EXPIRED`)을 괄호로 묶어서 적어두면, Spring doc 구동 시 `OpenApiResponseSupport` 커스터마이저가 정규식(`\(([A-Z0-9_]+)\)`)으로 이를 파싱하여 Swagger UI에 해당 `ErrorCode`의 JSON 예시를 동적으로 주입합니다.

- **작성 규칙**: `description` 문자열 내에 발생 가능한 도메인 에러코드명을 `(에러코드명)` 형식으로 포함시킵니다.
- **예시**:
  ```java
  @ApiResponse(
      responseCode = "401",
      description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  ```
- **효과**: Swagger UI에서 해당 응답의 "Example Value" 탭을 열어보면 `AUTH_INVALID_TOKEN`, `AUTH_EXPIRED`에 해당하는 각각의 실제 `ErrorResponse` JSON 예시 객체를 선택해서 볼 수 있습니다.
- **주의사항**:
  - 에러 코드명은 서버에 정의된 `ErrorCode` Enum 구현체의 실제 상수명(`[A-Z0-9_]+`)과 정확히 일치해야 합니다.
  - 괄호 안의 코드를 메모리에 로드된 `ErrorCode` 목록과 자동 매핑하므로, 별도의 `@ExampleObject`를 사용하여 예시를 하드코딩하지 마세요.
