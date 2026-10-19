# Layer 3 — Deterministic Guardrails (LLM이 개입하지 않는 강제 통제)

> 분류: **hook** (`.claude/settings.json` + `.claude/hooks/*.sh`) · 강제 수단: **shell exit code** · 대응 다이어그램: "Layer 3: Deterministic Guardrails"

## 1. 기본 사항

### 이 레이어가 나타내는 것

Layer 1(규칙)·Layer 2(스킬)는 결국 **에이전트가 읽고 따라줘야** 작동합니다. 되돌리기 어려운 명령은 그 신뢰에 맡길 수 없습니다. 이 레이어는 에이전트의 판단을 아예 경유하지 않고, 도구 호출 **직전에 shell 스크립트가 exit code로** 실행 여부를 결정합니다.

핵심 설계 명제: **"판단이 필요한 곳엔 LLM을, 항상 똑같이 동작해야 하는 곳엔 스크립트를."**

### 파일 위치와 분류

| 훅 | 파일 | 이벤트 | 매처 | 동작 |
|---|---|---|---|---|
| 수정 범위 밖 차단 | [`.claude/hooks/deny-out-of-scope-write.sh`](../../../.claude/hooks/deny-out-of-scope-write.sh) | `PreToolUse` | `Write\|Edit` | **exit 2 (차단)** |
| 위험 명령 차단 | [`.claude/hooks/deny-dangerous-bash.sh`](../../../.claude/hooks/deny-dangerous-bash.sh) | `PreToolUse` | `Bash` | **exit 2 (차단)** |
| ~~Breaking-Change 경고~~ | [`.claude/hooks/warn-breaking-change.sh`](../../../.claude/hooks/warn-breaking-change.sh) | — | — | **미등록 — 동작하지 않는다.** 파일만 있고 `settings.json`에 없다([Layer 4](layer4-api-contract-safety.md) 참고) |
| DB 마이그레이션 차단 | [`.claude/hooks/deny-db-migration.sh`](../../../.claude/hooks/deny-db-migration.sh) | `PreToolUse` | `Write\|Edit` | **exit 2 (차단)** |
| Java 자동 포맷 | [`.claude/hooks/auto-format-java.sh`](../../../.claude/hooks/auto-format-java.sh) | `PostToolUse` | `Edit\|Write` | **항상 exit 0** (non-blocking) |

등록 위치: [`.claude/settings.json`](../../../.claude/settings.json) (팀 공통, 버전 관리됨)

**파일명이 강도를 말합니다 (2026-09-04 `#128`에서 규칙화):** 차단은 `deny-`, 경고는 `warn-`, 자동 실행은 `auto-`. 이전 이름(`block-dangerous.sh`·`format-java.sh`)은 그 훅이 커밋을 막는지 그냥 도와주는지를 파일명만 보고 알 수 없었습니다. 아래 fail-closed / fail-open 비대칭이 이름에 그대로 드러나도록 맞춘 것입니다 — `deny-`는 exit 2로 막고, `warn-`은 무조건 exit 0입니다.

**`deny-out-of-scope-write.sh` (2026-09-04 추가):** 이 하네스가 `baro-ai/.claude/`에서 저장소 루트 `.claude/`로 옮겨지며 생긴 훅이다. 루트에 있어야 세션이 어디서 시작되든 하네스가 로드되지만, 그러면 `settings.json`에 등록된 훅이 모노레포 전체(다른 4개 모듈 포함)에 걸린다 — 특히 `deny-db-migration.sh`는 baro-ai 전제(RDB 없음)로 만들어져, 스코프 체크 없이 루트로 옮기면 RDB를 쓰는 `baro-order` 등의 정당한 Flyway 마이그레이션까지 막아버렸을 것이다. 이 훅이 `baro-ai/`·`.claude/` 밖 Write/Edit을 먼저 차단해 다른 모듈에 대한 부작용을 원천 차단한다.

