#!/bin/bash
echo "=== setsid mvn 19258 alive? ==="
ps -ef | grep -E '19258' | grep -v grep | head -3
echo ""
echo "=== mvn present? ==="
ps -ef | grep -E 'java.*GatewayApplication' | grep -v grep | awk '{print "pid="$1" ppid="$3" cmd_truncated="$8" "$9" "$10" "$11" "$12}' | head -3
echo ""
echo "=== port 8080 ==="
ss -ltn 2>/dev/null | grep ':8080' || echo "port 8080 free"
echo ""
echo "=== 15 recent log lines ==="
tail -20 /home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log
