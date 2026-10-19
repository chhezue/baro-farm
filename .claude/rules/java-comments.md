---
paths:
  - "**/*.java"
---

# Java Comments

레이어·Entity는 [`spring-boot-java.md`](spring-boot-java.md), SOLID/OOP/ACID는 [`spring-boot-java-design.md`](spring-boot-java-design.md), OpenAPI 어노테이션(`@Schema`·`@Operation` 등)은 [`openapi-conventions.md`](openapi-conventions.md)·[`openapi-conventions-operation.md`](openapi-conventions-operation.md) 참고 — 이 파일은 `//`·Javadoc 작성 스타일만 다룬다.

**독자:** 신규 서버 개발자. `//`는 구현자 메모·이슈 트래커용 약어가 아니라 **이름만으로 안 드러나는 것**을 평문으로 남긴다.
필드 의미는 `@Schema`, API 계약·요약은 `@Operation`·`@Parameter`가 SSOT.

## ⚠️ baro-ai는 "촘촘한 Javadoc" 방식을 쓴다 (2026-09-05 사용자 결정)

**`baro-ai/` 안에서는 아래 "쓰지 않음"·"생략 가능" 항목을 적용하지 않는다.** 이 모듈은 클래스·필드·메서드에 Javadoc을 빠짐없이 달고, 긴 메서드 본문을 `// [1단계: …]`처럼 단계 주석으로 나누는 방식을 **의도적으로 채택**했다. 포트폴리오·인수인계용으로 코드만 읽어도 흐름이 잡히는 편이 낫다는 판단이다.

따라서 baro-ai에서는 다음이 허용된다 — **되돌리지 않는다.**

- record 컴포넌트·필드마다 `@param`·`/** … */`로 의미를 적는 것 (필드명과 상당 부분 겹쳐도 된다)
- Controller Javadoc이 `@Operation` `summary`와 겹치는 설명을 담는 것
- 생성자·자명한 1라이너에도 Javadoc이 붙는 것
- 메서드 본문의 `// [N단계: …]` 구획 주석

**단, 아래 두 가지는 이 예외와 무관하게 그대로 지킨다.**

1. **주석은 사실이어야 한다.** 코드가 하지 않는 일을 적거나("비동기로 기록합니다" ← 실제로는 동기), 쓰지 않는 기술 이름을 붙이거나(`script_score`를 kNN이라 부르기), 삭제된 인프라를 현행처럼 가리키면(Eureka·`application-prod.yml`) 그건 스타일이 아니라 결함이다. 발견 즉시 고친다 — `core-guardrails.md` STOP §1·§4와 같은 취급이다.
2. **왜(Why)를 지우고 무엇(What)만 남기지 않는다.** 값의 근거(예: "SDK 기본값이 5회·60초라 3회로 줄였다"), 결정 문서 링크(`docs/decisions/…`), 감사 항목 참조는 Javadoc으로 바꿔 쓰더라도 **문장으로 살려 둔다.** `/** 최대 시도 횟수 (3회) */`처럼 값만 반복하고 근거가 사라지면, 나중에 그 값을 조정할 사람이 판단 근거를 잃는다.

**다른 모듈에는 이 예외를 적용하지 않는다** — `core-guardrails.md` STOP §0 4번에 따라 그 모듈의 실제 관례를 따른다.

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
public void indexProducts(List<ProductIndexRequest> requests) {
  // TODO: 재료명 추출을 상품별 1회에서 묶음 1회로 바꾼다
  // 상세: docs/audits/keyword-search/refactor-log.md "남긴 것"
}
```

## 레이어별 초점

| 레이어 | 주석 대상 |
|--------|-----------|
| **Controller** | 파라미터 검증(`@Valid`·범위 상한)과 그 실패가 어떤 `ErrorCode`로 나가는지. **유스케이스 역할 주석 금지**(Service에 둠) |
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
| **이름이 곧 설명인 facade 위임·1~2줄 자명한 메서드** | **생략 가능** — 억지로 채우지 않는다 (예: `recommendSimilarProducts`가 그대로 `vectorRecommendPipeline.recommend(...)`를 위임하면 주석 없이 통과) |
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
// 장바구니 상품을 요리 재료로 바꿔 레시피 하나를 받아 온다. 실패해도 예외를 올리지 않고
// reason이 담긴 빈 응답으로 돌려준다 — 빈 장바구니는 오류가 아니라 사용자 상태라서다.
// 같은 장바구니로 여러 번 불러도 부작용은 없지만 생성 모델을 매번 새로 호출한다.
public RecipeRecommendResponse recommendFromCartWithMissing(UUID userId) { ... }

// 취향 벡터를 다시 만든다. 유형별 최근 5건만 읽으므로 호출이 겹쳐도 결과가 같고,
// 큐가 차서 이번 갱신이 버려져도 다음 행동에서 재계산된다 — 유실을 허용하는 경로다.
public void updateUserProfileEmbedding(UUID userId) { ... }

// ✅ 이름이 곧 설명인 위임 — 주석 없이 통과
public List<ProductRecommendResponse> recommendSimilarProducts(UUID productId, int topK) {
  return vectorRecommendPipeline.recommend(...);
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
// TODO: 상태·가격만 바뀐 수정 이벤트에서는 임베딩·재료명 호출을 건너뛰어야 한다. 지금은 상품이
// 바뀔 때마다 외부 AI를 두 번 부르는데, 발행 측이 변경 필드를 주지 않아 무엇이 바뀌었는지 알 수 없다.
// 수정 이벤트 빈도를 재기 전에는 낭비 규모를 모른다 — docs/audits/open-items.md C-2
```

이슈 번호·스펙 경로가 없는 TODO를 새로 남기게 되면, 바로 이슈를 만들지 말고(생성 전 사용자 확인 필요 — `core-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절) 우선 관련 스펙에 `[미정]`으로 표기해두는 걸 검토한다 — `core-scope.md` `[미정]` 처리 절.

## Javadoc (`/** */`)

- **Controller 메서드:** Javadoc은 **개발자용**이다. baro-ai에는 `therapi-runtime-javadoc`이 없어 Swagger로 넘어가지 않으므로, 프론트가 봐야 하는 설명이라면 `@Operation(description = ...)`에도 따로 적는다 — `openapi-conventions-operation.md` 참고.
- **Service/Support 등 나머지 레이어:** 역할·Why를 `//`로 쓰거나 Javadoc으로 써도 된다 — **자유롭게 선택한다**(한 메서드에 둘 다 쓸 필요는 없다). 어느 쪽을 쓰든 위 절들의 산문체 원칙(완전한 문장, 화살표·설명 없는 전문용어 금지)은 동일하게 적용된다. 다만 `@param`/`@return`처럼 시그니처가 이미 보여주는 걸 그대로 반복하는 형식 태그는 여전히 쓰지 않는다 — 이름·시그니처로 못 담는 내용을 자유 서술로 담는 게 핵심이다.
- 어느 쪽이든 `#n`·`BR-*`만으로 쓰지 말 것.
