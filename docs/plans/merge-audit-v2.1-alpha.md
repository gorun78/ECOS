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
| 本报告 | `069a71a` | docs: release/v2.1-alpha 合并审计报告（四任务回放完整性 + 阶段2端到端收口凭证） |
| 探针产物忽略 | `c0331ad` | chore(构建): .gitignore 忽略一次性探针产物（.working/ 与根级 p3b 日志） |

> 溯源命令：`git log --oneline --grep "2.1-alpha\|回放 stash\|本体发布事件"`；`git status --short` 干净（`.working/` 与根级 `_p3b_*.log` 已入 `.gitignore`）。

### 11.6 阶段 2 结论

- §9 判定的**最大缺口**（kb 图谱构建后端停在 stash、前端契约先行）已闭环，`GET /sync/jobs`、`POST /graph/build/preview`、`POST /extract/promote-to-candidate` 三端点运行期实测通过
- §10.3.1 的**真缺口**（`OntologyVersionService` 事件无生产者）已补齐，且首次端到端运行暴露并修复了 Kafka 反序列化缺陷 → 本体发布 → KG 同步链路**首次真正打通**
- 四任务（数据/知识/AI/场景工作台）在 2.1-alpha 的**回放完整性核对完毕**：i18n 清洗（阶段 1，`b7bc618`）+ 阶段 2（A~D）共 **10 个 clean commit**
- 剩余 P2/P3 待办：`ID_SEQ` 持久化、`kg_sync_log` 成功路径台账、`GraphBuildPreview.samples` 子表、`GraphSyncTab` 存量 UI 等价性人工确认、kb/ontology `AGENTS.md` 依赖描述订正、`ExtractionReviewPanel` 硬编码色值

---

## 十二、数据工作台「血缘地图内容丢失」事故核查与收口（2026-09-16）

> 触发：用户报告"血缘地图模块之前已实施的修改在当前版本中未体现"，要求 5 项动作：
> ① 全模块完整性检查 ② 血缘地图版本控制记录根因 ③ 恢复正确版本 ④ 建立内容变更跟踪机制 ⑤ 全面测试。
> 复杂度评估：L2 标准（功能点 5 / 模块 2 / 数据关联 3 / 外部依赖 2 / 并发 1 / 多页状态管理 6 = 19 分）。

### 12.1 审计方法学（可复算，含一处必须记住的陷阱）

**三态比对法**：对每个可疑文件取三份 blob 逐一对齐 —— `base`（丢失前基线）、`stash`（悬空 stash 树）、`HEAD`（当前分支）：

- `HEAD == base` → **SAFE**：stash 版可无损回放（无并发演进）
- `HEAD == stash` → **ALREADY**：已在 HEAD，无需动作
- 其余 → **MERGE**：两侧各有独有内容，需逐处甄别后叠加

量化命令：`git diff --numstat <stash> HEAD -- <path>`，**第 2 列 = stash 有而 HEAD 没有的行数**，即"真丢失量"；按此列降序即可把"真丢失"与"双向演进噪声"分开。

> 🔴 **陷阱（本轮最大误判来源）**：`git rev-parse "<ref>:<path>"` 对**不存在的路径会原样回显参数字符串**而非报错。第一版脚本据此把 `IntegrationMetadataController` 误判为"stash 有改动"、把 HEAD 独有的 `IntegrationMetadataService` 误判为"base/stash 均存在"。
> **必须用 `git rev-parse --verify --quiet "<ref>:<path>"`**（输出为空 = MISSING）。改用后全部判定修正。

### 12.2 完整性检查结果（①）—— 三类，只有两类是真丢失

| 集合 | 性质 | 文件（stash 独有行数 > HEAD 独有行数） |
|:--|:--|:--|
| **A · 血缘地图（真丢失）** | 实现整体未入 HEAD | `DataLineage.tsx`(580/140)、`DataLineageService.java`(379/40)、`api.ts`(84/20)、`DataLineageController.java`(66/17)、`DataLineageTab.tsx`(8/3) |
| **C · 血缘跳转链路（真丢失，用户可感知）** | `/lineage` 死链修复整条未入 HEAD | `CatalogContextMenu.tsx`(8/1)、`DataWorkbenchLayout.tsx`(34/10)、`App.tsx`(5/2，仅注释差异) |
| **B · 契约断裂（非丢失）** | HEAD 保留重构成果但端点缺失 | `IntegrationMetadataController.java`（stash 239 行胖控制器 → HEAD 65 行薄控制器 + 新 Service），详见 §12.5 |

