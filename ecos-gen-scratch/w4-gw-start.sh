#!/bin/bash
exec 2>&1
# 必须在 WSL Ubuntu 内执行
kill -9 $(lsof -ti:8080 2>/dev/null) 2>/dev/null || true
sleep 1
if [ -f "/home/guorongxiao/.hermes/profiles/gorunkol/.env" ]; then
  export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs)
fi
export JWT_PRIVATE_KEY=$(cat /home/guorongxiao/.config/ecos/jwt-private-key.pem 2>/dev/null)
unset HOME
unset HERMES_HOME
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
cd /home/guorongxiao/ECOS/ecos_backend/gateway
exec $JAVA_HOME/bin/java -Xms512m -Xmx2048m -jar target/gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise
