#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME

echo "=== Wave-4.1 fix P0-1 super-admin 编译验证 ==="
mvn install -pl sysman/sysman-impl,gateway -am -P enterprise -Dmaven.test.skip=true -q 2>&1 | tail -8
echo "EXIT=$?"