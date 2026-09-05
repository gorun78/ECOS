#!/bin/bash
# Wave-3.3 编译验证脚本 (technical debt wave 3.3)
set -o pipefail
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=/home/guorongxiao/.local/apache-maven-3.9.11/bin:$JAVA_HOME/bin:$PATH
cd /home/guorongxiao/ECOS/ecos_backend && \
  mvn install -P enterprise -DskipTests -Dmaven.test.skip=true -Djacoco.skip=true -q 2>&1 | tail -60
