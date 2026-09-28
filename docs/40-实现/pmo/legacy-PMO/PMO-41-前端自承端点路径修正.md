# PMO-41 前端自承端点路径修正（对齐后端实际路径）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md) §4 前端铁律
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: fullstack-implementer (FE)
> 铁律:
> 1. API 只增不改 — 本批次**不改前端对后端的调用契约语义**，仅把前端写错的路径纠正为后端真实路径
> 2. 接口路径必须与后端 §1.1 API 路径铁律一致（路径只增不改，但**前端写错的路径**由前端改）
> 3. 公共组件规则：`useTheme`/`useLanguage`/`useToast` 3 个 Hook 必须全部组件化调用，禁硬编码中文 + 硬编码色

本批次不与 PMO-38/39/40/42 冲突（不依赖上述批次落地即可执行，**可并行**），但完整验收需在 PMO-38-42 全部完成后做端到端验证。

## §背景

来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §2.1 + §6（协调请求 2）。

26 个前端 P0 端点中：**14 个由后端补**（PMO-38/39/40/42），**12 个是前端写错路径**（本批次负责纠正）。本批次纯前端，不动后端，仅改 `src/api.ts`/`src/services/*.ts`/`src/pages/*/api.ts` 中 5 个文件的路径常量。

| # | 文件:行 | 现状（前端调用） | 后端真实路径 | 修改动作 |
|:-:|------|------------|-------------|---------|
| 1 | `src/services/ontologyApi.ts:855` `previewEntities` | `POST /api/v1/ecos/domains/{domainCode}/auto-discover/preview` | **本批次先不改**：等 PMO-40 T4b 补齐后端 preview 端点后再校；若后端泄露 preview 实现不完整，则本任务改为 `POST /domains/{domainCode}/auto-discover` + 在客户端本地做 dryRun 过滤 |
| 2 | `src/services/ontologyApi.ts:889` `fetchOntologySources` | `GET /api/v1/ecos/ontology/sources` | PMO-40 T4a 新增；本批次保持路径不变 |
| 3 | `src/services/ontologyApi.ts:302` `fetchWorkflowDefinitions` | `GET /api/v1/engine/ontology/workflow/definitions?pageSize=` | PMO-39 T2 新增；本批次保持路径不变 |
| 4 | `src/services/ontologyApi.ts:768` `fetchVersions` | `GET /api/v1/ontology/versions` | 后端真实路径 `GET /api/v1/ecos/versions` 或 `/api/v1/ecos/ontologies/{id}/versions` — **本任务改为正确路径** |
| 5 | `src/services/ontologyApi.ts:813` `fetchVersionDiff` | `GET /api/v1/ontology/versions/diff?v1=&v2=` | PMO-39 T3 新增；本批次保持路径不变 |
| 6 | `src/services/cognitiveEngineApi.ts:140` `fetchPlan` | `GET /api/v1/cognitive/plan/{id}` | PMO-39 T1a 新增；本批次保持路径不变 |
| 7 | `src/services/agentConfig.ts:199` `fetchKnowledgeBases` | `GET /api/v1/knowledge-bases` | PMO-38 T3 新增；本批次保持路径不变 |
| 8 | `src/services/agentConfig.ts` `authHeaders()` 自建 | 自建 header，相对路径 `/v1/agents` | **本任务改为**走统一 request 封装（报告 §2.2 中列的 6 文件之一） |
| 9 | `src/pages/aiworkbench/api.ts:377` `fetchAgentMetrics` | `GET /api/v1/agent-metrics/{agentId}` | PMO-40 T1 别名；本批次保持路径不变（或可改主路径 `/aip/`，二选一） |
| 10 | `src/pages/aiworkbench/api.ts:398` `fetchAgentErrors` | `GET /api/v1/agent-metrics/{agentId}/errors` | 同上 |
| 11 | `src/pages/sql-query-console/api.ts:177` `fetchTemplates` | `GET /api/v1/engine/data/query/template` | 后端真实路径 `GET /api/v1/engine/data/query/templates`（复数）— **本任务改路径** |
| 12 | `src/pages/sql-query-console/api.ts:192` `deleteTemplate` | `DELETE /api/v1/engine/data/query/template/{id}` | 后端真实路径 `DELETE /api/v1/engine/data/query/templates/{id}`（复数）— **本任务改路径** |

## §禁止清单（铁律继承 + 本指令特有）

