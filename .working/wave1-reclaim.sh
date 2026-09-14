#!/usr/bin/env bash
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
B=ecos

echo "=== 1) W-40 (t_260940f4) 仍在 run —— 不干预 ==="
echo "=== 2) W-41/43/44 从未被 claim, 尝试 reclaim ==="

# reclaim 需要一个 task 提出新 claim 时给她
$H kanban --board $B reclaim t_448ed2bb 2>&1 || echo "reclaim 41 failed"
$H kanban --board $B reclaim t_64e6e69e 2>&1 || echo "reclaim 43 failed"
$H kanban --board $B reclaim t_8986e024 2>&1 || echo "reclaim 44 failed"

echo
echo "=== 3) 看 dispatch / reclaim help ==="
$H kanban reclaim --help 2>&1
echo
$H kanban dispatch --help 2>&1