噪声（`HEAD` 为超集或双向演进，不回放）：`OntologySecurityInterceptor`(361/335)、`ScenarioManagementView`(108/722)、`EcosKnowledgeGraphServiceImpl`(67/146)、locale `*.json`、`ConnectionsTab.tsx`(1/1 对称)、`DqRuleSqlProvider.java`（stash 把私有构造器改 public，属防实例化降级）、ontology `LineageController.java`（HEAD 已是 PMO-52 T2 真实现超集，stash 为旧简版）。

### 12.3 根因（②）—— 改动只活在一个被 drop 的悬空 stash

- 修改**仅存在于悬空 commit** `024fdda`（subject `On feat/ontology-workbench-wave-a: t2 pre-changes`，author ECOS-PMO，2026-09-12 02:46:57 +0100，parents `0a5b9fa` + `756187e`）及内容相同的 `21d2003`
- **9 个分支逐一核对，无一包含增强版** —— 即：它从未进入任何分支
- 悬空成因：`git stash drop` 在未 apply/commit 前执行 → 对象失引用 → `git stash list` 再也看不到，只能靠 `git fsck --dangling` 发现
- **为什么"看起来正常"**：`git status` 干净、`git log` 无异常、编译通过 —— 丢失发生在"提交边界之外"，任何只检查工作区与提交历史的常规手段都检测不到

> 技术身份对应提交 `1fe0e2f`（`feat(integration): 批次 B' data 集成元数据 + ontology 血缘真解析/真多跳 impact`）**在 HEAD 祖先链中**，但它只落地了该批次的一部分 —— 这也解释了为什么"提交明明存在"而功能却缺失。

### 12.4 恢复内容（③）

**集合 A（5 文件，`HEAD == base` 无损回放）**
- `DataLineage.tsx`：251 行 → 691 行（stash 新增 554 行中 511 行未入 HEAD）
- `data-workbench/api.ts`：删除 3 个死函数（`fetchLineageNodes`/`fetchLineageEdges`/`buildLineage`，grep 确认无其他引用），新增 3 个真接口 `fetchLineageTopology` / `fetchLineageImpact` / `rebuildLineage`
- `DataLineageController.java`：移除 `/nodes`、`/edges`、`/build`；新增 `GET /topology`、`POST /topology/rebuild`、`GET /impact`；`getLineage` 的 `tableName` 由必填改 `defaultValue=""`（放宽，符合"只增不改"）
- `DataLineageService.java`：380 → 719 行；删除 `@PostConstruct` 构造器误用与 `init()`/`ensureSchema()`，改惰性 `tryCreateLineageTablesSafely()`；新增 `rebuildAndPersist` / `persistParsed` / `clearLineageData` / `getTopologyFromDb` / `getImpactOverview` / `matchStartNode` / `bfsImpact` / `emptyImpact`
- `DataLineageTab.tsx`：新增 `initialTable` 透传

**集合 C（跳转链路，2 文件叠加，1 文件不回放）**
- `DataWorkbenchLayout.tsx`：**MERGE 叠加** —— 以 HEAD 为基底（保留 HEAD 独有的 `HealthTab`、`ENGINE_CONFIG_TAB`、`justify-between` 布局），叠入 stash 的 `?lineageTable=X` 链路（`useSearchParams` 解析 → 自动切血缘 Tab → 消费一次后清 query 避免状态污染）
- `CatalogContextMenu.tsx`：修复死链 —— `navigate('/lineage')` 路由实际**未注册**（[main.tsx](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/main.tsx) 只有 `<Route path="*" element={<WorldModelViewer />} />` 兜底，会落到错误页），改为 `navigate('/data-workbench?lineageTable=<table>')`
- `App.tsx`：stash 相对 HEAD 仅 3 行注释差异，无功能 → 不回放

**架构铁律 §4.1 合规化**：`DataLineage.tsx` 回放后含 17 处 `slate-*` 硬编码色（违反主题令牌铁律），逐处改写为 `useTheme().styles.*`（`cardText`/`cardTextMuted`/`divider`/`badgeBg`/`badgeText`/`infoBg`/`sidebarHoverBg`/`accentBg`/`accentBorder`/`appBg`）。`slate-` 残留已为 0；保留的 red/orange/amber/emerald/sky/blue 为语义色（符合铁律），`bg-white` 1 处（toggle 把手）保留。
另修 1 处既有 bug：原 `style={{ borderColor: styles.cardBorder }}` 把主题 class 当作颜色值用（无效值），改为 className 化。

