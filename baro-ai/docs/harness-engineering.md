# AI 하네스 엔지니어링 기록 (TripFit-server)

CMC 개발자 세션·부스 질의 대비, **이 저장소에서 AI 코딩 에이전트(Claude Code)를 어떻게 통제하며 개발했는지**를 한 파일에 모은 기록입니다. "바이브 코딩"(AI에게 맡기고 결과만 확인)이 아니라 **하네스 엔지니어링**(AI의 실행 경로 자체를 문서·규칙·훅으로 제한해 일관된 결과를 강제)을 지향했다는 근거를 실제 파일·커밋·인시던트로 남깁니다.

다른 문서와의 관계: 이 파일은 **서술형 총정리**이고, 실제로 에이전트가 참조하는 SSOT는 각 절에서 링크하는 원본 파일들입니다(`AGENTS.md`, `.claude/rules/`, `.claude/skills/`, `docs/specs/` 등). 여기 내용이 원본과 어긋나면 원본이 맞습니다 — 이 파일도 코드처럼 드리프트될 수 있습니다.

**레이어별 실행 흐름 상세:** [`docs/harness/`](harness/README.md) — 이 문서가 "왜 이렇게 만들었나"라면, 그 폴더는 각 레이어가 **언제 발동해 어떤 파일의 어떤 부분을 읽고 어떻게 동작하는지**를 흐름 단위로 푼 문서입니다(Eraser 다이어그램 4종과 1:1 대응, 레이어별·종합 강조 우선순위 포함).

## 1. 왜 "하네스"인가 — 문제의식

AI 코딩 에이전트는 매 세션 컨텍스트가 없는 상태로 시작합니다. 지시만으로 일관성을 기대하면:

- 이번엔 맞게 구현했는데 다음 세션엔 다른 값을 씀 (TTL·enum·env 이름 등)
- "더 나은 방법"이라며 문서와 다른 임의 판단을 함
- 방금 짠 코드를 같은 대화에서 스스로 리뷰해 자기 판단을 재확인하는 편향(self-grading bias)이 생김
- 파괴적 명령(`git push --force`, `rm -rf` 등)을 프롬프트 지시만 믿고 실행함

이 저장소는 이런 실패를 **프롬프트 반복이 아니라 파일로 고정**해서 막습니다: 기획·계약을 문서로 못 박고(SSOT), 에이전트 행동을 규칙(`rules`)으로 제한하고, 반복 워크플로를 스킬(`skills`)로 강제하고, 되돌리기 어려운 명령은 훅(`hooks`)으로 결정론적으로 차단합니다. 아래 절들이 각 장치입니다.

## 2. 다층 문서 SSOT (기획 → 계약 → 규칙)

```
docs/product/       기획 SSOT (PRD, MVP, BR-*, glossary, Milestone 운영)
  ↓
docs/specs/         기능 계약 SSOT (API·DB·에러코드 — 구현 전 Approved 필수)
  ↓
docs/decisions/      되돌리기 어려운 아키텍처/인프라 결정 (ADR)
  ↓
.claude/rules/       에이전트 행동 규칙 (위 문서들을 "어떻게 지킬지"로 번역)
  ↓
src/, deploy/        실제 구현
```

**핵심 규칙:** 이 층들 사이에 값·계약이 어긋나면(예: 스펙엔 TTL 730일인데 코드는 365일) 에이전트가 "더 합리적인 쪽"을 임의로 고르지 않고 **작업을 멈추고 사용자에게 질문**합니다. SSOT: [`.claude/rules/core-guardrails.md`](../../.claude/rules/core-guardrails.md) ⛔ STOP §1.

이 정합성 요구가 실제로 어떻게 걸렸는지는 §6(인시던트) 참고.

## 3. 규칙 (`.claude/rules/`) — always-load vs path-scoped

Cursor의 `.mdc`(`globs`/`alwaysApply`)에 대응하는 구조. `paths:` frontmatter가 없으면 **매 세션 항상 로드**, 있으면 **매칭되는 파일에 접근할 때만 로드**됩니다. 구조 SSOT: [`.claude/rules/README.md`](../../.claude/rules/README.md).

