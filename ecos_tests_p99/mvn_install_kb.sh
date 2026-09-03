#!/bin/bash
# 编译 kb-engine-impl + 复制到 .m2 (只为 gateway 换 JAR 用)
set -x
export HOME=/home/guorongxiao
export PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
cd /home/guorongxiao/ECOS/ecos_backend || exit 1
pwd
mvn install -pl engine/kb-engine/kb-engine-impl -am -DskipTests -Dmaven.test.skip=true -q 2>&1 | tail -30
echo "exit=$?"
