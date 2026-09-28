#!/bin/bash
# docs-migration.sh — docs 目录一次性批量迁移脚本（v1.1, 基于 2026-09-22 真实清单）
# 来源: Arch-Reviewer | 日期: 2026-09-22
# 上游规范: docs/docs-writing-agent.md §0.7 ~ §0.9
#
# 用法:
#   bash _win_tasks/docs-migration.sh --dry-run   # 预览, 不实际 mv / commit
#   bash _win_tasks/docs-migration.sh             # 实际执行
#
# 前提（执行前必须全部满足）:
#   [ ] git status 工作树干净（或 commit 范围限定 docs/，见 §5）
#   [ ] 当前分支是 feature/docs-migration（非 release / 非 dev / 非 main）
#   [ ] PMO 指令 #PMO-{X}-docs-目录迁移 已批准
#   [ ] 已先跑 --dry-run 检查（输出与 §0.7 映射表一致）
#
# 校验（执行后必须全部满足）:
#   [ ] git log --oneline -1 出现 refactor(docs): 目录一次迁移
#   [ ] git status -- docs/ 应为 clean
#   [ ] grep -rn "docs/1-sysman\|docs/7-integration" docs/ 命中 = 0
#   [ ] 旧 14 个目录均已消失（各级下只有 *-legacy/ 子目录）
#   [ ] 新目录数 = 14（§0.5 目标态）

set -euo pipefail

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
  echo "  ============================================================"
  echo "  DRY-RUN 模式（只打印, 不实际 mv / commit）"
  echo "  ============================================================"
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"
cd docs   # 后续所有 git mv 都相对 docs/

# ---------- 工具 ----------
mv_keep() {
  local src="$1"
  local dst="$2"
  if [ ! -e "$src" ]; then
    echo "  [skip] 源不存在: $src"
    return 0
  fi
  if [ -e "$dst" ]; then
    echo "  [WARN] 目标 $dst 已存在 (将 git mv 到 $(basename "$dst").batch-2/)"
    dst="${dst%}/"
    [ -d "${dst%/}" ] && dst="${dst%/}-batch-2"
  fi
  if [ "$DRY_RUN" = true ]; then
    echo "  [dry] git mv $src $dst"
  else
    git mv "$src" "$dst"
    echo "  ✓ git mv $src $dst"
  fi
}
run_commit() {
  local msg="$1"
  if [ "$DRY_RUN" = true ]; then
    echo "  [dry] git add docs/; git commit -m \"$msg\""
    return 0
  fi
  # 限定 commit 范围只 docs/，避免夹带未提交代码改动
  cd "$REPO_ROOT"
  git add docs/
  git commit -m "$msg"
  git log --oneline -1
  echo "  ✓ 已提交 (仅 docs/ 范围)"
}

# ============ 一、创建目标态目录 ============
echo "==> 创建目标态目录（dry-run 跳过 mkdir）"
if [ "$DRY_RUN" = false ]; then
  mkdir -p \
    engine-security/1-sysman-legacy \
    engine-data/3-data-legacy \
    engine-ontology/4-onto-legacy \
    engine-ontology/04-benti-legacy \
    engine-cognitive/5-cognitive-legacy \
    engine-ai/2-aispace-legacy \
    10-gateway \
    20-services/sysman \
    20-services/datanet \
    20-services/buszhi \
    20-services/dccheng \
    20-services/aiming \
    21-runtime/legacy-plans \
    22-integration/7-integration-legacy \
    22-integration/07-jicheng-legacy \
    23-quality/legacy-08-quality \
    23-quality/9-checks-legacy \
    23-quality/08-product-legacy \
    30-cross-cutting-docs/reviews \
    30-cross-cutting-docs/pmo \
    30-cross-cutting-docs/refs \
    30-cross-cutting-docs/ops \
    30-cross-cutting-docs/99-archive
  echo "  ✓ 21 个目标态目录已创建"
fi

