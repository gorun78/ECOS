#!/bin/bash
echo "=== 8080 ==="
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/api/health)
echo "api/health=$code"
echo "=== gateway proc ==="
ps -ef | grep -E "gateway|java.*spring-boot" | grep -v grep | head -3
echo "=== log tail ==="
tail -20 /tmp/ecos-gateway.log
