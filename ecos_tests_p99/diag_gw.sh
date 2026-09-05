#!/bin/bash
echo "=== mvn running? ==="
ps -ef | grep -E 'spring-boot:run -pl gateway|mvn.*gateway' | grep -v grep | head -5
echo ""
echo "=== port 8080 ==="
ss -ltn 2>/dev/null | grep ':8080' || echo "port 8080 free"
echo ""
cat /home/guorongxiao/ECOS/ecos_tests_p99/gateway.pid 2>/dev/null
echo ""
echo "=== log size ==="
wc -c /home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log 2>/dev/null
