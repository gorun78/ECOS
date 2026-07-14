#!/usr/bin/env bash
# =============================================================================
# WSL侧清理脚本 — 删除6个ECOS Profile
# 运行前确认：Windows侧已确认迁移成功
# =============================================================================
set -e

echo "╔════════════════════════════════════════════════╗"
echo "║   WSL侧 ECOS Profile 清理                      ║"
echo "╚════════════════════════════════════════════════╝"
echo ""
echo "⚠️  即将删除以下WSL侧Profile："
echo "   ecos-pmo, ecos-pm, ecos-arch, ecos-fe, ecos-be, ecos-qa"
echo ""
echo "确认迁移已完成？Windows侧已能正常使用？"
echo "按 Ctrl+C 取消，或按任意键继续..."
read -r

HERMES_BASE=/home/guorongxiao/.hermes/profiles

for p in ecos-pmo ecos-pm ecos-arch ecos-fe ecos-be ecos-qa; do
    if [ -d "$HERMES_BASE/$p" ]; then
        rm -rf "$HERMES_BASE/$p"
        echo "🗑️  已删除: $p"
    else
        echo "⏭  跳过(不存在): $p"
    fi
done

# 清理wrapper (在gorunkol的bin下)
WRAPPER_DIR=/home/guorongxiao/.hermes/profiles/gorunkol/home/.local/bin
for p in ecos-pmo ecos-pm ecos-arch ecos-fe ecos-be ecos-qa; do
    if [ -f "$WRAPPER_DIR/$p" ]; then
        rm -f "$WRAPPER_DIR/$p"
        echo "🗑️  已删除wrapper: $p"
    fi
done

echo ""
echo "✅ WSL侧清理完成。"
echo "   Windows侧路径: C:\\Users\\guoro\\.hermes\\profiles\\"
echo "   看板路径:      D:\\workspace\\ECOS\\00-Kanban\\"
