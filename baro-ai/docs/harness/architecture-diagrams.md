# TripFit AI-Native Harness 다이어그램 모음

실제 [Eraser.io 워크스페이스](https://app.eraser.io/workspace/HmuAgWuJ2vS9ZvsDOv8u)에 반영되어 있는 다이어그램 코드 모음입니다.
[Eraser.io](https://app.eraser.io/)에서 빈 캔버스를 열고 좌측 텍스트 에디터에 원하는 블록을 그대로 붙여넣으면 깔끔하게 렌더링됩니다.

---

## Part 1. 포트폴리오 메인 다이어그램 (통합 뷰)

이 파트는 포트폴리오 첨부에 최적화된 **"AI-Native Engineering System"**의 핵심 다이어그램 3종입니다.
실제 서버 인프라부터 `.claude/` 내부의 방대한 규칙(Rules), 스킬(Skills), 훅(Hooks) 생태계, 그리고 API 검증까지 100% 팩트에 기반하여 시각화했습니다.

### 1. 배포 아키텍처 (Deployment Architecture)

> **"실제 서비스되는 인프라 구조입니다. GitHub Actions 기반의 CI/CD를 통해 Vercel(프론트엔드)과 AWS EC2(백엔드)로 계약 검증을 통과하여 배포되며, Prometheus/Grafana로 모니터링됩니다."**

```eraser
typeface clean
styleMode plain
direction right

// 1. CI/CD Pipeline & Alerts
github [label: "GitHub", icon: github, color: black]
github_actions [label: "GitHub Actions", icon: github-actions, color: blue]
docker_registry [label: "GHCR (Docker Registry)", icon: docker, color: blue]
discord [label: "Discord", icon: discord, color: purple]

// 2. Client & DNS
client [label: "Client", icon: smartphone, color: gray]
route53 [label: "Route 53", icon: aws-route-53, color: orange]
vercel [label: "Vercel Frontend", icon: vercel, color: black]

// 3. AWS EC2 Instances
ec2_d [label: "EC2 D (Cache)", icon: aws-ec2, color: red] {
  redis [label: "Redis", icon: redis, color: red]
}

ec2_b [label: "EC2 B (Database)", icon: aws-ec2, color: blue] {
  mysql [label: "MySQL (Main DB)", icon: mysql, color: blue]
}

ec2_a [label: "EC2 A (App Server)", icon: aws-ec2, color: orange] {
  nginx [label: "Nginx", icon: nginx, color: green]
  spring_boot [label: "Spring Boot", icon: spring, color: green]
}

// 4. Monitoring (EC2 C)
monitoring [label: "EC2 C (Monitoring)", color: purple] {
  prometheus [label: "Prometheus", icon: prometheus, color: orange]
  grafana [label: "Grafana", icon: grafana, color: orange]
}

// ============================================================
// Flow
// ============================================================

// CI/CD Flow
github > github_actions: "push"
github_actions > docker_registry: "push"
docker_registry > ec2_a: "pull"
github_actions > discord: "API Contract\nBreaking Alert"

// Request Flow
client > route53
route53 > vercel: "tripfit.online"
route53 > nginx: "api.tripfit.online\n:80/443"
vercel > nginx: "REST API"

nginx > spring_boot: ":8080"
spring_boot > redis: ":6379"
spring_boot > mysql: ":3306"

// Monitoring Flow
ec2_a --> prometheus: "Metrics"
ec2_b --> prometheus: "Metrics"
ec2_d --> prometheus: "Metrics"
prometheus > grafana
```

### 2. AI-Native Engineering Workflow (End-to-End Control Loop)

> **"AI의 판단과 실행을 분리하고, 사람의 승인이 필요한 영역과 시스템이 강제해야 하는 영역을 구분했습니다. 모든 작업은 성격에 따라 3개 트랙 중 하나로 진입해 공통 게이트 4개(리서치·승인·검증·회고)를 통과합니다. 직접 구축한 5대 커스텀 스킬·2대 서브에이전트·4대 로컬 훅으로 AI의 작업 범위를 구조화하고, 위험한 도구 사용을 결정론적으로 통제하며, 변경 결과를 기계적으로 검증합니다."**

```eraser
typeface clean
styleMode plain
direction down

// 1. Probabilistic Layer (AI가 확률적으로 판단하고 계획하는 영역)
probabilistic_layer [label: "1. Probabilistic Layer\n(AI Agent & Skills)", color: blue] {
  rules [label: "Rules\n(코어 룰 주입)", icon: file-text, color: blue]
  ai_agent [label: "AI Agent\n(Claude Code)", icon: bot, color: blue]
  plan_skills [label: "트랙 선택 (택 1)", color: purple] {
    specify [label: "A 트랙: specify\n(기능·API·DB)", icon: file-plus]
    refactor_audit [label: "B 트랙: safe-refactor\n(감사·무손실 리팩터)", icon: search]
    debug_bug [label: "C 트랙: debug\n(버그·테스트 실패)", icon: tool]
  }
  researcher [label: "G1 리서치\nresearcher 서브에이전트\n(로컬 버전 → 공식 문서)", icon: book-open, color: blue]
}

// 2. Human Decision Layer (사람의 개입 및 의사결정)
human_layer [label: "2. Human Decision Layer", color: orange] {
  human_gate [label: "G2 승인 게이트\n(STOP & Ask User)", icon: alert-triangle, color: orange]
}
defer_followup [label: "GitHub Issue\n(defer 분리)", icon: github, color: gray]

// 3. Implementation (코드 생성)
implement_phase [label: "구현\n(승인된 범위 내 코드 생성)", icon: edit, color: purple]

// 4. Deterministic Layer (결정론적 가드레일 통제)
deterministic_layer [label: "3. Deterministic Layer\n(Custom Hooks)", color: red, icon: shield] {
  block_dangerous [label: "deny-dangerous-bash.sh\n(Fail-Closed)", icon: x-octagon, color: red]
  block_db [label: "deny-db-migration.sh\n(Fail-Closed)", icon: database, color: red]
  warn_breaking [label: "warn-breaking.sh\n(Fail-Open)", icon: alert-circle, color: orange]
  format_java [label: "auto-format-java.sh\n(Non-blocking)", icon: code, color: blue]
}

// 5. Mechanical Verification (기계적 최종 검증)
verification_layer [label: "4. Mechanical Verification\n(G3 검증 게이트)", color: green] {
  preflight [label: "preflight Skill\n(Tests / oasdiff)", icon: check-circle, color: yellow]
  doc_reviewer [label: "doc-reviewer 서브에이전트\n(문서 품질 · advisory)", icon: file-text, color: yellow]
}

retro [label: "G4 회고 게이트\n(문서 갱신 점검 · 후속 제안 · 로그)", icon: repeat, color: orange]
safe_change [label: "Safe Change\n(안전한 변경 완료)", icon: check, color: green]

// ============================================================
// Workflow Pipeline
// ============================================================
rules > ai_agent
ai_agent > plan_skills: "작업 성격으로 트랙 판정"
plan_skills > researcher: "외부 지식 필요 시"

probabilistic_layer > human_gate

human_gate > implement_phase: "Approve"
human_gate > defer_followup: "Defer"

implement_phase > deterministic_layer: "Tool 실행 시 가로채기"
deterministic_layer > verification_layer
verification_layer > retro
retro > safe_change
```

### 3. AI-Safe API Contract Validation (계약 안전성 검증)

> **"AI가 생성한 코드로 인해 Frontend와의 API 계약이 예기치 않게 깨지는 것을 방지합니다. 로컬 Hook과 CI를 연계하여 구조적 스키마(oasdiff), 비즈니스 예외(ErrorCode), 반환 타입(@ApiResponse)을 3중으로 교차 검증합니다."**

```eraser
typeface clean
styleMode plain
direction right

// 1. AI Action & Local Guardrail
ai_agent [label: "AI Agent · API 변경", icon: bot, color: purple]
warn_hook [label: "warn-breaking-change.sh\n스키마 파괴 사전 경고", icon: shield, color: orange]

// 2. CI Pipeline
ci_node [label: "CI Pipeline\n무결성 검증", icon: git-pull-request, color: blue]

ci_checks [label: "3-Tier Detection\nAPI 계약 자동 검증", color: blue] {
  oasdiff [label: "oasdiff\n구조 검증", icon: file-text]
  trailer [label: "ErrorCode Trailer\n예외 검증", icon: code]
  api_response [label: "@ApiResponse\n반환 타입 검증", icon: search]
}

// 3. Outcomes
contract_valid [label: "Contract Valid\nBreaking Change 미감지", icon: check-circle, color: green]
discord_alert [label: "Discord 자동 알림\n프론트엔드 통지", icon: message-square, color: red]
deploy [label: "Deploy Pipeline\n계약 검증 후 배포", icon: server, color: green]
snapshot [label: "OpenAPI Snapshot\nSSOT 기준점 갱신", icon: refresh-cw, color: gray]

// ============================================================
// Verification Flow
// ============================================================
ai_agent > warn_hook
warn_hook > ci_node
ci_node > ci_checks

ci_checks > contract_valid: "검증 Pass"
ci_checks --> discord_alert: "계약 변경 감지"

contract_valid > deploy
contract_valid > snapshot
```

---

## Part 2. 레이어별 상세 다이어그램 (Deep Dive)

이 파트는 `docs/harness/layer*.md` 문서와 1:1로 대응되는 세부 아키텍처 다이어그램입니다. 각 레이어의 구체적인 동작 원리와 분기(Branching)를 깊게 파고들 때 사용합니다.

### 4. Layer 1: Human Gate (상세)

```eraser
typeface clean
styleMode plain
direction right

// 1. 규칙 로딩 (컨텍스트 예산)
rules_group [label: "Rules (Context Budget)", color: blue, icon: file] {
  always_load [label: "Always-load 규칙 (기본 7개)"]
  path_scoped [label: "Path-scoped 규칙 (조건부 8개)"]
}

// 2. 상태 정의
start_task [label: "변경 착수", shape: oval, icon: play, color: blue]
stop_ask [label: "STOP & Ask User", icon: alert-triangle, color: orange]
out_of_scope [label: "Out of Scope (범위 밖)", icon: skip-forward, color: gray]
proceed [label: "Proceed to Code", icon: check-circle, color: green]

// 3. 검증 게이트 (4단계)
gates_group [label: "Validation Gates (4단계)", color: purple, icon: shield] {
  gate_1 [label: "Gate 1: 문서 드리프트 (코드 우선 확인)"]
  gate_2 [label: "Gate 2: 기획/우선순위 [미정]"]
  gate_3 [label: "Gate 3: 원천 금지 (호환 레이어 등)"]
  gate_4 [label: "Gate 4: 동시 반영 (ErrorCode, 트레일러)"]
}

// === 플로우 ===
rules_group > start_task: "규칙 주입"
start_task > gates_group

gate_1 --> stop_ask: "문서 충돌 시"
gate_2 --> stop_ask: "기획 미확정 시"
gate_3 > out_of_scope: "원천 금지 항목 매칭 시"
gate_4 > proceed: "포함하여 진행"
gates_group > proceed: "전부 Pass"
```

### 5. Layer 2: Custom Skills & Subagents (상세)

```eraser
typeface clean
styleMode plain
direction right

// 1. Trigger
ai_agent [label: "AI Agent", icon: bot, color: purple]

// 2. 트랙 스킬 (택 1 — 작업 성격으로 분기)
track_skills [label: "트랙 스킬 (.claude/skills/)", color: blue, icon: folder] {
  specify [label: "A 트랙: specify\n(기능/설계 스펙 작성)", icon: file-plus]
  refactor_audit [label: "B 트랙: safe-refactor\n(무손실 감사·리팩터)", icon: search]
  debug_bug [label: "C 트랙: debug\n(버그 재현 및 분석)", icon: tool]
}

// 3. 게이트 스킬 (트랙 공통)
gate_skills [label: "게이트 스킬 (트랙 공통)", color: green, icon: folder] {
  preflight [label: "G3: preflight\n(기계적 검증)", icon: check-circle]
  defer_followup [label: "G4: defer\n(후속 이슈 분리)", icon: log-out]
}

// 4. 서브에이전트 (.claude/agents/ — Edit/Write 없음)
agents_group [label: "서브에이전트 (.claude/agents/)", color: purple, icon: users] {
  researcher [label: "G1: researcher\n(외부 문서 조사)", icon: book-open]
  doc_reviewer [label: "G3: doc-reviewer\n(문서 품질 리뷰)", icon: file-text]
}

// 5. Outcomes
outcome_spec [label: "docs/specs/", icon: file-text, color: purple]
outcome_audit [label: "audit.md / refactor-log.md", icon: file-text, color: purple]
outcome_issue [label: "GitHub Issue (Draft 스펙)", icon: github, color: black]
outcome_verify [label: "oasdiff / gradlew 통과", icon: check-circle, color: green]
outcome_research [label: "결론 + 근거 URL + 확인 버전", icon: book, color: blue]

// === 플로우 ===
ai_agent > track_skills: "작업 성격으로 트랙 판정"
track_skills > agents_group: "G1 — 외부 지식 필요 시"
track_skills > gate_skills: "구현 후 공통 게이트"

specify > outcome_spec
refactor_audit > outcome_audit
defer_followup > outcome_issue
preflight > outcome_verify
researcher > outcome_research
debug_bug > preflight: "수정 후 검증"
doc_reviewer > preflight: "문서 50줄+ 변경 시"
```

### 6. Layer 3: Deterministic Guardrails (상세)

```eraser
typeface clean
styleMode plain
direction right

// 1. 트리거
agent_action [label: "Agent Tool Use", icon: terminal, color: gray]

// 2. 결정론적 훅 (비대칭 설계)
hooks_group [label: "Custom Hooks (.claude/settings.json)", color: red, icon: shield] {
  block_dangerous [label: "deny-dangerous-bash.sh\n(PreToolUse/Bash, Fail-Closed)", icon: x-octagon, color: red]
  warn_breaking [label: "warn-breaking-change.sh\n(PreToolUse/Bash, Fail-Open)", icon: alert-circle, color: orange]
  block_db [label: "deny-db-migration.sh\n(PreToolUse/Write, Fail-Closed)", icon: database, color: red]
  format_java [label: "auto-format-java.sh\n(PostToolUse/Write, Non-blocking)", icon: code, color: blue]
}

// 3. 결과
outcomes_group [label: "Outcomes", color: gray] {
  block_exit2 [label: "Block (exit 2)", icon: x-octagon, color: red]
  allow_exit0 [label: "Allow (exit 0)", icon: check-circle, color: green]
}

// === 플로우 ===
agent_action > hooks_group: "Tool 실행 가로채기"

block_dangerous > block_exit2: "파괴적 명령어"
block_dangerous > allow_exit0: "Pass"

warn_breaking --> allow_exit0: "advisory 경고만 출력"

block_db > block_exit2: "DB 마이그레이션 생성"
block_db > allow_exit0: "Pass"

format_java --> allow_exit0: "자동 포맷 적용"
```
