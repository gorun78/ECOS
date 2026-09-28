#!/bin/bash
# ECOS Development — 一键停止全部引擎boot
PORTS=(18081 18082 18083 18084 18086 18089)
ENGINES=(security data ontology ai kb cognitive)

for port in "${PORTS[@]}"; do
  pid=$(lsof -ti:$port 2>/dev/null)
  if [ -n "$pid" ]; then
    kill $pid 2>/dev/null && echo "Stopped port $port (PID $pid)" || echo "Failed to stop port $port"
  else
    echo "Port $port: not running"
  fi
done

echo "=== All engines stopped ==="
