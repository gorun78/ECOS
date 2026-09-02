#!/usr/bin/env bash
# w4-gw-poll-real.sh — 轮询 /api/health 直到 200, 最长 300s
set +e
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 http://localhost:8080/api/health 2>/dev/null)
  if [ "$code" = "200" ]; then
    echo "GATEWAY_UP after ~${i}*10s"
    curl -s --max-time 5 http://localhost:8080/api/health | head -c 200
    echo
    exit 0
  fi
  sleep 10
done
echo "TIMEOUT after 300s"
tail -40 /tmp/w4-gw-v3.log
exit 1
