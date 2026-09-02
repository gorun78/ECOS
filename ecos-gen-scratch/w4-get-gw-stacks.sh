#!/usr/bin/env bash
# w4-get-gw-stacks.sh — 从 gateway 日志抽取 demo/wave3 异常 + transform 异常
TARGET=/tmp/gateway-wave4.log
# spring-boot:run 默认日志在 stdout, 已 nohup 拉起时不落盘, 改读 console log
# 我们的 w4-gw-mvn.sh 里 nohup sh -c "mvn spring-boot:run ..." 输出在 /tmp/w4-gw-mvn-$(date...)/console.log
LOG_FILE=""
for d in /tmp/w4-gw-*; do
  if [ -d "$d" ] && [ -f "$d/console.log" ]; then LOG_FILE="$d/console.log"; break; fi
done
[ -z "$LOG_FILE" ] && LOG_FILE="${TARGET}"
echo "LOG_FILE=$LOG_FILE"
[ ! -f "$LOG_FILE" ] && { echo "NO_LOG"; exit 2; }
echo "=== wave3 / demo 段 ==="
grep -nE "demo/wave3|Wave3|CausalReasoner|diagnose|ReasoningPath|EntityLinker" "$LOG_FILE" | tail -40
echo "=== 最近 30 分钟 ERROR ==="
awk -v now="$(date +%s)" 'match($0, /ERROR|Exception|Caused by|ClassCastException|ValidationException|Bad Request|RuntimeException|diagnose|transform/i)' "$LOG_FILE" | tail -60
echo "=== 文件末 60 行 ==="
tail -60 "$LOG_FILE"
