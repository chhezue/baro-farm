#!/bin/bash
# Blocks Write/Edit outside baro-ai/ (+ .claude/ for harness self-maintenance).
# core-guardrails.md STOP §0 — 5인 공용 모노레포에서 다른 모듈(baro-order 등) 코드·문서를
# 참조/조사하는 건 자유지만, 쓰기는 이 저장소가 실제로 소유하는 baro-ai/ 범위로 제한한다.
# .claude/ 자체는 하네스 유지보수(이 하네스는 저장소 루트에 있음)를 위해 허용한다.
# Hook input: JSON with "tool_input.file_path" field on stdin (Claude Code PreToolUse schema, Write/Edit).

input=$(cat)

file_path=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('tool_input', {}).get('file_path', ''))
except Exception:
    print('')
" <<< "$input")

[ -z "$file_path" ] && exit 0

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0

case "$file_path" in
  /*) rel_path="${file_path#"$repo_root"/}" ;;
  *) rel_path="$file_path" ;;
esac

case "$rel_path" in
  baro-ai/*|.claude/*) exit 0 ;;
esac

echo "'$rel_path'는 baro-ai 밖입니다. 이 하네스의 쓰기 범위는 baro-ai/(+.claude/)로 제한됩니다 (core-guardrails.md STOP §0) — 다른 모듈은 참조·조사만 하고, 실제로 고쳐야 하면 먼저 사용자에게 확인하세요. Blocked by .claude/hooks/deny-out-of-scope-write.sh." >&2
exit 2