> **다이어그램을 볼 때 주의:** 훅은 "하나의 관문"이 아니라 **트리거가 서로 다른 4개의 독립 스크립트**입니다(파일은 5개지만 `warn-breaking-change.sh`는 등록돼 있지 않습니다). `rm -rf`는 Bash 경로에만 걸리고 파일 변경 경로로는 애초에 지나가지 않습니다. `deny-db-migration.sh`는 그 반대입니다.

## 2. 언제 발동하고, 어떤 흐름을 타는가

### 공통 인터페이스

모든 훅은 stdin으로 **JSON**을 받습니다(Claude Code PreToolUse/PostToolUse 스키마). 각 스크립트는 `python3`로 필요한 필드만 뽑아 씁니다.

```bash
input=$(cat)
command=$(python3 -c "
import json, sys
data = json.load(sys.stdin)
print(data.get('tool_input', {}).get('command', ''))
" <<< "$input")
```

### 흐름 A — Bash 명령 실행 시

```
에이전트가 Bash 도구 호출
  ↓
[1] deny-dangerous-bash.sh
     tool_input.command 를 정규식으로 검사
     패턴: git push --force|-f
           rm -rf (플래그 순서·조합 무관하게 매칭)
           git reset --hard
           docker compose down -v / docker-compose down -v
     매칭 → stderr에 한국어 사유 출력 + exit 2  → 명령 실행 안 됨
     미매칭 → exit 0
  ↓
(warn-breaking-change.sh는 settings.json에 등록돼 있지 않아 이 경로에 끼지 않는다)
  ↓
명령 실행
```

### 흐름 B — 파일 Write/Edit 시

```
에이전트가 Write 또는 Edit 도구 호출
  ↓
[1] deny-out-of-scope-write.sh (PreToolUse)
     baro-ai/ 와 .claude/ 밖의 file_path 면 exit 2 → 파일 생성 안 됨
     사유: core-guardrails.md STOP §0 (5인 공용 모노레포, 쓰기 범위 제한)
  ↓
[2] deny-db-migration.sh (PreToolUse)
     baro-ai/ 밖이면 즉시 exit 0 (스코프 게이트)
     tool_input.file_path 를 검사
     패턴: /db/migration/  또는  V1__x.sql · V1.2__x.sql · R__x.sql
     매칭 → exit 2 → 파일 생성 안 됨
     사유: core-guardrails.md STOP §3 (상용 보존 데이터 없음,
           스키마 SSOT는 JPA 엔티티 + ddl-auto)
  ↓
파일 저장
  ↓
[2] auto-format-java.sh (PostToolUse)
     .java 가 아니면 즉시 exit 0 (비용 0)
     git rev-parse --show-toplevel 으로 repo root 이동
     ./gradlew -PspotlessIdeHook=<file> ... 으로 그 파일 하나만 포맷
     ⚠ spotlessApply(전체 재스캔)를 안 쓴 이유: Java 파일을 반복 수정하는
       세션에서 매 저장마다 src/**/*.java 전체를 스캔하면 비용이 누적됨
     항상 exit 0
```

## 3. 실제 사례 — agent-type 훅 실패 (핵심 인시던트)

이 레이어에서 **가장 설명 가치가 큰** 사건입니다.

**1) 처음 설계:** `warn-breaking-change.sh`를 `agent`-type 훅으로 만들었습니다 — 서브에이전트가 diff를 읽고 "이게 breaking change인가"를 **판단**하게 했습니다. LLM이 문맥을 이해하니 더 똑똑하게 잡을 거라고 봤습니다.

**2) 사고:** 이 훅이 staged가 아닌 **working tree의 무관한 변경까지** 읽고 오판해서, "이 훅은 절대 커밋을 막으면 안 된다"는 명시적 지시가 프롬프트에 있었는데도 **커밋을 차단**했습니다.

**3) 원인 분석:** advisory(경고만)라는 요구사항은 "판단"이 아니라 "불변식"입니다. 불변식을 LLM 판단에 맡기면 프롬프트로 아무리 못 박아도 100%가 보장되지 않습니다.

