# Harness Map — 이 프로젝트의 슬롯

하네스 규칙(`core-*.md`)은 **경로·명령을 직접 부르지 않는다.** 대신 `{{역할 이름}}`으로 부르고, 이 파일이 역할 → 실제 경로·명령을 잇는다.

**새 프로젝트에 하네스를 붙일 때 채우는 건 이 파일 하나다.** 규칙 파일은 건드리지 않는다.

채운 예시: [`examples/tripfit/harness-map.md`](../../examples/tripfit/harness-map.md)

## 이 프로젝트 특이사항 (2026-09-04)

- **모노레포 하위 모듈, 하네스는 저장소 루트**: `baro-farm-be`는 5인이 같이 쓰는 Gradle 멀티 모듈 저장소이고, `.git`은 저장소 루트 하나뿐이다. `.claude/`는 처음엔 `baro-ai/.claude/`(서브트리)에 뒀었는데, Claude Code가 프로젝트 설정을 **세션이 시작된 작업 디렉터리 기준**으로만 로드해서 저장소 루트에서 세션을 열면 전혀 로드되지 않는 문제가 있었다(2026-09-04 실측 — 세션의 스킬·에이전트 목록에 이 하네스의 것이 하나도 안 뜸). 그래서 **저장소 루트로 이동**했다. 다만 규칙 본문·강제 대상은 여전히 **`baro-ai/`(+ 하네스 자체인 `.claude/`)로 제한** — 참조·조사는 자유롭게 다른 모듈까지 하되, 쓰기는 벗어나면 STOP & 확인한다(`core-guardrails.md` STOP §0, 결정론적 백스톱: `.claude/hooks/deny-out-of-scope-write.sh`). 다른 모듈(`baro-order`, `baro-payment` 등)에 이 하네스의 스택 특정 규칙(ErrorCode 패턴·테스트 명령 등)을 강제하지 않는다 — STOP §0 4번.
- **`.github/CONTRIBUTING.md` 없음** — 저장소 루트엔 `.github/`가 실제로 있지만(워크플로·이슈 템플릿) `CONTRIBUTING.md`는 아직 없다. (하네스가 `baro-ai/.claude/`에 있던 시절엔 서브폴더 `.github`를 가리켜 GitHub이 아예 인식 못 하는 구조적 문제였는데, 루트 이동으로 그 문제 자체는 해소됐다 — 이제 파일만 없는 상태.) 브랜치명·커밋 컨벤션 SSOT가 아직 없다는 뜻 — 규칙 본문에서 `.github/CONTRIBUTING.md`를 가리키는 부분은 지금은 대상 문서 없음으로 취급.
- **`scripts/git-hooks` 미설치** — `install-git-hooks.sh`는 `.git/hooks`를 저장소 루트 기준으로 찾는데, 설치하면 이 저장소의 **모든** 커밋(다른 모듈 포함)에 훅이 걸린다. 5인 공용 저장소라 일단 설치를 보류했다 (2026-09-04 사용자 결정). 나중에 필요하면 재논의.

## 규칙

1. **빈 슬롯은 `(없음)`으로 명시한다.** 비워두지 않는다 — 에이전트가 "아직 안 정했다"와 "이 프로젝트엔 그 개념이 없다"를 구분해야 한다.
2. `(없음)`인 슬롯을 규칙이 요구하면 **에이전트는 멈추고 사용자에게 묻는다** (`core-guardrails.md` ⛔ §1.4와 동일).
3. 슬롯을 추가하려면 `core-*.md`에서 실제로 `{{...}}`를 쓰는 곳이 있어야 한다. 쓰지 않는 슬롯은 만들지 않는다.

## 경로 슬롯

하네스와 **함께 배달되는** 문서(`.github/CONTRIBUTING.md`, `docs/harness/`, `docs/templates/`, `docs/audits/harness-retro.md`)는 슬롯이 아니다 — 경로가 고정이므로 규칙이 직접 부른다.

| 슬롯 | 이 프로젝트의 경로 | 쓰는 곳 |
|------|-------------------|---------|
| `{{스펙 저장소}}` | `baro-ai/docs/specs/{domain}/` | `core-workflow` A 트랙 · `specify` 스킬 |
| `{{현재동작 요약}}` | (없음) — 필요해지면 `baro-ai/docs/how-it-works.md` 신설 | `core-guardrails` ⛔ §6 |
| `{{우선순위 SSOT}}` | (없음) — baro-ai 전용 마일스톤 문서 없음 | `core-workflow` 진입 · `core-scope` |
| `{{아키텍처 개요}}` | `baro-ai/docs/README.md`(아키텍처 설계 절) | `core-guardrails` ⛔ §1·§3 |
| `{{스키마 SSOT}}` | `baro-ai/docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md` (ES 인덱스 매핑 — RDB 스키마 아님) | `core-followup` 💡 ERD |
| `{{API 응답 규격}}` | `baro-common/src/main/java/com/barofarm/dto/ResponseDto.java` · `CustomPage.java` | `core-guardrails` ⛔ §2 |
| `{{API 문서}}` | springdoc 기본 경로(`/v3/api-docs`, `/swagger-ui/index.html`) — `SwaggerConfig.java`는 최소 설정만 있고 별도 문서 URL 미고정. 게이트웨이 경유 시 경로 재확인 필요 | `core-guardrails` ⛔ §5 |
| `{{제품 범위}}` | `baro-ai/docs/product/mvp.md` (상세 근거는 `baro-ai/docs/FEATURES.md`) | `core-workflow` 진입 |
| `{{클라이언트 전제}}` | `baro-ai/docs/product/platform.md` — 외부 클라이언트 없음, 내부 MSA(다른 모듈의 Feign/Kafka 호출)만 존재 | `core-workflow` 진입 · `specify` 스킬 |
| `{{용어집}}` | `baro-ai/docs/product/glossary.md` | `core-scope` |
| `{{결정 기록}}` | `baro-ai/docs/decisions/` | `core-guardrails` ⛔ §1 · `core-workflow` 진입 |
| `{{감사 로그}}` | `baro-ai/docs/audits/` | `core-workflow` G4 · `safe-refactor` 스킬 |
| `{{배포 SSOT}}` | (없음 — baro-ai 스코프 밖) 저장소 루트 `docs/BARO_DEPLOY_TEST_GUIDE.md`·`k8s/`에 배포 문서가 있으나 이 하네스 스코프(baro-ai) 밖 | `core-guardrails` ⛔ §1 |

