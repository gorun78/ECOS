# release/v2.1-alpha 版本合并审计报告

> 审计对象：`release/v2.1-alpha` @ `1c70fd4`
> 审计日期：2026-09-15
> 审计范围：四个并行任务的成果是否完整并入 2.1-alpha
> 结论摘要：**提交层面 100% 并入；内容层面存在 2 类真缺失（中文词条丢失 / 7 个 stash 未提交成果）**

---

## 一、被审计的四个任务与对应分支

| # | 任务 | 主要分支 | 关键提交 |
|:--|:--|:--|:--|
| ① | 修复数据工作台 | `main` | `18484ca` `647014a` `35ec190` `852ee3a` `3766c8d` |
| ② | 知识工作台 | `feature/knowledge-pmo-50` + `feat/ontology-workbench-wave-b` 血缘提交 | `5303e7c` `e6eaa7b` `e11d9c8` `60b4335` `1d118f7` `bd9a97e` `bfde3ae` |
| ③ | AI 工作台 | `feat/ontology-workbench-wave-a` | `575f96f` `bd07da4` `d05f373` `bfde3ae` |
| ④ | 场景工作台 + 认知引擎 | `feat/ontology-workbench-wave-b` + `feature/cognitive-mentalevidence-p0` | `5db9d74` `e5e6005` `1c6c294` `77dc4fb` + PMO-59 P0~P3 (`af15d92`…`535a02c`) |

---

## 二、审计方法（可复算）

1. **提交可达性**：`git merge-base --is-ancestor <task-commit> HEAD` —— 判定提交是否进入 2.1-alpha 祖先链。
2. **合并完整性**：`git diff HEAD^1 HEAD` / `git diff HEAD^2 HEAD` —— 判定合并是否引入合并后修补或丢改。
3. **冲突面扫描**：对每个 merge commit 计算 `merge-base(P1,P2)` 后双方都改动的文件集合（真冲突面），再逐文件做**行级存在性回查**（该侧新增行是否仍能在 HEAD 文本中命中）。
4. **stash 缺口量化**：对 `stash@{N}` 相对其基线做 `git diff`，取新增行，逐行判断是否出现在 `HEAD:<file>` 中，输出"HEAD 缺失行数"。
5. **文件删除识别**：`git diff --no-renames --diff-filter=D` —— 判定分支有而 HEAD 无的文件（区分"主动下线"与"合并丢失"）。

---

## 三、逐任务判定

| 任务 | 提交层面 | 内容层面 | 判定 |
|:--|:--|:--|:--|
| ① 修复数据工作台 | 全部祖先链 | 逐行回查全部命中（差异由后续合法重构造成） | ✅ 完整并入 |
| ② 知识工作台 | 全部祖先链 | **i18n 词条丢失 + stash 未并入** | ⚠️ 缺失严重 |
| ③ AI 工作台 | 全部祖先链 | stash 未并入（组件/pom 级） | ⚠️ 部分缺失 |
| ④ 场景工作台 + 认知引擎 | 全部祖先链 | 除 runtime/llm-gateway 遗留外完整 | ✅ 基本完整并入 |

**合并本身无问题**：`1c70fd4` 的树与第二父 `cb42bfb` 完全一致（无合并后修补）；相对第一父 `d53523e` 净增 8570 行 / 仅 13 行删除 → 该次合并为纯增量。

---

## 四、确认的缺失

### P0-1 知识工作台中文词条被合并丢弃（真丢失，用户可感知）

- 文件：`ecos_frontend/src/locales/knowledge/zh-CN.json`
- 合并 `dbc1934`（knowledge-pmo-50 → cognitive-p0）时取了 knowledge 侧版本（427 行），另一侧 **283 条中文词条被丢弃**；而 `en.json` 却按"并集"合并（853 行 / 708 唯一 key）→ 中英严重不对称。
- 词条统计：`4f9da3b` 版 706 key → HEAD 425 key → 丢失 283 key。
- **其中 176 条被当前代码引用**（中文界面显示原始 key）：

| 受影响页面 | 缺失 key 数 |
|:--|--:|
| `pages/knowledge/tabs/GraphExplorerTab.tsx` | 66 |
| `pages/knowledge/tabs/EngineConfigTab.tsx` | 35 |
| `pages/knowledge/tabs/GraphBuilderTab.tsx` | 31 |
| `pages/knowledge/tabs/VectorIndexTab.tsx` | 18 |
| `pages/knowledge/tabs/ExtractionReviewTab.tsx` | 17 |
| `pages/knowledge/tabs/OverviewDashboard.tsx` | 15 |
| `pages/KnowledgeView.tsx` | 5 |
| `SyncTab` / `RagTab` / `GlossaryTab` | 3 / 2 / 1 |

### P0-2 en.json 合并污染

- 文件：`ecos_frontend/src/locales/knowledge/en.json`
- 851 行 / 708 唯一 key / **143 个重复 key**，含 `knowledge.graphsynctab.304`（数字垃圾 key）与含中文的 key（`knowledge.closedlooptab.agent_sandbox如何完善`）→ 错误的并集式合并产物，需去重清洗。
- 另有 20 个被代码引用的 key 在 en.json 中缺失（`knowledge.graph.*`、`knowledge.synctab.*`）。

