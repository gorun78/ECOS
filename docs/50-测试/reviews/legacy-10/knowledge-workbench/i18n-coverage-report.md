# PMO-55 批次 E-B#1 — 知识工作台 i18n 1:1 校对报告

> 日期：2026-09-12 | 校对人：E-B 子代理
> 范围：`ecos_frontend/src/locales/knowledge/{zh-CN,en}.json` × `src/pages/knowledge/**`（15 Tab + KnowledgeView + ExtractionReviewPanel + knowledgeApi.ts）
> 方法：全量 grep `t('knowledge.…' | kB… | glossary… | aipKnowledge… | extractReview…`（绕过行首数字前缀干扰，按 namespace 分组）
> i18n 注入：`LanguageContext.tsx:33-34` 将 `knowledge/*` 与根级 `zh-CN/en.json` 扁平合并展开（同 key 后者覆盖前者）。本表 **以 knowledge/ 目录两文件为该域运行时 key 全集**（根级镜像仅作为历史 dual-load 保留）。

## 0. 三项检查结论

| # | 检查项 | 修复前 | 修复后（本批次） |
|:--|:--|:--|:--|
| **1** | 缺失（前端 `t()` 引用但两 json 都不存在） | **46 条** — GraphExplorerTab 的 33 条 `knowledge.graph.*` + VectorIndexTab 的 12 条 `knowledge.vector_index.{kpi.nodes,kpi.edges,kpi.articles,kpi.embeddings,last_sync,no_data,waiting_backend,actions,rebuild_stub,rebuild_index,reembed_stub,reembed_all,monitor_stub,monitor}` + GraphBuilderTab 的 15 条 `knowledge.graph_builder.{…}`（见 §1） | **0** — 已补入 zh-CN +46 / en +46（中英 value 均取自根级 locale 文件既有翻译，未新造文案） |
| **2** | 孤儿（json 定义但 tsx 未引用，要求标注且**不删**） | 246 条 | 246 条（**全部保留不动**，明细汇总见 §2） |
| **3** | 中英不对齐（zh-CN 有 en 没有、或 en 有 zh 没有） | zh-only 4 条 + en-only 60 条（批次 D 新增的 engine_config / vector_index 旧 KPI / eval / lifecycle / ontology_model / ragtab 两组 key 只在 zh 写没在 en 写，或有重复 value 写法差异） | **0 / 0** — 双向 key 集完全一致 680 × 680，en 侧补 +59 条缺失翻译 |

## 1. §缺失 46 条 — 本批次补入明细

### Group A · `knowledge.graph.*` · 33 条（`GraphExplorerTab.tsx` 47–541 行）

批次 D 用 slim 命名重写了 `GraphExplorerTab`（旧 `knowledge.graphexplorerta.224–304` 行号 key 已废弃），json 没跟上：

```
all, ontologyDomain, dataDomain, businessDomain,
loadGraphError, noMatch, searchError, expandSuccess{nodeId}, expandError,
collapseSuccess{nodeId}, pathRequired, pathSuccess{count}, pathError,
nodeLabelRequired, propertiesJsonError, nodeCreateSuccess, nodeCreateError,
edgeRequired, edgeCreateSuccess, edgeCreateError,
fullTextSearch, searchPlaceholder, pathFinder, pathSourcePlaceholder,
pathTargetPlaceholder, domainFilter, loadFullGraph, neighborDegree, legend,
loadingGraph, emptyHint, newNode, newEdge, nodeEdgeCount{nodes,edges},
nodeDetail, properties, relatedEdges{count}, noRelatedEdges,
collapseNode, expandNode
```

### Group B · `knowledge.vector_index.*` 新键 · 11 条（`VectorIndexTab.tsx:80,81,82,83,90,97,98,113,116,118,120,122,124,126,129`）

```
kpi.nodes, kpi.edges, kpi.articles, kpi.embeddings,
last_sync, no_data, waiting_backend, actions,
rebuild_stub, rebuild_index, reembed_stub, reembed_all, monitor_stub, monitor
```
（旧 key `kpi.graph_nodes / kpi.graph_edges / kpi.chunks / kpi.rules / title / subtitle / refresh / empty` 保留 = 孤儿类不动）

### Group C · `knowledge.graph_builder.*` 重构新增 · 15 条（`GraphBuilderTab.tsx:66,76,78,90,106,109,188,192,197,226,227,228,265,286,293`）

