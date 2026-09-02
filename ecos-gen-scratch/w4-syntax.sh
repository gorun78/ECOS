#!/usr/bin/env bash
NODE=/home/guorongxiao/.local/bin/node
cd /home/guorongxiao/ECOS/ecos-tests/integration/wave4
shopt -s nullglob
for f in lib/w4-common.mjs wave4-runner.mjs 01-*.mjs 02-*.mjs 03-*.mjs 04-*.mjs 05-*.mjs 06-*.mjs 07-*.mjs; do
  if $NODE --check "$f" >/dev/null 2>&1; then
    echo "  OK  $f"
  else
    echo "  SYNTAX-ERR $f"
    $NODE --check "$f" 2>&1 | head -10
  fi
done
