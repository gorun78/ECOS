#!/usr/bin/env bash
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
echo "=== W-38 events last 10 ==="
$H kanban --board ecos show t_d45a9f20 2>&1 > /home/guorongxiao/.working/tmp-w38.txt
tail -25 /home/guorongxiao/.working/tmp-w38.txt
echo
echo "=== 第1次 unblock W-38 ==="
$H kanban --board ecos unblock t_d45a9f20 2>&1