Always-load 규칙 7개(`core-guardrails`·`core-workflow`·`core-scope`·`core-followup`·`core-tools`·`core-reporting`·`tripfit-release`) + path-scoped 규칙 8개(`spring-boot-java`·`openapi-conventions`·`java-comments`·`client-platform`·`deployment`·`testing`·`doc-writing`·`README`)로 나뉩니다(2026-09-03 `doc-writing.md` 추가 — `docs/`·`.claude/` 마크다운 작성 규칙. 채팅 보고용 `core-reporting.md`, 코드 주석용 `java-comments.md`와 독자가 달라 별도 파일로 뒀습니다)(2026-08-27, `spring-boot-java.md`에서 OpenAPI·주석 규칙을 2개 파일로 분리하고, 다른 문서와 80% 이상 중복이던 `figma-product.md`는 폐기 — 표기 규칙만 `spring-boot-java.md`로 이관. `docs/product/fe-context/`가 더 이상 쓰이지 않게 되며 `fe-context.md`도 함께 폐기). 파일별 정확한 역할·`paths:` 패턴은 여기서 다시 나열하지 않습니다 — 표가 바뀔 때마다 이 문서까지 손으로 맞춰야 해서 실제로 드리프트가 난 적이 있습니다. 최신 표: [`.claude/rules/README.md`](../../.claude/rules/README.md) "Rules" 절.

이렇게 나눈 이유는 토큰 낭비 방지입니다 — Java 파일을 안 건드리는 세션에서 Spring 컨벤션 전체를 매번 로드할 필요가 없습니다.

## 4. 스킬 (`.claude/skills/`) — 승인 게이트가 있는 반복 워크플로

"바로 구현"을 막고 **문서 → 승인 → 코드 → 기계적 검증** 순서를 강제하는 5개 스킬(`specify`·`safe-refactor`·`preflight`·`defer`·`retro`)입니다(승인 게이트가 없는 절차형 스킬 `debug`는 아래 별도 서술). 각 스킬의 트리거·산출물은 이 문서에 표로 다시 두지 않습니다 — 최신 표: [`.claude/rules/README.md`](../../.claude/rules/README.md) "Skills" 절.

**공통 설계 원칙:** 세 스킬(`specify`/`safe-refactor`/`preflight`) 모두 "LLM의 자기 보고를 신뢰하지 않는다"가 핵심입니다 — `safe-refactor`은 "안 바꿨다"는 말 대신 `oasdiff` diff가 정말 0인지, `verify`는 "테스트 통과했다"는 말 대신 `./gradlew test`를 실제로 돌린 결과를 요구합니다.

**6번째 스킬 — 절차형, 승인 게이트 없음:** `debug`(버그 재현·원인 분리, 로컬 + 프로덕션 EC2 조사 절차)는 위 5개와 달리 "문서 승인"을 강제하지 않는 반복 조사 절차 캡슐화라 표에서 분리했습니다. 2026-08-11에 `core-tools.md`(always-load 규칙 파일) 안에 있던 프로즈 절차를 이 스킬로 옮겼습니다 — 그 내용은 "어떤 파일을 건드리는가"가 아니라 "버그 리포트를 받았는가"라는 상황 트리거라 `paths:` 스코프(파일 경로 기반)로는 옮길 수 없었고, 스킬(이름+한 줄 설명만 항상 노출되다가 필요할 때 전체 내용을 불러오는 방식)로 옮기는 게 always-load 규칙 파일의 토큰 크기를 줄이면서도 트리거 신뢰성을 유지하는 방법이었습니다.

**3 트랙 × 4 게이트로의 재구성 (2026-09-03, `#127`):** 위 스킬들은 원래 각자 다른 상황에서 독립적으로 트리거되는 구조였습니다. 그래서 동작은 하지만 "하나의 사이클"로 읽히지 않았고, `safe-refactor`은 사실상 별도 진입점이었습니다. 이를 **트랙 3개(A 기능=`specify` · B 감사·리팩터=`safe-refactor` · C 버그=`debug`)가 공통 게이트 4개(G1 리서치 · G2 승인 · G3 검증 · G4 회고)를 통과하는 구조**로 재구성했습니다. 게이트 정의 SSOT는 `core-workflow.md`이고, 스킬 문서는 자기 단계가 어느 게이트에 대응하는지만 표시합니다(중복 정의 금지).

