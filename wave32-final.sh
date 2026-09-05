#!/bin/bash
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$PATH:/home/guorongxiao/.local/apache-maven-3.9.11/bin
cd /home/guorongxiao/ECOS/ecos_backend
echo "=== FINAL WAVE3.2 ACCEPTANCE ===" | tee /home/guorongxiao/ECOS/wave32-final.log
echo
echo "[1/2] enterprise 全模块 install (skipTests+jacoco.skip=true):"
mvn install -P enterprise -DskipTests -Dmaven.test.skip=true -Djacoco.skip=true -q -B 2>> /home/guorongxiao/ECOS/wave32-final.log
ENTERPRISE_EXIT=$?
echo "ENTERPRISE_EXIT=$ENTERPRISE_EXIT" >> /home/guorongxiao/ECOS/wave32-final.log

echo
echo "[2/2] cognitive-engine-impl 单测:"
mvn test -pl engine/cognitive-engine/cognitive-engine-impl -B 2>> /home/guorongxiao/ECOS/wave32-final.log | grep -E "Tests run|BUILD" >> /home/guorongxiao/ECOS/wave32-final.log
TEST_EXIT=$?
echo "TEST_EXIT=$TEST_EXIT" >> /home/guorongxiao/ECOS/wave32-final.log

echo "=== FINAL STATUS: enterprise=$ENTERPRISE_EXIT, tests=$TEST_EXIT ==="
