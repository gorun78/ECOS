#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME

echo "=== R4: jacoco <skip> 修复 编译验证 ==="
mvn install -P enterprise -DskipTests -q 2>&1 | tail -8
echo "EXIT=$?"