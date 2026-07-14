#!/bin/bash
# =============================================================================
# deploy.sh — ECOS 三版本部署脚本 (Docker Compose)
#
# 用法:
#   bash deploy.sh standard     # 部署标准版 (PG + Gateway)
#   bash deploy.sh enterprise   # 部署企业版 (PG + Neo4j + Gateway)
#   bash deploy.sh ultimate     # 部署旗舰版 (PG + Neo4j + Doris + Gateway)
#
# 前置条件: 先执行 build.sh 构建对应版本的 JAR
# =============================================================================

set -euo pipefail

EDITION="${1:-standard}"

# 验证 edition 参数
case "$EDITION" in
    standard|enterprise|ultimate)
        ;;
    *)
        echo "ERROR: 无效的版本参数 '$EDITION'"
        echo "用法: bash deploy.sh [standard|enterprise|ultimate]"
        exit 1
        ;;
esac

echo "============================================"
echo "  ECOS Deploy — ${EDITION} Edition"
echo "============================================"

# 切换到 docker 目录
cd "$(dirname "$0")/docker"
DOCKER_DIR="$(pwd)"
echo "[INFO] Docker 目录: $DOCKER_DIR"

# 导出 EDITION 环境变量供 docker compose 使用
export ECOS_EDITION="$EDITION"

# 根据版本启动对应服务
echo "[INFO] 启动 ${EDITION} 版服务..."
case "$EDITION" in
    standard)
        docker compose -f docker-compose.base.yml -f docker-compose.standard.yml up -d --build
        ;;
    enterprise)
        docker compose -f docker-compose.base.yml -f docker-compose.enterprise.yml up -d --build
        ;;
    ultimate)
        docker compose -f docker-compose.base.yml -f docker-compose.ultimate.yml up -d --build
        ;;
esac

echo ""
echo "============================================"
echo "  DEPLOY DONE — ${EDITION} Edition"
echo "  Gateway: http://localhost:8080"
echo "  Health:  http://localhost:8080/api/health"
case "$EDITION" in
    enterprise|ultimate)
        echo "  Neo4j Browser: http://localhost:7474"
        ;;
esac
case "$EDITION" in
    ultimate)
        echo "  Doris FE: http://localhost:8030"
        echo "  Doris MySQL Protocol: localhost:9030"
        ;;
esac
echo "============================================"
echo ""
echo "查看日志: docker compose -f docker-compose.base.yml -f docker-compose.${EDITION}.yml logs -f"
echo "停止服务: docker compose -f docker-compose.base.yml -f docker-compose.${EDITION}.yml down"
