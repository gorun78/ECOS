#!/usr/bin/env bash
# Wave 1 派发 6 条 PMO swarm（v2，修正残留）
set -e
HERMES=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
BOARD="ecos"
LOG=/home/guorongxiao/ECOS/.working/wave1-dispatch.log

exec 2>&1
exec > "$LOG"

echo "=== hermes kanban boards ==="
$HERMES kanban boards || true

dispatch() {
  local worker="$1"
  local goal="$2"
  local out="$3"
  echo ""
  echo "=== Dispatching $worker ==="
  $HERMES kanban --board "$BOARD" swarm \
    --worker "$worker" \
    --verifier "ecos-fe" \
    --synthesizer "ecos-pmo" \
    --created-by "ecos-pmo" \
    --json \
    "$goal" | tee "$out"
}

GOAL_PMO38="执行 PMO-38 后端 P0 补齐批次 1。详见 /home/guorongxiao/ECOS/docs/PMO/PMO-38-后端P0端点补齐-批次1.md。架构铁律：/home/guorongxiao/ECOS/.trae/rules/架构铁律.md。Task T1-T5：T1 UserManagementController 加 force-logout/reset-password/batch 三端点；T2 新建 UserBatchSaveDTO；T3 kb-engine 新建 KnowledgeListController GET /api/v1/knowledge-bases；T4 SysConfigController 加 PUT /api/v1/sysconfig/{key}/reset；T5 三滤波器同步（Rewrite/Security/Clearance 双路径）。完成后 mvn install -DskipTests 0 ERROR、Gateway 启动、curl 5 端点全 200。完成后 git add 但 NOT commit。产出归集回 /home/guorongxiao/ECOS 主目录，自验 git diff。"
GOAL_PMO39="执行 PMO-39 后端 P0 补齐批次 2。详见 /home/guorongxiao/ECOS/docs/PMO/PMO-39-后端P0端点补齐-批次2.md。架构铁律同上。Task T1-T5：T1 cognitive CognitivePlannerController 新增 /plan /plan/{id} /optimize 三端点；T2 ontology OntologyWorkflowController 新增 GET /engine/ontology/workflow/definitions 含分页；T3 ontology VersionDiffController 新增 GET /ontology/versions/diff；T4 sysman 新建 PortalSearchController GET /api/v1/portal/search 含 RLS；T5 三滤波器 + GatewayApplication excludeFilters 同步。完成后 mvn install 0 ERROR、curl 5 端点全 200。完成后 git add 但 NOT commit。产出归集 /home/guorongxiao/ECOS，自验 git diff。"
GOAL_PMO40="执行 PMO-40 后端 P0 补齐批次 3。详见 /home/guorongxiao/ECOS/docs/PMO/PMO-40-后端P0端点补齐-批次3.md。架构铁律同上。Task T1-T5：T1 ai-engine AgentMetricsCompatController 加 /api/v1/agent-metrics/{id} 与 /errors 薄兼容；T2 data-engine MetadataStrategyController 加 PUT /api/v1/datanet/metadata/strategy/{id}；T3 写 docs/PMO/PMO-40-数据确认.md；T4 ontology OntologySourceController + AutoDiscoverPreviewController；T5 三滤波器同步。完成后 mvn install 0 ERROR、curl 3 端点全 200。完成后 git add 但 NOT commit。产出归集 /home/guorongxiao/ECOS，自验 git diff。"
GOAL_PMO41="执行 PMO-41 前端自承端点路径修正 12 项。详见 /home/guorongxiao/ECOS/docs/PMO/PMO-41-前端自承端点路径修正.md。架构铁律：/home/guorongxiao/ECOS/.trae/rules/前端开发规范.md。Task T1-T5：T1 ontologyApi.ts fetchVersions/fetchVersionDiff 改 /api/v1/ecos/versions...；T2 agentConfig.ts 去自建 authHeaders 改 apiFetchData；T3 sql-query-console/api.ts fetchTemplates/deleteTemplate 改 /templates 复数；T4 aiworkbench/api.ts fetchAgentMetrics/fetchAgentErrors 改 /api/v1/aip/agent-metrics；T5 全仓 grep闸 + npm run lint 0。完成后 npm run lint 0、产出归集 /home/guorongxiao/ECOS，自验 git diff。"
GOAL_PMO43="执行 PMO-43 前端 P0 边界-基础设施-网络。详见 /home/guorongxiao/ECOS/docs/PMO/PMO-43-前端P0边界-基础设施-网络.md。架构铁律：/home/guorongxiao/ECOS/.trae/rules/前端开发规范.md。Task T1-T5：T1 api.ts 5 处 .catch{warn,return empty} 反模式改 NetworkDownEvent/真实错误；T2 main.tsx 挂 ToastProvider，删 6 处 console.log toast 残缺；T3 aiworkbench/index.tsx 4 吞错 fetch 改 error/retry 态，AgentMesh 静默轮询改 retry UI；T4 EngineMonitor/MonitoringCenter/GuardrailsView 把 403 当 401 的语义拆开心跳回退；T5 公共服务件 NetworkErrorBanner（navigator.onLine 顶栏横幅）。完成后 npm run lint 0、产出归集 /home/guorongxiao/ECOS，自验 git diff。"
GOAL_PMO44="执行 PMO-44 前端 P0 规范 i18n/主题/图标/安全。详见 /home/guorongxiao/ECOS/docs/PMO/PMO-44-前端P0规范-i18n-主题-图标-安全.md。架构铁律：/home/guorongxiao/ECOS/.trae/rules/前端开发规范.md（主题 4.1/图标 4.2/i18n 4.3）。Task T1-T5：T1 Login.tsx 整页 20+ 硬编码色换 styles.* token + 7 处中文换 t() + 4 个自定义 SVG 换 lucide-react；T2 AIPKnowledgeView.tsx 整页（56 处中文）接 useLanguage/useTheme；T3 SystemConfigManager.tsx isZh 模式换 t()，TaskPanel.tsx alert×5 + window.confirm×1 换 useToast + ConfirmDialog，DataTable.tsx 165/189 硬编码中文换 t()；T4 ExtractionReviewPanel.tsx:171 dangerouslySetInnerHTML 加 DOMPurify.sanitize 白名单 + 全仓 dangerouslySetInnerHTML 扫描报告；T5 api.ts handleAuthExpired 403 分流提示「无权限」，RequireAuth.tsx 改 token + 拉 /api/v1/auth/me 校验，加 NoAccess 路由/页面。完成后 npm run lint 0、产出归集 /home/guorongxiao/ECOS，自验 git diff。"

dispatch "ecos-be:PMO-38批次1-sysman-kb"        "$GOAL_PMO38" /tmp/pmo38.json
dispatch "ecos-be:PMO-39批次2-cognitive"         "$GOAL_PMO39" /tmp/pmo39.json
dispatch "ecos-be:PMO-40批次3-ai-data"           "$GOAL_PMO40" /tmp/pmo40.json
dispatch "ecos-be:PMO-41前端路径修正"           "$GOAL_PMO41" /tmp/pmo41.json
dispatch "ecos-be:PMO-43边界基建网络"            "$GOAL_PMO43" /tmp/pmo43.json
dispatch "ecos-be:PMO-44i18n主题图标安全"        "$GOAL_PMO44" /tmp/pmo44.json

echo ""
echo "=== 6 swarm dispatched ==="
echo "Saved JSON: /tmp/pmo38.json, /tmp/pmo39.json, /tmp/pmo40.json, /tmp/pmo41.json, /tmp/pmo43.json, /tmp/pmo44.json"
