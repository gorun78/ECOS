#!/bin/bash
# soak-metrics.sh — 每 5min 采 GW 进程 heap/gc/cpu/rss + 5min k6 段 P99
# 用法: nohup bash soak-metrics.sh > ~/ecos-soak/metrics-$(date +%Y%m%d).csv &
# 输出 CSV: timestamp,heap_used_mb,heap_total_mb,gc_count,gc_time_ms,cpu_pct,vmrss_kb
set +e
JDK=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
JSTAT=$JDK/bin/jstat
JSTACK=$JDK/bin/jstack
OUT_FILE=${1:-/tmp/ecos_soak_metrics.csv}
INTERVAL=${2:-300}   # 5 min
echo "timestamp,heap_used_mb,heap_total_mb,ygc_count,ygc_time_ms,fgc_count,fgc_time_ms,cpu_pct,vmrss_kb" > "$OUT_FILE"

while true; do
  JPID=$(ps -ef | grep -E 'GatewayApplication' | grep -v grep | awk '{print $2}' | head -n1)
  if [ -z "$JPID" ]; then
    echo "  [WARN] no GW PID, sleep 30 retry"
    for i in $(seq 1 10); do
      sleep 30
      JPID=$(ps -ef | grep -E 'GatewayApplication' | grep -v grep | awk '{print $2}' | head -n1)
      [ -n "$JPID" ] && break
    done
    [ -z "$JPID" ] && { echo "  [CRIT] GW still dead, exit"; exit 1; }
  fi
  TS=$(date '+%Y-%m-%d %H:%M:%S')
  # jstat -gcutil (percent) + -gc (KB)
  gcutil=$($JSTAT -gcutil $JPID 2>/dev/null | tail -n 1)
  gc_kb=$($JSTAT -gc $JPID 2>/dev/null | tail -n 1)
  # 取 S0 S1 E O M CCS YGC YGCT FGC FGCT CGC CGCT GCT
  S0=$(echo $gcutil | awk '{print $1}')
  S1=$(echo $gcutil | awk '{print $2}')
  E=$(echo $gcutil | awk '{print $3}')
  O=$(echo $gcutil | awk '{print $4}')
  CCS=$(echo $gcutil | awk '{print $6}')
  YGC=$(echo $gcutil | awk '{print $8}')
  YGCT=$(echo $gcutil | awk '{print $9}')
  FGC=$(echo $gcutil | awk '{print $10}')
  FGCT=$(echo $gcutil | awk '{print $11}')
  GCT=$(echo $gcutil | awk '{print $13}')

  # gc_kb 是 KB
  S0_KB=$(echo $gc_kb | awk '{print $3}')
  S1_KB=$(echo $gc_kb | awk '{print $4}')
  E_KB=$(echo $gc_kb | awk '{print $5}')
  O_KB=$(echo $gc_kb | awk '{print $6}')

  # used = (S0+S1+E)/2 + O  (老年代 O 直接 KB)
  AVG_S=$(( (S0_KB + S1_KB) / 2 ))
  USED_KB=$(( AVG_S + E_KB + O_KB ))
  USED_MB=$(( USED_KB / 1024 ))
  TOTAL_MB=$(( (S0_KB + S1_KB + E_KB + O_KB) / 1024 ))

  # CPU% (jstack 不算稳, 用 top)
  CPU=$(top -bn1 -p $JPID 2>/dev/null | grep -E "^\s*$JPID" | awk '{print $9}')
  [ -z "$CPU" ] && CPU=0

  # RSS
  RSS_KB=$(grep VmRSS /proc/$JPID/status 2>/dev/null | awk '{print $2}')
  [ -z "$RSS_KB" ] && RSS_KB=0

  echo "${TS},${USED_MB},${TOTAL_MB},${YGC},${YGCT},${FGC},${FGCT},${CPU:-0},${RSS_KB}" >> "$OUT_FILE"
  echo "  ${TS} heapUsed=${USED_MB}MB heapTotal=${TOTAL_MB}MB YGC=${YGC} FGC=${FGC} CPU=${CPU}% RSS=${RSS_KB}KB"

  sleep $INTERVAL
done
