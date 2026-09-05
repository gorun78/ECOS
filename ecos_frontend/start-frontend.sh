#!/bin/bash
# 前端启动 (WSL 原生 node, 避开 Windows interop npm)
export PATH=/home/guorongxiao/.hermes/node/bin:$PATH
echo "NPM=$(which npm) NODE=$(which node)"
cd /home/guorongxiao/ECOS/ecos_frontend || exit 1
lsof -ti:3000 | xargs -r kill -9 2>/dev/null
sleep 1
exec npm run dev