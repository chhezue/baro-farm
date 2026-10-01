---
paths:
  - "**/*.java"
---

# Java Comments

레이어·Entity·SOLID/OOP/ACID는 [`spring-boot-java.md`](spring-boot-java.md), OpenAPI 어노테이션(`@Schema`·`@Operation` 등)은 [`openapi-conventions.md`](openapi-conventions.md) 참고 — 이 파일은 `//`·Javadoc 작성 스타일만 다룬다.

**독자:** 신규 서버 개발자. `//`는 구현자 메모·이슈 트래커용 약어가 아니라 **이름만으로 안 드러나는 것**을 평문으로 남긴다.
필드 의미는 `@Schema`, API 계약·요약은 `@Operation`·`@Parameter`가 SSOT.

**원칙: 이름을 먼저 의심하고, 주석은 이름이 못 담는 것만.** 메서드명·파라미터명만 읽고 신규 개발자가 오해할 만하면 주석 누락 — 그게 아니라 이름이 이미 자명하면 **주석 없이 통과**다(`removeMember`처럼 이름이 곧 설명인 1~2줄 facade 위임 등). "이름 우선" 원칙(`spring-boot-java.md` **네이밍 우선 원칙** 절)과 같은 방향 — 주석으로 이름의 결함을 메우지 않는다.

**쓰는 원칙 — 완전한 문장으로, 산문체로 쓴다.** 화살표(`→`)로 상태 전이를 압축 표기하거나, `(idempotent)`처럼 설명 없는 영어 전문용어를 괄호로 툭 붙이지 않는다. "SCHEDULE_PENDING에서 ACTIVE로 바뀐다"처럼 우리말 문장으로 풀어 쓰고, "여러 번 호출해도 결과가 같다" 같은 표현으로 전문용어의 뜻 자체를 설명한다. 한 줄에 여러 정보를 욱여넣지 않는다 — 필요하면 두세 줄로 나눠 쓴다. `//`가 필요하면 **(1) 이름이 못 담는 전제·부작용을 문장으로** + **(2) 필요하면 Why·정책·다단계 How**를 담는다.

## 역할 주석 쓰는 법

메서드가 **무엇을 하는지**와 **왜 그렇게 동작하는지**(전제·부작용·호출 순서 등 이름이 못 담는 것)를 완전한 문장 1~3개로 쓴다. 한 줄로 압축하려 하지 말고, 필요한 만큼 줄을 나눈다.

```java
// 사용자가 이 방의 일정 확인을 마치면 멤버 상태를 SCHEDULE_PENDING에서
// ACTIVE로 바꾼다. 방장과 참여자 모두 이 메서드를 거쳐 방에 들어온다.
// 이미 ACTIVE라면 아무것도 바꾸지 않고 같은 응답을 그대로 돌려준다
// (여러 번 호출해도 안전하다).
```

## 쓰지 않음

- `@Operation`/`@Parameter`·record 필드명과 **완전히 동일한** 문장 반복
- 단순 `@param`/`@return` Javadoc
- Controller에서 Service 비즈니스 로직을 장황히 반복
- 역할 주석 본문을 `#13`, `BR-TRIP-005`, `D5`, `R-freeze` **약어만으로** 대체
- `→` 화살표로 상태 전이를 압축 표기(`SCHEDULE_PENDING→ACTIVE`) — "SCHEDULE_PENDING에서 ACTIVE로 바뀐다"처럼 문장으로 쓴다
- `(idempotent)`처럼 설명 없는 영어 전문용어를 괄호로만 붙이기 — 그 용어가 뜻하는 효과를 우리말 문장으로 먼저 설명한다

## 역할 주석 · 본문 금지 / 허용

| | 역할 `//` (메서드 위) | 본문 Why / `// 1.` | `// TODO` · `// FIXME` |
|--|----------------------|-------------------|------------------------|
| **금지** | `#n`·`BR-*`·스펙 ID만으로 설명 | 약어만으로 Why 대체 | 방향 없는 `TODO` |
| **허용** | 도메인 용어의 **의미** (`SCHEDULE_PENDING`=일정 확인 전) | 평문 정책·에러코드명 | 말미에 스펙 경로·`#n` — `// TODO: … — docs/specs/….md (#13)` |

