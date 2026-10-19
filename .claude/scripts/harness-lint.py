#!/usr/bin/env python3
"""하네스가 "이 저장소에 없는 것"을 가리키는지 결정론적으로 검사한다.

배경: 이 하네스는 TripFit에서 이식했고, harness-map.md의 슬롯·옵션만 갈아 끼운 채
규칙 본문이 남의 저장소 전제를 들고 있었다. 규칙이 지시한 ErrorCode 형태가 컴파일조차
되지 않았고, spring-reviewer는 존재하지 않는 ArchitectureTest를 grep하고 있었다.
근거: baro-ai/docs/audits/harness-retro.md (2026-09-05 · 2026-09-08)

여기서 잡는 것은 exit code로 판정 가능한 것뿐이다. "이 문장이 지시인지 이력인지",
"이 예시가 실제 시그니처와 맞는지" 같은 판단은 사람·에이전트 몫이다
(retro 스킬 4단계 — 판정 불가능한 것을 기계에 맡기지 않는다).

사용:  python3 .claude/scripts/harness-lint.py
종료:  0 = 이상 없음 / 1 = 발견됨 (advisory — 커밋을 막지 않는다)

오탐을 줄이려고 두 가지를 건너뛴다.
  - 줄에 `harness-lint: ignore` 가 있으면 그 줄 전체
  - 다른 저장소를 명시적으로 이야기하는 줄(아래 OTHER_REPO_HINTS)
"""
import json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC_ROOT = ROOT / "baro-ai"        # {{문서 루트}}의 상위 — harness-map.md
TEST_CMD = "./gradlew :baro-ai:test"  # {{테스트 명령}}

# 이 표현이 있는 줄은 "우리 저장소에 대한 지시"가 아니라고 본다.
OTHER_REPO_HINTS = ("다른 저장소", "다른 프로젝트", "없다", "없음", "미등록", "미구축",
                    "등록돼 있지 않", "등록되지 않", "동작하지 않", "해당하지 않",
                    "신설", "쓰지 않는", "삭제", "이력", "교훈")
CONTEXT = 2   # 힌트가 앞뒤 몇 줄 안에 있으면 그 줄도 건너뛴다 (코드 펜스 대응)

RED, YEL, GRN, BLD, OFF = "\033[31m", "\033[33m", "\033[32m", "\033[1m", "\033[0m"
findings: list[str] = []


def targets() -> list[Path]:
    return sorted(p for p in (ROOT / ".claude").rglob("*.md")
                  if "worktrees" not in p.parts)


def lines(p: Path):
    """(줄번호, 줄, 문맥) — 문맥은 앞뒤 CONTEXT줄을 합친 문자열이다."""
    try:
        raw = p.read_text(encoding="utf-8").splitlines()
    except OSError:
        return
    for i, ln in enumerate(raw, 1):
        if "harness-lint: ignore" in ln:
            continue
        ctx = "\n".join(raw[max(0, i - 1 - CONTEXT): i + CONTEXT])
        yield i, ln, ctx


def skip(ctx: str) -> bool:
    """이 줄이 '우리 저장소에 대한 지시'가 아니라고 볼 근거가 문맥에 있는가."""
    return any(h in ctx for h in OTHER_REPO_HINTS)


def rel(p: Path) -> str:
    return str(p.relative_to(ROOT))


def section(title: str, hits: list[str]):
    print(f"\n{BLD}{title}{OFF}")
    if hits:
        findings.extend(hits)
        for h in hits:
            print(f"  {h}")
    else:
        print(f"  {GRN}이상 없음{OFF}")


# ── 1. 존재하지 않는 문서를 가리키는 참조 ──────────────────────
hits = []
for p in targets():
    for i, ln, ctx in lines(p):
        if skip(ctx):
            continue
        for m in re.finditer(r"docs/[A-Za-z0-9/_.-]*\.md", ln):
            if not (DOC_ROOT / m.group(0)).is_file():
                hits.append(f"{rel(p)}:{i} → {m.group(0)}")
section("1. 존재하지 않는 문서를 가리키는 참조", hits)

# ── 2. 깨진 상대 링크 ────────────────────────────────────────
hits = []
for p in targets():
    for i, ln, ctx in lines(p):
        for m in re.finditer(r"\]\(([^)#\s]+)\)", ln):
            t = m.group(1)
            if t.startswith(("http", "mailto")):
                continue
            if not (p.parent / t).resolve().exists():
                hits.append(f"{rel(p)}:{i} → {t}")
section("2. 깨진 상대 링크", hits)

# ── 3. 모듈 지정이 빠진 gradle 명령 ───────────────────────────
# 모노레포라 `./gradlew test` 는 의도한 대상을 실행하지 않는다.
hits = []
for p in targets():
    for i, ln, ctx in lines(p):
        if skip(ctx):
            continue
        if re.search(r"\./gradlew\s+(test|build|bootJar|spotlessApply)\b", ln):
            hits.append(f"{rel(p)}:{i} — 모듈 미지정 (→ {TEST_CMD} 형태로)")
section("3. 모듈 지정이 빠진 gradle 명령", hits)

# ── 4. settings.json에 등록되지 않은 훅을 동작한다고 서술 ──────
hits = []
try:
    blob = json.dumps(json.loads((ROOT / ".claude/settings.json").read_text()))
    registered = set(re.findall(r"[A-Za-z0-9_-]+\.sh", blob))
except Exception as e:            # noqa: BLE001
    hits.append(f"settings.json을 읽지 못했습니다 — {e}")
    registered = set()
for hook in sorted((ROOT / ".claude/hooks").glob("*.sh")):
    if hook.name in registered:
        continue
    for p in targets():
        for i, ln, ctx in lines(p):
            if hook.name in ln and not skip(ctx):
                hits.append(f"{rel(p)}:{i} — 미등록 훅 {hook.name} 을 동작하는 것처럼 서술")
section("4. 등록되지 않은 훅을 동작한다고 서술", hits)

# ── 5. 존재하지 않는 Java 파일 언급 ───────────────────────────
# `{ClassName}Test.java` 같은 자리표시자는 제외한다.
java_files = {Path(l).name for l in subprocess.run(
    ["git", "ls-files", "*.java"], cwd=ROOT, capture_output=True, text=True
).stdout.splitlines()}
hits = []
for p in targets():
    for i, ln, ctx in lines(p):
        if skip(ctx):
            continue
        for m in re.finditer(r"(?<![{*\w])[A-Z][A-Za-z0-9_]*\.java", ln):
            name = m.group(0)
            if name not in java_files:
                hits.append(f"{rel(p)}:{i} — {name} (저장소에 없음)")
section("5. 존재하지 않는 Java 파일 언급", hits)

# ── 결과 ─────────────────────────────────────────────────────
print()
if findings:
    print(f"{YEL}{len(findings)}건 — 확인이 필요합니다.{OFF}")
    print(f"{YEL}과거 이력을 서술한 문장이면 그대로 두어도 됩니다. 이 스크립트는")
    print(f"'지시'와 '이력'을 완전히 구분하지 못합니다 — 그 줄에 `harness-lint: ignore`를")
    print(f"붙이면 다음부터 건너뜁니다.{OFF}")
    sys.exit(1)
print(f"{GRN}통과 — 하네스가 가리키는 대상이 전부 실재합니다.{OFF}")
