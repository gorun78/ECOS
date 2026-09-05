#!/bin/bash
# 编译 Wave3DemoController → 重启 GW → 重跳 05
cd /home/guorongxiao/ECOS/ecos_backend
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME
echo "=== 编译 cognitive-engine ==="
mvn install -pl engine/cognitive-engine/cognitive-engine-impl -am -P enterprise -Dmaven.test.skip=true -q 2>&1 | tail -8
echo "=== COMPILE_EXIT=$? ==="

echo "=== 重启 GW (保留 3000 沙箱) ==="
OLD8080=$(lsof -ti:8080 2>/dev/null | grep -v 99600)
[ -n "$OLD8080" ] && kill -9 $OLD8080 2>/dev/null
sleep 3
(~/start-gateway.sh > /tmp/gw-w3-ctr.log 2>&1 &)
echo "GW starting..."
for i in $(seq 1 75); do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 --noproxy '*' http://localhost:8080/api/health 2>/dev/null)
  if [ "$code" = "200" ]; then echo "  GW UP at ${i}x2s (log:/tmp/gw-w3-ctr.log)"; break; fi
  sleep 2
done
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 http://localhost:8080/api/health 2>/dev/null)
echo "8080=$code"

echo "=== 重跳 05 (P0-3 修后) ==="
cd /home/guorongxiao/ECOS/ecos-tests/integration/wave4
PATH=/home/guorongxiao/.hermes/node/bin:$PATH node wave4-runner.mjs --filter=05 2>&1 | tail -40

echo "=== DONE ==="