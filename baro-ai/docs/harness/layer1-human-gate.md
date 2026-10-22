# Layer 1 — Human Gate (AI가 멈추고 사람에게 묻는 지점)

> 분류: **rule** (`.claude/rules/`) · 강제 수단: 프롬프트(소프트 가드레일) · 대응 다이어그램: "Layer 1: Human Gate"

에이전트가 코드를 쓰기 **전에** 스스로 판단하면 안 되는 지점을 규정한 층입니다. 이 문서는 그 규칙들이 **언제 로드되고, 어떤 흐름을 타고, 실제로 어떤 실패를 막았는지**를 다룹니다 — `harness-engineering.md`가 "왜 이렇게 만들었나"라면 이 문서는 "구체적으로 어떻게 동작하는가"입니다.

## 1. 기본 사항

### 이 레이어가 나타내는 것

핵심 명제는 하나입니다 — **문서와 구현이 어긋날 때 에이전트가 "더 합리적인 쪽"을 임의로 고르지 않는다.** 고르는 순간 그 선택은 아무 기록도 남기지 않고 코드에 박히기 때문입니다.

### 파일 위치와 분류

`baro-farm-be`는 5인이 함께 쓰는 모노레포이고, `.claude/`는 (다른 모듈이 아니라) 저장소 루트에 있습니다 — 이유는 [`harness-map.md`](../../../.claude/rules/harness-map.md) "이 프로젝트 특이사항" 참고. 이 저장소엔 `CLAUDE.md`가 없습니다 — 프로젝트 지도는 `AGENTS.md` 하나뿐입니다.

| 파일 | 분류 | 로딩 방식 | 담당 |
|---|---|---|---|
| [`AGENTS.md`](../../AGENTS.md) | rule (프로젝트 지도) | 세션 시작 시 항상 | 기술 스택·컨벤션·경로 맵 |
| [`.claude/rules/core-guardrails.md`](../../../.claude/rules/core-guardrails.md) | rule (**코어**) | 세션 시작 시 항상 | ⛔ STOP §0(수정 범위)·§1~§6 · 금지 요약 |
| [`.claude/rules/core-workflow.md`](../../../.claude/rules/core-workflow.md) | rule (**코어**) | 세션 시작 시 항상 | 3 트랙 × 4 게이트 사이클 · 구현 중 지킬 것 |
| [`.claude/rules/core-workflow-git.md`](../../../.claude/rules/core-workflow-git.md) | rule | 세션 시작 시 항상 | 브랜치·커밋·PR·이슈 컨벤션 (2026-09-04, `core-workflow.md`에서 분리) |
| [`.claude/rules/core-scope.md`](../../../.claude/rules/core-scope.md) | rule | 세션 시작 시 항상 | priority(must/could) 단정 금지 · `[미정]` 처리 |
| [`.claude/rules/core-followup.md`](../../../.claude/rules/core-followup.md) | rule | 세션 시작 시 항상 | 후속 제안 · Defer · ERD 개선 제안 |
| [`.claude/rules/core-tools.md`](../../../.claude/rules/core-tools.md) | rule | 세션 시작 시 항상 | 도구 우선순위·트랙×게이트→도구 매핑 |
| [`.claude/rules/core-reporting.md`](../../../.claude/rules/core-reporting.md) | rule | 세션 시작 시 항상 | 사용자 보고는 쉬운 말로 (코드 주석은 제외) |
| [`.claude/rules/harness-map.md`](../../../.claude/rules/harness-map.md) | rule (**구조**) | 세션 시작 시 항상 | 슬롯·스택 옵션 — 다른 규칙이 부르는 경로·명령의 실제 값 |
| [`.claude/rules/baro-ai-performance-refactor.md`](../../../.claude/rules/baro-ai-performance-refactor.md) | rule (**저장소 고유, 한시적**) | 세션 시작 시 항상 | 근거 기반 성능 리팩토링 이니셔티브의 범위·근거 기준 |
| `spring-boot-java.md`·`spring-boot-java-design.md`·`openapi-conventions.md`·`openapi-conventions-operation.md`·`java-comments.md` | rule | **`**/*.java` 접근 시에만** | Java 레이어·SOLID/ACID·`@Schema`·`@Operation`·주석 스타일 |
| `testing.md` | rule | **`*Test.java`·`src/test/**` 접근 시에만** | JUnit·테스트 네이밍 |
| `doc-writing.md` | rule | **`**/docs/**/*.md`·`.claude/**/*.md`·`.github/**` 접근 시에만** | 문서 유형·정보 구조·문장 |
| `.claude/rules/README.md`·`README-components.md` | rule(구조 인덱스) | **`agents/`·`skills/`·`hooks/`·`settings*.json`·이 두 파일** 접근 시 | 위 path-scoped 7개 + 이 2개 = **총 9개** |

