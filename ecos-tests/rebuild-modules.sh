#!/bin/bash
# 重新编译修改过的模块并重启 Gateway
set -e
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl runtime/runtime-access,engine/data-engine/data-engine-impl,gateway -am -DskipTests -Dmaven.test.skip=true -q'
echo "BUILD_EXIT=$?"
