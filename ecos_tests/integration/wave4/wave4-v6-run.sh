#!/bin/bash
# Wave-4.1 7 域 mjs 实跳 v6 (T-02 PM 接管)
cd /home/guorongxiao/ECOS/ecos-tests/integration/wave4 || exit 1
echo "=== Wave-4.2 v6 P0-3' 修后实跳 (T-02 by PM main-thread) ==="
echo "Start: $(date)"
PATH=/home/guorongxiao/.hermes/node/bin:$PATH node wave4-runner.mjs 2>&1 | tee /tmp/w4-v6-run.log
echo "=== EXIT=$? ==="
echo "Done: $(date)"
echo ""
echo "=== 8080 留活 ==="
lsof -ti:8080 || echo "(no 8080)"