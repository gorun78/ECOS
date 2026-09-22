# PMO-42 后端 26 个 P0 端点 — 交叉核对核验（QA）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: QA（reviewer 协助执行）
> 铁律:
> 1. 所有 26 个端点的 curl 必须通过（200/201/202 为合格）
> 2. 任一端点 404/405 即门禁 `deliverable_allowed=false`，回退到对应批次的 worker 修复
> 3. 验证基准：报告 §7 验收自检清单第 1 项

## §背景

来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §2.1（26 端点）+ §6 #1/§7 第 1 项。

**目的**：在 PMO-38~43 全部完成后，跑 26 个端点的最终 curl 核验，作为 P0-1「补 26 个不存在端点」的全量关闭凭证。

跨批次覆盖（端点派生自 PMO-38/39/40/41 + §5 P0 8 项前端）：
- §2.1 表中 #1-#26 共 26 项
- 其中 #1-#3（sysman）、#12（kb）、#13/#14/#15/#16（cognitive）、#17-#21（ontology）、#22/#23（ai）、#24/#25（data-engine，前端改）、#26（stratrgy）

## §禁止清单

1. ❌ 不允许"curl 失败但人工调用绕过"
2. ❌ 不允许用 `*.bat` 或 `*.sh` 一行带过；必须输出 26 行结果清单（端点 × HTTP code × 响应大小）
3. ❌ 不修改任何 src 代码（仅验证）

## §Task（原子）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos-tests/p0-api-completeness-shakedown.mjs`（**新脚本**，落仓 ecs-tests/）| 写 26 行的 curl 矩阵：每行校验 `<file>:<line>` 行号对应的 API 的 URL 拼接 — 使用 Node `fetch` + json 响应；不依赖 gateway 之外的中间件；输出 Markdown 表：`(26 行) × 端点 × code × size`；断言：任一 404/405 → exit 1 | `node p0-api-completeness-shakedown.mjs` 退出码 0；26 行全部 2xx；输出表打印到 to + 落仓 `ecos-tests/.results/p0-api-26.md` |
| T2 | 网关三滤波器交叉 grep | 在 git working tree 内 grep 5 个新 Controller 类（`CognitivePlannerController` / `OntologyWorkflowController` / `VersionDiffController` / `PortalSearchController` / `AgentMetricsCompatController` / `MetadataStrategyController` / `OntologySourceController` / `AutoDiscoverPreviewController`），确认全部出现在：① `VersionPrefixRewriteFilter.java` V1_REWRITE_MAP ② `SecurityConfig.java` permitAll ③ `ClearanceInterceptor.java` 豁免 ④ `GatewayApplication.java` excludeFilters | 4 处全部命中（`grep -l` 各文件 ≥ 1 行 hits） |
| T3 | AGENTS.md 端点清单核验（§2.3 强制项）| `grep -A 5 "##.*端点清单" ecos_backend/engine/*/AGENTS.md` 输出后人工审核：CognitivePlanner/OntologyWorkflow/VersionDiff/PortalSearch/AgentMetricsCompat/MetadataStrategy/OntologySource/AutoDiscoverPreview 等 8 个新端点全部在对应引擎 AGENTS.md 端点清单中 | 8 个端点名字全部出现在引擎 AGENTS.md |
| T4 | hol 报告 | 综合 T1+T2+T3 输出 `ecos-tests/.results/p0-probe-42.md`（含 26 行 curl 结果 + 三滤波器 grep 命中表 + AGENTS.md 命中表） | 文档存在；任一失败 → 整体 failed；全部过 → C³ 通过 |

## §目标工时

1 小时（T1 脚本 30min + T2/T3 grep 15min + T4 报告 15min）

## §依赖关系

- **前置**：PMO-38 + PMO-39 + PMO-40 + PMO-41 + PMO-43 全部 done（**硬依赖**）
- **输入**：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §2.1 + §7
- **后置**：PMO-46 最终验收（门禁前）
- **Worker**: `qa-*`（或 `fullstack-*` 执行，`reviewer-*` 复核），**Verifier**: `reviewer-*`

## §门禁

**deliverable_allowed=true** 当且仅当：
- 26 端点 curl 全 2xx
- 三滤波器交叉 grep 4 处全命中
- AGENTS.md 8 个端点全收录

否则 `deliverable_allowed=false`，回退 PMO-38/39/40 中失败端点所在批次重发。

## §分支

`fix/pmo-42-p0-api-shakedown`
