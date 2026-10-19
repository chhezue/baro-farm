---
paths:
  - "**/*.java"
---

# OpenAPI Conventions

springdoc OpenAPI 3. (`therapi-runtime-javadoc`은 **의존성에 없다** — Javadoc이 Swagger로 넘어가지 않는다. 상세: `openapi-conventions-operation.md`) 레이어·Entity·Enum은 [`spring-boot-java.md`](spring-boot-java.md), SOLID/OOP·ACID는 [`spring-boot-java-design.md`](spring-boot-java-design.md), 주석(`//`·Javadoc 작성 스타일)은 [`java-comments.md`](java-comments.md) 참고 — 이 파일은 **설명 문자열이 들어가는 어노테이션**(`@Schema`·`@Operation`·`@Parameter`·`@Tag`·`@ApiResponse`)만 다룬다.

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

## OpenAPI @Schema · @Operation 상세

Entity·DTO·enum의 `@Schema` 작성 규칙(상태성 enum·파생 필드 포함), Controller의 `@Operation`·JWT·200/에러 `@ApiResponse` 규칙 → [`openapi-conventions-operation.md`](openapi-conventions-operation.md) 참고
