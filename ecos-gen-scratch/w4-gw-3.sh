#!/usr/bin/env bash
set +e
echo "=== 05 diagnose 400 根因 ==="
grep -B 5 -A 30 "revenue\|diagnose\|Wave3Demo" /tmp/w4-gw-v3.log 2>/dev/null | tail -180
echo "=== 02 transform 400 根因 ==="
grep -B 3 -A 20 "transform/execute\|ValidationException\|op.*gt" /tmp/w4-gw-v3.log 2>/dev/null | tail -100
echo === ===
echo "=== data validation 检查 ==="
ls /home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/step/ | head -10
echo "=== DataValidationStep source ==="
head -120 /home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/step/DataValidationStep.java 2>/dev/null
