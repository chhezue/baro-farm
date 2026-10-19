---
paths:
  - "**/*.java"
---

# OpenAPI Conventions — `@Schema` · `@Operation` 상세

공통 규칙(설명 문자열 원칙·전 어노테이션 공통 금지)은 [`openapi-conventions.md`](openapi-conventions.md) — 여기는 Entity·DTO·enum의 `@Schema`와 Controller의 `@Operation`·JWT·`@ApiResponse`(200/에러) 세부 규칙을 다룬다.

## OpenAPI @Schema (Entity · DTO · enum)

대상별로 `@Schema`를 어디에 붙여야 하는지, API 노출 여부에 따라 필수인지를 정리한다.

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
- `@Document` 문서 모델은 Swagger UI에 직접 안 나와도 **코드 SSOT**로 동일하게 작성
- Javadoc 대신 `@Schema` 우선 (필드 의미). 메서드·API 흐름 주석은 `java-comments.md` 참고
- 계약 값·정책 SSOT: 해당 `docs/specs/` — 불일치 시 문서 먼저. **스펙 경로·스펙 ID는 `@Schema` 문자열에 넣지 않음**

### 상태성 enum `@Schema` (응답 이유 코드·판매 상태 등)

상수마다 **① 이 상태가 뭔지 → ② 언제 이 상태가 되는지 → ③ 이 상태에서 할 수 있는 것·없는 것**을 이어지는 문장으로 자연스럽게 설명한다. 억지로 줄을 나누거나 라벨을 달지 않는다 — 문단 하나로 읽혀도 된다. 클래스 `@Schema`는 "이 enum이 통째로 무엇을 나타내는지" 한 문장이면 충분하다.

```java
@Schema(description = """
		레시피 추천 응답이 왜 이 모양인지 알려 주는 이유 코드입니다.
		""")
public enum RecipeRecommendReason {
	@Schema(description = """
			장바구니는 가져왔지만 담긴 상품이 하나도 없는 상태입니다. 장바구니 서비스가 정상 응답했고
			그 안이 비어 있을 때 이 값이 나갑니다 — 오류가 아니라 사용자 상태라 HTTP는 200입니다.

			화면은 이 값을 받으면 "재료를 담아 보세요"를 보여 주면 됩니다. 잠시 후 다시 시도해야 하는
			상황(UPSTREAM_ERROR)과 구분하라고 나눠 둔 값입니다.
			""")
	EMPTY_CART,
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

- ❌ DTO 필드명만으로 "알아서 파생값" 가정 — OpenAPI·프론트 계약 깨짐
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
| baro-ai 전 엔드포인트 | `@Operation(summary)`만 | — |

**baro-ai는 인증을 하지 않는다.** 토큰 검증·인가는 게이트웨이 책임이고 이 모듈에 `SecurityConfig`도 `@AuthorizedUser`류 어노테이션도 없다. 따라서 `security` 속성을 쓸 일이 없다 — 아래 `security = {}` 관련 규칙은 인증을 직접 하는 다른 모듈에서만 해당한다.

**`summary`:** `@Operation` 어노테이션에 한국어 한 줄 (동사+대상). 상세 설명은 메서드 위 Javadoc(`/** ... */`)에 쓴다.

> ⚠️ **baro-ai에는 `therapi-runtime-javadoc` 의존성이 없다**(2026-09-08 `build.gradle` 확인). 따라서 **Javadoc은 Swagger `description`에 반영되지 않는다** — 지금 이 규칙대로 쓰면 상세 설명이 코드에만 남고 API 문서에는 나타나지 않는다. Javadoc은 개발자용으로 계속 쓰되, **프론트가 Swagger에서 봐야 하는 설명이면 `@Operation(description = ...)`에 직접 넣는다.** 의존성을 추가할지는 미결 — [`audits/harness-retro.md`](../../baro-ai/docs/audits/harness-retro.md) 2026-09-05 첫 행.

**Javadoc 내용 — Stripe/GitHub 스타일: 자유 서술, 고정 헤더 없음.** 이름·시그니처·`@ApiResponse` 예시로 이미 드러나는 건 반복하지 않고, **이름만으로는 모를 것만** 자연스러운 문장으로 쓴다 — 호출 전에 갖춰야 할 조건, 호출하면 어떤 상태가 바뀌는지, 같은 요청을 두 번 보내도 결과가 같은지(= idempotent 여부, 우리말로 먼저 설명하고 필요하면 용어를 덧붙인다), 자주 나는 에러의 원인 정도. 설명할 게 없으면(단순 CRUD 조회 등) Javadoc 자체를 생략해도 된다 — `summary`만으로 충분하면 그걸로 끝.

- 문단 구분은 Javadoc 그대로(빈 줄) — Markdown 헤더(`목적:`, `결과:` 등)를 강제하지 않는다
- `@Tag(name, description)` — 태그 한 줄 목적
- 쿼리 의미가 한눈에 안 들어오면 `@Parameter(description)` 보강
- **인증 여부를 설명 문구로 적지 않는다** (`"Bearer 필수"` 같은 문장 금지) — 인증을 하는 모듈이라면 `security` 속성으로만 표현한다

**Javadoc(`@Operation`/`@Tag`/`@Parameter` 설명 포함) 공통 금지**

- GitHub 이슈 번호 (`#39`, `#17` …)
- BR/스펙 ID (`BR-USER-007`, `D5`, `D-JOIN-ENTRY`, `C1` 단독 등)
- `docs/specs/...` 경로만 나열
- Bearer/JWT 문구 (자물쇠와 중복)

