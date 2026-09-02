#!/bin/bash
# 启动 ECOS Gateway 后台进程, 不阻塞当前 shell
cd /home/guorongxiao/ECOS/ecos_backend
nohup bash /home/guorongxiao/start-gateway.sh > /tmp/ecos-gateway.log 2>&1 &
PID=$!
echo "GATEWAY_PID=$PID"
sleep 2
if kill -0 $PID 2>/dev/null; then
  echo "GATEWAY_RUNNING=1"
  ps -p $PID -o pid,cmd 2>/dev/null | head -2
  echo "/tmp/ecos-gateway.log"
else
  echo "GATEWAY_DIED"
  tail -10 /tmp/ecos-gateway.log
fi
