# Layer 1 — Human Gate (AI가 멈추고 사람에게 묻는 지점)

> 분류: **rule** (`.claude/rules/`) · 강제 수단: 프롬프트(소프트 가드레일) · 대응 다이어그램: "Layer 1: Human Gate"

## 1. 기본 사항

### 이 레이어가 나타내는 것

에이전트가 코드를 쓰기 **전에** 스스로 판단하면 안 되는 지점을 규정한 층입니다. 핵심 명제는 하나입니다 — **문서와 구현이 어긋날 때 에이전트가 "더 합리적인 쪽"을 임의로 고르지 않는다.** 고르는 순간 그 선택은 아무 기록도 남기지 않고 코드에 박히기 때문입니다.

### 파일 위치와 분류

| 파일 | 분류 | 로딩 방식 | 담당 |
|---|---|---|---|
| [`CLAUDE.md`](../../CLAUDE.md) | rule (진입점) | 세션 시작 시 항상 | `@AGENTS.md` import + Claude Code 전용 보충 |
| [`AGENTS.md`](../../AGENTS.md) | rule (프로젝트 지도) | 세션 시작 시 항상 | 기술 스택·컨벤션·경로 맵 |
| [`.claude/rules/core-guardrails.md`](../../../.claude/rules/core-guardrails.md) | rule (**코어**) | 세션 시작 시 항상 | ⛔ STOP §1~§6 · 금지 요약 |
| [`.claude/rules/core-workflow.md`](../../../.claude/rules/core-workflow.md) | rule (**코어**) | 세션 시작 시 항상 | 3 트랙 × 4 게이트 사이클 · 구현 중 지킬 것 |
| [`.claude/rules/core-scope.md`](../../../.claude/rules/core-scope.md) | rule | 세션 시작 시 항상 | priority(must/could) 단정 금지 · `[미정]` 처리 |
| [`examples/tripfit/tripfit-release.md`](../../examples/tripfit/tripfit-release.md) | rule (**저장소 고유**) | 세션 시작 시 항상 | Release Gate · 일정 용어 · 도메인·배포 확정 사항 |
| [`.claude/rules/core-followup.md`](../../../.claude/rules/core-followup.md) | rule | 세션 시작 시 항상 | 후속 제안 · Defer · ERD 개선 제안 |
| [`.claude/rules/core-tools.md`](../../../.claude/rules/core-tools.md) | rule | 세션 시작 시 항상 | 도구 우선순위·트랙×게이트→도구 매핑 |
| [`.claude/rules/core-reporting.md`](../../../.claude/rules/core-reporting.md) | rule | 세션 시작 시 항상 | 사용자 보고는 쉬운 말로 (코드 주석은 제외) |
| `spring-boot-java.md` · `openapi-conventions.md` · `java-comments.md` | rule | **`**/*.java` 접근 시에만** | Java 레이어·`@Schema`·주석 스타일 |
| `client-platform.md` | rule | **controller/service 접근 시에만** | 클라이언트 계약·인증 전제 |
| `deployment.md` | rule | **yml·docker-compose 접근 시에만** | 배포 가드레일 |
| `testing.md` | rule | **`*Test.java`·`src/test/**` 접근 시에만** | JUnit·Testcontainers |
| `doc-writing.md` | rule | **`docs/**/*.md`·`.claude/**/*.md` 접근 시에만** | 문서 유형·정보 구조·문장 (2026-09-03 신설) |
| `.claude/rules/README.md`(이 표 자체가 실린 파일) | rule(구조 인덱스) | **`agents/`·`skills/`·`hooks/`·`settings*.json`·이 파일** 접근 시 (2026-09-04 축소, 이전 `.claude/**`) | 위 path-scoped 7개 + 이 파일 자체 = **총 8개** (아래 §4 "path-scoped 8개"의 근거) |

**로딩 메커니즘:** `.claude/rules/*.md`에 YAML frontmatter `paths:`가 **없으면** 세션 시작 시 항상 주입되고, **있으면** 그 glob에 매칭되는 파일을 읽거나 쓸 때만 주입됩니다(Cursor `.mdc`의 `alwaysApply`/`globs`에 대응). 이렇게 나눈 이유는 토큰 절약입니다 — Java를 안 건드리는 세션에서 Spring 컨벤션 전체를 매번 실을 필요가 없습니다.

## 2. 언제 발동하고, 어떤 흐름을 타는가

### 트리거

세션 시작 시 **무조건** 1회(always-load 규칙 주입), 그 뒤로는 특정 파일에 접근할 때마다 해당 path-scoped 규칙이 추가 주입됩니다. STOP 조건 자체는 "구현·기본값 변경·커밋 전"에 매번 재확인 대상입니다.

