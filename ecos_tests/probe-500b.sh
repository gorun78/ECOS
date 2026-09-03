#!/bin/bash
# 看 8080 + 500 堆栈
echo "=== 8080 状态 ==="
lsof -ti:8080 2>/dev/null || echo "(8080 空)"
curl -s -o /dev/null -w "health=%{http_code}\n" --max-time 3 http://localhost:8080/api/health 2>/dev/null

echo "=== 完整 500 异常 (grep 异常行 + context) ==="
grep -n -E "Exception|ERROR|causalReasoner|diagnose|NullPointer|ClassCast|Wave3Demo" /tmp/gw-w3-ctr.log 2>/dev/null | tail -30

echo
echo "=== 直接 curl (如果 GW up) ==="
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "Content-Type: application/json" \
  -d '{"sourceDocument":"# D\n- 销售\n```mermaid\ngraph LR\nA-->B\n```","domain":"finance"}' 2>/dev/null)
echo "status=$code"
if [ "$code" = "500" ]; then
  body=$(curl -s --max-time 30 -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
    -H "Content-Type: application/json" \
    -d '{"sourceDocument":"# D\n- 销售\n```mermaid\ngraph LR\nA-->B\n```","domain":"finance"}' 2>/dev/null)
  echo "body=$body" | head -c 500
fi
echo "DONE"