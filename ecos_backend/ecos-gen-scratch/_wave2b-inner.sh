#!/usr/bin/env bash
# Inner script — 假设 env -i 已生效，HOME=/home/guorongxiao WSL 路径
set -uo pipefail
cd /home/guorongxiao/ECOS/ecos_backend || { echo "cd fail"; exit 2; }
JAR=/home/guorongxiao/.m2/repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/data-engine-impl-1.0.0-SNAPSHOT.jar

echo "════════ V3a: install data-engine-impl -am ════════"
START=$(date +%s)
mvn install -pl engine/data-engine/data-engine-impl -am -DskipTests -Dmaven.test.skip=true -q 2>&1 | tail -50
INST_RC=${PIPESTATUS[0]}
echo "[install] exit=$INST_RC Took$(( $(date +%s) - START ))s"

echo ""
echo "════════ V3b: .m2 JAR contents ════════"
if [ -f "$JAR" ]; then
    unzip -l "$JAR" 2>/dev/null | grep -E "TransformController|TransformServiceImpl"
    TC_COUNT=$(unzip -l "$JAR" 2>/dev/null | grep -c "TransformController")
    echo "[jar] TransformController count=$TC_COUNT"
else
    echo "[jar] MISSING"
fi

echo ""
echo "════════ V3c: mvn test TransformControllerTest ════════"
mvn test -pl engine/data-engine/data-engine-impl -Dtest=TransformControllerTest -DfailIfNoTests=false -q 2>&1 | tee /tmp/ecos-wave2b-test.log | tail -80
TEST_RC=${PIPESTATUS[0]}
echo "[test] exit=$TEST_RC"

echo ""
echo "════════ 关键 grep ════════"
grep -E "Tests run:|BUILD|FAIL|ERROR" /tmp/ecos-wave2b-test.log | head -20 || true

exit $(( INST_RC + TEST_RC ))
