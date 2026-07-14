#!/bin/bash
set -e
CONTRACTS="/home/guorongxiao/ECOS/05-质量保障/api-contracts.txt"
BASE="http://localhost:8080"
CURL="/usr/bin/curl"

# Login and save token to file (avoid shell variable *** mangling)
$CURL -s -X POST "$BASE/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin@123"}' \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" \
    > /tmp/ecos_contract_token

TOKEN=$(cat /tmp/ecos_contract_token)
if [ -z "$TOKEN" ]; then
    echo "❌ 无法获取Token"
    exit 1
fi

PASS=0; FAIL=0

while IFS='|' read -r METHOD PATH EXPECTED JQ; do
    [ -z "$METHOD" ] && continue
    [[ "$METHOD" == \#* ]] && continue
    
    HTTP=$($CURL -s -o /dev/null -w "%{http_code}" -X "$METHOD" "$BASE$PATH" \
        -H "Authorization: Bearer *** -H "Content-Type: application/json" 2>/dev/null)
    
    if [ "$HTTP" = "$EXPECTED" ]; then
        echo "✅ $METHOD $PATH → $HTTP"
        PASS=$((PASS+1))
    else
        echo "❌ $METHOD $PATH → $HTTP (expected $EXPECTED)"
        FAIL=$((FAIL+1))
    fi
done < "$CONTRACTS"

rm -f /tmp/ecos_contract_token

echo "================================"
echo "结果: $PASS PASS, $FAIL FAIL"
[ $FAIL -gt 0 ] && exit 1
exit 0
