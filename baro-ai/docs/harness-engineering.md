# AI 하네스 엔지니어링 기록 (baro-ai)

**이 저장소(`baro-farm-be`)에서 AI 코딩 에이전트(Claude Code)를 어떻게 통제하며 개발했는지**를 한 파일에 모은, 포트폴리오 작성을 위한 기록입니다. "바이브 코딩"(AI에게 맡기고 결과만 확인)이 아니라 **하네스 엔지니어링**(AI의 실행 경로 자체를 문서·규칙·훅으로 제한해 일관된 결과를 강제)을 지향했다는 근거를 실제 파일·커밋·인시던트로 남깁니다.

다른 문서와의 관계: 이 파일은 **서술형 총정리**이고, 실제로 에이전트가 참조하는 SSOT(Single Source of Truth — 값이 어긋났을 때 어느 쪽을 믿을지 정해두는 단일 원본)는 각 절에서 링크하는 원본 파일들입니다(`AGENTS.md`, `.claude/rules/`, `.claude/skills/`, `docs/specs/` 등). 여기 내용이 원본과 어긋나면 원본이 맞습니다 — 이 파일도 코드처럼 드리프트될 수 있습니다(실제로 한 번 드리프트됐던 이력은 아래 참고 참조).

**레이어별 실행 흐름 상세:** [`docs/harness/`](harness/README.md) — 이 문서가 "왜 이렇게 만들었나"라면, 그 폴더는 각 레이어가 **언제 발동해 어떤 파일의 어떤 부분을 읽고 어떻게 동작하는지**를 흐름 단위로 푼 문서입니다.

> **참고 — 이 문서 자체의 드리프트 이력 (2026-09-04):** 이 문서는 원래 다른 프로젝트("TripFit")의 하네스를 baro-ai에 이식하며 그대로 옮겨진 초안이었고, 그 프로젝트 고유의 파일명·실측 수치·인시던트가 한동안 baro-ai 문서인 것처럼 남아 있었습니다. `.claude/rules/`의 긴 문서를 분리하던 세션에서 우연히 발견해 이번에 baro-ai 실제 사실로 다시 썼습니다 — "문서가 코드와 어긋나면 코드가 맞다"(STOP §1.5)는 원칙이 이 문서 자신에게도 예외가 아니었던 사례입니다.

## 1. 왜 "하네스"인가 — 문제의식

AI 코딩 에이전트는 매 세션 컨텍스트가 없는 상태로 시작합니다. 지시만으로 일관성을 기대하면:

- 이번엔 맞게 구현했는데 다음 세션엔 다른 값을 씀(재시도 횟수·캐시 TTL·모델명 등)
- "더 나은 방법"이라며 문서와 다른 임의 판단을 함
- 방금 짠 코드를 같은 대화에서 스스로 리뷰해 자기 판단을 재확인하는 편향(self-grading bias)이 생김
- 파괴적 명령(`git push --force`, `rm -rf` 등)을 프롬프트 지시만 믿고 실행함

이 저장소는 이런 실패를 **프롬프트 반복이 아니라 파일로 고정**해서 막습니다: 계약·설계 결정을 문서로 못 박고(SSOT), 에이전트 행동을 규칙(`rules`)으로 제한하고, 반복 워크플로를 스킬(`skills`)로 강제하고, 되돌리기 어려운 명령은 훅(`hooks`)으로 결정론적으로 차단합니다. 아래 절들이 각 장치입니다.

## 2. 다층 문서 SSOT (기획 → 계약 → 규칙)

```
docs/product/       기획 SSOT (MVP 범위, 클라이언트 전제, 용어집 — 우선순위 마일스톤 문서는 아직 없음)
  ↓
docs/specs/         기능 계약 SSOT (taste-vector·cart-recipe·vector-similarity·keyword-search·chat — 작업 단위별 API·정책)
  ↓
docs/decisions/      되돌리기 어려운 아키텍처 결정 (ADR)
  ↓
.claude/rules/       에이전트 행동 규칙 (위 문서들을 "어떻게 지킬지"로 번역)
  ↓
src/                 실제 구현 (com.barofarm.ai.*)
```

