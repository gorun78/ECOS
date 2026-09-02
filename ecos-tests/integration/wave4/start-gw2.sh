#!/bin/bash
set -x
echo "=== start marker $(date) ==="
ls -la /home/guorongxiao/start-gateway.sh || echo NO_START_SCRIPT
ls -la /home/guorongxiao/.local/jdk/jdk-17.0.19+10/ 2>/dev/null | head -3 || echo NO_JDK
echo "=== port pre-clean ==="
lsof -ti:8080 2>/dev/null | xargs -r kill -9 2>/dev/null
sleep 1
echo "=== launching ==="
cd /home/guorongxiao/ECOS/ecos_backend
setsid nohup bash /home/guorongxiao/start-gateway.sh > /tmp/w4-gw-v5.log 2>&1 < /dev/null &
echo "LAUNCHED"
sleep 5
echo "=== 5s later ==="
ps aux | grep -E "start-gateway|mvn.*spring-boot|java" | grep -v grep | head -5
echo "=== log 5s ==="
tail -n 20 /tmp/w4-gw-v5.log 2>/dev/null || echo NO_LOG_YET