### P1 stash 未提交成果未并入（7 个 stash，5 个与四任务相关）

| stash | 时间 | 基线 | 归属任务 | 未并入内容（HEAD 缺失行数） |
|:--|:--|:--|:--|:--|
| `{5}` | 09-12 07:05 | `bfde3ae` | 知识 | KgSyncServiceImpl 184、GraphSyncTab 128、EcosKnowledgeGraphServiceImpl 94、GraphSyncController 29、knowledgeApi.ts 27、OntologyVersionService 19、ExtractionReviewPanel 16、dccheng application.yml 11、typesAndConstants 3、Wave31 测试 6、runtime/pom 1 |
| `{6}` | 09-12 06:26 | `2139fd4` | 知识 | 同 `{5}`（+ oag-e2e-smoke.ps1 4） |
| `{4}` | 09-12 08:25 | `d05f373` | 知识 + AI + 横切 | GraphSyncTab 211、KgSyncServiceImpl 210、OntologyTab 151、EcosKnowledgeGraphServiceImpl 119、KnowledgeExtractionTab 107、GraphSyncController 71、LLMGatewayImpl 61、knowledgeApi.ts 31、sysman/impl/pom 10、RAGSearchServiceTest 8、LLMGateway 7、ClearanceInterceptor / SecurityConfig 各 3、LLMGatewayProperties 1 |
| `{3}` | 09-12 09:45 | `40ba536` | AI | knowledgeApi.ts 90、OntologyVersionService 47、AgentStudioService 43、DashboardView 12、CopilotMessageList 6、kb-engine/pom 4、dccheng/pom 3、logic 节点与 copilot 组件各 1~2、buszhi/ontology/runtime/ai pom 各 1~2 |
| `{2}` | 09-12 13:27 | `55bb833` | 本体 / AI | LineageController 42、ontology-engine-impl/pom 5 |
| `{0}` | 09-14 02:57 | `cdd3415` | — | 仅 `_win_tasks/` 脚本与日志，无源码 |
| `{1}` | 09-14 02:56 | `4f9da3b` | — | 仅 QA/Review 证据（72 文件），无源码 |

**孤儿改动警告**：`GraphSyncTab.tsx` / `KnowledgeExtractionTab.tsx` / `OntologyTab.tsx` 已被 `1d118f7`（批次 D 15 Tab 重构）**主动下线**，stash 中针对这三个旧文件的改动必须**迁移**到新 Tab（`GraphBuilderTab` / `ExtractionReviewTab` / `OntologyModelTab`），不可直接 apply。

### P2 双份并行实现（需人工裁定）

- `QueryEmbeddingHelper.java`：两条分支各写一份，HEAD 保留 162 行版，另一侧 153 行版被丢弃（82 行差异）。

---

## 五、已排除的"假丢失"

| 现象 | 结论 |
|:--|:--|
| 数据工作台 `ConnectionsTab.tsx` / `AsyncTaskCenterView.tsx` 等与提交时刻不一致 | 由 `b690aed`（主题 token）、`6bfbb39`（i18n 重构）、`e017f60` 等后续合法重构造成 |
| `GatewayApplication.java` 缺失 2 行 | `365e99f` 注释掉了引用已删除类 `workspace.controller.EngineTaskController` 的 excludeFilters 项（PMO-59-P2a 已核对） |
| `common/`→`runtime/common-api`、`buszhi/`→`services/buszhi/impl` 差异 | P8-A/B/C 计划内迁移 |
| 悬空提交 `fb2e317` / `8a99250` / `45d5189` 等 | 正常 amend 残留 |
| 9/14 03:19–03:27 的"合并 → reset(4f9da3b) → 重新合并(dbc1934)" | 两次合并真冲突面合计 6 个文件，已逐文件核过，除 `knowledge/zh-CN.json` 外均无丢失 |

---

## 六、修复计划（两阶段，已获用户确认）

### 阶段 1：i18n 止血（P0）

| Task | 文件 | 操作 | 验收 |
|:--|:--|:--|:--|
| T1 | `ecos_frontend/src/locales/knowledge/zh-CN.json` | 从 `4f9da3b` 版回补 283 条缺失中文词条（保留 HEAD 现有 425 条，并集合并、去重） | 文件 key 数 ≥ 706；被代码引用的 205 个 key 全部可解析；JSON 合法 |
| T2 | `ecos_frontend/src/locales/knowledge/en.json` | 清理 143 个重复 key（保留首/末者按其正确语义）、剔除数字垃圾 key 与含中文 key；补齐代码引用但缺失的 20 个 key | 无重复 key；被引用的 205 个 key 全部可解析 |
| T3 | 前端编译门 | `npm run lint`（tsc --noEmit） | 通过 |

### 阶段 2：stash 全量回放（P1/P2）

按文件归属分三批串行（避免并行改同一文件）：