**핵심 규칙:** 이 층들 사이에 값·계약이 어긋나면 에이전트가 "더 합리적인 쪽"을 임의로 고르지 않고 **작업을 멈추고 사용자에게 질문**합니다. SSOT: [`.claude/rules/core-guardrails.md`](../../.claude/rules/core-guardrails.md) ⛔ STOP §1.

**baro-ai 고유 범위 제약:** 이 저장소는 5인이 함께 쓰는 모노레포(`baro-farm-be`)의 한 모듈이고, `review`(리뷰·감정 분석) 패키지는 팀원이 소유해 하네스의 쓰기 범위 밖입니다. `baro-ai/` 밖 다른 모듈(`baro-order` 등)도 참조·조사는 자유롭지만 쓰기는 STOP §0으로 막혀 있습니다. 이 정합성 요구가 실제로 어떻게 걸렸는지는 §6(인시던트) 참고.

## 3. 규칙 (`.claude/rules/`) — always-load vs path-scoped

Cursor의 `.mdc`(`globs`/`alwaysApply`)에 대응하는 구조. `paths:` frontmatter가 없으면 **매 세션 항상 로드**, 있으면 **매칭되는 파일에 접근할 때만 로드**됩니다. 구조 SSOT: [`.claude/rules/README.md`](../../.claude/rules/README.md).

Always-load 규칙 9개(`core-guardrails`·`core-workflow`·`core-workflow-git`·`core-scope`·`core-followup`·`core-tools`·`core-reporting`·`harness-map`·`baro-ai-performance-refactor`) + path-scoped 규칙 9개(`spring-boot-java`·`spring-boot-java-design`·`openapi-conventions`·`openapi-conventions-operation`·`java-comments`·`testing`·`doc-writing`·`README`·`README-components`)로 나뉩니다. 파일별 정확한 역할·`paths:` 패턴은 여기서 다시 나열하지 않습니다 — 표가 바뀔 때마다 이 문서까지 손으로 맞춰야 해서 실제로 드리프트가 난 적이 있습니다(위 "참고" 문단). 최신 표: [`.claude/rules/README.md`](../../.claude/rules/README.md) "Rules" 절.

**2026-09-04, `core-workflow`·`spring-boot-java`·`openapi-conventions`·`README` 4개 문서 분리:** 이 하네스 자체 컨벤션(`doc-writing.md` "한 규칙 = 한 관심사, 형제 문서 ~70줄 권장")을 크게 초과한(14,000~21,000자) 문서 4개를 각각 둘로 나눴습니다. 원본 파일명은 유지하고 뒷부분만 새 파일로 옮겨, 다른 문서의 기존 링크가 대부분 깨지지 않게 했습니다. 이 재편이 실제로 무엇을 남겼는지는 [`docs/harness/layer1-human-gate.md`](harness/layer1-human-gate.md) §4-2·§4-3 참고.

이렇게 나눈 이유는 토큰 낭비 방지입니다 — Java 파일을 안 건드리는 세션에서 Spring 컨벤션 전체를 매번 로드할 필요가 없습니다.

## 4. 스킬 (`.claude/skills/`) — 승인 게이트가 있는 반복 워크플로

"바로 구현"을 막고 **문서 → 승인 → 코드 → 기계적 검증** 순서를 강제하는 5개 스킬(`specify`·`safe-refactor`·`preflight`·`defer`·`retro`)입니다(승인 게이트가 없는 절차형 스킬 `debug`는 아래 별도 서술). 각 스킬의 트리거·산출물은 이 문서에 표로 다시 두지 않습니다 — 최신 표: [`.claude/rules/README-components.md`](../../.claude/rules/README-components.md) "Skills" 절.

