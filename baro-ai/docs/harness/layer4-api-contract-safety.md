# Layer 4 — API Contract Safety (프론트와의 계약을 독립 검증)

> **⚠️ 이 레이어는 baro-ai에 설치돼 있지 않습니다.** [`harness-map.md`](../../../.claude/rules/harness-map.md) 스택 옵션의 "OpenAPI 계약 보호"가 꺼져 있고, `core-guardrails.md` STOP §5도 그에 맞춰 비어 있습니다. 아래에 나오는 CI 워크플로·스크립트·기준 스냅샷은 **이 저장소에 존재하지 않으며**(그래서 링크가 아니라 파일명만 적었습니다), 이 하네스를 물려받은 원본 저장소의 구성입니다.
>
> 이 문서는 **도입을 결정할 때 읽는 설계 배경**으로 남겨 둡니다. 실제로 켜려면 아래 구성요소를 이 저장소에 새로 만들고, `harness-map.md`에서 옵션을 켜고, STOP §5와 `warn-breaking-change.sh` 훅 등록을 함께 되살려야 합니다.

> 분류: **CI 워크플로 + shell script** (`.github/workflows/ci-cd.yml` + `scripts/notify-api-breaking-change.sh`) · 짝이 되는 규칙: `core-guardrails.md` STOP §5 · 대응 다이어그램: "API Contract Safety"

## 1. 기본 사항

### 이 레이어가 나타내는 것

프론트엔드가 **별도 저장소·별도 배포**(Vercel)라, 백엔드가 API 계약을 조용히 깨면 즉시 장애로 이어집니다. 그런데 계약 유지 여부를 Claude Code의 자기 보고에 맡길 수 없습니다. 그래서 **저장소 밖(GitHub Actions)에서 독립적으로** 계약 변경을 감지하고 프론트에 알립니다.

핵심 명제: **에이전트가 관여할 수 없는 곳에 검증을 둔다.**

### 파일 위치와 분류

| 구성요소 | 파일 | 분류 | 역할 |
|---|---|---|---|
| 트레일러 규칙 | [`.claude/rules/core-guardrails.md`](../../../.claude/rules/core-guardrails.md) STOP §5 | rule | 커밋 시 `Breaking-Change-Reason:` 작성 강제 |
| 로컬 경고 훅 | [`.claude/hooks/warn-breaking-change.sh`](../../../.claude/hooks/warn-breaking-change.sh) | hook | 트레일러 누락 시 advisory 경고 (막지 않음) |
| CI 파이프라인 | `.github/workflows/ci-cd.yml` (**없음** — 이 저장소는 모듈별 `baro-*-deploy.yml`만 있다) | CI | 스펙 export → base 비교 → 알림 |
| 감지·알림 스크립트 | `scripts/notify-api-breaking-change.sh` (**없음**) | script | 3중 감지 + Discord 웹훅 |
| 기준 스냅샷 | `docs/api/openapi.json` (**없음**) | 산출물 | `main` push마다 CI가 자동 갱신 (**손편집 금지**) |
| 상세·사고 이력 | `docs/api/README.md` (**없음**) | 문서 | 트레일러 형식·사각지대 기록 |

> `warn-breaking-change.sh` 훅 파일 자체는 있지만 `settings.json`에 등록돼 있지 않아 동작하지 않습니다.

## 2. 언제 발동하고, 어떤 흐름을 타는가

### 방어선 순서 (다이어그램의 핵심)

**CI는 1차 방어선이 아닙니다.** STOP §5 원문은 "CI의 `oasdiff` 판정을 **기다리지 않고** 변경을 만드는 커밋 시점에 직접 기록한다"입니다. CI는 놓쳤을 때의 안전망입니다.

```
[1차] 커밋 작성 시점 — 사람/에이전트가 직접
      DTO·enum·ErrorCode·경로를 건드리는 커밋을 만들기 "직전"에
      STOP §5를 재확인하고 커밋 본문에 트레일러 추가
        Breaking-Change-Reason: <한 줄 사유>
      ⚠ 대상은 oasdiff의 breaking 카테고리보다 넓음 —
        optional 필드 추가, enum 값 추가도 포함

[2차] 로컬 훅 — warn-breaking-change.sh (Layer 3)
      트레일러 없이 커밋하려 하면 stderr 경고. 막지는 않음

[3차] CI — 저장소 밖에서 독립 검증  ← 아래 상세
```

### CI 단계별 흐름과 실제 동작

```
push 또는 pull_request
  ↓
[1] 현재 코드 기준 OpenAPI 스펙 생성
     ./gradlew test --tests "...OpenApiSpecExportTest"
     → build/openapi/openapi.json
  ↓
[2] oasdiff 설치 + base 스냅샷 확보
     curl .../main/docs/api/openapi.json -o base-openapi.json
     실패 시 exists=false → "최초 실행, breaking 비교 스킵"
  ↓
[3] 비교 대상 커밋 범위 결정
     PR이면   base.sha..head.sha
     push면   event.before..event.after
  ↓
[4] 중복 알림 방지 조건 판정
     실행 조건: base 스냅샷이 있고, AND
       · pull_request 이벤트이거나
       · push인데 커밋 메시지가 "Merge pull request #"로 시작하지 않을 때
     ⚠ 이 휴리스틱의 근거: CONTRIBUTING이 Squash를 금지하고
       "Create a merge commit"만 허용하므로 PR 병합은 항상 그 메시지 형식을 남김
     ⚠ 사각지대(#67): PR 없이 로컬 merge 후 main에 직접 push하면
       pull_request 이벤트 자체가 없어 알림이 안 나갈 수 있었음
       → 위 "Merge pull request # 로 시작하지 않는 push는 알림"이 그 보정
  ↓
[5] notify-api-breaking-change.sh — 3중 감지
  ↓
[6] Discord #frontend 웹훅 발송
     ⚠ 스크립트는 항상 exit 0 — CI를 실패로 만들지 않고 deploy도 막지 않음
  ↓
[7] main push면 스냅샷 갱신
     jq . build/openapi/openapi.json > docs/api/openapi.json
     변경 없으면 커밋 스킵, 있으면 github-actions[bot]이 커밋
```