1. **批 A · 后端 kb/ontology**：`KgSyncServiceImpl`、`EcosKnowledgeGraphServiceImpl`、`GraphSyncController`、`KnowledgeRetrievalServiceImpl`、`OntologyVersionService`、`LineageController`、`RAGSearchServiceTest`、`Wave31OntologyConvergenceTest`
2. **批 B · 横切/依赖**：`LLMGateway` / `LLMGatewayImpl` / `LLMGatewayProperties`、`ClearanceInterceptor`、`SecurityConfig`、各 `pom.xml`、`services/dccheng/application.yml`
3. **批 C · 前端**：`knowledgeApi.ts`、`typesAndConstants.ts`、`ExtractionReviewPanel.tsx`、AI 工作台组件（`DashboardView` / copilot / logic 节点）、`AgentStudioService` 涉及的前端契约；**旧 Tab 改动迁移到新 Tab**

**回放技术约束（强制）**：
- 若 `git rev-parse <stash 基线>:<文件>` == `git rev-parse HEAD:<文件>` → 可直接 `git checkout <stash> -- <文件>`（无损应用）
- 否则必须做三方合并（base = stash 基线版、ours = HEAD 版、theirs = stash 版），**禁止整文件覆盖**
- `QueryEmbeddingHelper.java` 双版本差异提交人工裁定后再合并

**每批验收**：后端 `mvn install -DskipTests`；前端 `npm run lint`；按批次 clean commit（commit hash 即 DONE 凭证）。

---

## 七、风险与遗留

| # | 风险 | 处置 |
|:--|:--|:--|
| 1 | stash 内容为 09-12 的中间态，可能已与后续重构语义冲突 | 逐文件三方合并 + 编译门拦截，不整文件覆盖 |
| 2 | 旧 Tab（GraphSyncTab 等）改动无法直接回放 | 迁移到新 Tab，需人工确认功能等价性 |
| 3 | 2.1-alpha 分支为发布分支 | 回放在独立分支执行后合入，禁止直推主干 |
| 4 | 7 个 stash 清理 | 回放完成并 commit 后，经确认再 `git stash drop` |

---

## 八、阶段 1 执行记录（i18n 止血）— 已完成

- **提交凭证**：`b7bc618` — `fix(知识工作台): 2.1-alpha 合并丢失词条回补 + 词条文件去重清洗（key集收敛为代码实际引用）`
- 改动范围：仅 `ecos_frontend/src/locales/knowledge/{zh-CN.json,en.json}`（+424 / −1140），未夹带其他文件
- 用户裁定：**彻底清洗**（key 集收敛为代码实际引用）+ **直接在 release/v2.1-alpha 提交**

| 指标 | 修复前 | 修复后 |
|:--|:--|:--|
| `zh-CN.json` key 数 | 425（222 中文死键 + 203 ASCII） | **290**（全 ASCII） |
| `en.json` key 数 | 851 行 / 708 唯一（143 重复行） | **290** |
| 重复 key | zh 0 / en 143 行 | **0 / 0** |
| key 名含中文 | 各 222 | **0 / 0** |
| en 值含中文 | 53 处 | **0** |
| 两文件 key 集 | 中英不对称 | **完全一致** |
| 代码引用 key 可解析 | zh 缺 153 / en 缺 21 | **0 / 0**（275 字面量 + 15 个 `knowledge.nav.<tabId>` 动态 key） |

- 补写新增文案 20 个（`knowledge.graph.*` 17 个 + `knowledge.synctab.*` 3 个）
- 验证：JSON 合法、UTF-8 无 BOM、字母序、`npm run lint`（tsc --noEmit）通过
- **取证修正**：原判断"283 条中文词条丢失"更精确的表述是 **283 个 ASCII 业务 key 丢失**；两文件另有 222 个"key 名含中文"的死键在 `4f9da3b` 就已存在且代码零引用，非本次合并引入。

---

## 九、阶段 2 侦察增补（stash 回放）— 重要修正

### 9.1 原"全量回放"方案不成立

各 stash **不是可叠加的增量层，而是同一批工作的竞争快照**（同一文件在多个 stash 中存在互不相同的版本）：

| 文件 | `stash@{4}`（08:25） | `stash@{5}`（07:05） | HEAD |
|:--|--:|--:|--:|
| `KgSyncServiceImpl.java` | 274 行 | 243 行 | 77 行 |
| `EcosKnowledgeGraphServiceImpl.java` | 改 212 行 | 改 178 行 | — |
| `GraphSyncController.java` | 改 92 行 | 改 40 行 | 45 行 |

→ 正确做法是**按文件取最新快照做三方合并**（同文件多版本时以 `stash@{4}` 为准，`stash@{5}` 补其独有文件），而非逐 stash apply。

### 9.2 回放候选与合并方式（剔除路径迁移噪声后）

| stash | 回放候选 | 无损可直接 checkout | 需三方合并 |
|:--|--:|--:|--:|
| `{4}` | 12 | 5 | 7 |
| `{5}` | 11 | 5 | 6 |
| `{3}` | 23 | 3 | 20 |
| `{2}` | 14 | 0 | 14 |
| `{6}` | 13 | 5 | 8 |

