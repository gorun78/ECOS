#!/usr/bin/env bash
# 隔离验证测试是否真跑（surefire report）
find /home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/target/surefire-reports -name "*.txt" 2>/dev/null | xargs grep -l "TransformControllerTest" 2>/dev/null
echo "===== surefire TXTs ====="
for f in /home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/target/surefire-reports/*TransformControllerTest*.txt; do
  [ -f "$f" ] && echo "--- $f ---" && cat "$f"
done
echo "===== .m2 jar 检查（artifactId 真正名）====="
ls -la /home/guorongxiao/.m2/repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/*.jar 2>/dev/null | head
for j in /home/guorongxiao/.m2/repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/*.jar; do
  [ -f "$j" ] && echo "--- $j ---" && unzip -l "$j" 2>/dev/null | grep -E "TransformController|TransformServiceImpl|TransformService.class" | head
done
echo "===== target/classes ====="
find /home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/target/classes -name "TransformController*.class" 2>/dev/null
find /home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/target/classes -name "TransformServiceImpl*.class" 2>/dev/null
