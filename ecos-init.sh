#!/usr/bin/env bash
# =============================================================================
# ECOS 多Agent研发组织 — 一键初始化脚本
# 使用：source ./ecos-init.sh  或  bash ./ecos-init.sh
# =============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="/mnt/d/workspace/ECOS"
FE_ROOT="/mnt/d/workspace/c2eos"
BE_ROOT="/mnt/d/JavaProjects/databridge-v2"

echo "╔════════════════════════════════════════════════╗"
echo "║   ECOS 多Agent研发组织 — 初始化                ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

# ─── 1. 目录结构 ─────────────────────────────────────
echo "📁 1/5 创建目录结构..."
mkdir -p "$PROJECT_ROOT"/{00-Kanban,01-产品设计,02-研发计划,03-系统设计,04-测试设计,05-项目文档,06-会议纪要,07-知识库}
echo "   ✅ 目录就绪：$PROJECT_ROOT"

# ─── 2. 看板文件 ─────────────────────────────────────
echo "📋 2/5 初始化看板文件..."
if [ ! -f "$PROJECT_ROOT/00-Kanban/project-board.md" ]; then
    echo "   ⚠ project-board.md 不存在，请手动创建 (模板见下方)"
fi
echo "   ✅ 看板路径：$PROJECT_ROOT/00-Kanban/"

# ─── 3. Hermes Profile ───────────────────────────────
echo "🤖 3/5 创建Hermes Profile..."

PROFILES=("ECOS-PMO" "ECOS-PM" "ECOS-ARCH" "ECOS-FE" "ECOS-BE" "ECOS-QA")
for p in "${PROFILES[@]}"; do
    if hermes profile show "$p" &>/dev/null 2>&1; then
        echo "   ⏭ $p 已存在，跳过"
    else
        echo "   ✨ 创建 $p ..."
        hermes profile create "$p" --no-skills 2>&1 | tail -1
    fi
done
echo "   ✅ 6个Profile就绪"

# ─── 4. 配置模型 ──────────────────────────────────────
echo "⚙️  4/5 配置模型（deepseek-v4-pro）..."
for p in "${PROFILES[@]}"; do
    PROFILE_HOME=$(hermes profile show "$p" 2>/dev/null | grep "Path:" | awk '{print $2}')
    if [ -n "$PROFILE_HOME" ]; then
        cat > "$PROFILE_HOME/config.yaml" << 'YAML'
model:
  default: deepseek-v4-pro
  provider: deepseek
  base_url: https://api.deepseek.com/v1
providers: {}
fallback_providers: []
credential_pool_strategies: {}
max_concurrent_sessions: null
agent:
  max_turns: 100
  gateway_timeout: 1800
  image_input_mode: auto
YAML
    fi
done
echo "   ✅ 模型配置完成 (deepseek-v4-pro)"

# ─── 5. 验证 ──────────────────────────────────────────
echo "🔍 5/5 验证..."
echo ""
echo "   Profiles:"
hermes profile list 2>/dev/null | grep -i ecos || true
echo ""
echo "   目录:"
ls -d "$PROJECT_ROOT"/*/ 2>/dev/null || true
echo ""
echo "╔════════════════════════════════════════════════╗"
echo "║   🎉 ECOS 多Agent研发组织初始化完成！           ║"
echo "╚════════════════════════════════════════════════╝"
echo ""
echo "   ┌─────────────────────────────────────────┐"
echo "   │  ECOS-PMO（总控）                         │"
echo "   │  ├── ECOS-PM（产品经理）                   │"
echo "   │  ├── ECOS-ARCH（架构师）                   │"
echo "   │  ├── ECOS-FE（前端工程师）                  │"
echo "   │  ├── ECOS-BE（后端工程师）                  │"
echo "   │  └── ECOS-QA（测试工程师）                  │"
echo "   └─────────────────────────────────────────┘"
echo ""
echo "   启动方式："
echo "     hermes -p ECOS-PMO chat    # 启动PMO总控"
echo "     hermes -p ECOS-PM chat     # 启动产品经理"
echo "     hermes -p ECOS-ARCH chat   # 启动架构师"
echo "     hermes -p ECOS-FE chat     # 启动前端"
echo "     hermes -p ECOS-BE chat     # 启动后端"
echo "     hermes -p ECOS-QA chat     # 启动测试"
echo ""
echo "   下一步："
echo "     ① 为每个Profile写入SOUL.md (系统提示词)"
echo "     ② 用ECOS-PMO启动第一个看板任务"
echo "     ③ 按协同链路：PM → ARCH → PMO审核 → BE/FE → QA → PM验收 → PMO关闭"
echo ""
