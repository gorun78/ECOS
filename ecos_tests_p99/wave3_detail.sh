#!/bin/bash
# 慢日志和 LLM provider 实际耗时探测
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
BASE="http://localhost:8080"

echo "=== E1 wave3 单请求分阶段 (用内部日志时间戳) ==="
# 抓一段 gateway 日志
tail -200 /home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log | grep -iE 'wave3|extract|entityLink|diagnose|causal|decision' | head -30 && echo "" | tail -3

echo ""
echo "=== 单独测 NewsFeedReader (走 curl 拿 body, 时间) ==="
for i in 1 2 3; do
  python3 -c $'d={"markdown":"## Q3 毛利率下滑\\n\\n毛利率从 22% 降至 14%，其中库存周转天数从 45 升至 78。\\n\\n```mermaid\\ngraph LR\\nSales --> Margin\\n```","domain":"finance","maxDepth":4}; import json,os; print(json.dumps(d))' > /tmp/body.json
  code=$(curl -s -X POST "$BASE/api/v1/cognitive/demo/wave3" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/body.json -o /tmp/resp.json -w "%{http_code} time=%{time_total}" -m 60)
  echo "iter $i: $code"
done

echo ""
echo "=== 网关日志最近 200 行 降噪错误 ==="
tail -50 /home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log | grep -ivE '^\s*$' | head -20
