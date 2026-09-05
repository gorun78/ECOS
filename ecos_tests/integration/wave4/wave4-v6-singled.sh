#!/bin/bash
# 单跳 05 + 06 看 P0-3/P0-4 栽点
cd /home/guorongxiao/ECOS/ecos-tests/integration/wave4 || exit 1
echo "=== SINGE-JUMP 05 (P0-3' body 重读) ==="
PATH=/home/guorongxiao/.hermes/node/bin:$PATH node wave4-runner.mjs --filter=05 2>&1 | tee /tmp/w4-06-v6-05.log
echo
echo "=== SINGLE-JUMP 06 (P0-4 schema 撞) ==="
PATH=/home/guorongxiao/.hermes/node/bin:$PATH node wave4-runner.mjs --filter=06 2>&1 | tee /tmp/w4-v6-06.log
echo
echo "=== DONE ==="
echo "8080: $(lsof -ti:8080 | head -1)"