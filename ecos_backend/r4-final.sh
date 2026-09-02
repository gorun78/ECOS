#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME

echo "=== R4: -Dmaven.test.skip=true (跳 test compile+exec) ==="
mvn install -P enterprise -Dmaven.test.skip=true -q 2>&1 | tail -8
echo "EXIT=$?"
echo ""
echo "=== R4: -DskipTests (只跳 exec, 不过 test compile) 跑 runtime-core ==="
mvn install -pl runtime/runtime-core -P enterprise -DskipTests -q -e 2>&1 | grep -E "BUILD|jacoco|FAIL|ERROR" | head -5