**허용:** 도메인 용어의 **의미** (`SCHEDULE_PENDING` = 멤버이지만 일정 확인 전), HTTP 상태·`ErrorCode` 상수명, idempotent/정렬/쿼리 의미

**예시**

```java
/**
 * 장바구니에 담긴 상품으로 만들 수 있는 요리 하나를 추천하고, 그 요리에 필요하지만 장바구니에 없는
 * 재료를 살 수 있는 상품으로 이어 준다. 같은 장바구니로 여러 번 불러도 부작용은 없지만, 생성 모델을
 * 매번 새로 호출하므로 결과 요리가 달라질 수 있다. 실패해도 4xx를 내지 않고 200 + reason으로 알린다.
 */
@Operation(summary = "장바구니 기반 레시피 추천")
@GetMapping("/recipes/{userId}")
ResponseDto<RecipeRecommendResponse> recommendRecipe(...) { ... }

// ✅ 설명할 게 없으면 Javadoc 생략 — summary만으로 충분
@Operation(summary = "상품명 자동완성")
@GetMapping("/autocomplete")
ResponseDto<List<ProductAutocompleteResponse>> autocomplete(...) { ... }
```

### OpenAPI 200 성공 응답 (`@ApiResponse`) — 필수

지금까지 `@ApiResponses`는 **에러 코드만** 문서화해 Swagger UI에서 성공 example을 볼 수 없었다. **모든 API**는 성공 케이스도 에러와 **같은 `@ApiResponses` 배열**에 명시한다.

- 실제 성공 HTTP 상태로 `responseCode`(`"200"`/`"201"`/`"204"`) 지정 — 배열 **맨 앞**(에러보다 먼저)에 둔다.
- **Body 있는 응답: `useReturnTypeSchema = true`만 지정** — `schema`나 `content(examples = ...)`는 명시하지 않는다. Controller 메서드의 실제 반환 타입(`ResponseDto<XxxResponse>`)에서 springdoc이 파생 스키마를 자동 생성해, 그 안의 필드·enum까지 Swagger UI Schema 탭에 전부 노출된다.
- **제네릭 wrapper를 raw 타입으로 직접 지정 금지(성공 200/201에서).** `schema = @Schema(implementation = ResponseDto.class)`처럼 쓰면 제네릭이 지워져 springdoc이 실제 `data` 타입(리스트·필드·enum 포함)을 전혀 못 읽는다 — `core-guardrails.md` STOP §1.6이 이 사고를 다룬다.
- 204 No Content: `content` 없이 `@ApiResponse(responseCode = "204", description = "...")`만 기재한다.
- **Controller 내 `@ExampleObject`, `@Parameter(example = "...")` 사용 금지:** Swagger 어노테이션 간결화를 위해 컨트롤러에서는 예시 값 직접 작성을 생략하고, DTO의 `@Schema(example = "...")`에 작성된 예시를 활용한다.

```java
// ✅ 200을 배열 맨 앞에 추가 — useReturnTypeSchema로 파생 스키마 자동 생성
@ApiResponses({
		@ApiResponse(
				responseCode = "200",
				description = "조회 성공",
				useReturnTypeSchema = true),
		@ApiResponse(
				responseCode = "400",
				description = "추천 개수가 1~33 밖(INVALID_TOP_K)",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
// ✅ 반환 타입도 반드시 구체 타입 — ResponseDto<?>가 아니라 ResponseDto<List<ProductRecommendResponse>>
ResponseDto<List<ProductRecommendResponse>> recommendSimilar(@PathVariable UUID productId) { ... }
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
