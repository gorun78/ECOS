#!/usr/bin/env bash
# w4-gw-detach-v3.sh — fully detached, log to /tmp (avoids UNC)
set +e
kill $(lsof -ti:8080 2>/dev/null) 2>/dev/null || true
sleep 1

LOG=/tmp/w4-gw-v3.log
: > "$LOG"
# setsid 让 java 进程脱离当前 tty
setsid nohup bash /home/guorongxiao/ECOS/ecos-gen-scratch/w4-gw-real.sh </dev/null >"$LOG" 2>&1 &
PID=$!
echo "PID=$PID LOG=$LOG"
sleep 1
if kill -0 $PID 2>/dev/null; then
  echo "ALIVE-STARTED"
  ps -p $PID -o pid,stat,etime,cmd --no-headers | head -2
else
  echo "DIED-IMMEDIATELY"
  tail -10 "$LOG"
fi