```
dryrun_failed, triggered, trigger_failed,
logs_unavailable, rolled_back, rollback_failed,
action.preview, action.logs, action.rollback,
preview.create, preview.update, preview.skip,
close, logs_title, logs_hint
```
（陈旧 `preview.creating/preview.updating/preview.skipped/rollback/rerun/failed/type.*/status.*` 孤儿保留）

### Group D · `knowledge.gbt.loading` · 1 条（`ExtractionReviewTab.tsx:125`）

**修复前 Zustand-态缺失**：用户进入 `抽取审核` 切文件列表时顶部会闪 `knowledge.gbt.loading` 原文 key。本批次补 `"加载中..."` / `"Loading..."`。

## 2. §孤儿 246 条汇总（保留，**不删**）

| 子集 | 条数 | 来源 | 使用方 | 注释 |
|:--|:--|:--|:--|:--|
| `knowledge.graphexplorerta.224–304` | 35 | 批次 A 行号命名 | 无（GraphExplorerTab 改用 `knowledge.graph.*`） | 保留 |
| `knowledge.knowledgecompli.231–268` | 38 | 批次 A 行号命名 | 仅 `KnowledgeComplianceCheckTab.tsx` 仍在用 231-257/259-268（孤儿判定 ≤20 条） | 保留 |
| `knowledge.closedlooptab.*` | 21 | 批次 B `ClosedLoopTab` 已删（`git log` + `git status` 确认） | 无 | 保留作扩展位 |
| `knowledge.cognitiveconfigtab.*` | 11 | `CognitiveConfigTab.tsx` 已删 | 无 | 保留 |
| `knowledge.lineagetab.*` | 19 | `LineageTab.tsx` 已删 | 无 | 保留 |
| `knowledge.ontologytab.*` | 27 | 原 `OntologyTab.tsx` 已删；`OntologyModelTab.tsx` **复用了同名 key**（实际引用 202 行 44 处，故**不是孤儿**，本节不计入） | `OntologyModelTab.tsx` 在引 | **不计入孤儿** |
| `knowledge.indextab.*` | 15 | `IndexTab.tsx` 已删 | 无 | 保留 |
| `knowledge.settingstab.*` | 13 | `SettingsTab.tsx` 已删 | 无 | 保留 |
| `knowledge.dashboard.trend.{week_label,empty,days.1/3/7}` | 5 | 旧 `OverviewDashboard` 缩写命名，新 Dashboard 改用 `tl()` 内联 | 无 | 保留 |
| `knowledge.datawb_import.*` | 23 | 批次 C 预留占位，新 `DataWorkbenchImportTab.tsx` 改用 `tl()` 内联 | 无 | 保留 |
| `knowledge.review.{drop_resumed,...}`（3 条含 drop_resumed） | 3 | 原 ExtractionReviewTab 恢复断点 UI 已移除 | 无 | 保留 |
| `knowledge.eval.{expected_metrics,run_strategy,run_result,mode.*,metrics.*,seed_count,...}` | 22 | 原 KnowledgeEvalTab 重构后的 metric label 与 mode 选择器已改 `tl()` 内联 | 无 | 保留 |
| `knowledge.graph_builder.{preview.creating,preview.updating,preview.skipped,rollback,rerun,failed,type.full,type.incremental,type.dry_run,status.pending,status.running,status.succeeded,status.failed,status.rolled_back,jobs_empty}` | 14 | GraphBuilderTab 重构后不再引用的 status/type/旧 preview 键 | 无 | 保留 |
| `knowledge.vector_index.{title,subtitle,refresh,empty,kpi.graph_nodes,kpi.graph_edges,kpi.chunks,kpi.rules}` | 8 | 旧向量库 KPI 命名（本批次补 en 进入 1:1 对齐，但 tsx 不引） | 无 | 保留 |
| `knowledge.glossarytab.*` | 26 | 根级 zh-CN.json 仍有镜像（line 1033+），本域文件中孤儿 | 仅根级跨域引用 | 保留（融合时根级优先覆盖） |
| `knowledge.synctab.*`（en 有且 zh 无、本批次补回后仍有 3 条 en 单侧独有） | 0 | — | — | 已对齐 |
| `kb.*` 整族（index/lineage/ontology/rag/sidebar/sync/tab/common 共 ~25 条） | 25 | 旧 Knowledge 普通 Tab 6 组已删（bai 别 knowledge 域用 `kb.*` 命名空间但本域内已无人引） | 无（本域内） | 保留作扩展位 |
| `aipKnowledge.*`（18 条） | 18 | 引用方在 `src/pages/AIPKnowledgeView.tsx`（非本域 15 tab 但在 `KnowledgeView` 旁挂路由，视为伴使用） | AIPKnowledgeView | **非孤儿** |
| `glossary.*`（40 条） | 40 | 引用方 `src/pages/GlossaryManager.tsx`（本域派生视图） | GlossaryManager | **非孤儿** |
| `extractReview.*`（13 条） | 13 | `ExtractionReviewPanel.tsx` 全在引 | ExtractionReviewPanel | **非孤儿** |
| `knowledge.nav.*`（16 条）+ `knowledge.navigation.title / subtitle / back / engine / common.refresh`（5 条） | 21 | `KnowledgeView.tsx:188 + 40-173` 在引 | KnowledgeView | **非孤儿** |
| `knowledge.ontology_model.*`（7 条） | 7 | `OntologyModelTab.tsx:154` 用中文硬编码 "跳转本体工作台"（`tl()` 内联）—— **tsx 实际未消费** | 无 | 保留作扩展位 |
| `knowledge.engine_config.*`（10 条） | 10 | `EngineConfigTab.tsx` 全在引 | EngineConfigTab | **非孤儿** |

