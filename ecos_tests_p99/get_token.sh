#!/bin/bash
# 登录拿 token
set -e
RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')
echo "$RESP" | python3 -c "import sys,json
d=json.load(sys.stdin)
data=d.get('data') or {}
tok=data.get('accessToken') or data.get('token') or d.get('accessToken') or d.get('token')
if not tok:
    print('NO_TOKEN', d, file=sys.stderr); sys.exit(1)
print(tok)" > /tmp/ecos_token.txt
echo "token_len=$(wc -c < /tmp/ecos_token.txt)"
