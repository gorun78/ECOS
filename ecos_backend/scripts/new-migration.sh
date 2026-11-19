#!/usr/bin/env bash
# =============================================================================
# DDL 新脚本生成器（治理建议 §三 第 4 件）
# -----------------------------------------------------------------------------
# 用途：新建迁移脚本时，**自动**从门禁常量读取下一版本号，生成合规头注，
# 落到活单源目录。版本号不再手抄，避免 AGENTS/规范/索引多处复述漂移。
#
# 版本单源：ecos_backend/gateway/src/test/java/.../MigrationVersionUniquenessTest.java
#   的 NEXT_FREE_VERSION 常量。本脚本不做任何"猜测下一个号"，只读该常量。
#
# 用法：
#   bash ecos_backend/scripts/new-migration.sh "add_foo_column"
#   → 生成 docs/50-db_script/02-flyway-migration/gateway/V<n>__add_foo_column.sql
#
# 退出码：
#   0 = 已生成；1 = 目标已存在 / 常量读取失败；2 = 参数错误
# =============================================================================

set -eu

if [ "$#" -lt 1 ]; then
  echo "用法: bash $(basename "$0") \"<描述_用下划线>\" [--dry-run]" >&2
  echo "示例: bash $(basename "$0") add_foo_column" >&2
  exit 2
fi

DESC="$1"
DRY_RUN="${2:-}"

case "$DESC" in
  *[!a-z0-9_]*)
    echo "[FATAL] 描述只允许小写字母/数字/下划线，收到: $DESC" >&2
    exit 2
    ;;
esac

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GATE="${ROOT_DIR}/ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/MigrationVersionUniquenessTest.java"
LIVE_DIR="${ROOT_DIR}/docs/50-db_script/02-flyway-migration/gateway"

if [ ! -f "$GATE" ]; then
  echo "[FATAL] 找不到版本常量源: $GATE" >&2
  exit 1
fi

NEXT="$(grep -oE 'NEXT_FREE_VERSION[[:space:]]*=[[:space:]]*[0-9]+' "$GATE" | grep -oE '[0-9]+$' || true)"
if [ -z "$NEXT" ]; then
  echo "[FATAL] 未能从 $GATE 解析 NEXT_FREE_VERSION（常量已改名？）" >&2
  exit 1
fi

TARGET="${LIVE_DIR}/V${NEXT}__${DESC}.sql"
if [ -e "$TARGET" ]; then
  echo "[FATAL] 目标已存在，拒绝覆盖: $TARGET" >&2
  exit 1
fi

BODY="-- V${NEXT}__${DESC}
-- 来源: $(date +%Y-%m-%d) | 责任人: (填写)
--
-- 红线自检（.trae/rules/数据库访问规范.md）：
--   MC01 主键 VARCHAR(36) 应用侧 UUID，禁 gen_random_uuid()
--   MC02 禁 JSONB / SERIAL / BIGSERIAL，JSON 用 TEXT 承载
--   DR0x 表名 ecos_ 前缀 + 显式 schema 限定（ST07：五引擎 schema 或主控制 schema）
--   IR03 禁 DROP/ALTER 已有表（设计资产历史版本表除外，见铁律 §3.1）

"

if [ "$DRY_RUN" = "--dry-run" ]; then
  echo "[DRY-RUN] 将生成: $TARGET"
  echo "----------"
  printf '%s' "$BODY"
  exit 0
fi

mkdir -p "$LIVE_DIR"
printf '%s' "$BODY" > "$TARGET"

echo "[OK] 已生成: $TARGET"
echo "[NEXT] 写完后须同批把 MigrationVersionUniquenessTest 的"
echo "       MAX_BASELINE_VERSION=${NEXT} / NEXT_FREE_VERSION=$((NEXT + 1)) 一并提升，"
echo "       并跑: mvn -o test -pl gateway -Dtest=MigrationVersionUniquenessTest"
