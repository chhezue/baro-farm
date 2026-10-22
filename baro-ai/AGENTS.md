# baro-ai

바로팜(baro-farm) MSA의 AI 모듈 — 상품 검색, 개인화·레시피 추천, 리뷰 감정분석/요약, 사용자·상품 임베딩을 담당하는 Spring Boot 서비스. AI 에이전트가 작업할 때 참고하는 프로젝트 지도입니다.

> baro-farm-be는 5인이 같이 쓰는 모노레포다. `.claude/`는 **저장소 루트**에 있다 — 서브디렉터리(`baro-ai/.claude/`)에 두면 루트에서 시작한 세션에 하네스가 전혀 로드되지 않아서다(2026-09-04 실측). 다만 이 하네스가 다루는 대상은 여전히 **`baro-ai/`(+ 하네스 자체인 `.claude/`)뿐**이다 — 다른 모듈(`baro-order` 등)은 참조·조사만 하고, 쓰기는 `../.claude/rules/core-guardrails.md` STOP §0 + `deny-out-of-scope-write.sh` 훅으로 막혀 있다(정말 필요하면 먼저 사용자에게 확인).
>
> **운영 상태(2026-09-04): dev 모드** — 운영 서버·배포 환경이 전부 내려가 있고 실사용자·실트래픽이 없다. 에이전트는 이걸 전제로 판단한다:
> - ES 인덱스 재생성·전체 재색인·매핑 변경(임베딩 **차원 변경** 포함)을 데이터 마이그레이션 부담 없이 결정할 수 있다. "재색인 비용" 을 반대 근거로 들지 말 것.
> - 성능 판단에 **실측 트래픽을 쓸 수 없다** — 구조적 근거(요청 키 공간, 호출 횟수, 직렬/병렬 여부)로 판단한다. 근거 없는 "느릴 것 같다"는 금지 (`docs/decisions/001-autocomplete-caching.md`가 이 방식의 예시).
> - **파괴적 작업이 무해하다는 뜻은 아니다** — 삭제·재색인·인덱스 드롭은 여전히 사용자 승인 후 실행한다.
>
> **현재 이니셔티브**: 근거 기반 성능 리팩토링(캐싱·프롬프트 튜닝·비동기 처리) 진행 중 — 범위·근거 기준은 [`.claude/rules/baro-ai-performance-refactor.md`](../.claude/rules/baro-ai-performance-refactor.md)가 SSOT. 지금은 Track A(기능 개발)가 아니라 **Track B(`safe-refactor`)**가 기본이다.

## How We Build

**0. 문서·구현 정합 (최우선)** — 스펙·결정·문서 간 값·계약이 어긋나면 **구현하지 말고 사용자에게 질문**. 상세: 저장소 루트 `.claude/rules/core-guardrails.md` ⛔ STOP. (2026-09-04 기준 baro-ai README가 실제로 미구현 Redis 캐시를 사실처럼 서술했던 이력이 있었음 — 이 규칙이 특히 중요한 이유)

기획·검증 기준을 먼저 고정하고, 그에 맞춰 구현합니다. **기획**은 `docs/product/mvp.md`(+ 상세 `docs/FEATURES.md`), **기능 설계**는 `docs/specs/`, **아키텍처 선택**은 `docs/decisions/`에 둡니다. DB(ES 매핑)·다파일 변경 시 스펙 필수 (`core-workflow` 규칙). 구현 후 `./gradlew :baro-ai:test`와 PR로 검증합니다. **우선순위 SSOT는 아직 없음** — GitHub 이슈 라벨로 판단.

## Tech Stack

- Java 21
- Spring Boot (Spring AI 1.1.8 — Google Gemini Chat/Embedding, `docs/decisions/003-gemini-migration.md`)
- Gradle (멀티 모듈, `:baro-ai` 서브프로젝트)
- Elasticsearch — 검색/추천/로그/리뷰 저장소 (RDB·JPA 없음)
- Kafka — cart/order/product/review 이벤트 소비 + review-summary 발행
- 테스트: JUnit 5 설정만 존재, **테스트 클래스 0개** (대공사 대상 — 새 로직에 테스트 없이 얹지 말 것)
- 배포: 저장소 루트 `docs/BARO_DEPLOY_TEST_GUIDE.md`, `k8s/` (baro-ai 스코프 밖)

## Conventions

