#!/usr/bin/env bash
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
B=ecos
echo "=== 批量 unblock (W-38/39/40/43 worker + V-44 verifier) ==="
for id in t_d45a9f20 t_b40d584f t_260940f4 t_64e6e69e t_c14e22ca; do
  echo -n "unblock $id: "
  $H kanban --board $B unblock $id 2>&1
done
echo
echo "=== post-unblock status ==="
python3 /home/guorongxiao/ECOS/.working/wave1-status.py
