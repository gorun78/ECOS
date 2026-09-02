#!/bin/bash
# Wave-3.2 enterprise 档全模块编译验收
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$PATH:/home/guorongxiao/.local/apache-maven-3.9.11/bin
cd /home/guorongxiao/ECOS/ecos_backend
mvn install -P enterprise -DskipTests -Dmaven.test.skip=true -Djacoco.skip=true -q -B 2> /home/guorongxiao/ECOS/wave32-enterprise-test-stderr.log | tail -40
tail -5 /home/guorongxiao/ECOS/wave32-enterprise-test-stderr.log