**4) 조치:** `command`-type으로 전환하고, 스크립트 상단에 그 이유를 주석으로 박아뒀습니다.

```bash
# Command-type (not agent-type): guarantees non-blocking via exit 0, no matter what — an earlier
# agent-type version inspected the unstaged working tree instead of --cached and blocked a commit
# despite explicit "never block" instructions, so blocking risk is not left to LLM judgment here.
```

동시에 검사 범위도 `git diff --cached`(스테이징된 것만)로 좁혀 원인이던 오탐을 제거했습니다.

**결론으로 확정된 규칙:** advisory-only 훅은 command-type을 기본으로 한다. ([`.claude/rules/README-components.md`](../../../.claude/rules/README-components.md) "agent-type 훅 관련 교훈" 절이 SSOT)

## 4. fail-closed vs fail-open — 의도적 비대칭

| 훅 | 실패 시 | 이유 |
|---|---|---|
| `deny-out-of-scope-write.sh` | **fail-closed** (막음) | 모노레포 5인 공용 저장소에서 다른 모듈에 대한 영향은 조용히 넘어갈 수 없는 사고 — 오탐으로 한 번 막혀 사용자에게 확인받는 비용 < 다른 모듈을 조용히 건드리는 비용 |
| `deny-dangerous-bash.sh` | **fail-closed** (막음) | 오탐으로 한 번 막히는 비용 < `rm -rf`가 한 번 통과하는 비용 |
| `deny-db-migration.sh` | **fail-closed** (막음) | 위와 동일. 정말 필요하면 사람이 확인 후 진행 |
| ~~`warn-breaking-change.sh`~~ | (해당 없음) | 미등록이라 발동하지 않는다. 설계 의도는 fail-open이었다 — 커밋을 막는 건 워크플로 파괴라고 봤다 |
| `auto-format-java.sh` | **fail-open** (통과) | 포맷 실패로 작업을 막을 이유가 없음 |

이 비대칭이 의도적이라는 점이 중요합니다 — "전부 차단"이 아니라 **되돌리기 비용에 따라 차단 강도를 다르게** 설계했습니다.

## 5. AI-native 관점에서의 강조 포인트

**이 레이어가 4개 중 가장 강한 신호입니다.**

| 순위 | 강조할 것 | 근거 |
|---|---|---|
| 1 | **agent-type → command-type 전환 인시던트** | "LLM으로 만들었다가 실패해서 결정론적으로 바꿨다"는 서사. AI를 써본 사람만 할 수 있는 판단이고, AI의 한계를 인정한 설계라 신뢰도가 높음. 스크립트 주석에 그 근거가 코드로 남아있음 |
| 2 | **판단 vs 불변식의 경계 설정** | "이건 LLM에게, 저건 shell에게"를 비용 기준으로 나눈 것. 도구를 쓸 줄 아는 것과 도구의 한계를 아는 것의 차이 |
| 3 | **fail-closed / fail-open 비대칭** | 등록된 4개 훅을 일괄 차단하지 않고 되돌리기 비용으로 강도를 구분. 보안·운영 감각을 보여줌 |
| 4 | **비용 최적화 (`spotlessIdeHook`)** | 전체 재스캔 대신 단일 파일 포맷. 사소해 보이지만 "매 저장마다 도는 훅의 비용"을 생각했다는 증거 |

### 면접에서 쓸 한 문장

> "규칙 문서는 AI가 읽고 따르는 소프트 가드레일이라 100%가 안 됩니다. 그래서 되돌리기 어려운 것만 골라 shell hook의 exit code로 내렸고, 그 경계는 처음부터 안 게 아니라 LLM 기반 훅이 커밋을 잘못 막은 사고를 겪고 나서 확정했습니다."

### 함께 보면 좋은 대비

[Layer 1](layer1-human-gate.md)은 "AI가 지키기로 한 것", 이 레이어는 "AI가 지키든 말든 강제되는 것"입니다. 두 개를 **대비**시켜 말할 때 각각의 설계 의도가 드러납니다.
