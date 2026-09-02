#!/bin/bash
# Wave-3.2 单测脚本（仅 cognitive-engine-impl 模块）
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=$PATH:/home/guorongxiao/.local/apache-maven-3.9.11/bin
cd /home/guorongxiao/ECOS/ecos_backend
mvn test -pl engine/cognitive-engine/cognitive-engine-impl -Dtest='*Wave32Test,*ReasoningPathFromCausalBuilderTest,*PrecedentRecallerTest,*OagNodesTest,*RuleRefCollectorTest,*EntityLinkerTest,*NewsFeedReaderDemoTest' -B 2> /home/guorongxiao/ECOS/wave32-test-stderr.log | tee /home/guorongxiao/ECOS/wave32-test.log
if grep -q "BUILD FAILURE" /home/guorongxiao/ECOS/wave32-test.log; then
  echo "===TEST_FAIL===" >> /home/guorongxiao/ECOS/wave32-test.log
else
  echo "===TEST_PASS===" >> /home/guorongxiao/ECOS/wave32-test.log
fi
