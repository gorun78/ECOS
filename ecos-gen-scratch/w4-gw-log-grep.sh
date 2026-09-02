#!/usr/bin/env bash
# w4-gw-log-grep.sh — grep gateway log 找 wave3 异常
set +e
LOG=/tmp/w4-gw-v3.log
echo "=== 最后 200 行 ==="
tail -300 "$LOG" 2>/dev/null
echo "=== 1h 内 ERROR/WARN ==="
grep -E "ERROR|WARN|Exception|at com.chinacreator.gzcm|wave3|Reasoning|CausalReason|diagnose|Wave3Demo|rejection" "$LOG" 2>/dev/null | tail -80
echo "=== 完整搜 异常 stack trace ==="
grep -B 1 -A 20 -E "at com.chinacreator.gzcm|Exception.*:[^ ]+.*\n" "$LOG" 2>/dev/null | head -120
echo === ===
echo "=== 搜 compliance-rules 异常 ==="
grep -B 2 -A 15 -E "compliance|ComplianceRule" "$LOG" 2>/dev/null | tail -80
