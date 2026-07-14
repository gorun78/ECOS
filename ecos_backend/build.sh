#!/bin/bash
# =============================================================================
# build.sh — ECOS 三版本构建脚本
#
# 用法:
#   bash build.sh standard     # 构建标准版 (默认)
#   bash build.sh enterprise   # 构建企业版
#   bash build.sh ultimate     # 构建旗舰版
#
# 产出: gateway/target/gateway-*.jar
# =============================================================================

set -euo pipefail

EDITION="${1:-standard}"

# 验证 edition 参数
case "$EDITION" in
    standard|enterprise|ultimate)
        ;;
    *)
        echo "ERROR: 无效的版本参数 '$EDITION'"
        echo "用法: bash build.sh [standard|enterprise|ultimate]"
        exit 1
        ;;
esac

echo "============================================"
echo "  ECOS Build — ${EDITION} Edition"
echo "============================================"

# 切换到项目根目录
cd "$(dirname "$0")"
PROJECT_ROOT="$(pwd)"
echo "[INFO] 项目根目录: $PROJECT_ROOT"

# 加载环境变量 (如 JAVA_HOME, MAVEN_HOME 等)
if [ -f ~/ecos-env.sh ]; then
    source ~/ecos-env.sh
    echo "[INFO] 已加载 ~/ecos-env.sh"
fi

# 执行 Maven 构建
echo "[INFO] 执行: mvn -P${EDITION} install -Dmaven.test.skip=true -q"
mvn -P"${EDITION}" install -Dmaven.test.skip=true -q

# 验证产出
JAR_FILE="gateway/target/gateway-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo ""
    echo "============================================"
    echo "  BUILD SUCCESS: ${EDITION} Edition"
    echo "  JAR: $PROJECT_ROOT/$JAR_FILE"
    echo "  Size: $JAR_SIZE"
    echo "============================================"
else
    echo ""
    echo "ERROR: 构建产物未找到: $JAR_FILE"
    echo "请检查 Maven 构建日志"
    exit 1
fi