**공통 설계 원칙:** 세 스킬(`specify`/`safe-refactor`/`preflight`) 모두 "LLM의 자기 보고를 신뢰하지 않는다"가 핵심입니다 — `safe-refactor`는 "안 바꿨다"는 말 대신 `./gradlew :baro-ai:test`가 실제로 통과하는지, `preflight`는 "테스트 통과했다"는 말 대신 명령을 실제로 돌린 결과를 요구합니다.

**6번째 스킬 — 절차형, 승인 게이트 없음:** `debug`(버그 재현·원인 분리)는 위 5개와 달리 "문서 승인"을 강제하지 않는 반복 조사 절차 캡슐화라 표에서 분리했습니다. 트리거가 "버그 리포트를 받았는가"라는 상황이라 `paths:`(파일 경로 기반) 스코프로는 표현할 수 없어, always-load 규칙이 아니라 스킬(이름+한 줄 설명만 항상 노출되다가 필요할 때 전체 내용을 불러오는 방식)로 뒀습니다.

**3 트랙 × 4 게이트 구조:** 스킬들은 트랙 3개(A 기능=`specify` · B 감사·리팩터=`safe-refactor` · C 버그=`debug`)가 공통 게이트 4개(G1 리서치 · G2 승인 · G3 검증 · G4 회고)를 통과하는 구조로 짜여 있습니다. 게이트 정의 SSOT는 `core-workflow.md`이고, 스킬 문서는 자기 단계가 어느 게이트에 대응하는지만 표시합니다(중복 정의 금지).

**서브에이전트(`.claude/agents/`):** 게이트에서 호출하는 조사·리뷰 전용 에이전트 3개입니다. 셋 다 `tools` 화이트리스트에서 `Edit`/`Write`를 뺐습니다 — 다만 셋 다 `Bash`는 갖고 있어 **물리적으로 완전히 봉쇄되지는 않고**(`sed -i` 등으로 우회 가능), 마지막 한 겹은 각 에이전트 지침의 "수정하지 않는다" 규범이 담당합니다.

- **`researcher`(G1)** — 외부 라이브러리 문서 조사. **로컬 `build.gradle`·jar 실물 확인을 웹 조회보다 먼저** 하도록 강제하고, 블로그·StackOverflow 인용을 금지합니다.
- **`doc-reviewer`(G3)** — 문서 유형·정보 구조·문장 3단계 리뷰(기준: `doc-writing.md`). advisory라 커밋을 막지 않습니다.
- **`spring-reviewer`(G3)** — Java 변경 diff를 트랜잭션 경계·N+1·하네스 계약·레이어 재사용·캡슐화 5축으로 리뷰(기준: `spring-boot-java.md`·`spring-boot-java-design.md`). baro-ai엔 JPA 아키텍처 테스트(ArchUnit)가 없어(§11 참고) 이 에이전트가 코드 규칙 검증의 상당 부분을 떠맡습니다.

### 실제 적용 사례 — 근거 기반 성능 리팩토링 이니셔티브 (2026-09-04~)

`safe-refactor` 스킬을 `cross-cutting`·`recommend`·`search` 세 도메인에 적용한 사례입니다. 근거 기준(`baro-ai-performance-refactor.md`)이 실제로 값을 낸 지점 두 가지입니다.

- **근거 기준이 처음 진단을 뒤집었습니다.** "Gemini 호출에 재시도가 0회 걸려 있다"는 조사 결과를 그대로 구현하지 않고 재확인했더니, 구글 SDK가 **아무 설정 없이도 기본 5회를 이미 재시도**하고 있어 문제의 방향이 정반대("통제되지 않는 재시도")였습니다. "적극적 증거" 요구가 없었다면 잘못된 전제 위에 코드를 얹을 뻔했습니다. 기록: [`cross-cutting/refactor-log.md`](audits/cross-cutting/refactor-log.md).
- **"느릴 것 같다"가 아니라 실제로 죽어 있던 코드를 찾았습니다.** 자동완성 API에 `@Cacheable`이 붙어 있었지만 `@EnableCaching`이 빠져 있어 캐시가 **한 번도 동작한 적이 없었습니다** — grep과 Spring Boot 공식 문서·jar 바이트코드로 교차 확인한 뒤에야 A 항목(반드시 수정)으로 분류했습니다. 기록: [`search/refactor-log.md`](audits/search/refactor-log.md).

