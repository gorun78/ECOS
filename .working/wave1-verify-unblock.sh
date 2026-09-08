#!/usr/bin/env bash
set -e
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
B=ecos

echo "=== 1) git diff --cached (staged) ==="
cd /home/guorongxiao/ECOS
git diff --cached --stat 2>/dev/null
echo
echo "=== 2) git diff (unstaged) ==="
git diff --stat 2>/dev/null
echo
echo "=== 3) 未跟踪/新增 ==="
git status --short 2>/dev/null
echo
echo "=== 4) 批量 unblock (W-38/40 + V-43/44) ==="
for id in t_d45a9f20 t_260940f4 t_6d63b733 t_c14e22ca; do
  echo -n "unblock $id: "
  $H kanban --board $B unblock $id 2>&1
done