**로딩 메커니즘:** `.claude/rules/*.md`에 YAML frontmatter `paths:`가 **없으면** 세션 시작 시 항상 주입되고, **있으면** 그 glob에 매칭되는 파일을 읽거나 쓸 때만 주입됩니다(Cursor `.mdc`의 `alwaysApply`/`globs`에 대응). 이렇게 나눈 이유는 토큰 절약입니다 — Java를 안 건드리는 세션에서 Spring 컨벤션 전체를 매번 실을 필요가 없습니다.

## 2. 언제 발동하고, 어떤 흐름을 타는가

### 트리거

세션 시작 시 **무조건** 1회(always-load 규칙 9개 주입), 그 뒤로는 특정 파일에 접근할 때마다 해당 path-scoped 규칙이 추가 주입됩니다. STOP 조건 자체는 "구현·기본값 변경·커밋 전"에 매번 재확인 대상입니다.

### 실제 로딩 흐름

```
1. 세션 시작
   → AGENTS.md 주입
   → .claude/rules/ 중 frontmatter 없는 9개 주입
     (core-guardrails · core-workflow · core-workflow-git · core-scope
      · core-followup · core-tools · core-reporting · harness-map
      · baro-ai-performance-refactor)

2. 에이전트가 search/application/ProductIndexService.java 를 Read
   → paths: ["**/*.java"] 매칭
   → spring-boot-java.md · spring-boot-java-design.md · openapi-conventions.md
     · openapi-conventions-operation.md · java-comments.md 5개 추가 주입

3. 에이전트가 .claude/rules/README.md 를 Edit
   → paths 매칭 (agents/**·skills/**·hooks/**·settings*.json·README.md·README-components.md)
   → README.md · README-components.md 2개 추가 주입
```

즉 규칙은 "한 번에 다 읽는 매뉴얼"이 아니라 **작업 대상에 따라 그때그때 조립되는 컨텍스트**입니다.

### STOP 조건별로 실제 읽는 파일

