#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH
unset HOME

echo "=== Wave-4.2 P0 全部修 编译验证 (4 模块) ==="
mvn install -pl gateway,engine/data-engine/data-engine-impl,engine/kb-engine/kb-engine-api,sysman/sysman-impl -am -P enterprise -Dmaven.test.skip=true -q 2>&1 | tail -10
echo "EXIT=$?"