### 3중 감지 — `oasdiff` 하나로 안 되는 이유

`oasdiff`는 **OpenAPI 스키마 diff만** 봅니다. 스키마 밖에서 계약이 깨지는 경우가 실제로 있었기 때문에 감지를 겹쳐 놨습니다.

| 감지 | 방법 | 왜 필요한가 (실제 사고) |
|---|---|---|
| **1차 — 스키마** | `oasdiff breaking --format json` + changelog INFO에서 `new-optional-request-property`·`response-optional-property-added` 추출 | 필드 제거·필수화 등 정통 breaking. optional 추가도 프론트가 알아야 해서 별도로 골라냄 |
| **2차 — 트레일러 독립 추출** | `oasdiff 결과와 무관하게` 커밋 범위에서 `Breaking-Change-Reason:` 스캔 (줄바꿈 wrap된 값까지 접어서 병합) | **#64** — Apple `authorizationCode`가 필드는 optional인데 서비스 로직이 조건부로 필수화. 스키마 diff엔 안 잡힘 |
| **2차 — ErrorCode 신규/변경** | `**/*ErrorCode.java` diff에서 `NAME(HttpStatus...` 한 줄 컨벤션으로 추출. **`-`(제거) 줄에도 같은 이름이 있으면 "변경", 없으면 "신규"로 구분** | **#75** — `+` 줄만 보면 "기존 상수의 HttpStatus만 바뀐 것"도 "완전 신규"로 오판. `AUTH_FORBIDDEN`에서 재현 확인 |
| **3차 — 상태 코드 교차검증** | `ErrorCode.getHttpStatus()` vs Controller `@ApiResponse(responseCode=...)` 리터럴 대조. **diff가 아니라 현재 트리 전체를 매번 검사** | `ErrorResponse.code`가 String이라 컴파일 타임 연결이 없음. enum만 바꾸고 컨트롤러를 깜빡하면 위 감지 둘 다 못 잡음. "enum은 그대로 두고 컨트롤러만 잘못 손댄" 경우까지 잡으려면 커밋 범위로는 부족 |

이 3차 감지가 성립하는 이유는 [`openapi-conventions.md`](../../../.claude/rules/openapi-conventions.md)가 `@ApiResponse` description에 `NAME — 설명` 형태로 ErrorCode 이름을 쓰도록 고정해뒀기 때문입니다 — **컨벤션이 곧 파싱 가능한 인터페이스**가 된 사례입니다.

## 3. 프론트와의 계약 전달 방식

- 프론트는 `docs/api/openapi.json`을 **인증 없이 raw fetch**해 codegen 소스로 사용
- enum 값 목록의 SSOT는 `/v3/api-docs`(Swagger) — enum 전용 md 파일을 따로 두지 않음(이중 관리·드리프트 방지)
- 계약 변경 알림은 Discord `#frontend` embed로 전달, 트레일러가 없으면 `⚠️ 사유 미기재` 문구가 대신 들어감

## 4. AI-native 관점에서의 강조 포인트

**Layer 3 다음으로 강한 신호입니다.**

| 순위 | 강조할 것 | 근거 |
|---|---|---|
| 1 | **도구의 실패 모드를 알고 그 밖을 직접 메웠다** | `oasdiff`를 갖다 쓴 게 아니라, "스키마 diff로는 못 잡는 게 있다"는 걸 실제 사고(#64)로 확인하고 2·3차 감지를 직접 구현. 도구 사용자와 도구 한계 이해자의 차이 |
| 2 | **#75 오분류 수정 — 정확도를 위해 diff를 두 방향으로 읽음** | `+`만 보면 오탐. `-`와 교차해야 "신규 vs 변경"이 갈림. 알림이 틀리면 팀이 알림을 무시하게 된다는 걸 알고 정확도에 투자 |
| 3 | **CI를 실패시키지 않는 선택** | 계약 변경은 "막을 일"이 아니라 "알릴 일"이라는 판단. deploy를 막지 않고 Discord로 넘김 — 팀 워크플로를 이해한 설계 |
| 4 | **컨벤션을 파싱 가능한 인터페이스로 설계** | `@ApiResponse` description의 `NAME — 설명` 형식 고정 덕분에 3차 감지가 성립. 문서 컨벤션이 자동화의 입력이 됨 |
| 5 | 사각지대를 문서에 남긴 것 (#67) | 해결하지 못한 한계(main 직접 push)를 숨기지 않고 `docs/api/README.md`에 기록 |

### 면접에서 쓸 한 문장

> "프론트가 다른 저장소라 계약이 조용히 깨지면 바로 장애입니다. `oasdiff`를 붙였는데 필드가 optional인 채로 로직만 조건부 필수가 된 케이스를 못 잡는 걸 실제로 겪어서, 스키마 diff 밖에 커밋 트레일러·ErrorCode diff·상태코드 교차검증 3중 감지를 더 얹었습니다. 대신 CI를 실패시키진 않습니다 — 배포를 막는 게 아니라 프론트에 알리는 게 목적이라서요."