- 패키지: `com.barofarm.ai.{도메인}.{presentation|application|domain|infrastructure}` — 도메인: `search`·`recommend`·`embedding`·`review`·`log`·`event`·`chat`(스텁만 존재, 미구현)
- API 응답은 `baro-common`의 `ResponseDto`/`CustomPage` 사용
- 주석: 저장소 루트 `.claude/rules/java-comments.md` (메서드 역할 `//` 한 줄, WHAT 설명 금지)
- 범위 밖 리팩터링·포맷 변경 금지 — 요청된 작업만 수정
- **절대 마음대로 커밋하지 않는다** — 사용자가 명시적으로 요청할 때만 실행
- 목적·주제별로 나눠 **최대 5개**까지 커밋할 수 있다
- **작업이 끝나면 묻지 않아도 커밋 분할안을 먼저 제안한다** (제안 ≠ 실행 — 승인 후 실행)
- **커밋 컨벤션 SSOT 없음** (`.github/CONTRIBUTING.md` 미도입 — 모노레포라 서브폴더에 둬도 GitHub이 인식 못 함). 기존 로그 관례를 따른다: `[Type] #이슈번호 - 한글 설명` (예: `[Feat] #116 - 결제 멱등성 보장 로직 추가`)
- **문서·스펙·결정 정합 최우선** — 문서 간·문서-구현 간 충돌 시 질문 없이 구현·기본값 변경 금지
- **DB:** RDB 마이그레이션 개념 자체가 없음(전부 Elasticsearch 문서) — Flyway/Liquibase 작성 금지
- **레거시:** 현행과 다른 코드·호환 레이어·교체된 구 메서드는 같은 PR에서 즉시 삭제
- 비밀값(`.env`, API 키)은 코드·커밋에 포함하지 않음

## Important Paths

| 경로 | 용도 |
|------|------|
| `src/main/java/com/barofarm/ai/` | 애플리케이션·도메인 코드 |
| `src/main/resources/` | `application.yml` 하나(포트 8092) — 프로필별 파일 없음. 카테고리별 설정(`recipe-recommend.yml` 등)은 여기서 import |
| `src/test/` | 비어 있음 — 테스트 클래스 0개(쓰이지 않던 `application-test.yml`은 2026-09-05 삭제) |
| [`.claude/rules/harness-map.md`](../.claude/rules/harness-map.md) | **슬롯 SSOT** — 규칙이 부르는 역할 → 실제 경로 |
| [`docs/README.md`](docs/README.md) | **문서 SSOT** — 기능·아키텍처 개요 |
| [`.claude/rules/README.md`](../.claude/rules/README.md) | 규칙·스킬·훅 구조 |

## Product Context

- **클라이언트**: 없음 — 내부 MSA 전용. 다른 모듈이 Feign(`CartClient`)·Kafka 이벤트로 호출. 상세: [`docs/product/platform.md`](docs/product/platform.md)
- 새 기능 구현 전 [`docs/product/mvp.md`](docs/product/mvp.md)(In/Out) + [`docs/FEATURES.md`](docs/FEATURES.md)(상세)로 범위 확인
- 용어는 [`docs/product/glossary.md`](docs/product/glossary.md) 기준
- ES 스키마는 [`docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md`](docs/ELASTICSEARCH_SEARCH_EMBEDDING_ARCHITECTURE.md) 참조

## Workflow

1. **계획** — 큰 기능은 Plan 모드 또는 `specify` 스킬로 `docs/specs/`에 스펙 작성
2. **승인** — 스펙 확인 후 구현
3. **실행** — Agent 모드, 필요 시 `Agent` 서브에이전트
4. **검증** — `./gradlew :baro-ai:test` 통과, 변경 범위 최소화

상세: 저장소 루트 `.claude/rules/core-workflow.md` (3 트랙 × 4 게이트) · 도구 매핑 `core-tools.md`

## Commands

```bash
# 최초 셋업 (저장소 루트에서)
docker-compose -f docker-compose.data.yml up -d

# 로컬 실행
./gradlew :baro-ai:bootRun

# 테스트 / 빌드 / 포맷
./gradlew :baro-ai:test
./gradlew :baro-ai:build
./gradlew :baro-ai:spotlessApply
```

`scripts/install-git-hooks.sh`는 **설치하지 않음** — baro-ai가 독립 git 저장소가 아니라 모노레포 전체 커밋에 영향을 주기 때문 (2026-09-04 사용자 결정, `harness-map.md` "이 프로젝트 특이사항" 참고).

## 금기사항

- `git push --force` (main/master)
- `rm -rf` 등 파괴적 shell 명령 (`deny-dangerous-bash.sh` 훅이 차단)
- 테스트 없이 핵심 로직만 추가 (요청이 없는 한) — baro-ai는 이미 테스트 0개라 특히 주의
