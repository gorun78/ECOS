#!/usr/bin/env bash
# w4-gw-real.sh — 真实启动入口
set +e

# --- env (same as ~/start-gateway.sh) ---
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

# --- kill prior java on 8080 ---
kill $(lsof -ti:8080 2>/dev/null) 2>/dev/null || true
sleep 1

# --- launch via mvn directly (NOT via bash -c with backslashes) ---
cd /home/guorongxiao/ECOS/ecos_backend
exec /home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn \
  -f /home/guorongxiao/ECOS/ecos_backend/pom.xml \
  spring-boot:run -pl gateway \
  -Dspring-boot.run.profiles=enterprise \
  -Dmaven.test.skip=true \
  -Dspring-boot.run.jvmArguments="-Xms512m -Xmx2048m" \
  -B
