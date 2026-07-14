#!/bin/bash
# ECOS Agent提交脚本 — 先跑pre-check，通过则commit+push
# 用法: bash ~/ecos-commit.sh "feat: Pipeline Controller — P0-1完成"

set -e

if [ -z "$1" ]; then
    echo "用法: bash ~/ecos-commit.sh "commit message""
    exit 1
fi

echo ">>> Step 1: pre-check"
bash /home/guorongxiao/pre-check.sh
if [ $? -ne 0 ]; then
    echo ">>> pre-check FAILED — 禁止提交"
    exit 1
fi

echo ""
echo ">>> Step 2: git add"
cd /home/guorongxiao/databridge-v2
git add -A

echo ">>> Step 3: git commit"
git commit -m "$1"

echo ">>> Step 4: git push (可选，跳过如果无权限)"
git push 2>/dev/null || echo "  (push skipped — 手动push或PR)"

echo ""
echo "✓ 提交完成: $(git log --oneline -1)"
