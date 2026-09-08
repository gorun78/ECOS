#!/usr/bin/env bash
cd /home/guorongxiao/ECOS/ecos_frontend
echo "=== npm run lint ==="
npm run lint 2>&1 | head -80
echo
echo "=== npm run build ==="
npm run build 2>&1 | tail -30
echo "BUILD_EXIT=$?"
