#!/bin/bash
cd /home/guorongxiao/ECOS
# 2 根 build script — Wave-4.2 Wave-4.0 verified 接 (不只是跑 5)
# 这些是 debug 陪, 不 commit
echo "=== 看 2 个根 build script 内容 (is we commit?) ==="
head -n 5 ecos_backend/run_kb_extraction_test.sh 2>/dev/null
echo "..."
echo ""
echo "=== run_tests.sh ==="
head -n 5 ecos_backend/run_tests.sh 2>/dev/null
echo "..."
echo ""
echo "=== 这 2 与 docs 不 related. 不 commit. ==="
# 保留 on  disc, 不 commit
ls -la ecos_backend/run_*.sh