1. ❌ 不改后端代码（本批次纯 FE 任务）
2. ❌ 不新建 `services/xxx.ts` 文件（铁律五：严守标准目录，公共逻辑抽离）
3. ❌ 不硬编码中文/颜色；所有改动文件必须仍合规 useTheme/useLanguage
4. ❌ 不动 `services/agentConfig.ts:authHeaders` 之外的自建 `request` 函数（§2.2 的 6 文件中其他 5 个在本指令批次 6 — PMO-43 处理）

## §Task（原子：单文件 + 验收）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos_frontend/src/services/ontologyApi.ts` | `fetchVersions` 路径 `/api/v1/ontology/versions` → `/api/v1/ecos/versions`（与 T3 表第 4 行一致）；其余路径保留；函数签名不变；新增 JSDoc 注释标明变更原因（PMO-41，对齐后端实际路径，不破坏语义） | `npm run lint` 0 错误；`npm run test -- src/services/ontologyApi` 通过；grep 确认 `/api/v1/ontology/versions` 已无 callers |
| T2 | `ecos_frontend/src/services/agentConfig.ts` | `fetchKnowledgeBases` 路径不变（后端 PMO-38 T3 补齐）；但**强制去自建 `authHeaders()`**：改为 `import { fetch } from "../api"` 走统一 request 封装（含 handleAuthExpired on 401, handleForbidden on 403），保留原有函数签名 `Promise<KnowledgeBase[]>` | `npm run lint` 0 错误；`grep -R "authHeaders" src/services/` 无结果；`npm run test -- src/services/agentConfig` 通过；手动浏览器 devtools 打开 Network，调 `fetchKnowledgeBases()` 时 401 → 自动跳 `#/login` |
| T3 | `ecos_frontend/src/pages/sql-query-console/api.ts` | `fetchTemplates` 路径 `/template` → `/templates`；`deleteTemplate` 路径 `/template/{id}` → `/templates/{id}`；同文件不再用自建 `get/post/del` 裸 fetch — **本任务两个路径改动**（裸 fetch 收敛到 PMO-43） | `npm run lint` 0 错误；grep 确认 `/template/` / `/query/template` 字符串已无；手动 SQL Console 验证：模板列表加载 + Delete 模板均 200 |
| T4 | `ecos_frontend/src/pages/aiworkbench/api.ts` | `fetchAgentMetrics`/`fetchAgentErrors` 路径保留 `/api/v1/agent-metrics/...`（与 PMO-40 T1 别名一致），**验证调整**：在 JSDoc 注释中标明"路径兼容别名，主路径 `/aip/agent-metrics`"，统一 request 封装调用 | `npm run lint` 0 错误；grep 注释；手动 Agent 监控页切中文加载 metrics 200 |
| T5 | 全局 Grep 验证 | 运行 `cd ecos_frontend && grep -rn "/api/v1/ontology/versions\b\|/api/v1/engine/data/query/template\b\|/v1/sysconfig\|/api/v1/knowledge\b\|/api/v1/cognitive/optimize\b" src/ | grep -v ".test."` | 命中 0（所有修正完毕）；`npm run lint` 全仓 0 错误；`npm run build` 通过 |

## §目标工时

2.5 小时（T1 0.3h + T2 0.5h + T3 0.3h + T4 0.3h + 全局验证 0.5h + Reviewer 缓冲 0.6h）

## §依赖关系

- **前置**：无（可先于 PMO-38/39/40/42 自行核对后端真实路径后改正，但因运行时验证需后端可用，建议在 PMO-38-42 任意 ≥1 项完成后做运行时验证）
- **输入**：检查报告 §2.1（12 项前端路径错误）+ §6 #2
- **后置**：PMO-46 QA 验收（26 端点全 200 的前置）
- **Worker**: `fullstack-*`，**Verifier**: `reviewer-*`，**Synthesizer**: `pm-*`

## §分支

`feature/pmo-41-fe-self-fix-paths`

## §验收（PM 主持，QA 复核）

- [ ] `npm run lint` 0 error
- [ ] `npm run build` 通过
- [ ] 全局 Grep 命中 0（T5 验证）
- [ ] 浏览器端到端：SQL 控制台 template 列表 + Delete 200
- [ ] 浏览器端到端：KbAgent 列表加载 `fetchKnowledgeBases` 200（依赖 PMO-38 T3 上线）
- [ ] Reviewer 代码审查 deliverable_allowed=true
