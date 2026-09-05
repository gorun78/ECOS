#!/bin/bash
# 用 setsid 彻底脱离 session
export PATH=/usr/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export HOME=/home/guorongxiao

LOG=/home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log
: > "$LOG"
cd /home/guorongxiao/ECOS/ecos_backend

setsid /usr/bin/nohup /home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn \
  -f /home/guorongxiao/ECOS/ecos_backend/pom.xml \
  spring-boot:run -pl gateway \
  -Dspring-boot.run.profiles=enterprise \
  -Dmaven.test.skip=true \
  -Dspring-boot.run.jvmArguments="-Xms512m -Xmx2048m" \
  >> "$LOG" 2>&1 < /dev/null &
GWPID=$!
echo "$GWPID" > /home/guorongxiao/ECOS/ecos_tests_p99/gateway.pid
disown
echo "setsid bg pid=$GWPID"