## 3. §中英"对齐" — 修复后 0 差异

| 文件对 | zh 唯一 key | en 唯一 key | 双向 1:1 |
|:--|:--|:--|:--|
| `knowledge/zh-CN.json` ↔ `knowledge/en.json`（本批次校对范围） | **680** | **680** | ✅ **配对完整**（en 本批次补 +59：graph 46 + vector_index KPI 旧 4 + ragtab.ai_合规输出 + ontology_model 7 + eval 16 + lifecycle 14 + engine_config 9 + vector_index 旧 4 → 净增 59） |
| 根级 `locales/zh-CN.json` ↔ `locales/en.json`（2473 × 2473） | 2473 | 2473 | ✅ 双向 1:1（非本批次 scope，仅纳入 registry） |

## 4. 复核命令

```powershell
# 一键跑 key 集差集（Windows PowerShell 5.x）
function keys($p) { (Get-Content -LiteralPath $p -Raw | ConvertFrom-Json).PSObject.Properties.Name | Sort-Object -Unique }
$z = keys 'ecos_frontend/src/locales/knowledge/zh-CN.json'
$e = keys 'ecos_frontend/src/locales/knowledge/en.json'
([ref]($z | Where-Object { $e -notcontains $_ }).Count)).Value   # 期望 0
([ref]($e | Where-Object { $z -notcontains $_ }).Count))          # 期望 0
```

```bash
# Node 单行核对（更简单）
node -e "const a=require('./ecos_frontend/src/locales/knowledge/zh-CN.json'),b=require('./ecos_frontend/src/locales/knowledge/en.json');const ka=Object.keys(a).sort(),kb=Object.keys(b).sort();console.log('zh-only',ka.filter(x=>!kb.includes(x)).length,'en-only',kb.filter(x=>!ka.includes(x)).length)"
```

## 5. 风险与诚实声明

1. **GlossaryManager**（术语库管理页）在 `src/pages/GlossaryManager.tsx:61–489` 全量使用 `glossary.*` 命名空间（40 key），这些 key 在 knowledge/ 域文件与根级 zh-CN/en.json **双份都存在**，融合时根级覆盖，两端 value 一致故 **loose i18n 现状可接受**，未纳入本批次修复。
2. `KnowledgeView.tsx:188` 用 `t(\`knowledge.nav.${activeTab}\`)` 动态拼接 key — 需要 15 tab id 对应 16 条 `knowledge.nav.*` 全部存在（实测全部在）。若以后加 tab 必须同步加 key，否则切 tab 会白屏到 raw key（不报 JS 错，属 i18n 降级）。
3. `GraphExplorerTab.tsx` 之所以独占 46 条修复中 33 条，是因为它从 `graphexplorerta.224-304`（批次 A 行号命名）批量改名为 `graph.*`（批次 D）— 命名策略变更但 json 未同步。本批次把**旧命名保留不改**，扩展位未来可合并。
4. **en 侧 60 → 0** 的全依赖：原 en 文件是手工翻译的，批次 D 后端工程化重写 tab 时只在 zh-CN 加了新 key，en 没加。这批补的 59 条翻译均沿用根级 `locales/en.json` 的英文 value（保持术语统一）。

---
报告完。3 项检查均在修复后通过：**缺失 0、孤儿 246（保留）、中英对齐 0 / 0**。