### 실제 로딩 흐름 (이 문서를 쓴 세션에서 관측된 순서)

```
1. 세션 시작
   → CLAUDE.md 주입 → 그 안의 @AGENTS.md import 따라 AGENTS.md 주입
   → .claude/rules/ 중 frontmatter 없는 7개 주입
     (core-guardrails · core-workflow · core-scope · core-followup
      · core-tools · core-reporting · tripfit-release)

2. 에이전트가 src/main/java/.../JwtProperties.java 를 Read
   → paths: ["**/*.java"] 매칭
   → spring-boot-java.md · openapi-conventions.md · java-comments.md 3개 추가 주입

3. 에이전트가 .../controller/AuthController.java 를 Read
   → paths: ["**/controller/**", "**/service/**"] 매칭
   → client-platform.md 추가 주입
```

즉 규칙은 "한 번에 다 읽는 매뉴얼"이 아니라 **작업 대상에 따라 그때그때 조립되는 컨텍스트**입니다.

### STOP 조건별로 실제 읽는 파일

STOP은 성격이 3가지로 갈립니다. 다이어그램은 가독성을 위해 4단계 순차 판단으로 단순화했지만, 실제로는 아래가 **모든 변경에 동시에** 걸립니다.

| STOP | 조건 | 발동 시 읽는 파일 | 결과 |
|---|---|---|---|
| §1 문서·구현 정합 | 스펙·ADR·기획 문서와 코드의 TTL·enum·env·경로가 다름 | `docs/specs/{domain}/*.md`, `docs/decisions/*.md`, `docs/architecture.md` | **중단 → 충돌 목록화 → 사용자 질문** |
| §1.5 | "미구현"이라고 보고하기 직전 | 관련 Controller·Service·테스트를 **직접 grep/Read** | 스펙 문구만으로 단정 금지 |
| §1.6 | "Swagger에 있다"고 보고하기 직전 | `/v3/api-docs` 또는 `docs/api/openapi.json` **실제 생성 문서** | `@Schema` 존재만으로 단정 금지 |
| §2 ErrorCode·AOP | 새 실패 분기·권한 게이트·`last_activity_at` touch 추가 | `{Domain}ErrorCode.java`, 스펙 에러 표 | **같은 턴에** enum+throw+스펙 동시 갱신 (미루기 금지) |
| §3 DB | Flyway·`V1__*.sql` 작성 시도 | — | **금지** (Layer 3 훅이 물리적으로도 차단) |
| §4 레거시 | 경로·상수·API를 교체 | 교체된 구 메서드·상수·테스트 assert | **같은 PR에서 삭제** (호환 레이어 금지) |
| §5 API 계약 | DTO·enum·ErrorCode·경로 변경 | — | 커밋 본문에 `Breaking-Change-Reason:` 트레일러 |
| §6 보안·아키텍처 | 토큰·세션·결제·개인정보 저장 방식 변경 | `{{현재동작 요약}}` | **같은 턴에** 쉬운 말로 갱신 |
| 별도 | 새 이슈·브랜치·PR 생성 | — | 실행 전 채팅으로 먼저 확인 |
| 별도 | priority must/could 판단 | 이슈의 `priority:` 라벨 | 에이전트가 임의 부여 금지 |

## 3. 실제 사례 — Redis 문서 드리프트 (2026-08-28)

이 레이어가 **실패했다가 복구된** 사례라 오히려 설명 가치가 큽니다.

**1) 실패:** 사용자가 배포 다이어그램을 검토해 달라고 했을 때, 에이전트가 [`docs/decisions/010-redis-infra.md`](../decisions/010-redis-infra.md)를 읽고 "EC2 D의 Redis는 access token 블랙리스트 저장소"라고 단정해 사용자 코드를 잘못 지적했습니다.

**2) 문서가 stale했음:** 실제로는 `#2`(PR #121)에서 블랙리스트가 폐기되고 Redis가 refresh token 저장소로 바뀐 상태였는데, ADR이 갱신되지 않았습니다.

**3) 복구 — STOP §1.5 절차 적용:** 사용자 지적 후 문서가 아니라 **코드를 먼저** 확인했습니다.

```bash
grep -rn "blacklist\|Blacklist" src/main/java   # → 결과 0건
ls src/main/java/com/tripfit/tripfit/auth/service/
# → RefreshTokenService.java, IssuedRefreshToken.java
```