- `stash@{2}/{3}/{4}` 的 diffstat 含 3318~3681 行**删除**，实为 P8-A/B/C 路径迁移（`common/`→`runtime/common-api`、`buszhi/`→`services/buszhi/impl`）的中间态噪声，非真实内容，回放时必须忽略这些删除。
- **`stash@{5}` / `stash@{6}` 修改了 `locales/knowledge/{zh-CN,en}.json`**，其版本是被污染的旧态 → **必须排除**，禁止覆盖阶段 1 已清洗的文件。
- **孤儿改动**：`GraphSyncTab.tsx` 已被 `1d118f7`（15 Tab 重构）下线，现存 `GraphBuilderTab.tsx` 仅在其注释中提及；stash 中针对旧 Tab 的改动需迁移，不可直接 apply。

### 9.3 新发现：知识工作台图谱构建链路存在"提交层面"的实现缺口（🔴 P0）

前端 `knowledgeApi.ts`（**已提交且在线的 `GraphBuilderTab` 正在调用**）与后端实际实现的契约比对：

| 前端调用 | HEAD 后端 | 结论 |
|:--|:--|:--|
| `POST /api/v1/knowledge/graph/build` | `KnowledgeIngestController` ✅ | 契约完备 |
| `GET /api/v1/knowledge/sync/jobs` | **无任何实现（连 stash 也没有）** | 🔴 从未实现 |
| `POST /api/v1/knowledge/graph/build/preview` | **无任何实现（连 stash 也没有）** | 🔴 从未实现 |
| `GET /api/v1/knowledge/sync/jobs/{id}/preview` | 仅存在于 stash | ⚠️ 可回放恢复 |
| `POST /api/v1/knowledge/sync/jobs/{id}/rollback` | 仅存在于 stash | ⚠️ 可回放恢复 |
| `GET /api/v1/knowledge/sync/jobs/{id}/logs` | 仅存在于 stash | ⚠️ 可回放恢复 |

支撑证据：
- `HEAD` 的 `GraphSyncController.java` 仅 45 行，只有 `/status` `/trigger` `/object/{objectType}` `/logs` 四个映射。
- `HEAD` 的 `KgSyncServiceImpl.java` 仅 77 行，**缺** `runKgMapper` / `getJobLogs` / 日志落库 / `emitAudit`；而 `KgMapperService.java` 的 javadoc 已 `{@link KgSyncServiceImpl#runKgMapper(...)}` → 悬空引用，证明该实现被写下但从未提交。
- `git log --all -S"sync/jobs" -- ecos_backend` **无任何提交命中** → 该端点在任何分支都从未实现。
- `stash@{4}` 版本新增 `runKgMapper` / `getJobLogs` / `insertLog` / `markLogSuccess` / `markLogFailed` / `coerceToUiStatus` / `formatTs` / `truncate` / `emitAudit` / `sanitize`，**这是被搁置的核心实现**。

**结论**：这是四任务成果未完整并入的**最实质性一处** —— 不是合并丢失，而是"实现停在 stash、前端契约先行提交"，导致在线的"图谱构建"Tab 有 5/6 个接口调用落空。

### 9.4 阶段 2 建议批次（修订）

| 批次 | 范围 | 文件 | 优先级 |
|:--|:--|:--|:--|
| **A** | kb 图谱构建后端（🔴 恢复前后端契约） | `GraphSyncController`、`KgSyncServiceImpl`、`EcosKnowledgeGraphServiceImpl`、`RAGSearchServiceTest`（无损取 `stash@{4}`）；`KnowledgeRetrievalServiceImpl`、`OntologyVersionService`、`Wave31OntologyConvergenceTest`、`services/dccheng/application.yml`、`runtime/pom.xml`（补 `stash@{5}` 独有） | P0 |
| **B** | 横切/依赖 | `LLMGateway` / `LLMGatewayImpl` / `LLMGatewayProperties`、`ClearanceInterceptor`、`SecurityConfig`、各 `pom.xml` | P1 |
| **C** | 前端 | `knowledgeApi.ts`、`typesAndConstants.ts`、`ExtractionReviewPanel.tsx`；旧 `GraphSyncTab` 改动迁移到 `GraphBuilderTab`；AI 工作台组件（`DashboardView` / copilot / logic 节点） | P1 |
| **D** | 缺口补开发 | 3 个从未实现的端点（`GET /sync/jobs`、`POST /graph/build/preview`）+ 确认 `/sync/jobs/{id}/*` 契约对齐 | P0（新开发，非合并修复） |

**强制约束**：词条文件排除；旧 Tab 改动需迁移；每批以编译门（后端 `mvn install -DskipTests` / 前端 `npm run lint`）+ 契约验证收口；分批 clean commit。

---

## 十、阶段 2 执行记录（批 A / 批 B 判定 / 批 D 契约）

### 10.1 量化工具有效性修正（🔴 方法学）