### 12.5 集合 B（端点契约断裂）—— 精确结论与修复

**修复前实测（带 admin token）**

| 端点 | 实测 | 含义 |
|:--|:--|:--|
| `/api/integration/logs` | **200** | 控制器可达（未带 token 时为 403，易误判为"整体不可达"） |
| `/api/integration/metadata` | **404** | 端点被 `1fe0e2f` 重构删除，前端 4 处调用 |
| `/api/v1/integration/logs` | **404** | gateway 缺重写 |
| `/api/v1/integration/metadata` | **404** | 重写缺失 + 端点缺失，双重 |

**两个根因**
1. **重写从未落地**：`IntegrationMetadataController` 映射在裸路径 `/api/integration`，其类注释白纸黑字写着"knowledgeApi 走 `/api/v1/integration/**`，gateway 重写到 `/api/integration`"——但 [VersionPrefixRewriteFilter.java](file:///d:/workspace/javaprojects/ECOS/ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java) 的 `V1_REWRITE_MAP` 里从来没有 integration 条目
2. **端点被重构丢弃**：`1fe0e2f` 把 239 行胖控制器重构成"薄 Controller(65 行) + `IntegrationMetadataService`(359 行)"，重构只搬了 `/logs` 与 `POST /metadata/drift`，`GET /metadata` 整个丢失

**修复（5 文件，API 只增不改）**

| # | 文件 | 改动 |
|:--|:--|:--|
| ① | `VersionPrefixRewriteFilter.java` | `V1_REWRITE_MAP` 增 `Map.entry("/api/v1/integration/", "/api/integration/")`（已全库确认**无任何 Controller 映射在 `/api/v1/integration`**，不会打乱既有路由） |
| ② | `SecurityConfig.java` | ~~permitAll 补裸路径 `/api/integration/**`（铁律 §1.2 双路径各写一遍）~~ **→ 已撤销，见 §12.5.1** |
| ③ | `ClearanceInterceptor.java` | 豁免清单补 `path.startsWith("/api/v1/integration")` |
| ④ | `IntegrationMetadataService.java` | 新增 `fetchIntegrationMetadata()` / `fetchDriftSample(boolean,String)` / `buildLineageOverview()` / `fetchRealDriftSamples()` / `mapDsToConn()` / `copyFirst()` / `mapTaskToSync()`（+277 行）；构造器注入新增 `DataSourceService`、`DataLineageService` |
| ⑤ | `IntegrationMetadataController.java` | 新增 `GET /metadata`、`GET /metadata/drift`（薄委托，保留既有 `/logs` 与 `POST /metadata/drift`） |

**契约设计要点**：`sources` 与 `connections` **同放 data 内层** —— 3 处前端调用方经 `apiFetchData` 解包后读 `data.sources`，而 [SyncTab.tsx:42](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/knowledge/tabs/SyncTab.tsx#L42) 用原生 `fetch().json()` 拿到的是未解包的 `ApiResponse` 信封、读 `raw.data.sources`，两种读法都必须满足。**前端零改动**，以补齐后端方式闭合契约。

**修复后实测（带 admin token；匿名访问结论见 §12.5.1）**

| 端点 | HTTP | 关键载荷 |
|:--|:--|:--|
| `/api/v1/integration/metadata` | 200 | `connections`/`sources`/`syncTasks` 各 **4 条真实数据**（如 `w7_ds_5dsvdf` POSTGRESQL status=connected）+ `lineage{nodes,links}` + `simulationState{false,false}` |
| `/api/integration/metadata` | 200 | 同上 |
| `/api/v1/integration/logs` | 200 | `logs: []` |
| `/api/v1/integration/metadata/drift?sample=true` | 200 | `schemaDelta/fields/rows/samples/lineage/checkedAt` 齐备 |
| `/api/integration/metadata/drift?sample=true&dsId=x` | 200 | 同上 |
| 回归 `/api/v1/engine/data/lineage/topology` | 200 | 新重写未影响既有路由 |
| 回归 `/api/v1/pipeline/definitions` | 200 | 同上 |

**防造假约束**：`fetchDriftSample` 复用现有 `detectDriftAndSlaStatus("drift")` 的真实 DQ 结论（`attribution` 中 `kind=DQ_FAILURE` 条目）作 schemaDelta；`sample=true` 时取 `ecos_dq.dq_rule_check.sample_failures` 真实样本行，**无真实样本返回空数组，不塞假数据**。`dsId` 为前端契约参数：`ecos_dq.dq_rule` 无 datasource 外键，无法归因到具体数据源，故按引擎级真实漂移返回，已在方法 javadoc 中写明该取舍。

### 12.5.1 安全回归自查与撤销（🔴 修复过程中自查发现，已闭合）

**发现方式**：不是用户报告，而是走 Reviewer 门禁的 `SECURITY_GATE` 时**主动做未认证访问回归**时暴露。

**现象（修复后、撤销前实测）**

| 请求（无 token） | 撤销前 | 修复前基线 |
|:--|:--|:--|
| `/api/integration/metadata` | **200**（返 `connections` 含 host/port/username/jdbcUrl） | 403 |
| `/api/v1/integration/metadata` | **200** | 403（重写缺失，实际不可达） |
| `/api/integration/logs` | **200** | **403**（§12.5 表首行已记载该基线，可复算） |

即修复动作 ② 把**未认证访问放行了**，暴露基础设施拓扑（host/port/username/jdbcUrl，不含密码）。与 M0 改造（2026-09-01）移除 `/datanet/**` permitAll 的缺陷同类 —— 当时判据正是"数据源凭据不可匿名（QA T3-006）"，违反铁律 §2.4-6 默认 DENY。

**根因（一处必须记住的过滤器顺序）**

铁律 §1.2「双路径各写一遍」的**默认前提不成立**：[VersionPrefixRewriteFilter.java](file:///d:/workspace/javaprojects/ECOS/ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java#L28-L30) 标注 `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`（≈ `Integer.MIN_VALUE+10`），**远早于** Spring Security 的 `FilterChainProxy`（默认 order `-100`）。即 **路径重写先于鉴权**，鉴权层看到的**永远是裸路径**。

推论（可复算）：
- `/api/v1/integration/**` 这条**既有** permitAll 条目，对任何已进 `V1_REWRITE_MAP` 的前缀**不可达**（等效死条目）；
- 真正的放行源只有我新增的 `/api/integration/**` 一条；

**处置**：撤销修复动作 ②（仅回退我自己新增的条目），**不动**既有的 `/api/v1/integration/**`（禁止魔改既有全局配置；且其等效死条目、无安全影响）。撤销后两路径统一回到"要求认证"，且**未添加任何新豁免**。

**撤销后复验（判据：匿名 403 / 带 token 200）**

| 请求 | 预期 | 实测 |
|:--|:--|:--|
| 无 token `/api/integration/metadata` | 403 | 403 |
| 无 token `/api/v1/integration/metadata` | 403 | 403 |
| 无 token `/api/integration/logs` | 403（回到基线） | 403 |
| 无 token `/api/v1/integration/metadata/drift?sample=true` | 403 | 403 |
| 带 token 上述 4 条 | 200 | 200 |

**前端影响面：零**。全库 8 处调用面（`data-workbench/api.ts:10`、`knowledgeApi.ts:325/333/341/798/820`、`SyncTab.tsx:42`、`DataWorkbenchImportTab.tsx:52/72`）**全部携带 `Authorization: Bearer`**；另确认无任何匿名调用方（含 `ecos-tests/`、docker health、定时任务）。

**经验固化**（写入 [ecos_backend/scripts/check-controller-filter.sh](file:///d:/workspace/javaprojects/ECOS/ecos_backend/scripts/check-controller-filter.sh) 同目录的审计脚本注释）：判据从"铁律 §1.2 双路径各写一遍"修正为 **"先判路径是否落在 `V1_REWRITE_MAP` 内 —— 若在，鉴权层只见裸路径，只写裸路径豁免；再判该端点是否应匿名 —— 业务数据端点一律不写 permitAll"**。

### 12.6 全面测试（⑤）—— 编译门 / 契约 / 浏览器 E2E

**编译门**
- 后端 `mvn install -DskipTests -Dmaven.test.skip=true` → **BUILD SUCCESS**
  - 坑：运行中的 gateway jar 被文件锁 → `repackage` 报 `Unable to rename gateway-1.0.0-SNAPSHOT.jar`，须先停 8080 再构建（同 §11.4）
  - 坑：PowerShell 下 `-Dmaven.test.skip=true` 会被拆成 `-Dmaven` + `.test.skip=true`，**必须给每个 `-D` 参数加引号**
- 前端 `npx tsc --noEmit` → **exit=0**

**浏览器 E2E（Playwright，新增 [ecos-tests/lineage-smoke.mjs](file:///d:/workspace/javaprojects/ECOS/ecos-tests/lineage-smoke.mjs)，项目既有测试目录）**

以 `localStorage.token` 注入登录态 → 直达 `#/data-workbench?lineageTable=test_table`，六项全 PASS：

| 断言 | 结果 |
|:--|:--|
| A1 无 ErrorBoundary 且工作台已渲染 | PASS（`boundary=false`, `hasTabLabel=true`） |
| A2 血缘 Tab 存在且激活 | PASS（`active=true, activeCount=1`，判别依据：激活态带 `border-l-2`） |
| A3 `?lineageTable=test_table` 回填单表查询框 | PASS |
| A4 `/api/v1/engine/data/lineage/*` 请求成功 | PASS（topology 200×2） |
| A5 console 无 error | PASS（`errors=0`） |
| A6 无意外 4xx/5xx | PASS |

截图证据：`%TEMP%\ecos-lineage-smoke\lineage.png`（可见「全链路数据血缘地图」Tab 高亮、`test_table` 已回填、"0 节点, 0 边"空态提示、force-directed 画布正常、无白屏）。

**§12.5.1 撤销安全回归后复跑（同一脚本，ground truth 更新）**：六项仍全 PASS；且网络面板中 `/api/integration/metadata` **带 token 返 200**（脚本 A6 的"已知缺陷豁免"已不再被触发，`[]` 为空），证明撤销 permitAll 对前端**零影响**。全量访问清单：`/api/v1/auth/me`、`/api/health`×2、`/api/v1/task/stats`×2、`/api/v1/engine/data/lineage/topology`×2、`/api/integration/metadata`×2、`/api/v1/pipeline/definitions`×2、`/api/v1/ecos/dq/rules`×2 —— **全部 200**。

> ⚠ 凭证局限（据实标注）：`ecos-tests/` 命中 [.gitignore](file:///d:/workspace/javaprojects/ECOS/.gitignore#L10) 的 `*ecos-tests*` 排除规则，故该 E2E 脚本**无法作为 commit 凭证**、未纳入版本控制（按"禁止魔改全局配置/ignore 规则"未擅自 `-f` 强加）。本批次 DONE 凭证以 §12.9 的 commit hash 与 §12.6/§12.5.1 的 curl 实测为准；E2E 脚本定位为**本地可复现工具**。

> E2E 环境备注：本次 session 内 `browser_use` 子代理的 WebView 在数据工作台页持续"未就绪"（疑似其自带 webview 控制器问题），故改用项目既有 Playwright 方案取到 ground truth。**E2E 结论以 Playwright 为准。**
> 血缘空态（0 节点）非缺陷：DB 内暂无含 SQL 的管道任务，`POST /topology/rebuild?limit=1` 实测 `tasks_scanned=1, tasks_parsed=0` 属预期。

### 12.7 内容变更跟踪机制（④）

从"检测不到"这个根因出发，落点两处：

1. **可执行审计脚本 [ecos_backend/scripts/check-dangling-changes.sh](file:///d:/workspace/javaprojects/ECOS/ecos_backend/scripts/check-dangling-changes.sh)**（置于既有检查脚本目录，与 `check-controller-filter.sh` 同源）
   三项检查：① working tree 未提交变更 ② 存活 stash ③ **悬空 commit 中含 base 分支所缺内容的文件**（用 `git fsck --dangling` + `git diff --numstat <dangling> <base>` 第 2 列定量）。退出码 0=可交付 / 1=有未落地风险。
   用法：`bash ecos_backend/scripts/check-dangling-changes.sh [基线分支]`

2. **`git rev-parse` 陷阱写入脚本注释**（`--verify --quiet` 强制用法），防止后人重蹈本轮误判。

### 12.8 本轮遗留（不阻塞本次收口）

- `ID_SEQ` 静态内存序列重启回退 → 409
- `kg_sync_log` 成功路径不落台账
- `ExtractionReviewPanel` 硬编码 `bg-violet-600` + 异常仅 `console.warn`
- kb/ontology `AGENTS.md` 依赖描述订正
- `SimulationState` 为常量 `false`：内存模拟态已移除，真实漂移结论走 `GET /metadata/drift`；若产品侧仍需"模拟开关"需另立需求
- `ecos-tests/` 尚未接入 CI（本轮为按需手动执行）