# ============ 二、按 §0.7 映射表批量迁移（git mv 保留历史） ============
echo "==> 迁移 6 引擎目录 (1-sysman → engine-security, 2-aispace → engine-ai 等)"
mv_keep 1-sysman      engine-security/1-sysman-legacy/
mv_keep 3-data        engine-data/3-data-legacy/
mv_keep 4-onto        engine-ontology/4-onto-legacy/
mv_keep 04-本体       engine-ontology/04-benti-legacy/
mv_keep 5-cognitive   engine-cognitive/5-cognitive-legacy/
mv_keep 2-aispace     engine-ai/2-aispace-legacy/

echo "==> 迁移横切类旧目录"
mv_keep 6-techdebt      30-cross-cutting-docs/pmo/6-techdebt-legacy/
mv_keep 09-PMO指令     30-cross-cutting-docs/pmo/legacy-09/
mv_keep PMO             30-cross-cutting-docs/pmo/legacy-PMO/
mv_keep 10-审查证据    30-cross-cutting-docs/reviews/legacy-10/
mv_keep reviews         30-cross-cutting-docs/reviews/legacy-reviews/
mv_keep 03开发阶段     30-cross-cutting-docs/reviews/legacy-03/
mv_keep 07项目管理     30-cross-cutting-docs/reviews/legacy-07/
mv_keep swarm           30-cross-cutting-docs/reviews/legacy-swarm/
mv_keep 11-运维        30-cross-cutting-docs/ops/legacy-11/
mv_keep .migration     30-cross-cutting-docs/ops/legacy-migration/
mv_keep .trae          30-cross-cutting-docs/reviews/legacy-system-docs/
mv_keep 08-产品化重构方案 23-quality/08-product-legacy/
mv_keep 08-产品质量     23-quality/legacy-08-quality/
mv_keep 9-checks       23-quality/9-checks-legacy/
mv_keep 7-integration  22-integration/7-integration-legacy/
mv_keep 07-集成联调     22-integration/07-jicheng-legacy/
mv_keep plans          21-runtime/legacy-plans/

# ============ 三、根目录散落文件迁移 ============
echo "==> 迁移根目录散落文件"
mv_keep "24-交付质量报告-2026-09-03.md"         23-quality/
mv_keep "ECOS-产品化完善方案.md"                  23-quality/
mv_keep "ECOS-技术债修复计划.md"                  30-cross-cutting-docs/pmo/
mv_keep "palantir_aip_architecture_diagram.html" 30-cross-cutting-docs/refs/
mv_keep "周报素材-数据科学本体论知识图谱进展-20260803.md" 30-cross-cutting-docs/refs/
mv_keep "ARCHITECTURE-RULES.md"                  00-架构/
# docs-writing-agent.md 保留根（本规范的来源文档）

# ============ 三补、删除 screenshots/ (R5 禁 png 入 docs, 用户已批) ============
echo "==> 删除 screenshots/ (R5 不合规, 用户批准 2026-09-22)"
if [ -d "screenshots" ]; then
  if [ "$DRY_RUN" = true ]; then
    echo "  [dry] git rm -r screenshots/  (含 2 png)"
  else
    git rm -r screenshots/
    echo "  ✓ git rm -r screenshots/"
  fi
fi

# ============ 四、为每个目标态一级目录建 README.md ============
echo "==> 创建目标态一级目录 README (dry-run 跳过)"
if [ "$DRY_RUN" = false ]; then
  cat > engine-security/README.md << 'EOF'
# engine-security (护·横切)

> 端口: :18081 | 模块: ecos_backend/engine/security-engine
> 责任: 认证 / 授权 / 审计 / 脱敏 / ABAC（OPA）
EOF
  cat > engine-data/README.md << 'EOF'
# engine-data (土·D)

> 端口: :18082 | 模块: ecos_backend/engine/data-engine
> 责任: 数据源 / 管道 / 血缘 / DQ / 查询
EOF
  cat > engine-ontology/README.md << 'EOF'
# engine-ontology (金·I)

> 端口: :18083 | 模块: ecos_backend/engine/ontology-engine
> 责任: 本体建模 / 对象 / 关系 / 版本
EOF
  cat > engine-cognitive/README.md << 'EOF'
