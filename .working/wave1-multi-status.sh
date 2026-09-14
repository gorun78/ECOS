#!/usr/bin/env bash
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
for id in t_d45a9f20 t_b40d584f t_260940f4 t_448ed2bb t_64e6e69e t_8986e024; do
  echo "============ $id ============"
  $H kanban --board ecos show $id 2>&1 | sed -n '1,15p;/^Events/,/^[A-Z][a-z]*:/p' | head -40
  echo
done
