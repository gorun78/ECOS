#!/usr/bin/env bash
set -e
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
B=ecos
for id in t_d45a9f20 t_b40d584f t_260940f4 t_448ed2bb t_64e6e69e t_8986e024; do
  echo "--- unblock $id ---"
  $H kanban --board $B unblock $id 2>&1 || echo "  (already unblocked/not blocked)"
done
echo ""
echo "=== Git status post-unblock ==="
cd /home/guorongxiao/ECOS
git status --short | head -25