**서브에이전트 (`.claude/agents/`, 2026-09-03 신설):** 게이트에서 호출하는 조사·리뷰 전용 에이전트 3개입니다. 셋 다 `tools` 화이트리스트에서 `Edit`/`Write`를 뺐습니다 — 다만 셋 다 `Bash`는 갖고 있어 **물리적으로 완전히 봉쇄되지는 않고**(`sed -i` 등으로 우회 가능), 마지막 한 겹은 각 에이전트 지침의 "수정하지 않는다" 규범이 담당합니다. 이 구분은 2026-09-04 `spring-reviewer` 스모크 테스트에서 그 에이전트가 **자기 지침의 "도구가 없어 고칠 수 없다"는 서술이 사실과 다르다**고 지적해 드러났습니다.

- **`researcher`(G1)** — 외부 라이브러리 문서 조사. **로컬 `build.gradle`·jar 실물 확인을 웹 조회보다 먼저** 하도록 강제하고, 블로그·StackOverflow 인용을 금지합니다. 이 저장소가 Spring Boot 4.1.0을 쓰는데 웹 예제 대다수가 3.x 기준이라 그대로 옮기면 깨지기 때문입니다. 도입 당시 스모크 테스트에서 이 에이전트가 **지침 자체의 오류를 잡아냈습니다** — "`docs.spring.io`는 `/4.1/` 경로로 고정하라"고 썼는데 실측해 보니 그 경로도 리다이렉트돼 URL로는 버전 고정이 불가능했고, 지침을 "도착 페이지의 버전 표시를 확인하고 로컬 jar로 교차 확인"으로 고쳤습니다.
- **`doc-reviewer`(G3)** — 문서 유형·정보 구조·문장 3단계 리뷰(기준: `doc-writing.md`). advisory라 커밋을 막지 않습니다.
- **`spring-reviewer`(G3, 2026-09-04 추가)** — Java 변경 diff를 트랜잭션 경계·N+1·하네스 계약·레이어 재사용·캡슐화 5축으로 리뷰(기준: `spring-boot-java.md`). 설계의 핵심은 **`ArchitectureTest`(ArchUnit)가 이미 검증하는 7개 규칙을 지적 대상에서 명시적으로 제외**한 것입니다 — 기계가 결정론적으로 잡는 것을 LLM이 다시 보면 비용만 늘고 신호가 흐려지므로, 정적 규칙으로 표현할 수 없어 ArchUnit이 못 잡는 결함에만 집중시켰습니다. 도입 전 조사한 공개 서브에이전트 컬렉션 3종(wshobson·VoltAgent·hesreallyhim)은 모두 `tools`를 제한하지 않거나 `Write`/`Edit`을 포함시켜 **리뷰어가 코드를 고칠 수 있게 방치**돼 있었고, 그 부분은 채택하지 않았습니다.

### 실제 적용 사례 — `auth` 도메인 리팩터 감사 (2026-08-04)

`safe-refactor` 스킬을 처음으로 전체 사이클 적용한 사례: 신선한 서브에이전트로 `auth` 패키지를 읽기 전용 감사 → A/B 9건을 사용자에게 요약 보고·승인 → 구현 → `./gradlew test` 통과 확인까지 진행했습니다(`oasdiff` 검증은 로컬 샌드박스의 Docker 제약으로 보류). 기록: [`docs/audits/auth/audit.md`](audits/auth/audit.md), [`docs/audits/auth/refactor-log.md`](audits/auth/refactor-log.md). 나머지 도메인(`user`, `user-schedule`, `trip`, `notification`, `cross-cutting`)은 아직 미시작 — 진행 현황 SSOT: [`docs/audits/README.md`](audits/README.md).

## 5. 훅 (`.claude/hooks/`) — 프롬프트로 안 되는 결정론적 하한선

규칙 문서는 에이전트가 "읽고 따르는" 소프트 가드레일이지만, 훅은 `.claude/settings.json`에 등록된 이벤트에서 **exit code로 결정론적으로** 실행을 막거나 자동화합니다. 프롬프트 지시만으로는 100% 보장이 안 되는 것들(특히 파괴적 명령)을 여기서 강제합니다. 현재 5개(`deny-out-of-scope-write.sh`·`deny-dangerous-bash.sh`·`deny-db-migration.sh`·`warn-breaking-change.sh`·`auto-format-java.sh`) — 이벤트·매처·동작 표는 여기서 다시 두지 않습니다: [`.claude/rules/README.md`](../../.claude/rules/README.md) "Hooks" 절이 SSOT.

### 인시던트에서 배운 것 — "판단이 필요 없는 곳엔 LLM을 쓰지 않는다"

`warn-breaking-change.sh`는 처음엔 `agent`-type(서브에이전트가 diff를 읽고 breaking 여부를 판단)으로 만들었다가, working tree의 무관한 변경까지 오판해 "절대 막지 마라"는 지시에도 커밋을 막는 사고를 낸 뒤 `command`-type(exit code로 결정론적 통제)으로 전환했습니다 — "판단"은 LLM에게, "항상 이렇게 동작해야 한다"는 결정론적 스크립트에게 맡기는 경계를 이 사고로 얻었습니다. 전체 경위: [`.claude/rules/README.md`](../../.claude/rules/README.md) "agent-type 훅 관련 교훈" 절(SSOT).

## 6. 실제 인시던트가 규칙이 된 사례들

이 저장소의 규칙 상당수는 처음부터 예측해서 넣은 게 아니라, **실제로 한 번 터진 문제를 겪고 나서 재발 방지용으로 문서에 박아 넣은 것**입니다. 발표 때 "AI 활용이 실제로 어떤 효율/인사이트를 줬는가"에 답할 수 있는 근거이기도 합니다.

