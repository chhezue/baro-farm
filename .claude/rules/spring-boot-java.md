---
paths:
  - "**/*.java"
---

# Spring Boot / Java

OpenAPI 어노테이션(`@Schema`·`@Operation`·`@Parameter`·`@ApiResponses`) 규칙은 [`openapi-conventions.md`](openapi-conventions.md), 주석(`//`·Javadoc) 작성 스타일은 [`java-comments.md`](java-comments.md) — 이 파일은 패키지 구조·레이어·문서 모델·Enum·표기규칙·네이밍 원칙을 다룬다. SOLID/OOP·트랜잭션·스타일·테스트는 [`spring-boot-java-design.md`](spring-boot-java-design.md).

> **이 팩은 baro-ai 기준이다.** baro-ai에는 **RDB도 JPA도 없다** — 저장소는 Elasticsearch 하나이고 영속 모델은 `@Document`다. 다른 모듈(`baro-order`·`baro-shopping` 등)은 JPA를 쓰지만 이 하네스의 강제 대상이 아니다(`core-guardrails.md` STOP §0 4번). 그 모듈을 작업하라고 명시적으로 요청받으면 이 팩을 강제하지 말고 그쪽 실제 컨벤션을 코드에서 확인해 따른다.

## Package Layout (Domain-Driven Layered Architecture)

최상위는 **도메인 단위**로 분리하고(baro-ai는 `search`·`recommend`·`embedding`·`log`·`event`·`review`·`config`·`common`), 각 도메인 내부는 **계층형 레이어**(`presentation`/`application`/`domain`/`infrastructure`, 필요 시 `exception`)를 일관되게 유지한다. **전체 패키지 트리 SSOT는 [`docs/README.md`](../../baro-ai/docs/README.md)의 "현재 구조 (As-Is)" 절** — 여기서 중복 유지하지 않는다.

**풀 DDD 미적용** — 문서 모델은 조회·색인을 위한 평평한 구조이고, 도메인 간 참조는 객체 그래프가 아니라 ID로 한다.

`infrastructure` 아래는 연동 대상으로 한 번 더 나눈다 — `elasticsearch/`(Spring Data 저장소), `llm/`(Gemini 호출), `client/`(Feign), `event/`·`kafka/`(메시징). `controller/dto/`처럼 **레이어만 중첩**하는 것은 금지.

**자동 검증(ArchUnit)은 없다.** 다른 저장소에서는 `ArchitectureTest`가 구조 규칙(레이어 의존 방향, 필드 주입 금지 등)을 `./gradlew test`마다 기계적으로 잡기도 하지만 **baro-ai에는 그 테스트가 없다**(2026-09-08 확인). 아래 규칙은 전부 사람·`spring-reviewer`가 직접 봐야 한다.

## 레이어 (최소 규칙)

- **presentation**: HTTP·DTO만. 비즈니스 로직 금지. 사용자 식별은 **게이트웨이가 넣어 주는 `X-User-Id` 헤더가 우선**이고, 경로 변수(`/{userId}`)는 아직 남아 있다(2026-09-08). 헤더가 오면 경로 변수와 같은지 확인하고 다르면 `USER_MISMATCH`(403)로 끊는다 — 그전에는 경로 변수를 그대로 믿어 남의 장바구니로 만든 레시피를 볼 수 있었다. 헤더가 **없으면 막지 않는다**: 게이트웨이가 실제로 넣어 주는지 미확인이라(`audits/open-items.md` B-2) 지금 필수로 만들면 API가 통째로 막힌다. 확인되면 헤더만 신뢰하도록 바꾸고 경로 변수를 지운다.
- **application**: 유스케이스 조율. 외부 호출(ES·LLM·Feign) 순서와 실패 처리를 여기서 정한다.
- **domain**: Elasticsearch `@Document` 클래스·enum·순수 계산 유틸. 프레임워크 의존을 최소로 둔다.
- **infrastructure**: `ElasticsearchRepository` 구현체·LLM 클라이언트·Feign 클라이언트·Kafka 컨슈머.
- **exception**: `{Domain}ErrorCode`. 예외는 `GlobalExceptionHandler`가 `baro-common`의 envelope(`ResponseDto`)로 변환한다.

**요청 경로에서 저장소를 직접 부르지 않는다.** 상품 인덱스 질의는 `ProductSearchService`를 거친다 — 쿼리 조립이 한 곳에 모여 있어야 오탈자·필터 규칙이 갈라지지 않는다.

### ErrorCode enum

