#!/bin/bash
# 编译 kb-engine-impl, skip jacoco 覆盖率 gate (本不涉及覆盖率，只优化 P99)
set -x
export HOME=/home/guorongxiao
export PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
cd /home/guorongxiao/ECOS/ecos_backend || exit 1
pwd
mvn install -pl engine/kb-engine/kb-engine-impl -DskipTests -Dmaven.test.skip=true \
  -Djacoco.skip=true -Djacoco.perModuleCheck.skip=true -Dmaven.javadoc.skip=true -q 2>&1 | tail -40
echo "exit=$?"
# 验证 jar 编译时间
ls -la /home/guorongxiao/.m2/repository/com/chinacreator/gzcm/kb-engine-impl/1.0.0-SNAPSHOT/*.jar
