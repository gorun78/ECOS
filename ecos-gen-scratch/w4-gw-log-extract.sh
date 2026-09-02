#!/usr/bin/env bash
# w4-gw-log-extract.sh — 提取 wave3 demo / data transform 的关键日志
LOG=/tmp/w4-gw-v3.log
echo "LOG size=$(wc -c < $LOG) lines=$(wc -l < $LOG)"

echo
echo "=== 最近 1 分钟前的 demo/wave3 相关 ==="
# 取最后 15 分钟内 (按时间戳过滤) 与 wave3 / 推理 相关的行
grep -nE "demo/wave3|Wave3|CausalReasoner|Diagnosis|ReasoningPath|EntityLinker|NewsFeedReader" "$LOG" | tail -50

echo
echo "=== 最近所有 ERROR/Exception (last 30) ==="
grep -nE "^\[.*ERROR|\bERROR\b|Exception|Caused by" "$LOG" | tail -30

echo
echo "=== 文件末 80 行 ==="
tail -80 "$LOG"