## stub / 미구현

역할 주석과 TODO를 **분리**한다. 역할 = 유스케이스 의미, TODO = 남은 작업.

```java
// 방장이 추천 모드로 TOP3 후보를 생성한다 (미구현 stub)
@Transactional
public void generateRecommendations(...) {
  // TODO: 기존 추천 hard DELETE 후 TOP3 INSERT, lastRecommendationMode 갱신
  // 상세: docs/specs/trip/trip-recommendation.md (#13)
}
```

## 레이어별 초점

| 레이어 | 주석 대상 |
|--------|-----------|
| **Controller** | 접근 권한(`@AuthorizedUser`, `@TripMemberOnly` 등)·인터셉터·`@Valid` 검증. **유스케이스 역할 주석 금지**(Service에 둠) |
| **Service / facade** | 이름으로 안 드러나면 역할 주석(아래 절). 분기 Why · 다단계 `// 1.` |
| **Support / Helper / Resolver** | 공유 검증·매핑·가드의 **역할 `//`** + 정책·에러코드·배치 vs lazy Why |
| **Interceptor / Aspect / Filter / ArgumentResolver / Scheduler** | 엔트리(`preHandle`·advice·`runForDate` 등) **역할 `//`** + 교차 관심사 Why |
| **client** | Service와 동일 — 역할 `//` + 단계·catch 의도 |
| **Repository** | `@Query` 정렬·필터·fetch 의도. **파생 API 필드용 EXISTS·집계**는 SSOT 조회 한 줄 |
| **DTO / Entity / 공통 envelope** | 필드는 `@Schema` SSOT. Schema로 안 담기는 배경만 `//` |
| **exception** | `ErrorCode` 계약·message override·Handler 범위 |

## 메서드 역할 주석 — Service · Support · Interceptor · Aspect · Scheduler · client

**위치:** 필요하면 메서드 시그니처·어노테이션 **바로 위**에 `//` 주석(필요한 만큼 줄을 나눠도 된다).

| 대상 | 규칙 |
|------|------|
| **이름·시그니처만으로 동작이 안 드러나는 public 메서드** | 역할 주석 필수 — 전제·부작용·같은 요청을 여러 번 보내도 안전한지 등 이름이 못 담는 것 |
| **이름이 곧 설명인 facade 위임·1~2줄 자명한 메서드** | **생략 가능** — 억지로 채우지 않는다 (예: `removeMember`가 그대로 `tripCommandService.removeMember(...)`를 위임하면 주석 없이 통과) |
| **비자명 `private` 헬퍼** | live/snapshot 빌더·윈도우 검증·복합 매핑 등 — 역할 `//` |
| **생략 가능** | 생성자 · getter/setter · 이름만으로 자명한 1라이너 (`findUser`, `normalizeX`, 단순 DTO `toXxx`) |

**Before (금지 — 약어·기호 압축은 신규 개발자가 못 읽는다)**

```java
// #13 stub — 추천 생성 (BR-TRIP-005 hard DELETE·TOP3)
// 방장 SCHEDULE_PENDING → ACTIVE. 이미 ACTIVE면 idempotent (#39)
```

**After — 완전한 문장으로**

```java
// 사용자가 이 방의 일정 확인을 마치면 멤버 상태를 SCHEDULE_PENDING에서
// ACTIVE로 바꾼다. 방장과 참여자 모두 이 메서드를 거쳐 방에 들어온다.
// 이미 ACTIVE라면 아무것도 바꾸지 않고 같은 응답을 그대로 돌려준다
// (여러 번 호출해도 안전하다).
@Transactional
@TripActivity(tripIdParam = "tripId")
public TripDetailResponse activateMembership(UUID tripId, UUID userId) { ... }

// 멤버 목록을 조회한다. 응답에는 모집률과, 동명이인을 구분하기 위한
// 표시용 이름(displayName)이 포함된다 — 둘 다 이름만 봐서는 알 수 없는 정보다.
public TripMembersResponse listMembers(UUID tripId, UUID userId) { ... }

// ✅ 이름이 곧 설명인 facade 위임 — 주석 없이 통과
public TripMembersResponse removeMember(...) {
  return tripCommandService.removeMember(...);
}
```

