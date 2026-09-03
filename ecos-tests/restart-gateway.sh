#!/bin/bash
# 重启 Gateway 并等待就绪
set -u

echo "=== .m2 JAR 新时间戳 ==="
ls -la ~/.m2/repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/data-engine-impl-1.0.0-SNAPSHOT.jar \
       ~/.m2/repository/com/chinacreator/gzcm/runtime-access/1.0.0-SNAPSHOT/runtime-access-1.0.0-SNAPSHOT.jar 2>/dev/null | awk '{print $6, $7, $8, $NF}'

echo ""
echo "=== 清端口 8080 ==="
lsof -ti:8080 | xargs -r kill -9
sleep 2
lsof -ti:8080 && echo "端口仍占用" || echo "端口已清空"

echo ""
echo "=== 启动 Gateway ==="
cd ~
nohup bash ~/start-gateway.sh > /tmp/gateway-restart.log 2>&1 &
echo "started, pid=$!"

echo ""
echo "=== 等待就绪 (最多 180s) ==="
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/auth/login -X POST -H 'Content-Type: application/json' -d '{"username":"x","password":"x"}' 2>/dev/null | grep -qE '200|400|401'; then
    echo "Gateway READY after ~$((i*3))s"
    exit 0
  fi
  sleep 3
done
echo "TIMEOUT waiting for gateway"
tail -20 /tmp/gateway-restart.log
exit 1
