#!/bin/bash
# tmux detach 后 server 保持
export PATH=/usr/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export HOME=/home/guorongxiao

# 清理既有 session
tmux kill-session -t ecgw 2>/dev/null
# 新建
tmux new-session -d -s ecgw \
  "cd /home/guorongxiao/ECOS/ecos_backend && \
   /home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn -f /home/guorongxiao/ECOS/ecos_backend/pom.xml \
     spring-boot:run -pl gateway \
     -Dspring-boot.run.profiles=enterprise \
     -Dmaven.test.skip=true \
     -Dspring-boot.run.jvmArguments='-Xms512m -Xmx2048m' \
     > /home/guorongxiao/ECOS/ecos_tests_p99/gateway_restart.log 2>&1"
tmux ls
echo "tmux session ecgw spawned"
