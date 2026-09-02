#!/bin/bash
# 等待 gateway 起来
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40; do
  if lsof -ti:8080 >/dev/null 2>&1; then
    echo READY
    break
  fi
  sleep 3
done
echo "----- last 25 lines of gw log -----"
tail -n 25 /tmp/w4-gw-v4.log 2>/dev/null
if lsof -ti:8080 >/dev/null 2>&1; then echo "--- RUNNING"; else echo "--- NOT UP"; fi