`git diff --numstat HEAD <stash>` 的方向极易误判（"HEAD 独有行" 不等于 "stash 新增丢失"）。正确口径：

> **对每个 stash 取其基线 `git rev-parse "stash@{N}^"`，抽取 `git diff <基线> <stash> -- <file>` 的 `+` 行，再逐行判断是否出现在 `HEAD:<file>`**；分母为该文件 stash 新增行总数。

按此口径复核，§9.2 的候选表中有相当一部分是**误报**：HEAD 侧其实是 **更新/更完整的实现**，"stash 独有行" 是 stash 侧的**旧变体**。

### 10.2 批 A（kb 图谱构建后端）— 已完成

| 文件 | stash@{4} 新增行未入 HEAD | 判定 |
|:--|:--|:--|
| `KgSyncServiceImpl.java` | **0 / 251** | ✅ 已 100% 回放 |
| `GraphSyncController.java` | **0 / 88** | ✅ 已 100% 回放 |
| `EcosKnowledgeGraphServiceImpl.java` | **0 / 145** | ✅ 已 100% 回放 |

- 提交凭证：`d7028a2`（无损 `git checkout stash@{4} -- <file>`）
- 回放后启动失败一次：`GraphSyncController` 直接注入实现类 `KgSyncServiceImpl`，与 `@Async` 触发 JDK 动态代理后的 Bean 类型不匹配 → `HikariDataSource ... has been closed`
- 修复：`KgSyncService` 接口化（新增 `runKgMapper` / `getJobLogs` / `listJobs`），控制器与 `EcosKnowledgeGraphServiceImpl` 改注入接口，`@Lazy` 保留
- 残留差异（`stash@{5}` 同文件 61/214）经核为 `stash@{4}` 的**旧变体**（`stash@{5}` 早 1.4h），不回放

### 10.3 批 B（横切/依赖）—  实质只需 1 处

| 文件 | 缺口量化 | 判定 |
|:--|:--|:--|
| `LLMGateway.java` / `LLMGatewayProperties.java` | s4=7 / s4=1 | **无需回放** — 差异仅为 javadoc 措辞与字段声明顺序，HEAD 为超集 |
| `LLMGatewayImpl.java` | s4=61/170 | **无需回放** — HEAD 已含 `buildEmbeddingRequestBody` / `callEmbeddingNonStreaming` / `parseEmbeddingResponse` 全套；stash 独有部分为其早期日志变体。**唯一行为差异**：空输入语义 HEAD=`fail("既无 input 也无 texts")`、stash=`ok(空列表)` → 列为 P2 人工裁定项，不阻塞 |
| `LineageController.java` | s2=42/140 | **无需回放** — HEAD 为 PMO-52 T2 真实实现（`OntologyLineageService` JSqlParser 字段级 + 多跳 BFS + `GET /events` 持久化），stash@{2} 为其早期 `extractNodes/extractEdges` 简版 |
| `OntologyVersionService.java` | s3=47/55、s5=19/25 | 🔴 **真缺口**，见 10.3.1 |
| 各 `pom.xml` / `runtime/pom.xml` | s5=1、s3=2 | 低优先，随 10.3.1 一并核对 |

#### 10.3.1 真缺口：`OntologyVersionService` 发布事件**无生产者**（P1）

- 契约类已并入 HEAD：`common-api` 的 `OntologyPublishedEvent` + `KafkaTopics.ONTOLOGY_PUBLISHED`
- 消费侧已并入 HEAD：`kb-engine-impl` 的 `EcosOntologyEventConsumer`（`@EventListener` 同 JVM 路径 + `@KafkaListener` 跨 JVM 路径，groupId=`dccheng-ontology-consumer`）
- 但 **`grep OntologyPublishedEvent` 全后端仅命中契约类与消费者，无任何 `publishEvent` / `eventBusService.publish`** → 消费者永不触发
- 生产者代码停格在 `stash@{3}`：`OntologyVersionService` 在 `publishVersion` 状态提交后做**事件双发**（`ApplicationEventPublisher` 内存路径 + `EventBusService` Kafka 路径，`@Autowired(required=false)` 条件化，异常仅告警不挂主流程）
- **回放方式**：HEAD 的 `OntologyVersionService` 为超集（含全套 `*VO` 强类型方法），stash@{3} 版**缺**这些方法 → **禁止整文件覆盖**，必须把事件双发段**叠加**到 HEAD 版本上
- 前置依赖核对：`runtime-event` 模块与 `EventBusService` 在 HEAD 存在；`ontology-engine-impl` 是否已依赖 `runtime-event` 需确认（stash@{3} 用的是 `@Autowired(required=false)` 兜底，故即使缺依赖也不挂主流程）

### 10.4 批 D（缺口补开发）— 契约核对通过

