#!/bin/bash
# 找 GatewayApplication 进程 PID, 杀掉, 重启 gateway
GATEWAY_PID=$(ps -eo pid,cmd | grep -E 'java.*gateway.*GatewayApplication' | grep -v grep | awk '{print $1}' | head -1)
echo "current gateway pid: $GATEWAY_PID"
if [ -n "$GATEWAY_PID" ]; then
  # 也杀掉父 mvn 进程
  PARENT=$(ps -o ppid= -p "$GATEWAY_PID" 2>/dev/null | tr -d ' ')
  echo "parent mvn pid: $PARENT"
  kill -9 "$GATEWAY_PID" 2>/dev/null
  if [ -n "$PARENT" ]; then kill -9 "$PARENT" 2>/dev/null; fi
  sleep 2
  # 再确认 8080 已 release
  for i in 1 2 3 4 5; do
    port_used=$(ss -ltn 2>/dev/null | grep ':8080' | wc -l)
    if [ "$port_used" = "0" ]; then
      echo "port 8080 released after ${i}s"
      break
    fi
    sleep 1
  done
fi
echo "--- new token (后续压测需要) ---"
echo "ready"