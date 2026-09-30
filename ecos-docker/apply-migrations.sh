#!/bin/sh
# 一次性脚本: 按版本号升序灌入 ECOS gateway 迁移 SQL (Flyway 禁用替代方案)
# 失败脚本打印前 4 行错误但不中断 (部分迁移在空库环境允许部分失败)
export PGPASSWORD=postgres
fails=0
for f in $(ls /tmp/migrations/V*.sql | sort -V); do
  r=$(psql -U postgres -d sys_man -q -f "$f" 2>&1)
  rc=$?
  tag=$(basename "$f")
  if [ "$rc" -ne 0 ]; then
    fails=$((fails+1))
    echo "=== FAIL: $tag ==="
    echo "$r" | head -4
  fi
done
echo "DONE total_fails=$fails"
