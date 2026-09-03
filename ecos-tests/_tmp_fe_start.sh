#!/bin/bash
# 启动 FE 3000 (后台 nohup)
exec 1> /tmp/ecos-fe-start.log 2>&1
cd /home/guorongxiao/ECOS/ecos_frontend
if [ ! -d node_modules ]; then
  echo "npm install..."
  npm install --no-progress 2>&1 | tail -n 10
fi
nohup env PATH=/home/guorongxiao/.hermes/node/bin:$PATH npm run dev -- --port 3000 --host 127.0.0.1 > /tmp/ecos-fe-dev.log 2>&1 &
echo "FE start pid=$!"
sleep 8
curl -s -o /dev/null -w "FE after 8s: %{http_code}\n" --max-time 5 http://127.0.0.1:3000
echo "DONE"
exec > /dev/null
