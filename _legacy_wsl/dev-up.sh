#!/bin/bash
# ECOS Development — 一键启动全部引擎boot
# 用法: bash dev-up.sh [engine1 engine2 ...]
#       不带参数 = 全部6个引擎
#       bash dev-up.sh ai kb = 只启动ai和kb

set -e

ECOS_ROOT="/home/guorongxiao/ECOS/ecos_backend"
JAVA_HOME="${JAVA_HOME:-/home/guorongxiao/.local/jdk/jdk-17.0.19+10}"
MAVEN="/home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn"

# 从Hermes系统配置加载 DEEPSEEK_API_KEY
if [ -f "/home/guorongxiao/.hermes/profiles/gorunkol/.env" ]; then
  export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs)
fi

# 引擎定义: name|port
ALL_ENGINES=(
  "security|18081"
  "data|18082"
  "ontology|18083"
  "ai|18084"
  "kb|18086"
  "cognitive|18089"
)

# 如果传了参数，只启动指定引擎
if [ $# -gt 0 ]; then
  SELECTED=()
  for arg in "$@"; do
    for eng in "${ALL_ENGINES[@]}"; do
      if [ "${eng%%|*}" = "$arg" ]; then
        SELECTED+=("$eng")
      fi
    done
  done
  ENGINES=("${SELECTED[@]}")
else
  ENGINES=("${ALL_ENGINES[@]}")
fi

if [ ${#ENGINES[@]} -eq 0 ]; then
  echo "No matching engines found"
  exit 1
fi

echo "=== ECOS Dev Mode ==="
echo "Starting ${#ENGINES[@]} engine(s)..."
echo ""

PIDS=()
for eng in "${ENGINES[@]}"; do
  name="${eng%%|*}"
  port="${eng##*|}"
  dir="${ECOS_ROOT}/engine/${name}-engine"

  if [ ! -d "$dir" ]; then
    echo "[SKIP] ${name}: directory not found"
    continue
  fi

  echo "[${name}] Starting on port ${port}..."
  (
    cd "$ECOS_ROOT"
    env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}" bash -c "mvn -f $ECOS_ROOT/pom.xml spring-boot:run -pl engine/${name}-engine/${name}-engine-boot -q 2>&1" \
      | sed "s/^/[${name}:${port}] /"
  ) &
  PIDS+=($!)
done

echo ""
echo "All engines launched. PIDs: ${PIDS[*]}"
echo "Frontend: cd ~/ECOS/ecos_frontend && npm run dev"
echo ""
echo "Press Ctrl+C to stop all"

trap "echo 'Stopping all engines...'; kill ${PIDS[*]} 2>/dev/null; exit 0" INT TERM

wait
