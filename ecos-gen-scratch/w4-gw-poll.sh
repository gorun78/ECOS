#!/usr/bin/env bash
# w4-gw-poll.sh — 轮询 gateway, 等到 /api/health == 200 (600s 超时) 或打印当前尾部
set +e
cur=0
while [ $cur -lt 60 ]; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 http://localhost:8080/api/health 2>/dev/null)
  if [ "$code" = "200" ]; then
    echo "GATEWAY_UP after ${cur}0s"
    exit 0
  fi
  sleep 10
  cur=$((cur+1))
done
echo "TIMEOUT after 600s"
echo "=== log tail ==="
tail -50 /tmp/w4-gw-v2.log 2>/dev/null
ls -la /tmp/w4-gw-v2.log