`JwtProperties.java`의 주석에는 이미 "access token 블랙리스트를 폐기해"라고 적혀 있었습니다. 즉 **코드가 진실, 문서가 거짓**인 상태였습니다.

**4) 결과:** `docs/` 전체를 코드와 대조해 19개 파일을 정정하고 3개 커밋으로 분리 반영했습니다(`caa16e3`, `1eba357`, `758e9fa`). 부수적으로 Closed된 이슈 9개가 문서엔 Open으로 남아있던 것, 깨진 링크 2건도 함께 정리했습니다.

**교훈:** STOP §1.5("구현 상태 보고 전 코드 우선 확인")는 바로 이 실패 모드 때문에 미리 규칙에 박아둔 조항이었고, 실제로 그 절차가 복구 경로가 됐습니다. 규칙이 실패를 **막지는** 못했지만, 실패를 **체계적으로 되돌리는 절차**를 제공했습니다.

## 4. AI-native 관점에서의 강조 포인트

| 순위 | 강조할 것 | 근거 |
|---|---|---|
| 1 | **path-scoped 규칙 로딩으로 컨텍스트 예산을 설계했다** | always-load 7개 + path-scoped 8개로 분리. "규칙을 많이 쓰면 좋다"가 아니라 "언제 무엇을 실을지"를 토큰 비용 관점에서 설계했다는 점이 차별점. **2026-09-04에 실측으로 재조정**했다 — 아래 절 참고 |
| 2 | **문서 드리프트를 실패 모드로 인정하고 절차를 만들었다** | STOP §1.5·§1.6은 "문서를 믿지 말고 코드/생성물을 확인하라"는 규칙. 문서 SSOT를 만들면서 동시에 그 SSOT가 썩는다는 걸 전제한 설계 |
| 3 | 충돌 시 임의 판단 금지 (STOP §1) | 흔한 주장이라 단독으로는 약함. 위 2번 사례와 묶어서 말해야 설득력이 생김 |

### 4-1. 실측으로 재조정한 컨텍스트 예산 (2026-09-04)

"컨텍스트 예산을 설계했다"고 말하려면 숫자가 있어야 합니다. 이날 처음 실제 크기를 쟀고, 그 결과로 세 가지를 고쳤습니다.

| 항목 | 크기 | 언제 지불하나 |
|---|---|---|
| always-load 규칙 7개 | 약 42KB | **매 세션 무조건** |
| `AGENTS.md` + `CLAUDE.md` | 약 7.7KB | **매 세션 무조건** |
| Java 규칙 3개(`spring-boot-java`·`openapi-conventions`·`java-comments`) | 약 45KB | `**/*.java` **한 개만 열어도 전부** |
| `.claude/rules/README.md` | 약 18KB | 구 설정에서는 `.claude/` 안 어디든 건드리면 |

**드러난 것 ①** — Java 파일 하나를 여는 순간 붙는 45KB가 **always-load 전체(42KB)보다 큽니다.** 세 규칙이 모두 `**/*.java`에 걸려 있어 테스트 한 줄을 고쳐도 셋 다 실립니다.

**드러난 것 ②** — `.claude/rules/README.md`는 자기 파일에 "사람이 보는 디렉터리 맵이라 행동 규칙이 아님"이라고 적어두고도, `.claude/**` 스코프라 규칙 *내용*만 고치는 세션에도 18KB가 실렸습니다. **구성 요소를 추가·삭제할 때**(유지보수 체크리스트가 실제로 필요할 때)만 로드되도록 좁혔습니다.

**기각한 것** — "Swagger 규칙을 Controller·DTO 경로로 좁히자"는 안은 실제 코드를 확인하고 폐기했습니다. `@Schema`가 `domain`·`exception`·`schedule` 등 전 패키지에 퍼져 있어, 좁히면 필요한 자리에 규칙이 **안 실리는** 사고가 납니다. 측정 없이 직관으로 좁혔으면 통제가 약해졌을 지점입니다.

### 4-2. 규칙 5개 → 7개 재편과 그 대가 (2026-09-04, `#128`)

같은 날 규칙 파일의 **경계**도 다시 그었습니다. 이름이 내부 은어(`harness-*`)였고, 한 파일이 두 가지 일을 하고 있었습니다.

| 이전 | 이후 | 가른 기준 |
|---|---|---|
| `harness-workflow.md` (195줄·21KB, always-load 중 최대) | `core-guardrails.md` + `core-workflow.md` | **하지 말 것**(⛔ STOP §1~§6) vs **어떤 순서로 할 것**(트랙·게이트) |
| `harness-milestone.md` (70줄) | `core-scope.md` + `tripfit-release.md` | **프로젝트 무관 원칙** vs **이 저장소 고유 사실**(Release Gate·일정 용어) |

