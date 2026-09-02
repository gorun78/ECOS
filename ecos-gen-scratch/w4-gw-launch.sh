#!/usr/bin/env bash
# w4-gw-launch.sh — WSL gateway full launch (referencing start-gateway.sh)
# stdout → /home/guorongxiao/ECOS/ecos-gen-scratch/w4-gw.log
set +e
exec 2>&1
TIMESTAMP() { date +"%Y-%m-%d %H:%M:%S"; }
echo "[$(TIMESTAMP)] w4-gw-launch.sh start (pid $)"
kill $(lsof -ti:8080 2>/dev/null) 2>/dev/null || true
sleep 0.5
if [ -f "/home/guorongxiao/.hermes/profiles/gorunkol/.env" ]; then
  export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs) || true
fi
JWTPEM=/home/guorongxiao/.config/ecos/jwt-private-key.pem
if [ -f "$JWTPEM" ]; then
  export JWT_PRIVATE_KEY=$(cat "$JWTPEM")
fi
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:$PATH
unset HOME
unset HERMES_HOME
cd /home/guorongxiao/ECOS/ecos_backend/gateway
echo "[$(TIMESTAMP)] JAVA=$JAVA_HOME"
echo "[$(TIMESTAMP)] starting java -jar ... (profile=enterprise)"
exec $JAVA_HOME/bin/java -Xms512m -Xmx2048m -jar target/gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise
