#!/usr/bin/env bash
set +e
echo "=== 最后 100 行 ==="
tail -150 /tmp/w4-gw-v3.log 2>&1
echo "=== 搜 '400' 上下文 ==="
grep -B 5 -A 30 "diagnose\|Wave3Demo\|wave3\|CausalReasoner" /tmp/w4-gw-v3.log 2>/dev/null | head -200
echo "=== 搜 '400' / HTTP 500 最近入口 ==="
grep -B 5 -A 5 "Servlet.service\|400 BAD REQUEST\|MissingServletRequest" /tmp/w4-gw-v3.log 2>/dev/null | tail -150