`*ErrorCode`는 `baro-common`의 **`BaseErrorCode` 인터페이스를 구현**하고 `@Getter`·`@AllArgsConstructor`를 붙인다. 상수는 **한 상수 = 한 줄**(`NAME(HttpStatus, "메시지"),`)로 쓰고, 각 상수 위에 **언제 이 코드가 나가는지를 Javadoc `/** */`으로** 적는다.

```java
// ✅ 실제 관례 (SearchErrorCode)
@Getter
@AllArgsConstructor
public enum SearchErrorCode implements BaseErrorCode {

    /** 페이지 번호가 0 미만(음수)으로 전달된 경우 */
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다."),

    /** 한 페이지 상품 크기(size)가 1 미만이거나 100을 초과한 경우 */
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 1 이상 100 이하여야 합니다.");

    private final HttpStatus status;
    private final String message;
}

// ❌ 상수·인자를 줄바꿈해 이어 붙이기 · 발생 조건 설명 없음
INVALID_PAGE(HttpStatus.BAD_REQUEST,
    "…"), INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "…");
```

`BaseErrorCode`는 `getStatus()`·`getMessage()`만 요구한다 — **문자열 코드(`"AUTH_INVALID_TOKEN"` 같은 세 번째 인자)는 이 저장소에 없다.** 다른 프로젝트 예시를 옮겨 오지 않는다.

**새 실패 분기 추가 시 (같은 턴):** enum 상수 → Service throw → 스펙 에러 표. Harness: [`core-guardrails.md`](core-guardrails.md) ⛔ §2.

**쓰이지 않는 ErrorCode는 두지 않는다.** HTTP로 나갈 경로가 없는 enum은 "이 API가 4xx를 돌려준다"는 오해를 만든다 — 실제로 `EmbeddingErrorCode`가 그래서 삭제됐다(`audits/cross-cutting/003` B-4).

### 교차관심사 (AOP · Interceptor)

**baro-ai에는 없다.** `@Aspect`도 커스텀 권한 어노테이션도 쓰지 않는다(`harness-map.md` 스택 옵션 "권한·활동 어노테이션" = 꺼짐). 인증·인가는 게이트웨이 책임이고, baro-ai는 넘어온 식별자를 그대로 신뢰한다.

새로 도입해야 할 상황이면 **먼저 사용자에게 확인한다** — 단일 Controller 파싱이나 cross-cutting 로깅 정도는 converter·record로 충분한 경우가 많다(`core-followup.md` 인터셉터 절).

## Elasticsearch 문서 모델 (Document Conventions)

- 클래스 위치: `{domain}/domain/`, 이름은 `*Document`
- `@Document(indexName = "...")`로 인덱스명을 고정한다. 인덱스명은 snake_case(`product`·`cart_event_logs`·`user_profile_embeddings`)
- 저장소는 `{domain}/infrastructure/elasticsearch/`에 `ElasticsearchRepository<TDocument, TId>` 인터페이스로 둔다
- `@Id` 필드는 **의미 있는 값**을 쓴다 — 자동 생성에 맡기지 않는다. 재처리가 덮어쓰기가 되도록 결정적 ID를 만든다(로그 문서는 `{파티션}-{오프셋}`, 프로필은 `userId`)
- 필드 타입은 `@Field(type = FieldType.…)`로 명시한다. 검색어가 닿는 필드는 `@MultiField`로 용도를 나눈다(주 필드 nori 형태소, 서브필드 `word`는 standard — 오탈자 보정용)
- `dense_vector`는 `dims`를 명시한다(현재 **768** — 근거: [`decisions/003`](../../baro-ai/docs/decisions/003-gemini-migration.md) "이후 변경")
- **매핑을 바꾸면 인덱스 재생성이 필요하다.** ES는 이미 만들어진 필드의 타입을 바꾸지 못한다. dev 전제라 지우고 다시 만들지만(`core-guardrails.md` STOP §3), **되돌릴 수 없는 삭제라 에이전트가 실행하지 않는다** — `audits/open-items.md` A-3에 올리고 사람에게 넘긴다
- **캡슐화:** 클래스 레벨 `@Setter` 금지. 상태 전이가 필요하면 도메인 메서드로 한다 — 상세: [`spring-boot-java-design.md`](spring-boot-java-design.md)
- **여러 경로가 함께 봐야 하는 상수는 문서 클래스에 `public static final`로 둔다**(예: `ProductDocument.PURCHASABLE_STATUSES`). 다만 저장소 `@Query` 문자열처럼 상수를 끼워 넣을 수 없는 자리가 있으면 그 사실을 주석으로 남긴다

