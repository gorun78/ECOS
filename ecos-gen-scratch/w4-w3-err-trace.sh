#!/usr/bin/env bash
LOG=/tmp/w4-gw-v3.log
echo "=== 13:11 段 (刚才 curl A/B/C 时间窗) ==="
grep -nE "T13:1[01]:[0-5].*(Wave3|demo/wave3|Quota|Required request|Bad Request|Insufficient|permit|tenantId|demo\(java)" "$LOG" | tail -60
