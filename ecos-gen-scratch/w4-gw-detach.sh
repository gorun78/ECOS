#!/usr/bin/env bash
# w4-gw-detach.sh — fully detached Java start (no inpside nohup death)
set +e
# Kill prior java on 8080
kill $(lsof -ti:8080 2>/dev/null) 2>/dev/null
sleep 1
cd /home/guorongxiao/ECOS/ecos_backend/gateway
if [ -f /home/guorongxiao/.hermes/profiles/gorunkol/.env ]; then
  export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs) || true
fi
if [ -f /home/guorongxiao/.config/ecos/jwt-private-key.pem ]; then
  export JWT_PRIVATE_KEY=$(cat /home/guorongxiao/.config/ecos/jwt-private-key.pem)
fi
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
unset HOME
unset HERMES_HOME
# nohup with setsid to detach from Windows terminal
setsid nohup $JAVA_HOME/bin/java -Xms512m -Xmx2048m -jar target/gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise </dev/null >/home/guorongxiao/ECOS/ecos-gen-scratch/w4-gw.log 2>&1 &
PID=$!
echo "Started PID=$PID"
echo "tip: tail -f /home/guorongxiao/ECOS/ecos-gen-scratch/w4-gw.log"
sleep 1
ps -p $PID -o pid,stat,etime,cmd --no-headers && echo "alive"
echo "EXIT=$?"