| 端点 | 实现位置 | 前端消费（`GraphBuilderTab.tsx`） | 契约判定 |
|:--|:--|:--|:--|
| `GET /api/v1/knowledge/sync/jobs` | `GraphSyncController#listJobs` → `KgSyncService#listJobs` | `fetchGraphJobs()` 取 `jobId/type/status/createdAt/updatedAt/error` | ✅ 字段齐备（`coerceJobType`/`coerceJobStatus` 已完成枚举映射） |
| `POST /api/v1/knowledge/graph/build/preview` | `KnowledgeIngestController#previewBuild` → `KgMapperService#previewDryRun` | `previewGraphBuild({dryRun:true})` 取 `create/update/skip` | ✅ 字段齐备 |
| `GET/POST /sync/jobs/{id}/preview\|rollback\|logs` | 批 A 已从 stash 恢复 | `previewGraphJob` / `rollbackGraphJob` / `fetchGraphJobLogs` | ✅ |

- 非阻塞差异：前端 `GraphBuildPreview.samples`（样本明细表）为**可选**字段且带 presence 判断，后端 `previewDryRun` 不返回 → 该子表不渲染。stash 各版本同样未实现 `samples` → 非本次合并丢失，属 PMO-54 前端先行预期，记为 P2 待办
- 非阻塞差异：`GraphBuildJob.sourceCounts/targetCounts` 在 `GraphBuilderTab` 中未被渲染（仅类型声明），缺省无影响

### 10.5 环境修复记录

- 后端构建必须 `mvn install`；本次因 PowerShell 将 `-Dmaven.test.skip=true` 断词为生命周期阶段而报 `Unknown lifecycle phase ".test.skip=true"` → 参数须加引号：`"-DskipTests" "-Dmaven.test.skip=true" "-Djacoco.perModuleCheck.skip=true"`

### 10.6 批 C（前端回放）— 已完成

口径同 §10.1：对 `stash@{3..6}` 各取其基线 `git rev-parse "stash@{N}^"`，抽取前端 `+` 行后逐行核对 `HEAD`。

| 文件 | 回放结果 | 判定 |
|:--|:--|:--|
| `knowledgeApi.ts` | `graphSearch` GET→POST（`GraphSearchInput`）、`previewGraphJob` 补 POST+`type`、新增 `ingestKnowledge` / `fetchAllMappings` / `saveCognitiveConfig` / `promoteToCandidate` 及导出登记 | ✅ 5 处真回放 |
| `typesAndConstants.ts` | `SyncLog` 补 `jobId` / `nodes` / `edges` / `errorMessage` | ✅（s5=3 行、s4=8 行） |
| `ExtractionReviewPanel.tsx` | 转候选本体按钮 + `promoting` 态 + `handlePromoteToCandidate` | ✅ |
| `locales/knowledge/zh-CN.json` / `en.json` | 单键 `extractReview.promoteToCandidate` | ✅（未触碰阶段 1 清洗结果） |

- **旧 Tab 不回放**：`GraphSyncTab` / `KnowledgeExtractionTab` / `OntologyTab` 已被 `1d118f7` 下线。其 stash 新增行分两类 —— ① 主题令牌替换（`useTheme().styles.*`）属存量内容改造，非新增能力；② **新增的"同步作业列表 + 单作业 preview/rollback/logs"**（PMO-50 T4.5）在 `GraphBuilderTab.tsx` 中**已存在并实现**（`previewGraphJob` / `rollbackGraphJob` / `fetchGraphJobLogs` / `previewGraphBuild` / `triggerGraphBuild`），故不迁移旧文件即已等价
- **AI 工作台组件不回放**：`DashboardView` / `copilot/*` / `aiworkbench/logic/*` 的 stash 新增行全部为主题令牌替换，且 HEAD 为**更完整实现**（如 `${styles.cardTextMuted}`/`${styles.appBg}` 覆盖面积更大），stash 独有行为 0
- **编译门**：`npx tsc --noEmit` → exit code **0**（`npm run lint` 同一命令）
- **契约核对（grep 全后端）**：`POST /api/v1/knowledge/graph/search`（`GraphQueryPostController#search`）、`POST /api/v1/knowledge/graph/path`（同前 `#path`）、`POST /api/v1/knowledge/ingest`（`KnowledgeIngestController#ingest`，字段 `entityId/label/type/sourceRef/properties/payload` 与 `KnowledgeIngestInput` 一致）、`GET /api/v1/ecos/mappings/full`（`EcosMappingFullController#full`，含 `If-None-Match`/ETag）、`POST /api/v1/knowledge/sync/jobs/{jobId}/preview?type=`（`GraphSyncController#previewJob`）、`PUT /api/v1/cognitive/config`（`CognitiveConfigController`，`saveCognitiveConfig` 落点）—— 全部命中
- **遗留（同 §9.3 性质）**：`promoteToCandidate` 指向 `POST /api/v1/kb/extraction/extract-entity`，`grep extract-entity ecos_backend` = **0 命中** → 该按钮上线即 404，属"前端契约先行、后端实现缺失"，需批 D 补端点或在前端下线该按钮（本次按回放口径保留，未擅自改指向）
- **存量能力缺失提示（非本批引入）**：旧 `GraphSyncTab` 的"对象类型同步状态表 + 单类型触发同步"（`fetchSyncStatuses` / `triggerObjectSync`）在 HEAD 全前端**已无 UI 消费者**（15-Tab 重构未被 `GraphBuilderTab` 承接），属 §134 "迁移等价性需人工确认" 项，本批不擅自补 UI

