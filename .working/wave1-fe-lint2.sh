#!/usr/bin/env bash
export HOME=/home/guorongxiao
export PATH=/home/guorongxiao/.hermes/node/bin:$PATH
cd /home/guorongxiao/ECOS/ecos_frontend
echo "PWD: $(pwd)"
echo "node: $(which node)"
echo "tsc: $(which tsc)"
echo "=== npm run lint (tsc --noEmit) ==="
npm run lint 2>&1 | tail -40
echo "LINT_EXIT=$?"
echo
echo "=== npm run build ==="
node_modules/.bin/vite build 2>&1 | tail -30
echo "BUILD_FINISHED"
