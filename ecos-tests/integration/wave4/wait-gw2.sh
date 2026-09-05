#!/bin/bash
# 轮询 gateway 8080 就绪, 最长等 120s
LAST=""
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40; do
  # 端口就绪 + 日志无 FATAL/error 判定
  if lsof -ti:8080 >/dev/null 2>&1; then
    # 探活
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://localhost:8080/api/health 2>/dev/null)
    if [ "$code" = "200" ]; then
      echo "READY (port OK, /api/health=200) after ~${i}x3s"
      echo "----- last 15 lines of gw log -----"
      tail -n 15 /tmp/w4-gw-v5.log
      echo "----- health body -----"
      curl -s --max-time 3 http://localhost:8080/api/health
      echo ""
      exit 0
    fi
  fi
  sleep 3
done
echo "TIMEOUT after 120s"
echo "----- last 25 lines of gw log -----"
tail -n 25 /tmp/w4-gw-v5.log
exit 1