---

## 十一、阶段 2 收口记录（端到端实证 · commit 凭证）

阶段 2（A~D）编码回放全部完成，本节记录**运行期实证**而非静态核对结论。

### 11.1 端到端链路实证（批 A + 批 B 真缺口）

链路：发布本体版本 → 事件双发 → kb 侧消费 → 快照落库 → 对象→KG 映射 → 审计。

| 环节 | 验证方式 | 结果 |
|:--|:--|:--|
| 发布版本 | `POST /api/v1/ecos/ontologies/{id}/versions` | ✅（见 11.3 已知 409） |
| 同 JVM 内存路径 | 网关日志线程 `nio-8080-exec-2` | ✅ `TOPOLOGY_EVENT_CONSUMED ontology=fb972746 version=1.0.2 jobId=kg-ont-1.0.2` |
| 跨 JVM Kafka 路径 | 网关日志线程 `ntainer#1-0-C-1` | ✅ 同上，双路径均命中 |
| 快照落库 | `ecos_knowledge.kb_ontology_snapshot` | ✅ `fb972746 / 1.0.1 / hash=5ce86fe2… / created_by=audit / is_deleted=0` |
| 对象→KG 映射 | `KgMapperService.syncFromOntology(ontologyId, jobId)` | ✅ jobId 前缀 `kg-ont-<version>` |
| 契约端点 | `GET /api/v1/knowledge/sync/jobs` | ✅ 200 |
| 契约端点 | `POST /api/v1/knowledge/graph/build/preview` | ✅ `{"create":0,"update":0,"skip":0}` |
| 契约端点 | `POST /api/v1/knowledge/extract/promote-to-candidate` | ✅ `{"requested":1,"created":1,"proposalIds":["17"],"failures":[]}` |
| 统计接口 | `GET /api/v1/knowledge/stats` | ✅ `ontologySnapshotCount=1` / `ontologyVersionCount=1` / `publishedOntologyVersionCount=1` / `lastOntologySnapshotAt=2026-09-15T17:15:18Z` |

> §10.6 遗留的 `promoteToCandidate` 404 问题**已由本阶段新建** `POST /api/v1/knowledge/extract/promote-to-candidate`（`ExtractionController` + `KnowledgeExtractionService`）闭环解决，遗留项作废。

### 11.2 本阶段新发现的真实缺陷（2 处，均已修复复验）

| # | 缺陷 | 根因 | 修复 | 复验 |
|:--|:--|:--|:--|:--|
| 1 | 4 张迁移表在库中缺失 → `kb_ontology_snapshot` INSERT 报 `bad SQL grammar`，job 状态 FAILED | Flyway 禁用（`spring.flyway.enabled: false`），2.1-alpha 手工合并时 V116~V119 脚本未应用 | 按各写入方 schema 手工应用（V116 → `SET search_path TO ecos_knowledge, public`；V117/V118 → `public` 裸名；V119 脚本自带 `public.` 前缀） | 再次发布 1.0.1 → 快照行出现 ✅ |
| 2 | `OntologyPublishedEvent` Kafka 通道**每次发布必反序列化失败**（`no Creators, like default constructor, exist`） | 契约类全 `final` 字段 + 无默认构造器 + `Instant ts` 无 `JavaTimeModule` | 生产/消费两侧补齐：`@JsonCreator` + 构造参数级 `@JsonProperty`；两侧 `ObjectMapper` 注册 `JavaTimeModule` | 发布 1.0.2 → 内存与 Kafka 双路径均消费成功 ✅ |
| 3 | `GET /api/v1/knowledge/stats` 本体 4 字段恒为 0/null | `KnowledgeStatsAggregator` 用裸表名 `kb_ontology_snapshot` → search_path 回退 `public`，而写入侧写 `ecos_knowledge` | 5 处 SQL 补 `ecos_knowledge.` 限定，对齐写入侧 | 4 字段全部非 0 ✅ |

> 缺陷 2 的性质说明：契约类与消费者在 2.1-alpha 中均**已并入**，但从未在运行期被触发过（生产者直到批 B 才补齐），属"合并后首次端到端运行暴露的存量缺陷"，非本次回放引入。

### 11.3 既有缺陷 / 设计取舍（记录，不阻塞本次收口）

