#!/bin/bash
echo "=== gateway jar ==="
ls -la /home/guorongxiao/ECOS/ecos_backend/gateway/target/*.jar 2>/dev/null | head -n 3
echo "=== curl 回归脚本 ==="
ls /home/guorongxiao/ECOS/ecos_tests/curl_all_regress.* 2>/dev/null
echo "=== V108 ==="
ls /home/guorongxiao/ECOS/ecos_backend/gateway/src/main/resources/db/migration/V108* 2>/dev/null
echo "=== GW 当前 ==="
lsof -ti:8080 | head -n 1
curl -s -o /dev/null -w "health=%{http_code}\n" --max-time 3 http://127.0.0.1:8080/actuator/health
echo "=== END ==="
