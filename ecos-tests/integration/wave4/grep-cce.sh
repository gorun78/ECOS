#!/bin/bash
LOG=/tmp/w4-gw-v5.log
# 找 13:36:18.832 ClassCastException 上下文
N=$(grep -n "13:36:18.832" "$LOG" | head -1 | cut -d: -f1)
echo "=== ClassCastException context (N=$N, +40 lines) ==="
sed -n "$((N-2)),$((N+45))p" "$LOG"
