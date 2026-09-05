#!/bin/bash
# 跑 wave4 7 域 runner, 输出到 /tmp/wave4-test-results-v5.log
cd /home/guorongxiao/ECOS/ecos-tests/integration/wave4
export PATH="/home/guorongxiao/.local/bin:/usr/bin:/usr/local/bin:$PATH"
export ECOS_BASE="http://localhost:8080"
node wave4-runner.mjs 2>&1 | tee /tmp/wave4-test-results-v5.log
echo "=== exit code: ${PIPESTATUS[0]} ==="
