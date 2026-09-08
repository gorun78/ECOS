#!/usr/bin/env bash
# Wave 1 追踪脚本
HERMES=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
BOARD="ecos"
IDS=(
  worker:t_d45a9f20
  worker:t_b40d584f
  worker:t_260940f4
  worker:t_448ed2bb
  worker:t_64e6e69e
  worker:t_8986e024
  verifier:t_b1abcc91
  verifier:t_acbf4a05
  verifier:t_3ccd06e8
  verifier:t_d1ad3bca
  verifier:t_6d63b733
  verifier:t_c14e22ca
  synthesizer:t_eececa8a
  synthesizer:t_e284cfcd
  synthesizer:t_a1ec47dd
  synthesizer:t_7d597404
  synthesizer:t_5d803bd9
  synthesizer:t_48efbdbe
)
RUNDOWN=/home/guorongxiao/ECOS/.working/wave1-rundown.log

for i in $(seq 1 30); do   # 最多 30 轮 * 60s = 30 分钟
  echo "=== ROUND $i @ $(date +%H:%M:%S) ===" >> "$RUNDOWN"
  OUTPUT=$($HERMES kanban --board $BOARD list --json 2>/dev/null)
  ALL_DONE=0
  for entry in "${IDS[@]}"; do
    id=${entry#*:}
    layer=${entry%%:*}
    LINE=$(echo "$OUTPUT" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for t in data:
    if t.get('id') == '$id':
        print(f\"{$layer} {$id} -> {t.get('status','?')} ({len(t.get('body',''))} char body)\")
        break
else:
    print(f\"{$layer} {$id} -> GC arch (board 已清理)\")
" 2>/dev/null)
    echo "  $LINE" >> "$RUNDOWN"
  done
  # 判断是否全 done/archived
  ALL_DONE=$(echo "$OUTPUT" | python3 -c "
import sys, json
data = json.load(sys.stdin)
ids = ['$id']
# placeholders replaced below
" 2>/dev/null)
  # simpler: grep statuses
  DONE_COUNT=$(echo "$OUTPUT" | python3 -c "
import sys, json
data = json.load(sys.stdin)
ids = ['$t_d45a9f20','t_b40d584f','t_260940f4','t_448ed2bb','t_64e6e69e','t_8986e024','t_b1abcc91','t_acbf4a05','t_3ccd06e8','t_d1ad3bca','t_6d63b733','t_c14e22ca','t_eececa8a','t_e284cfcd','t_a1ec47dd','t_7d597404','t_5d803bd9','t_48efbdbe']
done = 0
total = 0
for t in data:
    if t.get('id') in ids:
        total += 1
        s = t.get('status', 'unknown')
        if s in ('done', 'archived'):
            done += 1
print(f'{done}/{total}')
" 2>/dev/null)
  echo "  Progress: $DONE_COUNT done" >> "$RUNDOWN"
  if [[ $DONE_COUNT == *"18/18"* ]]; then
    echo "ALL DONE — stopping loop" >> "$RUNDOWN"
    break
  fi
  sleep 60
done
echo "=== TRACKING COMPLETED @ $(date +%H:%M:%S) ===" >> "$RUNDOWN"
