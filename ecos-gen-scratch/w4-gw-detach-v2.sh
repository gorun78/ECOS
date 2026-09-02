#!/usr/bin/env bash
# w4-gw-detach-v2.sh — fully detach via setsid + nohup, write log to /tmp (avoids UNC)
set +e
kill $(lsof -ti:8080 2>/dev/null) 2>/dev/null || true
sleep 1
if [ -f /home/guorongxiao/.hermes/profiles/gorunkol/.env ]; then
  export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs) || true
fi
if [ -f /home/guorongxiao/.config/ecos/jwt-private-key.pem ]; then
  export JWT_PRIVATE_KEY=$(cat /home/guorongxiao/.config/ecos/jwt-private-key.pem)
fi
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME
unset HERMES_HOME

LOG=/tmp/w4-gw-v2.log
: > "$LOG"

# 用 setsid nohup 创建新 session, 写日志到 /tmp (本地盘 faster)
setsid nohup bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && \`
  export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 && \`
  export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH && \`
  if [ -f /home/guorongxiao/.hermes/profiles/gorunkol/.env ]; then export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs) || true; fi; \`
  if [ -f /home/guorongxiao/.config/ecos/jwt-private-key.pem ]; then export JWT_PRIVATE_KEY=$(cat /home/guorongxiao/.config/ecos/jwt-private-key.pem); fi; \`
  unset HOME; unset HERMES_HOME; \`
  echo "[$(date)] mvn spring-boot:run $(date)" >> /tmp/w4-gw-v2.log; \`
  /home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn -f /home/guorongxiao/ECOS/ecos_backend/pom.xml \`
    spring-boot:run -pl gateway -Dspring-boot.run.profiles=enterprise -Dmaven.test.skip=true -Dnonexisting.skip=true -Dspring-boot.run.jvmArguments="-Xms512m -Xmx2048m" -B 2>> /tmp/w4-gw-v2.log' \
  </dev/null >"$LOG" 2>&1 &

PID=$!
echo "PID=$PID"
sleep 1
if kill -0 $PID 2>/dev/null; then
  echo "alive"
else
  echo "DIED"
  tail -20 "$LOG"
fi
echo "LOG=$LOG"