| 인시던트 | 무슨 일이었나 | 남긴 것 |
|----------|---------------|---------|
| `GET /trips` 401 위장(2026-07-30) | 유효한 토큰인데 401이 뜬 신고 — 정적 코드 리뷰로는 원인을 못 찾았고, 실제 EC2 로그·DB를 직접 봐야 진짜 원인(Hibernate native query의 UUID 바인딩 타입 불일치로 발생한 500이 `/error`에서 인증 필터를 우회해 401로 위장)이 드러남 | "프로덕션에서만 재현되는 버그는 추측하지 말고 실제 서버 로그부터 본다"는 절차를 `core-tools.md`에 명문화. 동시에 미처리 예외를 500으로 보이게 하는 catch-all 핸들러 추가로 같은 유형의 위장을 원천 차단 |
| Apple `authorizationCode` 조건부 필수화(#64, 2026-07-30) | 필드 자체는 optional인데 서비스 로직이 특정 조건에서만 필수로 강제 — `oasdiff` 스키마 diff엔 안 잡히고, 트레일러를 달았는데도 Discord로 전달되는 경로가 아예 없어서 프론트가 계약 변경을 놓칠 뻔함 | `Breaking-Change-Reason` 트레일러 + 신규 `ErrorCode` 추가를 `oasdiff` 결과와 **무관하게** 항상 스캔해 별도 Discord embed로 알리는 2차 감지 로직 추가 |
| 신규 `ErrorCode` "신규 vs 변경" 오분류(#75, 2026-07-31) | 기존 상수의 `HttpStatus`만 바뀐 것도 diff의 `+` 줄만 보고 "완전 신규"로 오판 | `-`(제거)에도 같은 이름이 있으면 "변경"으로 분리 표시하도록 스크립트 보정 |
| PR 없이 main에 직접 push(#67) | merge-push는 중복 알림 방지로 breaking-change 알림을 skip하는데, PR 없이 직접 push하면 이 휴리스틱이 안 걸려 알림 자체가 안 나감 | `docs/api/README.md`에 이 사각지대를 명시적으로 기록 — PR 경유가 알림의 전제 조건임을 문서화 |
| `warn-breaking-change.sh` 오차단 | §5 참고 | agent-type → command-type 훅으로 전환 |
| `NotificationController` Swagger 스키마 소실 | 제네릭 wrapper(`SuccessResponse<T>`)를 raw 타입으로 `@Schema(implementation=...)`에 지정하면 springdoc이 실제 `data` 타입을 못 읽어 스키마 전체가 사라짐 | "`@Schema` 존재 ≠ 실제 Swagger 노출"이라는 STOP §1.6 규칙 + `useReturnTypeSchema=true` 해결책을 `openapi-conventions.md`에 고정 |

## 7. API 계약 변경 감지·알림 파이프라인

DB 마이그레이션이 금지된 대신(§8), **API 계약 변경 감지는 오히려 공을 들인 영역**입니다 — 프론트가 별도 저장소라 계약이 조용히 깨지면 바로 장애로 이어지기 때문입니다.

```
push/PR → OpenApiSpecExportTest → oasdiff breaking (스키마 diff)
                                 → Breaking-Change-Reason 트레일러 · 신규/변경 ErrorCode (스키마 밖 2차 감지)
                                 → ErrorCode/@ApiResponse 상태 불일치 · 권한 게이트 추가·제거 (3차 감지)
                                        → Discord #frontend 알림 (CI는 항상 통과, deploy 안 막음)
```

- 기준 스냅샷: [`docs/api/openapi.json`](api/openapi.json) — `main` push마다 CI가 자동 갱신, 손편집 금지
- 프론트는 이 파일을 인증 없이 raw fetch해 codegen 소스로 사용
- 상세·실제 사고 히스토리: [`docs/api/README.md`](api/README.md) (§6 표의 절반이 여기서 나온 인시던트)

## 8. DB 스키마 — 마이그레이션 대신 "엔티티가 유일한 SSOT"

상용에 보존해야 할 데이터가 없는 프로젝트 특성을 이용해, Flyway/Liquibase 같은 마이그레이션 파일 자체를 **작성 금지**로 정책화했습니다. 스키마 SSOT는 JPA 엔티티 최신본 + Hibernate `ddl-auto` 하나뿐이고, 로컬·dev DB는 필요하면 그냥 폐기·재생성합니다. 이 정책은 §5의 `deny-db-migration.sh` 훅으로 파일 생성 시점에 물리적으로 차단됩니다. SSOT: [`core-guardrails.md`](../../.claude/rules/core-guardrails.md) STOP §3.

부수 효과로 ERD도 "고정된 계약"이 아니라 "언제든 더 나은 모델을 적극 제안해야 하는 대상"으로 다뤄집니다 — [`core-followup.md`](../../.claude/rules/core-followup.md) 💡 ERD 절.

## 9. 승인 게이트 — 에이전트가 스스로 결정하지 않는 지점들

- **새 이슈·새 브랜치·새 PR 생성**은 사용자가 이미 명시적으로 요청한 게 아니면 항상 먼저 채팅으로 확인합니다(2026-08-04/08-05 사용자 결정) — 구현·커밋까지 승인받았다고 PR 생성까지 자동으로 승인된 게 아닙니다.
- **DB·인증·다파일 변경**은 `specify` 스킬의 Approved 스펙 없이 구현을 시작하지 않습니다.
- **priority: must/could**는 이슈나 스펙 문구만으로 단정하지 않고, 이슈의 `priority:` 라벨(`release-milestones.md` §2가 SSOT)로 확인합니다.
- **커밋은 사용자가 명시적으로 요청할 때만** 생성합니다 — 목적·주제별 최대 5개로 분할하며, 작업이 끝나면 에이전트가 묻지 않아도 분할안을 먼저 제안합니다(제안까지가 에이전트 몫, 실행은 승인 후).

## 10. 컨텍스트 격리 — 자기 채점 편향을 피하는 구조

방금 작성한 코드를 같은 대화 맥락에서 스스로 리뷰하면 자기 판단을 재확인하는 편향이 생깁니다. 그래서:

- **광범위 탐색**은 `Explore`/`general-purpose` 서브에이전트로 위임해 메인 대화의 컨텍스트를 아낍니다.
- **PR·머지 전 리뷰**(특히 API·DB 등 Must Have급 변경)는 `code-review`/`simplify` 스킬을 **별도 서브에이전트 컨텍스트**에서 diff와 기준만 보고 판단하게 합니다.
- **아키텍처 감사**(`safe-refactor`)도 1단계를 반드시 신선한 서브에이전트로 돌립니다 — "이번 세션에서 안 건드린 도메인"이어도 같은 대화 맥락이면 편향이 생길 수 있다고 보기 때문입니다.
- 방향이 불확실한 큰 변경은 코드를 먼저 짜지 않고 **Plan Mode**로 설계부터 굳힙니다.

**격리가 손해인 작업도 있습니다 (2026-09-04).** `retro` 스킬은 위 원칙의 예외로, **메인 대화 컨텍스트에서 실행합니다.** 회고의 입력이 세션 대화 이력 자체인데 서브에이전트는 그 이력에 접근할 수 없고, 세션 트랜스크립트 파일 경로는 공식 규약이 아니라 의존할 수 없기 때문입니다. 같은 저장소 안에서 "격리가 편향을 줄이는 작업"(감사·리뷰)과 "격리가 입력을 없애는 작업"(회고)이 갈린다는 것 — 컨텍스트 격리를 원칙이 아니라 **트레이드오프**로 다뤘다는 게 이 예외의 요점입니다.

## 11. 실행 가능한 규칙 — prose가 아니라 테스트로

레이어 의존 방향·PK 전략 같은 구조 규칙 일부는 `spring-boot-java.md`에 글로만 적어두지 않고 [`ArchitectureTest.java`](../src/test/java/com/tripfit/tripfit/architecture/ArchitectureTest.java)(ArchUnit)가 `./gradlew test`마다 실제로 검증합니다 — 에이전트가 규칙을 "깜빡"해도 CI가 기계적으로 잡아냅니다.

## 12. 외부 도구 채택 기준 — 안 쓰기로 한 것도 기록

Superpowers 같은 서드파티 플러그인(`brainstorming`, `writing-plans`, `systematic-debugging` 등)을 도입할지 2026-07-23에 감사했습니다. 결론은 **미채택** — Claude Code 기본 기능(Plan Mode, `Agent` 서브에이전트, `code-review`/`simplify` 스킬)으로 이미 충분히 대체되고, 유일한 gap이었던 `systematic-debugging`도 자체 `debug` 스킬로 충분해 플러그인 설치 비용을 정당화하지 못했습니다. "기능이 있다는 이유만으로 쓴다"를 명시적 배제 조건으로 못 박아 둔 게 핵심 — 도구 자체보다 **도구를 안 쓰기로 한 판단 근거를 기록**하는 게 이 저장소의 스타일입니다. SSOT: [`.claude/rules/core-tools.md`](../../.claude/rules/core-tools.md).

## 13. 숫자로 보는 활용도

2026-08-05 기준 `git log` 측정:

- 전체 커밋 383개 중 **155개**가 `Co-Authored-By: Claude` 트레일러 포함 (`git log --grep "Co-Authored-By: Claude" --oneline | wc -l`)
- 스펙 문서 42개(`docs/specs/` 전 도메인 합계), ADR 9개 — 2026-08-05 시점 수치
- 하네스 구성 요소는 2026-09-04 기준: always-load 규칙 7개 + path-scoped 규칙 8개, 스킬 6개, 서브에이전트 3개, 훅 5개 (정확한 목록 SSOT: [`.claude/rules/README.md`](../../.claude/rules/README.md))

이 수치는 "AI가 얼마나 많이 타이핑했는가"가 아니라 — 위 §1~§12의 장치들이 실제로 매 세션 반복 적용됐다는 근거로 읽는 게 맞습니다. 코드 자체뿐 아니라 이 문서를 포함한 스펙·ADR·규칙 문서 대부분도 AI가 초안을 작성하고 사람이 승인·확정하는 방식으로 만들어졌습니다.

## 14. 한 줄 요약 (발표용)

> "AI에게 코드를 맡긴 게 아니라, AI가 지킬 수밖에 없는 문서·규칙·훅·테스트를 먼저 설계했다. 실제로 그 설계가 뚫렸던 지점(§6)마다 다시 규칙을 보강했고, 그 이력 자체가 이 저장소의 커밋 로그에 남아 있다."

관련 문서: [`README.md`](../README.md) AI 개발 워크플로 절 · [`AGENTS.md`](../AGENTS.md) · [`.claude/rules/README.md`](../../.claude/rules/README.md) · [`docs/README.md`](README.md)
