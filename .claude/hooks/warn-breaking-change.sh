#!/bin/bash
# Advisory (non-blocking) reminder for core-guardrails.md STOP §5 (Breaking-Change-Reason trailer).
# Hook input: JSON with "tool_input.command" field on stdin (Claude Code PreToolUse schema).
# Command-type (not agent-type): guarantees non-blocking via exit 0, no matter what — an earlier
# agent-type version inspected the unstaged working tree instead of --cached and blocked a commit
# despite explicit "never block" instructions, so blocking risk is not left to LLM judgment here.

input=$(cat)

command=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('tool_input', {}).get('command', ''))
except Exception:
    print('')
" <<< "$input")

if ! echo "$command" | grep -q 'git commit'; then
  exit 0
fi

if echo "$command" | grep -q 'Breaking-Change-Reason:'; then
  exit 0
fi

staged=$(git diff --cached --name-only 2>/dev/null)
matched=$(echo "$staged" | grep -E '(/dto/.*\.java$|/domain/.*\.java$|ErrorCode\.java$|Controller\.java$)')
if [ -n "$matched" ]; then
  echo "⚠️  DTO/ErrorCode/Controller 변경이 스테이징된 커밋에 'Breaking-Change-Reason:' 트레일러가 없습니다. 변경된 파일:" >&2
  echo "$matched" | sed 's/^/  /' >&2
  echo "실제 계약 영향이 있으면(필드 추가·삭제·타입변경·필수화, enum 값, ErrorCode 신규·변경 등) core-guardrails.md STOP §5에 따라 트레일러를 추가하세요. 아래 diff로 직접 판단:" >&2
  echo "$matched" | xargs -I{} git diff --cached -- {} 2>/dev/null | head -80 >&2
fi

exit 0
