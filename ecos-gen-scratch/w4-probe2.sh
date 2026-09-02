#!/usr/bin/env bash
set +e
TOKEN=$(curl -s -m 5 -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null)
if [ -z "${TOKEN:-}" ]; then
  echo "NO TOKEN — admin 登录失败"
  curl -s -m 5 -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' 2>&1 | head -c 400
  echo
  exit 1
fi
echo "TOKEN_LEN=${#TOKEN}"
H="Authorization: Bearer $TOKEN"
J='-H Content-Type:application/json'

echo "═══ cognitive /diagnose (metric 必填) ═══"
curl -s -m 30 -X POST http://localhost:8080/api/v1/cognitive/diagnose \
  -H "$H" -H "Content-Type: application/json" \
  -d '{"metric":"sales","deviation":-15,"domain":"finance","maxDepth":4}' 2>&1 | head -c 1200
echo

echo "═══ cognitive /diagnose (full sample from wave3 demo record) ═══"
curl -s -m 30 -X POST http://localhost:8080/api/v1/cognitive/diagnose \
  -H "$H" -H "Content-Type: application/json" \
  -d '{"metric":"销售额","deviation":-12,"domain":"finance","maxDepth":3}' 2>&1 | python3 -c "import sys,json; d=json.load(sys.stdin); print('code:', d.get('code')); print('keys:', list(d.get('data',{}).keys())); rp=d.get('data',{}).get('reasoningPath') or {}; print('rp.keys:', list(rp.keys())); print('steps:', len(rp.get('steps') or [])); print('ruleRefs:', len(rp.get('ruleRefs') or [])); print('precedentRefs:', len(rp.get('precedentRefs') or [])); print('first_step:', (rp.get('steps') or [{}])[0])[:200])" 2>&1 | head -c 1500
echo

echo "═══ cognitive /demo/wave3 — 输入正确格式 (T7 用例) ═══"
# markdown 必须含 "metric" "偏差" "根因" 列表项, 注意服务层 NewsFeedReader.parseMarkdown 取 mermaid 行
curl -s -m 60 -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "$H" -H "Content-Type: application/json" \
  --data @- <<EOF
{"markdown":"# Wave-4.1 联调 财报\n\n销售额 较 上期 下降 12% (deviation=-12%)\n\n## 根因分析\n- 库存 成本 上升\n- 配件 涨价\n- 订单 量 下降\n\n\`\`\`mermaid\ngraph LR\n  Sales --> CashFlow\n  CashFlow --> Margin\n  InventoryCost --> Margin\n\`\`\`\n","domain":"finance","maxDepth":4}
EOF
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('code:', d.get('code'), 'msg:', d.get('message')); dd=d.get('data') or {}; print('keys:', list(dd.keys())); cd=dd.get('causalDiagnosis') or {}; print('metric:', cd.get('metric'), 'rootCause:', cd.get('rootCause'), 'chain:', len(cd.get('causalChain') or [])); rp=cd.get('reasoningPath') or {}; print('rp.keys:', list(rp.keys())); print('steps:', len(rp.get('steps') or [])); print('ruleRefs:', len(rp.get('ruleRefs') or [])); print('precedentRefs:', len(rp.get('precedentRefs') or [])); print('decision:', dd.get('decision'));" 2>&1 | head -c 1500
echo

echo "═══ compliance-rule detail (test-spel-1) ═══"
curl -s -m 10 http://localhost:8080/api/v1/knowledge/compliance-rules/test-spel-1 -H "$H" 2>&1 | head -c 600
echo
echo "═══ compliance-rule detail (test-legacy-1) ═══"
curl -s -m 10 http://localhost:8080/api/v1/knowledge/compliance-rules/test-legacy-1 -H "$H" 2>&1 | head -c 600
echo
echo "═══ kb entities (for S5 cross kbase) ═══"
curl -s -m 10 http://localhost:8080/api/v1/ecos/ontology/objects -H "$H" 2>&1 | head -c 200
echo
echo "═══ sysman tenant (probe) ═══"
curl -s -m 10 http://localhost:8080/api/v1/system/tenants -H "$H" 2>&1 | python3 -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data') or []; print('code:',d.get('code'),'total:',len(dd) if isinstance(dd,list) else dd); print('first:', dd[0] if isinstance(dd,list) and dd else 'n/a')" 2>&1 | head -c 400
echo
echo "DONE"
