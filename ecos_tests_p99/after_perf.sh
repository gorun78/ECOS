#!/bin/bash
echo "=== 1. 重新拿 token ==="
bash /home/guorongxiao/ECOS/ecos_tests_p99/get_token.sh

echo ""
echo "=== 2. 5 端点 after 压测 (串行 N=50) ==="
bash /home/guorongxiao/ECOS/ecos_tests_p99/baseline.sh

echo ""
echo "=== 3. 5 端点 after 并发压测 (c8, 每端点 13 req) ==="
bash /home/guorongxiao/ECOS/ecos_tests_p99/concurrent_baseline.sh