`chat`은 목적이 정해지지 않아 보류다(코드도 없다) — 진행 현황은 [`docs/audits/README.md`](audits/README.md).

## 5. 훅 (`.claude/hooks/`) — 프롬프트로 안 되는 결정론적 하한선

규칙 문서는 에이전트가 "읽고 따르는" 소프트 가드레일이지만, 훅은 `.claude/settings.json`에 등록된 이벤트에서 **exit code로 결정론적으로** 실행을 막거나 자동화합니다. 프롬프트 지시만으로는 100% 보장이 안 되는 것들(특히 파괴적 명령)을 여기서 강제합니다. 현재 **등록된 것은 4개**(`deny-out-of-scope-write.sh`·`deny-dangerous-bash.sh`·`deny-db-migration.sh`·`auto-format-java.sh`). `warn-breaking-change.sh`는 파일만 있고 `settings.json`에 없어 동작하지 않는다 — 이벤트·매처·동작 표는 여기서 다시 두지 않습니다: [`.claude/rules/README-components.md`](../../.claude/rules/README-components.md) "Hooks" 절이 SSOT.

**`deny-out-of-scope-write.sh`가 이 저장소 특유의 문제를 푼 방식:** 5인 공용 모노레포에서 `.claude/`가 저장소 루트에 있어야 하는데(§3 참고), 그러면서도 다른 팀원의 모듈(`baro-order` 등)에 하네스가 실수로 손대지 않게 해야 했습니다. 이 훅이 "쓰기는 `baro-ai/`+`.claude/`로 제한, 그 밖은 STOP §0로 확인 후 진행"을 물리적으로 강제합니다.

**인시던트에서 배운 것 — "판단이 필요 없는 곳엔 LLM을 쓰지 않는다":** `warn-breaking-change.sh`는 이 하네스가 이식되며 함께 가져온 설계 원칙을 반영한 훅입니다. 애초에 `agent`-type(서브에이전트가 diff를 읽고 breaking 여부를 판단)으로 만들면 working tree의 무관한 변경까지 오판해 "절대 막지 마라"는 지시에도 커밋을 막는 사고로 이어질 수 있다는 교훈이 있어, 처음부터 `command`-type(exit code로 결정론적 통제)으로 설계했습니다 — "판단"은 LLM에게, "항상 이렇게 동작해야 한다"는 결정론적 스크립트에게 맡기는 경계입니다.

## 6. 실제 인시던트가 규칙이 된 사례들

이 저장소의 규칙 상당수는 처음부터 예측해서 넣은 게 아니라, **실제로 한 번 터진 문제를 겪고 나서 재발 방지용으로 문서에 박아 넣은 것**입니다. "AI 활용이 실제로 어떤 문제를 어떻게 풀었는가"를 포트폴리오에 쓸 때 그대로 쓸 수 있는 근거이기도 합니다.

