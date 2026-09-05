#!/bin/bash
# 等待 Gateway 启动
for i in $(seq 1 60); do
  sleep 3
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/api/health 2>/dev/null || echo "000")
  code2=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/health 2>/dev/null || echo "000")
  echo "poll $i: api/health=$code /health=$code2"
  if [ "$code" = "200" ] || [ "$code2" = "200" ]; then
    echo "READY"
    exit 0
  fi
done
echo "GATEWAY_NOT_READY_AFTER_180s"
echo "--- stderr tail ---"
tail -40 /tmp/ecos-gateway.log
exit 1
