#!/bin/bash
echo "=== log file ==="
ls -la /tmp/w4-gw-v4.log 2>/dev/null || echo NO_LOG
echo "=== gateway procs ==="
ps aux | grep -E "start-gateway|spring-boot|java.*gateway" | grep -v grep | head -5
echo "=== port 8080 ==="
lsof -ti:8080 2>/dev/null && echo PORT_OK || echo NO_PORT
echo "=== log tail ==="
tail -n 30 /tmp/w4-gw-v4.log 2>/dev/null || echo NO_LOG