## 명령 슬롯

| 슬롯 | 이 프로젝트의 명령 | 쓰는 곳 |
|------|-------------------|---------|
| `{{테스트 명령}}` | `./gradlew :baro-ai:test` | `core-workflow` 구현·G3 · `preflight` 스킬 |
| `{{빌드 명령}}` | `./gradlew :baro-ai:build` | `preflight` 스킬 |
| `{{포맷 명령}}` | `./gradlew :baro-ai:spotlessApply` (훅은 파일 단위로 `-PspotlessIdeHook` 사용 — 이미 일치, 수정 불필요) | `auto-format-*` 훅 — **훅 스크립트는 명령을 직접 박아 쓴다.** 여기 적은 값과 `.claude/hooks/auto-format-*.sh` 본문을 함께 고칠 것 |
| `{{의존성 조회}}` | `./gradlew :baro-ai:dependencies` | `core-workflow` G1 |

## 스택 옵션

경로로 뺄 수 없는 것들. **프로젝트 상황을 전제한 정책**이라 켜고 끄는 판단이 필요하다.

| 옵션 | 이 프로젝트 | 근거 |
|------|-------------|------|
| **DB 마이그레이션 금지** | ☑ 켬 | baro-ai에는 RDB/JPA 엔티티가 없다 — 검색·추천·리뷰 전부 Elasticsearch 문서(`ProductDocument` 등) 기반. Flyway/Liquibase 자체가 해당 없음. ES 인덱스는 코드에서 `IndexOperations.createWithMapping()`으로 생성 |
| **에러코드 enum 동시갱신** | ☑ 켬 | `EmbeddingErrorCode`·`RecommendErrorCode` + `CustomException` 패턴이 이미 있음 |
| **권한·활동 어노테이션(AOP)** | ❌ 끔 | `@Aspect`·커스텀 권한 어노테이션 없음. 인증은 게이트웨이가 처리하고 baro-ai는 `X-User-Id` 헤더를 신뢰하는 방식 |
| **OpenAPI 계약 보호** | ❌ 끔 (일단) | `oasdiff`·CI 계약 검증 미구축. 나중에 도입하면 켜고 `warn-breaking-change.sh` 훅 활성화 |
| **생성 문서 검증** | ☑ 켬 | 이 세션에서 실제로 문서-코드 drift(README가 미구현 Redis 캐시·바뀐 API 파라미터를 사실처럼 서술)를 발견한 이력이 있어, Swagger 실물(`/v3/api-docs`) 확인 규율이 특히 필요한 프로젝트 |
| **스택 함정 메모** | ❌ 끔 | Java 21 / Spring Boot 표준 사용, 현재까지 알려진 버전 함정 없음 |
| **스택 리뷰어** | ☑ 켬 | `spring-reviewer` — API·DB(ES 매핑) 변경 시 유지 |

## 스택 팩

특정 언어·프레임워크 전용 규칙. 해당 스택이 아니면 **파일째 지운다.**

| 팩 | 파일 | 대상 |
|----|------|------|
| Java · Spring Boot | `spring-boot-java.md` · `java-comments.md` · `openapi-conventions.md` · `testing.md` · `agents/spring-reviewer.md` | Java 21 / Spring Boot / Gradle. **JPA는 baro-ai에 거의 해당 없음**(Elasticsearch 중심) — 팩 본문 중 JPA 특정 조언은 참고만, 강제 아님 |

인프라 성격 규칙(클라이언트 전제·배포 토폴로지)은 스택이 아니라 **저장소 고유 사실**이라 팩에 넣지 않았다 — 견본 `examples/tripfit/client-platform.md`·`deployment.md`를 보고 프로젝트별로 새로 쓴다.

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | `.claude/`를 `baro-ai/.claude/`에서 **저장소 루트**로 이동 — 서브트리에 두면 루트에서 시작한 세션에 하네스가 전혀 로드되지 않는 문제 발견. `core-guardrails.md` STOP §0(쓰기 범위 `baro-ai/`+`.claude/` 제한) + `deny-out-of-scope-write.sh` 훅 신설로 다른 모듈에 대한 영향 차단 |
| 2026-09-05 | 초안 — TripFit 하네스에서 슬롯 17개(경로 13·명령 4)·옵션 7개 추출 |
| 2026-09-04 | baro-ai에 하네스 이식, 전 슬롯·옵션 1차 채움 (git-hooks·`.github/CONTRIBUTING.md`는 모노레포 제약으로 보류 — 위 "이 프로젝트 특이사항" 참고). 테스트 0개·챗봇 미구현 등은 `docs/audits/`에 후속 감사로 남길 예정 |
| 2026-09-04 | search·recommend·embedding·event-log·chat 5개 도메인 스펙(`docs/specs/`) 역작성 + `docs/product/mvp.md`·`glossary.md`·`platform.md` 신설. `{{제품 범위}}`·`{{클라이언트 전제}}`·`{{용어집}}` 슬롯을 새 문서로 갱신 |
