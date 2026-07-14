#!/bin/bash
set +e
CONTRACTS="/home/guorongxiao/ECOS/05-质量保障/api-contracts.txt"
BASE="http://localhost:8080"

# Get auth token
TOKEN=*** -s -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

echo "Token acquired: ${TOKEN:0:15}..."
echo ""

PASS=0
FAIL=0

while IFS='|' read -r METHOD PATH EXPECTED JQ; do
    # Skip empty lines
    [ -z "$METHOD" ] && continue
    # Skip comments
    [[ "$METHOD" == \#* ]] && continue
    
    # Make the API call
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
      -X "$METHOD" "$BASE$PATH" \
      -H "Authorization: Bearer *** -H "Content-Type: application/json")
    
    if [ "$HTTP" = "$EXPECTED" ]; then
        echo "OK: $METHOD $PATH -> $HTTP"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $METHOD $PATH -> $HTTP (expected $EXPECTED)"
        FAIL=$((FAIL + 1))
    fi
done < "$CONTRACTS"

echo ""
echo "================================"
echo "Results: $PASS PASS, $FAIL FAIL"

if [ $FAIL -gt 0 ]; then
    exit 1
fi
exit 0