| STOP | 조건 | 발동 시 읽는 파일 | 결과 |
|---|---|---|---|
| §0 수정 범위 | `baro-ai/`+`.claude/` 밖 파일 쓰기 시도 | — | `deny-out-of-scope-write.sh`가 물리적으로 차단(Layer 3) — 다른 모듈은 참조는 자유, 쓰기 전엔 사용자 확인 |
| §1 문서·구현 정합 | 스펙·ADR·기획 문서와 코드의 값(재시도 횟수·캐시 정책·env 이름 등)이 다름 | `docs/specs/{domain}/*.md`, `docs/decisions/*.md` | **중단 → 충돌 목록화 → 사용자 질문** |
| §1.5 | "미구현"이라고 보고하기 직전 | 관련 Controller·Service·테스트를 **직접 grep/Read** | 스펙 문구만으로 단정 금지 |
| §1.6 | "Swagger에 있다"고 보고하기 직전 | `/v3/api-docs` 등 **실제 생성 문서** | `@Schema` 존재만으로 단정 금지 |
| §2 ErrorCode | 새 실패 분기·`{Domain}ErrorCode` 추가 | `{Domain}ErrorCode.java`, 스펙 에러 표 | **같은 턴에** enum+throw+스펙 동시 갱신 (미루기 금지) |
| §3 DB 마이그레이션 | Flyway·`V1__*.sql` 작성 시도 | — | **금지** — 애초에 baro-ai엔 RDB/JPA가 없어 해당사항 자체가 거의 없음 |
| §4 레거시 | 경로·상수·API를 교체 | 교체된 구 메서드·상수·테스트 assert | **같은 PR에서 삭제** (호환 레이어 금지) |
| §5 API 계약 트레일러 | DTO·enum·ErrorCode·경로 변경 | — | **옵션 꺼짐** — `oasdiff` 등 계약 보호가 아직 없어(`harness-engineering.md` §7 참고) 이 STOP은 baro-ai에 해당 없음 |
| §6 보안·아키텍처 | 토큰·세션·개인정보 저장 방식 변경 | `{{현재동작 요약}}` | 슬롯이 아직 **`(없음)`** — 필요해지면 `how-it-works.md` 신설 |
| 별도 | 새 이슈·브랜치·PR 생성 | — | 실행 전 채팅으로 먼저 확인 |
| 별도 | priority must/could 판단 | 이슈의 `priority:` 라벨 | 에이전트가 임의 부여 금지 — §3 사례가 이 조항이 생긴 계기 |

## 3. 실제 사례 — priority must/could 자체 판단 (2026-08-26, `#9`·`#86`)

이 레이어가 **막으려던 실패가 실제로 일어났다가, 그 자리에서 규칙으로 굳어진** 사례입니다.

**1) 실패:** 이슈 `#9`(S3 미러링, 이미지 서빙 안정성 개선)와 `#86`(Calendar 동기화 방식 변경)을 다루면서, 에이전트가 "새 기능([Feat]) 태그가 붙어 있다"는 표면적 신호만으로 두 이슈를 **스스로 `priority: must`로 분류**했습니다.

**2) 무엇이 잘못이었나:** 두 이슈의 실제 작업 성격은 신규 기능 구현이 아니라 **안정성·신뢰성 개선**(S3 미러링은 이미지 서빙 안정성, Calendar 동기화는 동기화 신뢰성)이었습니다. 이 저장소의 판단 기준(성능 개선·구조 정리·리팩터·최적화 = `could`, 그 외 기능 구현·버그 수정 = `must`)으로 보면 `could`가 맞았습니다. `[Feat]` 태그는 이슈 템플릿이 기계적으로 붙이는 접두사일 뿐 작업 성격을 말해주지 않는데, 그 표면적 신호에 끌려 판단한 것이 문제였습니다.

**3) 복구:** 사용자가 두 이슈를 `could`로 정정했습니다.

**4) 규칙으로 남긴 것:** [`core-scope.md`](../../../.claude/rules/core-scope.md)에 "⛔ 에이전트가 스스로 must/could를 판단해 라벨을 부여·변경하지 않는다 — 반드시 사용자에게 먼저 확인"을 명문화하고, 이 사고를 계기 자체로 문서에 남겼습니다. priority 판정 근거는 **이슈 라벨만**이라는 것, "실제 작업 성격(안정성·효율 개선인지)"을 봐야 한다는 것까지 함께 못 박았습니다.

**교훈:** 이 사고는 STOP 조건 자체가 없어서가 아니라, "임의 판단 금지"라는 원칙이 **priority처럼 언뜻 사소해 보이는 판단에도 예외 없이 적용된다는 것**을 규칙에 명시적으로 적어두지 않았던 게 원인이었습니다. 큰 계약(API·DB)만 지키면 된다고 여기기 쉬운 지점에서 실패가 났다는 것 자체가, 이 레이어가 왜 "모든 임의 판단"을 겨냥해야 하는지 보여주는 사례입니다.

## 4. AI-native 관점에서의 강조 포인트