```java
// ✅
@Getter
@Document(indexName = "product")
public class ProductDocument {

    @Id
    private UUID productId;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "nori"),
        otherFields = @InnerField(suffix = "word", type = FieldType.Text, analyzer = "standard")
    )
    private String productName;

    @Field(type = FieldType.Dense_Vector, dims = 768)
    private float[] vector;
}

// ❌ JPA 어노테이션(@Entity·@ManyToOne·@GeneratedValue) — 이 저장소엔 RDB가 없다
// ❌ Controller가 Document를 그대로 반환 — 응답 DTO로 변환한다
```

## Enum

DTO·문서 모델·도메인 값에 **닫힌 집합**(상태·모드·이유 등)이 생기면 `String` magic value 대신 **`{domain}/domain/`(또는 `application/dto/`) Java enum**으로 분리한다.

| 분리 ✓ | 분리하지 않음 |
|--------|----------------|
| API/인덱스에 노출되는 고정 상수 (`OK`, `EMPTY_CART`, `ON_SALE`) | 자유 입력 문구, 표시명 |
| 분기·검증·동등 비교가 필요한 코드 | 계약이 String인 필드 |
| 새 상수 추가가 스펙으로 관리되는 값 | 외부 시스템 opaque 토큰·임의 ID |

**옆 모듈이 보내는 값은 옮겨 적지 않고 enum을 그대로 쓴다.** 발행 측 `HistoryEventType`을 문자열로 베껴 비교했다가 **모든 장바구니 가중치가 1이 되는** 사고가 있었다(`audits/taste-vector/001` A-1). 지금은 발행 측 enum을 그대로 저장하고 `default` 없는 완전 분기로 처리해, 종류가 늘면 컴파일 에러로 드러난다.

**프론트·팀원에게 enum을 전하는 SSOT (enum 전용 md 파일은 만들지 않음)**

- **계약(값 목록):** OpenAPI `/v3/api-docs` · Swagger UI
- **의미·정책:** 해당 `docs/specs/`, 필요 시 [`docs/product/glossary.md`](../../baro-ai/docs/product/glossary.md)
- ❌ `docs/enums/*.md` 또는 enum마다 md — 코드·스펙과 이중 관리·드리프트 유발

## 표기 규칙 (casing)

| 계층 | 규칙 | 예 |
|------|------|-----|
| ES 인덱스·필드 | 인덱스는 snake_case, 필드는 camelCase(문서 클래스 필드명 그대로) | `cart_event_logs`, `ingredientName` |
| Java | PascalCase / camelCase | `ProductDocument`, `productName` |
| API path | kebab-case | `/api/v1/recommendations/similar/{productId}` |
| UI 한글 | [`docs/product/glossary.md`](../../baro-ai/docs/product/glossary.md) 기준 | "취향 벡터" ≠ 임의 영문 |

## 네이밍 우선 원칙 (프론트 · 신규 개발자 가독성)

`@Schema`·`@Operation`·`//`로 "왜 이런 이름인지"부터 설명해야 한다면, 설명을 늘리기 전에 **이름 자체를 바꿀 수 있는지 먼저 검토한다.** 좋은 이름은 설명 없이도 오해를 안 만든다.

- 새 enum 상수·필드명을 짓거나 리뷰할 때: "이 이름만 보고 신규 개발자·프론트가 오해하지 않을까?"를 먼저 묻는다. 오해 소지가 있으면 `@Schema`로 땜질하지 말고 **이름을 먼저 교체**한다.
- **이름이 하는 일과 달라지면 고친다.** `MedoidDiversityService`는 실제로 메도이드(중심 재계산) 알고리즘이 아니라 max-min(farthest-first) 선택이었다 — 발표·인수인계에서 잘못 설명하게 되고 읽는 사람이 있지도 않은 반복 계산을 찾는다. `MaxMinDiversityService`로 개명했다(`audits/vector-similarity/001` B-2).
- **같은 개념 = 같은 필드명.** 같은 값을 DTO마다 다른 이름으로 부르지 않는다.
- 이름을 바꾸면 **같은 턴에** 전부 최신화한다: 코드·테스트·`docs/specs/`·`docs/product/glossary.md`. 한 곳이라도 구 이름이 남으면 `core-guardrails.md` STOP §4(레거시)와 동일하게 취급한다.

## 설계 원칙 · 스타일 · 테스트

SOLID/OOP 원칙(SRP·OCP/ISP·캡슐화)·트랜잭션 경계·Style(Lombok·DTO record·Controller 파라미터)·API 추가 시 규칙·Configuration·Tests → [`spring-boot-java-design.md`](spring-boot-java-design.md) 참고