## 다단계 · Why (본문)

- 본문이 **2단계 이상**이면 `// 1.` `// 2.` `// 3.` 로 **How** 순서 표시 (역할 주석과 **병행**) — 각 단계는 압축된 명사구가 아니라 완전한 문장으로 쓴다
- 정책·에러코드 선택은 단계 옆·직후 **Why**를 자연스러운 문장으로 — Why만/How만 강제하지 않음
- 외부 API 호출·복잡한 Stream 처리·트랜잭션 경계·`catch`에서 그렇게 처리하는 이유(Why)
- 어조: 자연스러운 서술형 문장(`~한다`, `~때문에 이렇게 분기한다`) — `~함`·`~위해 분기`처럼 명사형으로 축약하지 않는다

## 기술 부채

`// TODO:` / `// FIXME:`는 나중에 이 코드를 다시 보는 사람(자신 포함)이 "왜 그때 안 했는지"와 "지금 해도 되는지"를 바로 판단할 수 있게 아래 세 가지를 담는다. 이 세 가지가 없으면 TODO는 grep으로 찾아지긴 해도 방치될 뿐이다.

1. **무엇이 남았는지** — 완전한 문장으로, 어떤 동작을 추가·수정해야 하는지 구체적으로 쓴다. "나중에 개선"처럼 모호한 말만 쓰지 않는다 — 그 자체로는 왜 남아있는지 아무 정보도 주지 않는다.
2. **왜 지금 안 하는지** — 의존성이 아직 없어서인지, 이번 작업 범위 밖이라 미룬 건지, 위험이 낮아 우선순위를 낮춘 건지. 이 이유를 쓸 수 없다면 사실 지금 바로 처리하는 게 맞다는 신호일 수 있다.
3. **어디서 다시 찾을 수 있는지** — 관련 이슈 번호나 스펙 경로를 남긴다. 이게 없으면 나중에 grep으로 이 TODO를 찾아도 "그래서 지금 어떻게 하면 되는지"를 아무도 판단하지 못한다.

`// FIXME:`는 지금 동작은 하지만 알려진 결함·임시방편일 때, `// TODO:`는 아직 구현하지 않은 기능일 때 쓴다.

```java
// TODO: 애플 발급자(iss=https://appleid.apple.com)까지 명시적으로 검증해야 한다. 지금은 JWKS
// 자체가 애플 전용 소스라 실질적인 위험은 낮지만, 같은 프로젝트의 AppleNotificationVerifier는
// 이미 iss까지 검증하고 있어서 방식을 맞추는 게 낫다 — docs/specs/apple-oauth-multi-audience.md
```

이슈 번호·스펙 경로가 없는 TODO를 새로 남기게 되면, 바로 이슈를 만들지 말고(생성 전 사용자 확인 필요 — `core-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절) 우선 관련 스펙에 `[미정]`으로 표기해두는 걸 검토한다 — `core-scope.md` `[미정]` 처리 절.

## Javadoc (`/** */`)

- **Controller 메서드:** `@Operation` `description`을 대체하는 용도로 Javadoc을 쓴다 — `openapi-conventions.md`의 **OpenAPI @Operation · JWT** 절 참고(`therapi-runtime-javadoc`이 Swagger로 읽어감).
- **Service/Support 등 나머지 레이어:** 역할·Why를 `//`로 쓰거나 Javadoc으로 써도 된다 — **자유롭게 선택한다**(한 메서드에 둘 다 쓸 필요는 없다). 어느 쪽을 쓰든 위 절들의 산문체 원칙(완전한 문장, 화살표·설명 없는 전문용어 금지)은 동일하게 적용된다. 다만 `@param`/`@return`처럼 시그니처가 이미 보여주는 걸 그대로 반복하는 형식 태그는 여전히 쓰지 않는다 — 이름·시그니처로 못 담는 내용을 자유 서술로 담는 게 핵심이다.
- 어느 쪽이든 `#n`·`BR-*`만으로 쓰지 말 것.
