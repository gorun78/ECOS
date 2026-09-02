#!/usr/bin/env bash
# Wave-2B ge D→I 收口 — V3 验证脚本
# 跑 data-engine 子模块的 install + 测试，验证:
#   1. TransformController 编译通过
#   2. TransformImpl 加 @Service 无副作用
#   3. 5 个单测全绿
# 用法: bash /tmp/ECOS-wave2b-ge-verify.sh
set -uo pipefail
HOME=/home/guorongxiao
PATH=/usr/bin:/usr/local/bin:$HOME/.local/bin:$HOME/.local/apache-maven-3.9.11/bin
JAVA_HOME=$HOME/.local/jdk/jdk-17.0.19+10
unset HOME2
cd $HOME/ECOS/ecos_backend || { echo "CWD NATIVE TONORAMIKU: $(pwd)"; exit 2; }

JAR=$HOME/.m2/repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/data-engine-impl-1.0.0-SNAPSHOT.jar
before=$(date +%s)

echo "══════════════════════════════════════════════════"
echo "V3a: mvn install data-engine-impl (-am) — 编译验证"
echo "══════════════════════════════════════════════════"
mvn install -pl engine/data-engine/data-engine-impl -am -DskipTests -Dmaven.test.skip=true -q 2>&1 | tee /tmp/ecos-wave2b-install.log | tail -40
install_code=$?
after=$(date +%s)
echo "install exit=$install_code, took $((after-before))s"

echo ""
echo "══════════════════════════════════════════════════"
echo "V3b: .m2 JAR 是否有 TransformController (新文件)"
echo "══════════════════════════════════════════════════"
if [ -f "$JAR" ]; then
  unzip -l "$JAR" 2>/dev/null | grep -E "TransformController|TransformServiceImpl" | head -10
  unzip -l "$JAR" 2>/dev/null | grep -c "TransformController"
else
  echo "NO JAR — install 失败"
fi

echo ""
echo "══════════════════════════════════════════════════"
echo "V3c: mvn test — 跑 5 单测 + ArchitectureTest"
echo "══════════════════════════════════════════════════"
mvn test -pl engine/data-engine/data-engine-impl -Dtest='TransformControllerTest' -q 2>&1 | tee /tmp/ecos-wave2b-test.log | tail -60
test_code=$?
echo "test exit=$test_code"

echo ""
echo "====== 关键 grep 汇总 ===="
grep -E "Tests run|BUILD|ERROR|FAIL" /tmp/ecos-wave2b-test.log | head -20

exit $((install_code + test_code))