| 项 | 现象 | 判定 | 处置 |
|:--|:--|:--|:--|
| `ID_SEQ` 重启回退 | `OntologyVersionService` 的 `private static final AtomicInteger ID_SEQ = new AtomicInteger(5000)` 生成 `ver{N}`，重启后序列归零 → 与已持久化主键冲突 → `duplicate key … ecos_ontology_version_pkey`，HTTP 409（实测重启后连续 3 次失败，第 4 次 `ver5004` 成功） | **既有缺陷，非本次合并引入**（P2） | 本次不修；建议后续改为读 `MAX(id)` 或 DB 序列 |
| 成功路径不写 `kg_sync_log` | `KgMapperService.syncFromOntology` 成功时仅 `log` + 审计，不落 `kg_sync_log` 行；仅消费方异常时兜底写 FAILED 行 | 设计取舍（P2） | 保留；副作用是"本体发布驱动的同步"不出现在 `GET /sync/jobs` 列表 |
| `ontology_objects` / `object_relationships` 表不存在 | 映射源表缺失 | pre-existing | 代码已容忍降级为空结果（`preview` 返回 `{"create":0,"update":0,"skip":0}`），不阻塞 |
| `KnowledgeStatsAggregator` 中 `kb_cognitive_pipeline` / `kb_lineage_event` 仍为裸名 | — | 与写入侧同用 `public`，**一致，无需改** | 保留 |
| `ExtractionReviewPanel` 硬编码 `bg-violet-600` + 异常仅 `console.warn` 无用户反馈 | 违反前端铁律 §4.1（禁硬编码颜色） | P2 待办 | 记入待办，本阶段回放口径不动业务实现 |

### 11.4 编译门 / 构建凭证

| 门 | 命令 | 结果 |
|:--|:--|:--|
| 后端全量构建 | `mvn clean install -DskipTests "-Dmaven.test.skip=true" "-Djacoco.perModuleCheck.skip=true"` | ✅ BUILD SUCCESS（日志 `.working/be_build_b4.log`） |
| 前端编译门 | `npx tsc --noEmit`（= `npm run lint`） | ✅ exit 0 |
| 运行期 | `_win_tasks/start-gateway.ps1` 重启网关 | ✅ pid 31788，健康检查通过 |

> 构建注意（复现预防）：运行中的 gateway jar 被文件锁 → `repackage` 报 `Unable to rename gateway-1.0.0-SNAPSHOT.jar`，须先 `Get-NetTCPConnection -LocalPort 8080 -State Listen | Stop-Process -Force` 再构建。

### 11.5 阶段 2 取整批 commit 凭证（`release/v2.1-alpha`）

| 批次 | commit | message |
|:--|:--|:--|
| 批 A | `d7028a2` | fix(知识工作台): 回放 stash 中 kb 图谱构建后端实现（恢复 /sync/jobs/{id} preview\|rollback\|logs 契约） |
| 批 A 收口 | `bf2d408` | fix(知识工作台): kb 图谱构建后端接口化与端点补齐 |
| 批 B 真缺口 | `31ee6e8` | feat(本体引擎): 补本体发布事件生产者（驱动 kb 侧 KG 同步） |
| 批 B 缺陷修复 | `5ff9569` | fix(事件契约): 修复 OntologyPublishedEvent Kafka 通道反序列化失败 |
| 批 B 横切/依赖 | `1852f41` | fix(横切): 放行 /api/v1/llm/** 并补 kb-engine-impl lombok 依赖 |
| 批 C 前端 | `8ccfcd4` | fix(知识工作台): 回放 stash 前端契约（图谱构建/抽取转候选 API + 面板联动） |
| 批 D 契约补齐 | `8eb183c` | feat(知识工作台): 补抽取实体转候选本体端点 |
| 迁移补齐 | `7eb2144` | chore(知识工作台): 补回 V115 kg_sync_log 迁移脚本（修复 V114→V116 断号） |
| 批 D 缺陷修复 | `447ce61` | fix(工作台): 统计聚合器 kb_ontology_snapshot 补 schema 限定对齐写入侧 |
| 临时产物清理 | `54bb9fa` | chore: 清理历史临时校验产物（.working 脚本/日志、wave1 探测输出） |
| 本报告 | 随本次提交 | docs: release/v2.1-alpha 合并审计报告 |

> 溯源命令：`git log --oneline --grep "2.1-alpha\|回放 stash\|本体发布事件"`；工作区干净后 `git status --short` 应为空（`.working/` 运行期日志已列入待清理）。

### 11.6 阶段 2 结论

- §9 判定的**最大缺口**（kb 图谱构建后端停在 stash、前端契约先行）已闭环，`GET /sync/jobs`、`POST /graph/build/preview`、`POST /extract/promote-to-candidate` 三端点运行期实测通过
- §10.3.1 的**真缺口**（`OntologyVersionService` 事件无生产者）已补齐，且首次端到端运行暴露并修复了 Kafka 反序列化缺陷 → 本体发布 → KG 同步链路**首次真正打通**
- 四任务（数据/知识/AI/场景工作台）在 2.1-alpha 的**回放完整性核对完毕**：i18n 清洗（阶段 1，`b7bc618`）+ 阶段 2（A~D）共 **10 个 clean commit**
- 剩余 P2/P3 待办：`ID_SEQ` 持久化、`kg_sync_log` 成功路径台账、`GraphBuildPreview.samples` 子表、`GraphSyncTab` 存量 UI 等价性人工确认、kb/ontology `AGENTS.md` 依赖描述订正、`ExtractionReviewPanel` 硬编码色值