#!/bin/bash
# Blocks destructive shell commands. Registered in .claude/settings.json (PreToolUse/Bash).
# Hook input: JSON with "tool_input.command" field on stdin (Claude Code PreToolUse schema).

input=$(cat)

command=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('tool_input', {}).get('command', ''))
except Exception:
    print('')
" <<< "$input")

# Patterns: force push, recursive delete (any flag order/combination), hard reset, prod DB wipe
if echo "$command" | grep -qE \
  'git push (--force|-f)|rm[[:space:]]+(-[a-zA-Z]*[rR][a-zA-Z]*[fF][a-zA-Z]*\b|-[a-zA-Z]*[fF][a-zA-Z]*[rR][a-zA-Z]*\b|-[a-zA-Z]*[rR][a-zA-Z]*[[:space:]]+-[a-zA-Z]*[fF][a-zA-Z]*\b|-[a-zA-Z]*[fF][a-zA-Z]*[[:space:]]+-[a-zA-Z]*[rR][a-zA-Z]*\b|--recursive[[:space:]]+--force|--force[[:space:]]+--recursive)|git reset --hard|docker compose down -v|docker-compose down -v'; then
  echo "위험한 명령이 차단되었습니다 (force push, rm -rf, hard reset, docker compose down -v). Blocked by .claude/hooks/deny-dangerous-bash.sh — use safer alternatives or ask the user explicitly." >&2
  exit 2
fi

exit 0
