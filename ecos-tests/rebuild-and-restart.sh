#!/bin/bash
# 重编译 data-engine-impl (含 mapper 修复) 并重启 Gateway
set -u

env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/data-engine/data-engine-impl -DskipTests -Dmaven.test.skip=true -q'
BUILD=$?
echo "BUILD_EXIT=$BUILD"
[ $BUILD -ne 0 ] && exit 1

echo "=== 重启 Gateway ==="
lsof -ti:8080 | xargs -r kill -9
sleep 2
cd ~
nohup bash ~/start-gateway.sh > /tmp/gateway-restart2.log 2>&1 &
echo "started pid=$!"

echo "=== 等待就绪 ==="
for i in $(seq 1 60); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"x","password":"x"}' 2>/dev/null)
  if [ "$CODE" = "200" ] || [ "$CODE" = "400" ] || [ "$CODE" = "401" ]; then
    echo "Gateway READY after ~$((i*3))s"
    exit 0
  fi
  sleep 3
done
echo "TIMEOUT"
tail -20 /tmp/gateway-restart2.log
exit 1
