#!/bin/bash
# 探测 8080 起来
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 6
  code=$(curl -s -o /dev/null -w "%{http_code}" -m 3 http://localhost:8080/api/v1/auth/login 2>/dev/null)
  if [ "$code" = "401" ] || [ "$code" = "403" ] || [ "$code" = "200" ] || [ "$code" = "405" ]; then
    echo "ready at $((i*6))s code=$code"
    exit 0
  fi
  echo "t=$((i*6))s code=$code  still booting"
done
echo "TIMEOUT 60s"
tail -20 /home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log
exit 1
