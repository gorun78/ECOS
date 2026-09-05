#!/bin/bash
LOG=/tmp/w4-gw-v5.log
# 13:36:18.867 compliance-rules 500 上下文
N=$(grep -n "13:36:18.86" "$LOG" | head -1 | cut -d: -f1)
echo "=== P0-4 compliance-rules 500 context (N=$N) ==="
[ -n "$N" ] && sed -n "$((N-3)),$((N+8))p" "$LOG"
echo ""
# 13:36:18.896 onto domains 500 上下文
N2=$(grep -n "13:36:18.89" "$LOG" | head -1 | cut -d: -f1)
echo "=== 03-onto GET domains 500 context (N=$N2) ==="
[ -n "$N2" ] && sed -n "$((N2-3)),$((N2+12))p" "$LOG"
