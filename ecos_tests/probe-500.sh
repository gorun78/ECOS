#!/bin/bash
# 抓 05 T2 500 的堆栈 (GW 日志)
echo "=== GW 日志最后 60 行 (找 500 异常) ==="
tail -60 /tmp/gw-w3-ctr.log 2>/dev/null | grep -A 15 -E "Exception|Error|500|causalReasoner|diagnose" | head -60
echo
echo "=== 直接 curl 05 T2 看响应体 ==="
curl -s -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "Content-Type: application/json" \
  -d '{"sourceDocument":"# Demo\n- 销售下降\n```mermaid\ngraph LR\nA-->B\n```","domain":"finance"}' \
  --max-time 30 | head -c 600
echo
echo "=== GW 日志最后 30 行 ==="
tail -30 /tmp/gw-w3-ctr.log 2>/dev/null
echo "DONE"