| 순위 | 강조할 것 | 근거 |
|---|---|---|
| 1 | **path-scoped 규칙 로딩으로 컨텍스트 예산을 설계했다** | always-load 9개 + path-scoped 9개로 분리. "규칙을 많이 쓰면 좋다"가 아니라 "언제 무엇을 실을지"를 토큰 비용 관점에서 설계했다는 점이 차별점 |
| 2 | **문서 드리프트를 실패 모드로 인정하고 절차를 만들었다** | STOP §1.5·§1.6은 "문서를 믿지 말고 코드/생성물을 확인하라"는 규칙. `harness-engineering.md` 자신이 다른 프로젝트 내용을 담은 채 방치됐다가 이 절차로 발견·복구된 것이 실례([`harness-engineering.md`](../harness-engineering.md) 서두 "참고" 문단) |
| 3 | 충돌 시 임의 판단 금지 (STOP §1, priority 포함) | §3의 `#9`·`#86` 사례가 "큰 계약뿐 아니라 사소해 보이는 판단에도 적용된다"는 걸 보여줌 |

### 4-1. 컨텍스트 예산 (2026-09-04 실측)

| 항목 | 크기 | 언제 지불하나 |
|---|---|---|
| always-load 규칙 9개 | 약 70.7KB | **매 세션 무조건** |
| `AGENTS.md` | 약 7.5KB | **매 세션 무조건** |
| Java 규칙 5개(`spring-boot-java`·`spring-boot-java-design`·`openapi-conventions`·`openapi-conventions-operation`·`java-comments`) | 약 49.6KB | `**/*.java` **한 개만 열어도 전부** |
| `README.md` + `README-components.md` | 약 22.9KB | `agents/`·`skills/`·`hooks/`·`settings*.json`·이 두 파일 접근 시 |

**드러난 것 ①** — Java 파일 하나를 여는 순간 붙는 49.6KB가 **always-load 전체(70.7KB)의 70%에 이릅니다.** 다섯 규칙이 모두 `**/*.java`에 걸려 있어 테스트 한 줄을 고쳐도 다섯 다 실립니다.

**드러난 것 ②** — `.claude/rules/README.md`는 자기 파일에 "사람이 보는 디렉터리 맵이라 행동 규칙이 아님"이라고 적어두면서도, **구성 요소를 추가·삭제할 때**(유지보수 체크리스트가 실제로 필요할 때)만 로드되도록 `paths:`를 좁혀 뒀습니다 — 그 좁힌 범위에 `README-components.md`(2026-09-04 분리분)를 함께 넣어, 둘이 항상 같이 로드되게 맞췄습니다.

### 4-2. 긴 규칙 문서 분리 (2026-09-04)

`.claude/rules/`의 문서 4개(`core-workflow`·`spring-boot-java`·`openapi-conventions`·`README`, 각각 14,000~21,000자)가 이 하네스 자체 컨벤션(`doc-writing.md` "형제 문서 ~70줄 권장")을 크게 초과한 채로 있다가, 이번에 각각 둘로 나뉘었습니다.

| 이전 | 이후 | 가른 기준 |
|---|---|---|
| `core-workflow.md`(14,186자) | `core-workflow.md` + `core-workflow-git.md` | **트랙·게이트 사이클** vs **Git 브랜치·커밋·PR·이슈 컨벤션** |
| `spring-boot-java.md`(21,175자) | `spring-boot-java.md` + `spring-boot-java-design.md` | **패키지·레이어·Entity·Enum** vs **SOLID/OOP·ACID·Style·Tests** |
| `openapi-conventions.md`(14,025자) | `openapi-conventions.md` + `openapi-conventions-operation.md` | **공통 규칙** vs **`@Schema`·`@Operation`·`@ApiResponse` 세부** |
| `README.md`(20,343자) | `README.md` + `README-components.md` | **구조 인덱스(디렉터리·Rules 목록)** vs **Skills·Agents·Hooks·유지보수 체크리스트** |

