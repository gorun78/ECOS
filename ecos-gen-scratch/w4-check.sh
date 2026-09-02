#!/usr/bin/env bash
set +e
sleep 8
echo "=== procs ==="
ps -ef | grep gateway | grep -v grep | head -3
echo "=== port 8080 ==="
lsof -ti:8080 2>/dev/null | head -3
echo "=== log size ==="
ls -la /home/guorongxiao/ECOS/ecos-gen-scratch/w4-gw.log 2>&1 | head -2
echo "=== log tail ==="
tail -15 /home/guorongxiao/ECOS/ecos-gen-scratch/w4-gw.log 2>&1
echo "=== health ==="
curl -s -o /dev/null -w "alive:%{http_code}\n" --max-time 4 http://localhost:8080/api/health 2>&1