| 인시던트 | 무슨 일이었나 | 남긴 것 |
|----------|---------------|---------|
| Gemini 재시도 진단 뒤집힘(2026-09-04) | "재시도 0회"라는 1차 조사를 그대로 구현하기 직전, SDK를 다시 확인해 실제로는 기본 5회가 이미 걸려 있음을 발견 | `baro-ai-performance-refactor.md`에 "코드로 확인 가능한 사실만 근거로 인정" 원칙을 명문화. `LlmClientConfig`에 재시도를 명시적으로 재설정(3회, 1~10초) |
| `@EnableCaching` 누락으로 캐시 no-op(2026-09-04) | `@Cacheable`이 있는데 `@EnableCaching`이 없어 자동완성 캐시가 한 번도 동작하지 않고 있었음 | "동작하는 것처럼 보이는 코드"도 실제 설정 존재 여부를 grep으로 확인하는 관행을 감사 체크리스트에 반영 |
| Kafka 컨슈머 주석과 실제 동작 불일치(2026-09-04) | 코드 주석은 "Kafka가 재시도하도록 예외를 던진다"고 적혀 있었지만 실제 재시도 설정은 0회 | `docs` 문구뿐 아니라 **코드 주석도** 실제 동작과 대조 확인 대상이라는 사례로 축적. 컨슈머 재시도 값을 명시적으로 설정 |
| priority must/could 자체 판단(#9·#86, 2026-08-26) | 에이전트가 "새 기능([Feat]) 태그가 붙어 있다"는 표면적 신호만으로 must로 분류했다가 사용자가 could로 정정 | `core-scope.md`에 "사용자 확인 없이 priority 라벨을 부여·변경하지 않는다"를 ⛔로 명문화 |
| `.claude/` 위치 이동(2026-09-04) | 하네스를 처음 `baro-ai/.claude/`에 뒀는데, 세션이 저장소 루트에서 시작되면 하네스가 전혀 로드되지 않는 문제를 실측으로 발견 | `.claude/`를 저장소 루트로 이동 + STOP §0(쓰기 범위 `baro-ai/`+`.claude/`) 신설 + `deny-out-of-scope-write.sh` 백스톱 |
| Antigravity 미러(`.agents/`) frontmatter 문법 충돌(2026-09-04) | "다른 AI 코딩 툴(Google Antigravity)도 이 하네스를 쓰게 심볼릭 링크로 공유하자"는 요청을 받아 진행하다가, Antigravity의 규칙 활성화 문법(`trigger: always_on`/`trigger: glob`+`globs:`)이 Claude Code의 `paths:`와 키 자체가 달라 **순수 심볼릭 링크로는 애초에 두 툴을 동시에 만족시킬 수 없음**을 발견 | 연동 자체를 폐기 — "가능해 보이는 방향"도 실제 스펙을 열어 확인하기 전엔 착수하지 않는다는 사례로 남김 |
| `README.md`/`README-components.md` frontmatter 불일치(2026-09-04) | 위 문서 분리(§3) 직후 두 파일의 `paths:` 표기가 서로 달라(`.claude/` 접두사 유무), 실제로는 같은 시점에 같이 로드되도록 의도했는데 그렇게 안 될 뻔함 | `doc-reviewer` 서브에이전트가 리뷰에서 잡아냄 — 자매 문서를 분리할 때 frontmatter까지 대조 확인하는 게 검증 체크리스트에 필요하다는 근거 |

## 7. API 계약 변경 감지·알림 파이프라인 — 아직 없음

TripFit류 프로젝트에서는 `oasdiff` 기반 스키마 diff·Discord 알림 파이프라인을 갖추기도 하지만, **baro-ai엔 아직 없습니다.** `harness-map.md` "스택 옵션" 표의 "OpenAPI 계약 보호"가 꺼져 있는 상태입니다 — CI에서 계약 검증이 자동으로 도는 게 없다는 뜻입니다.

이유는 단순합니다: baro-ai는 프론트가 아니라 다른 백엔드 모듈(내부 MSA)만 호출하는 구조라, 계약이 깨졌을 때 감지가 늦어도 지금까지는 장애로 직결된 적이 없었습니다. 도입하면 `warn-breaking-change.sh` 훅과 함께 켤 수 있도록 설계는 이미 남겨져 있습니다(`core-guardrails.md` STOP §5, 현재 "옵션 꺼짐" 상태). 필요해지면 그때 켭니다 — 갖추지 않은 것을 갖춘 것처럼 적지 않는다는 원칙(§6 인시던트들과 같은 맥락)에 따라 이 절도 정직하게 "없음"으로 남깁니다.

## 8. 데이터 스키마 — RDB 없음, Elasticsearch 인덱스가 유일한 SSOT

baro-ai에는 RDB·JPA 엔티티가 **아예 없습니다.** 검색·추천·리뷰 전부 Elasticsearch 문서(`ProductDocument`, `UserProfileEmbeddingDocument` 등) 기반이라, "마이그레이션 금지" 정책의 의미가 RDB 프로젝트와 다릅니다 — Flyway/Liquibase가 해당사항 자체가 없고, ES 인덱스 매핑은 코드에서 `IndexOperations.createWithMapping()`으로 생성합니다.

**스키마 SSOT는 코드(Document 클래스) + [`docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md)**입니다. 상용 운영 서버가 내려가 있어 보존할 실데이터가 없으므로, 매핑 변경·인덱스 재생성·전체 재색인이 필요한 개선안도 "마이그레이션 비용" 때문에 기각하지 않습니다(`baro-ai-performance-refactor.md` "데이터 마이그레이션 방침" — 사용자 승인 후 실행). 실제로 재료명을 색인 시점에 미리 계산해 저장하도록 바꾼 변경([`recommend/refactor-log.md`](audits/recommend/refactor-log.md))이 이 방침 위에서 가능했습니다.

## 9. 승인 게이트 — 에이전트가 스스로 결정하지 않는 지점들

- **새 이슈·새 브랜치·새 PR 생성**은 사용자가 이미 명시적으로 요청한 게 아니면 항상 먼저 채팅으로 확인합니다 — 구현·커밋까지 승인받았다고 PR 생성까지 자동으로 승인된 게 아닙니다.
- **DB·인증·다파일 변경**은 `specify` 스킬의 Approved 스펙 없이 구현을 시작하지 않습니다.
- **priority: must/could**는 이슈나 스펙 문구만으로 단정하지 않고, 이슈의 `priority:` 라벨로 확인합니다 — §6의 #9·#86 사고가 이 규칙이 생긴 계기입니다.
- **커밋은 사용자가 명시적으로 요청할 때만** 생성합니다 — 목적·주제별 최대 5개로 분할하며, 작업이 끝나면 에이전트가 묻지 않아도 분할안을 먼저 제안합니다(제안까지가 에이전트 몫, 실행은 승인 후).

## 10. 컨텍스트 격리 — 자기 채점 편향을 피하는 구조

방금 작성한 코드를 같은 대화 맥락에서 스스로 리뷰하면 자기 판단을 재확인하는 편향이 생깁니다. 그래서:

- **광범위 탐색**은 `Explore`/`general-purpose` 서브에이전트로 위임해 메인 대화의 컨텍스트를 아낍니다.
- **PR·머지 전 리뷰**(특히 API·DB 등 Must Have급 변경)는 `code-review`/`simplify` 스킬을 **별도 서브에이전트 컨텍스트**에서 diff와 기준만 보고 판단하게 합니다.
- **아키텍처 감사**(`safe-refactor`)도 1단계를 반드시 신선한 서브에이전트로 돌립니다 — "이번 세션에서 안 건드린 도메인"이어도 같은 대화 맥락이면 편향이 생길 수 있다고 보기 때문입니다.
- 방향이 불확실한 큰 변경은 코드를 먼저 짜지 않고 **Plan Mode**로 설계부터 굳힙니다.

**격리가 손해인 작업도 있습니다.** `retro` 스킬은 위 원칙의 예외로, **메인 대화 컨텍스트에서 실행합니다.** 회고의 입력이 세션 대화 이력 자체인데 서브에이전트는 그 이력에 접근할 수 없고, 세션 트랜스크립트 파일 경로는 공식 규약이 아니라 의존할 수 없기 때문입니다. 같은 저장소 안에서 "격리가 편향을 줄이는 작업"(감사·리뷰)과 "격리가 입력을 없애는 작업"(회고)이 갈린다는 것 — 컨텍스트 격리를 원칙이 아니라 **트레이드오프**로 다뤘다는 게 이 예외의 요점입니다.

## 11. 실행 가능한 규칙 — baro-ai엔 아직 없음

TripFit류 Spring Boot + JPA 프로젝트에서는 레이어 의존 방향·PK 전략 같은 구조 규칙을 `ArchUnit`(`ArchitectureTest.java`)이 `./gradlew test`마다 기계적으로 검증하기도 합니다. **baro-ai엔 이 테스트 자체가 없습니다** — JPA 엔티티가 없어 그 형태의 아키텍처 테스트가 애초에 맞지 않습니다. (자동화 테스트 자체는 2026-09-06에 들어와 지금 10클래스 59건이지만, 전부 순수 계산·서비스 경계를 보는 단위 테스트라 구조 규칙을 강제하지는 않습니다.)

그 공백을 지금은 `spring-reviewer` 서브에이전트(§4)가 사람이 짠 규칙(`spring-boot-java.md`·`spring-boot-java-design.md`)을 매 리뷰마다 다시 읽고 대조하는 방식으로 메우고 있습니다 — 기계적 검증보다 느리고 매번 비용이 들지만, 지금 규모에서는 ArchUnit을 새로 도입하는 비용을 정당화할 근거가 아직 없습니다. 테스트 인프라 자체가 생기면 재검토 대상입니다.

## 12. 외부 도구 채택 기준 — 안 쓰기로 한 것도 기록

Superpowers 같은 서드파티 플러그인(`brainstorming`, `writing-plans`, `systematic-debugging` 등)을 도입할지 감사했습니다. 결론은 **미채택** — Claude Code 기본 기능(Plan Mode, `Agent` 서브에이전트, `code-review`/`simplify` 스킬)으로 이미 충분히 대체되고, 유일한 gap이었던 `systematic-debugging`도 자체 `debug` 스킬로 충분해 플러그인 설치 비용을 정당화하지 못했습니다. "기능이 있다는 이유만으로 쓴다"를 명시적 배제 조건으로 못 박아 둔 게 핵심 — 도구 자체보다 **도구를 안 쓰기로 한 판단 근거를 기록**하는 게 이 저장소의 스타일입니다. SSOT: [`.claude/rules/core-tools.md`](../../.claude/rules/core-tools.md).

## 13. 숫자로 보는 활용도

2026-09-04 기준 실측:

- 전체 커밋 1,460개 중 `baro-ai/` 경로를 다루는 커밋이 **124개**. `Co-Authored-By: Claude` 트레일러가 붙은 커밋은 저장소 전체 기준 **16개**이고, 그중 **15개**가 `baro-ai/`를 건드림
- 스펙 문서 6개(`docs/specs/` — search·recommend(2)·embedding·event-log·chat), ADR 3개
- 감사 문서 5건(`cross-cutting` 1·`recommend` 2·`search` 2) — 근거 기반 성능 리팩토링 이니셔티브(§4) 진행분
- 하네스 구성 요소: always-load 규칙 9개 + path-scoped 규칙 9개, 스킬 6개, 서브에이전트 3개, 훅 5개 (정확한 목록 SSOT: [`.claude/rules/README.md`](../../.claude/rules/README.md))

이 수치는 "AI가 얼마나 많이 타이핑했는가"가 아니라 — 위 §1~§12의 장치들이 실제로 매 세션 반복 적용됐다는 근거로 읽는 게 맞습니다. 코드 자체뿐 아니라 이 문서를 포함한 스펙·ADR·규칙 문서 대부분도 AI가 초안을 작성하고 사람이 승인·확정하는 방식으로 만들어졌습니다.

## 14. 한 줄 요약 (포트폴리오용)

> "AI에게 코드를 맡긴 게 아니라, AI가 지킬 수밖에 없는 문서·규칙·훅을 먼저 설계했다. 실제로 그 설계가 뚫렸던 지점(§6)마다 다시 규칙을 보강했고, 이 문서 자신이 드리프트됐던 것조차 그 절차(STOP §1.5)로 되돌렸다."

관련 문서: [`AGENTS.md`](../AGENTS.md) · [`.claude/rules/README.md`](../../.claude/rules/README.md) · [`docs/README.md`](README.md)