**원본 파일명은 유지하고 뒷부분만 새 파일로 옮겼습니다** — 다른 문서가 이미 원본 파일명으로 걸어둔 링크(`spring-reviewer.md`가 `spring-boot-java.md`를 참조하는 것 등)가 최대한 깨지지 않게 하려는 선택입니다. always-load 총량은 줄지 않았습니다(내용이 옮겨졌을 뿐입니다) — 얻은 것은 **찾는 시간**(Git 컨벤션이 필요하면 `core-workflow-git.md`로 바로 감)과 **가독성**(각 파일이 한 관심사만 다룸)입니다.

### 4-3. 분할하면 포인터가 어긋난다 — 실제로 걸린 것들

문서를 쪼개면서 **8곳의 참조**를 갱신해야 했습니다(SOLID/ACID를 언급하며 `spring-boot-java.md`만 가리키던 `java-comments.md`, Git 컨벤션 절을 가리키던 `core-guardrails.md`·`harness-map.md`·`defer`/`safe-refactor` 스킬 등). 파일명 자체는 안 바뀌었지만, 그 파일 **안의 특정 절**을 가리키던 참조는 절이 다른 파일로 옮겨가면서 부정확해졌습니다.

**`doc-reviewer` 서브에이전트가 그중 하나를 더 잡아냈습니다:** 분리 직후의 `README.md`와 `README-components.md`가 서로 다른 `paths:` 표기(`.claude/` 접두사 유무)를 쓰고 있었습니다 — 자매 문서가 항상 같이 로드되도록 의도했는데, frontmatter가 어긋나 있으면 그 의도가 조용히 깨집니다. 별도 컨텍스트의 리뷰(Layer 2)가 방금 나눈 사람(같은 세션)이 놓친 것을 잡은 사례입니다.

**남긴 교훈:** 규칙 파일을 분할할 때는 파일명 유지만으로 끝나지 않습니다. **"이 절을 가리키는 안내가 실제로 그 파일에 있는가"**와 **"자매 파일들의 frontmatter가 서로 맞물리는가"**를 기계적으로 확인해야 합니다 — 지금은 grep으로 수동 확인하지만, 훅(Layer 3)으로 승격할 수 있는 성격입니다.

**주의 — 이 레이어만 강조하면 약합니다.** "AI에게 규칙 파일을 잘 써줬다"는 프롬프트 엔지니어링에 가깝고, 누구나 보여줄 수 있습니다. 이 레이어는 [Layer 3](layer3-deterministic-hooks.md)의 결정론적 강제와 **대비**시킬 때 가치가 살아납니다 — "소프트 가드레일로 되는 것과 안 되는 것을 구분했다"는 판단이 핵심입니다.

## 5. `docs/`가 별도 레이어가 아닌 이유

`docs/product/` → `docs/specs/` → `docs/decisions/`로 이어지는 다층 SSOT(Single Source of Truth — 값이 어긋났을 때 믿을 단일 원본)는 이 레이어의 **판단 근거 데이터**이지 독립된 통제 장치가 아닙니다. 문서가 잘 정리돼 있다는 것 자체는 AI-native의 증거가 아니고(문서 잘 쓰는 팀은 AI 없이도 많습니다), 이 저장소에서 `docs/`가 의미 있는 이유는 **에이전트가 매 턴 읽고 어긋나면 멈추는 제어면**이기 때문입니다. 그 역할은 위 STOP 표가 이미 표현하고 있습니다.

## 관련 문서

- [`harness-engineering.md`](../harness-engineering.md) — 이 레이어를 포함한 하네스 전체의 "왜 이렇게 만들었나" 서술형 총정리
- [`docs/harness/layer2-workflow-skills.md`](layer2-workflow-skills.md) — 승인 게이트가 있는 반복 워크플로(스킬) 레이어
- [`docs/harness/layer3-deterministic-hooks.md`](layer3-deterministic-hooks.md) — 프롬프트가 아니라 exit code로 강제하는 훅 레이어
- [`.claude/rules/README.md`](../../../.claude/rules/README.md) — 규칙 파일 구조의 SSOT
