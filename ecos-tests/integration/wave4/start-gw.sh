#!/bin/bash
# 以 setsid + nohup 持久启动 gateway，脱离调用者会话
lsof -ti:8080 2>/dev/null | xargs -r kill -9 2>/dev/null
sleep 1
cd /home/guorongxiao/ECOS/ecos_backend
setsid nohup bash /home/guorongxiao/start-gateway.sh > /tmp/w4-gw-v4.log 2>&1 < /dev/null &
echo "LAUNCHED pid=$!"