# engine-cognitive (木·C)

> 端口: :18086 (与 kb 共享, dccheng 双引擎) | 模块: ecos_backend/engine/cognitive-engine
> 责任: 因果推理 / 情景推演 / 混合推理（不新增 DB 表）
EOF
  cat > engine-ai/README.md << 'EOF'
# engine-ai (火·W)

> 端口: :18084 | 模块: ecos_backend/engine/ai-engine + services/agent-service
> 责任: Agent / Loop / Memory / LLM 调用
> 历史: 2-aispace-legacy/ 自 2-aispace git mv 入
EOF
  cat > workspace/README.md << 'EOF'
# workspace (顶层场景应用层)

> 端口: :18090 | 模块: ecos_backend/workspace
> 责任: 对象运行时 / ObjectQL / Scenario / Workbook / 跨服务场景编排
EOF
  cat > 10-gateway/README.md << 'EOF'
# 顶层网关

> 端口: :8080 | 模块: ecos_backend/gateway
> 责任: 组织 / 认证 / 限流 facade（fat-JAR 双跑期兼容）
EOF
  cat > 00-架构/README.md << 'EOF'
# 00-架构

> 系统级设计（跨所有 6 引擎 + 5 服务层）
> 核心: ARCHITECTURE-RULES.md (架构宪法 v1.1) + adr/ (独立 ADR)
EOF
  echo "  ✓ 8 个 README.md 已创建"
fi

# ============ 五、提交（commit hash 是 DONE 凭证, 仅 docs/ 范围） ============
echo "==> 提交迁移 (commit message 含 docs-migration 关键词)"
run_commit "refactor(docs): 目录一次迁移 - 6 引擎 + 5 service 目标态

- 旧 14+ 目录按 docs-writing-agent.md §0.7 映射表 git mv 到 *-legacy/
- 新建 engine-{security,data,ontology,kb,cognitive,ai} + 20-services/{5} + 21-runtime + 22-integration + 23-quality + 30-cross-cutting-docs/{5}
- 根目录散落文件归位 (含 ARCHITECTURE-RULES.md → 00-架构/)
- 每个目标态一级目录建 README.md (端口 + 模块锚定)

Refs: docs/docs-writing-agent.md#0_9"

# ============ 六、自动校验（基于当前 git 树状态） ============
if [ "$DRY_RUN" = true ]; then
  echo "  [dry] 跳过校验 (未执行 mv)"
else
  echo "==> 自动校验:"
  cd "$REPO_ROOT"
  echo "  [1] git log --oneline -1"
  git log --oneline -1 | sed 's/^/      /'
  echo "  [2] git status -- docs/ (应仅剩 untracked 或 empty)"
  git status -- docs/ --porcelain | head -5 | sed 's/^/      /'

  echo "  [3] 旧路径引用残留 (应为 0, 但 plans/current-plan.md 等内部引用除外):"
  LEFTOVER=$(grep -rn "docs/1-sysman\|docs/2-aispace\|docs/7-integration\|docs/10-审查证据\|docs/9-checks\|docs/08-产品化\|docs/09-PMO\|docs/11-运维\|docs/07-集成\|docs/07项目管理" docs/ --include="*.md" 2>/dev/null | grep -v "legacy-\|migration-README\|docs-writing-agent" | wc -l)
  echo "      旧路径残留: $LEFTOVER 处 (排除 legacy 过渡注释)"

  echo "  [4] 新目录数 (应 = 14 一级):"
  NEW_COUNT=$(cd docs && ls -d 2>/dev/null | grep -vc "^\.\|legacy" | head -1)
  ls -d engine-* 21-* 22-* 23-* 30-* 10-* 00-* workspace 2>/dev/null | wc -l | sed 's/^/      /'

  echo "  [5] 请手动执行 (脚本不强依赖):"
  echo "      mvn -f ecos_backend/pom.xml validate -q"
  echo "      cd ecos_frontend && npm run lint"
fi

echo "==> 迁移流程结束"