**핵심은 토큰이 아닙니다.** 파일이 5개에서 7개로 늘었지만 always-load 총량은 42KB로 거의 그대로입니다 — 내용이 이동했을 뿐이기 때문입니다. 얻은 것은 두 가지입니다.

- **찾는 시간** — "이거 해도 되나"는 `core-guardrails`, "다음에 뭘 하지"는 `core-workflow`로 갈립니다
- **이식 경계** — `core-` 접두사는 다른 프로젝트로 가져갈 것, `tripfit-`는 두고 갈 것을 파일명이 스스로 말합니다

**같은 날 시도한 always-load 다이어트는 거의 실패했습니다.** `tripfit-release.md`에서 폐지 이력 2건을 `docs/product/release-milestones.md`로 옮겼는데, 총량은 25,635자 → 25,490자로 **0.6%밖에 줄지 않았습니다**(사전 추정은 5%). 이력을 걷어낸 만큼 "폐지 사유는 저기 있다"는 안내 문장을 새로 써야 했기 때문입니다. **토큰 절감은 규칙 파일을 쪼개는 근거가 되지 못한다**는 것이 이날의 실측 결론입니다.

### 4-3. 이 레이어의 새 실패 모드 — 분할하면 포인터가 어긋난다

규칙을 쪼개면서 **상호참조 오류 5건**이 생겼습니다. 참조 127곳을 "STOP 문맥이면 `core-guardrails`, 게이트 문맥이면 `core-workflow`"로 자동 분류했는데, 분류기가 **바로 옆 문장의 "⛔ STOP" 문구에 끌려** 게이트 안내를 guardrails로 보냈습니다.

| 잘못된 안내 | 실제 위치 |
|---|---|
| `core-tools.md` — "진입 — 트랙 분류"·"사이클" 절이 `core-guardrails.md`에 있다 | `core-workflow.md` |
| `core-workflow.md` — priority·`[미정]`을 `tripfit-release.md`에서 찾아라 | `core-scope.md` |
| `specify` 스킬 — "G3 검증·G4 회고는 `core-guardrails.md`가 SSOT" | `core-workflow.md` |

**왜 이게 오타보다 나쁜가:** 안내를 따라간 에이전트가 그 파일을 열면 규칙이 **없습니다.** 없으면 "규칙이 없구나" 하고 스스로 판단해버립니다 — priority 안내가 어긋났다면 `core-scope.md`가 ⛔로 금지한 "에이전트가 must/could를 임의 판단"이 그대로 재현됩니다.

**어떻게 잡았나:** `doc-reviewer` 서브에이전트(L2)가 2건을 지적했고, 그 유형을 단서로 저장소 전체를 기계 검사해 3건을 더 찾았습니다. 소프트 가드레일(L1)의 결함을 별도 컨텍스트의 리뷰(L2)가 잡은 사례이고, **자기가 방금 나눈 문서를 자기가 검토하면 놓친다**는 self-grading 편향의 실례이기도 합니다.

**남긴 교훈:** 규칙 파일을 분할할 때는 이름 치환만으로 끝나지 않습니다. **"A를 가리키는 안내가 실제로 A에 있는가"를 기계적으로 확인**해야 합니다 — 절 제목이 어느 파일에 있는지 대조하는 검사로, 지금은 수동이지만 훅(L3)으로 승격할 수 있는 성격입니다.

**주의 — 면접에서 이 레이어만 강조하면 약합니다.** "AI에게 규칙 파일을 잘 써줬다"는 프롬프트 엔지니어링에 가깝고, 누구나 보여줄 수 있습니다. 이 레이어는 [Layer 3](layer3-deterministic-hooks.md)의 결정론적 강제와 **대비**시킬 때 가치가 살아납니다 — "소프트 가드레일로 되는 것과 안 되는 것을 구분했다"는 판단이 핵심입니다.

## 5. `docs/`는 왜 별도 레이어가 아닌가

`docs/product/` → `docs/specs/` → `docs/decisions/`로 이어지는 다층 SSOT는 이 레이어의 **판단 근거 데이터**이지 독립된 통제 장치가 아닙니다. 문서가 잘 정리돼 있다는 것 자체는 AI-native의 증거가 아니고(문서 잘 쓰는 팀은 AI 없이도 많습니다), 이 저장소에서 `docs/`가 의미 있는 이유는 **에이전트가 매 턴 읽고 어긋나면 멈추는 제어면**이기 때문입니다. 그 역할은 위 STOP 표가 이미 표현하고 있습니다.
