#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME

echo "=== runtime-core 单模块 -e 看错 ==="
mvn install -pl runtime/runtime-core -P enterprise -DskipTests -q -e 2>&1 | tail -40
echo "EXIT=$?"