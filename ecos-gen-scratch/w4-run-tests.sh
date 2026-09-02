#!/usr/bin/env bash
# w4-run-tests.sh — 跑全部 7 个 mjs 测试脚本, 输出完整日志到 0.log
set +e
NODE=/home/guorongxiao/.local/bin/node
DIR=/home/guorongxiao/ECOS/ecos-tests/integration/wave4
LOG=/tmp/wave4-test-results.log
: > "$LOG"
$NODE $DIR/wave4-runner.mjs --concurrency=1 >> "$LOG" 2>&1
EXIT=$?
echo "=== EXIT=$EXIT ==="
echo "=== 最后 200 行 ==="
tail -200 "$LOG"
echo "=== 摘要 (从日志中提取 7 域结论) ==="
grep -E "reason|PASS|FAIL|verdict|判定|Summary|IXX" "$LOG" 2>/dev/null | head -50
echo "=== /tmp/wave4_*.json 摘要 ==="
for f in /tmp/wave4_*.json; do
  [ ! -f "$f" ] && continue
  echo "--- $(basename $f) ---"
  head -5 "$f" | sed 's/^\s*//'
done
echo "DONE - exit=$EXIT"
