#!/bin/bash
# 多探针: 抓 gateway 在 13:36:18 探针时间窗的 ROOT 异常 (不只是 message 行)
LOG=/tmp/w4-gw-v5.log
N=$(grep -n "13:36:18" "$LOG" | head -1 | cut -d: -f1)
echo "first 13:36:18 line at: $N"
# 从 N-5 行开始抓 +80 行
if [ -n "$N" ]; then
  START=$((N-10))
  [ $START -lt 1 ] && START=1
  sed -n "${START},$((START+120))p" "$LOG" | grep -E "ERROR|Exception|Caused by|Bad value|WrongNumberOf|SQLState|BadSql|tracer|OtelSpan|05:36:18" | head -80
fi
