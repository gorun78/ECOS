#!/bin/bash
# 临时编译脚本（Wave-3.2 自检）
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$PATH:/home/guorongxiao/.local/apache-maven-3.9.11/bin
cd /home/guorongxiao/ECOS/ecos_backend
mvn install -pl engine/cognitive-engine/cognitive-engine-api,engine/cognitive-engine/cognitive-engine-impl -am -DskipTests -Dmaven.test.skip=true -q -B 2> >(tee -a /home/guorongxiao/ECOS/wave32-compile.log) | tail -60
# 输出末尾状态
if [ ${PIPESTATUS[0]} -eq 0 ]; then
  echo "===COMPILE_OK===" >> /home/guorongxiao/ECOS/wave32-compile.log
else
  echo "===COMPILE_FAIL===" >> /home/guorongxiao/ECOS/wave32-compile.log
fi
