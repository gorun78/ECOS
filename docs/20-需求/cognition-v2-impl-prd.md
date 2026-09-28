# ECOS 认知工作台 V2 实施 PRD

> 来源: PMO | 日期: 2026-09-22 | 责任人: PMO
> 版本: v1.0
> 追溯: 认知工作台原型 V2.0 (ECOS-Cognitive-Workbench-Prototype-v2.0)

---

## 1. 执行摘要

本 PRD 定义 ECOS 认知工作台（V2）前端 7 页重写的完整实施范围、后端依赖、数据库变更、安全合规要求和验收标准。

**核心原则**：

- **C（认知）域前端归属 AI 工作台（火 W + 木 C）**——对应「系统 1 快·直觉（ai-engine）/ 系统 2 慢·审慎（cognitive-engine）」，两者同台。引擎层 `cognitive-engine` 仍独立（木 C），仍由 `services/aiming`（:18084）聚合加载；本次只调整**用户视角的工作台归属**，不动引擎层与数据边界（见架构铁律 v1.5 §0.5）
- **推理结果不持久化**——认知推理输出（诊断、反事实、模拟）仅用于即时展示，不写入业务表
- **D / I / K 只读输入**——数据层（D）、本体层（I）、知识层（K）对认知工作台均为只读消费，认知工作台不得反写这三层存储
- **推理即焚**——所有推理上下文（causal graph context、scenario state）在页面卸载后释放，不落 PG

**重写范围**：前端 7 页（`src/pages/aiworkbench/cognition/`），后端不新增算法（复用既有 `CausalReasonerService` / `KnowledgeReasonerService` / `ScenarioSimulatorService`），数据库仅 `ALTER ADD COLUMN`（R9 只加不删）。

---

## 2. 后端现状（Backend Inventory）

当前 `cognitive-engine` 及关联服务共暴露 **18 个 `@RestController`**，约 30 个端点。以下为完整清单：

| # | Controller | Base Path | Key Endpoints |
|:--:|:--|:--|:--|
| 1 | `DiagnosisController` | `/api/v1/cognitive` | `POST /diagnose`, `GET /diagnose/history` |
| 2 | `ScenarioController` | `/api/v1/cognitive/scenario` | `POST /simulate`, `GET /list`, `POST /compare` |
| 3 | `CounterfactualController` | `/api/v1/cognitive` | `POST /counterfactual` |
| 4 | `CognitiveHypothesisController` | `/api/v1/cognitive/hypotheses` | `GET` (list), `GET /{id}`, `POST` (create), `POST /{id}/invalidate` |
| 5 | `CognitiveEvidenceController` | `/api/v1/cognitive/evidence` | `GET` (list), `GET /{id}`, `POST` (create) |
| 6 | `CognitiveBeliefController` | `/api/v1/cognitive/beliefs` | `GET` (list), `GET /{id}`, `POST` (create), `POST /{variable}/update-by-evidence`, `POST /{variable}/override`, `GET /{variable}/{version}/replay` |
| 7 | `ForecastController` | `/api/v1/cognitive` | `POST /forecast`, `GET /models`, `GET /models/{modelId}`, `POST /models` |
| 8 | `MentalReviewController` | `/api/v1/cognitive` | `GET /mental-reviews` |
| 9 | `CognitivePipelineController` | `/api/v1/cognitive/pipeline` | `POST` (create), `GET` (list), `POST /{id}/execute`, `DELETE /{id}`, `GET /{id}/execution/{execId}` |
| 10 | `CognitivePlannerController` | `/api/v1/cognitive` | `POST /plan`, `GET /plan/{id}`, `POST /optimize` |
| 11 | `DecisionController` | `/api/v1/cognitive/decision` | `POST /record`, `POST /{id}/link`, `GET /similar`, `GET /{id}/chain`, `GET /{id}/impact`, `POST /{id}/check-rules` |
| 12 | `ProvenanceController` | `/api/v1/cognitive/provenance` | `GET` (list) |
| 13 | `WorldModelController` | `/api/v1/world-model` | `GET /state`, `POST /scenarios`, `POST /strategy/recommend`, `GET /causal-graph` |
| 14 | `CognitiveConfigController` | `/api/v1/cognitive/config` | `GET`, `PUT` |
| 15 | `CognitiveEngineHealthController` | `/api/v1/cognitive` | `GET /health` |
| 16 | `CognitiveEngineOpenHealthController` | `/api/v1/engine/cognitive` | `GET /health` |
| 17 | `Wave3DemoController` | `/api/v1/cognitive/demo/wave3` | `POST` |
| 18 | *(见备注)* | — | 部分端点复用上述 Controller 内的方法 |

> **备注**：上述 17 个独立 Controller 合计覆盖 ~30 个 REST 端点。`Wave3DemoController` 为演示用途，生产环境应 `@Profile("!prod")` 守卫。

**前端 API 层现状**：`cognitiveEngineApi.ts`（171 行）中约 14 个方法，仅 ~3 个命中真实端点，其余为占位/decoy 调用。需全量重写。

**前端页面现状**：

- 新建目录 `src/pages/aiworkbench/cognition/`（V2 七页，前端归属 AI 工作台）
- 现有认知页面散落于：
  - `CognitiveEngineView.tsx`（384 行，大部分为 decoy API）
  - `CognitiveOperatingSystem.tsx`（840 行）
  - `CognitionView.tsx`（aiworkbench 模块）
  - `scenario/` 标签页：`CounterfactualTab.tsx`、`OverrideTab.tsx`、`ReplayTab.tsx`、`MentalReviewTab.tsx`

---

## 3. 前端现状（Frontend Gap）

### 3.1 API 层缺口

`cognitiveEngineApi.ts`（171 行）需重写：

- **删除**假端点：`start` / `stop` / `settings` / `compile-context` / `index-status` / `reason` / `optimize` / `blueprint` / `execute-action` / `agent-mesh` / `guardrails`
- **保留**真实端点（对应 §2 表格中的 Controller）
- 新增方法：`preflight`（Sprint B 新增）、`beliefReplay`、`provenanceList`、`worldModelState`、`causalGraph` 等

### 3.2 页面层缺口

7 页均未在任何路由中注册，需从零创建：

| 页面文件 | 原型页码 | 功能定位 |
|:--|:--:|:--|
| `01-overview.tsx` | P1 | 认知态势总览 |
| `02-situation-diagnosis.tsx` | P2 | 态势诊断（5 步向导） |
| `03-causal-analysis.tsx` | P3 | 因果分析（因果图 + 证据 + 假设） |
| `04-scenario-simulation.tsx` | P4 | 情景模拟（滑块 + 对比 + 敏感性） |
| `05-hypothesis.tsx` | P5 | 假设管理（CRUD + 状态机 + 证据绑定） |
| `06-cognitive-models.tsx` | P6 | 认知模型（卡片网格 + 生命周期） |
| `07-cognitive-state.tsx` | P7 | 认知状态（Belief 表 + 演化时间线） |

### 3.3 路由注册

`App.tsx`（606 行，需先读），在 `/knowledge/cognition/*` 路径下注册 7 个子路由，使用 `React.lazy` + `Suspense` 懒加载。

---

## 4. 数据库现状

5 张表（4 张 ADR-8/9 + 1 张 pipeline）：

| 表名 | 迁移版本 | `domain` (DR08) | `version_no` (DR07) | 备注 |
|:--|:--:|:--:|:--:|:--|
| `ecos_cognitive_model` | V124 | ❌ 缺失 | ❌ 缺失 | V159 修复 |
| `ecos_cognitive_evidence` | V127 | ✅ 已有 | ❌ 缺失 | V159 补 `version_no` |
| `ecos_cognitive_hypothesis` | V128 | ✅ 已有 | ❌ 缺失 | V159 补 `version_no` |
| `ecos_cognitive_belief` | V129 | ✅ 已有 | ❌ 缺失 | V159 补 `version_no` |
| `kb_cognitive_pipeline` | V117 | — | — | `kb_` 前缀（legacy，不修改） |

### 4.1 V159 修复（Sprint A）

**文件**：`ecos_backend/database/V159__ecos_cognitive_schema_fix.sql`

```sql
-- V159: 认知工作台表 DR07/DR08 合规修复
-- 只 ADD COLUMN，不 ALTER/DROP（R9）

-- 1. ecos_cognitive_model：补 domain + version_no
ALTER TABLE ecos_cognitive_model ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_cognitive_model ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

-- 2. ecos_cognitive_evidence：补 version_no
ALTER TABLE ecos_cognitive_evidence ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

-- 3. ecos_cognitive_hypothesis：补 version_no
ALTER TABLE ecos_cognitive_hypothesis ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

-- 4. ecos_cognitive_belief：补 version_no
ALTER TABLE ecos_cognitive_belief ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

-- 索引（乐观锁查询）
CREATE INDEX IF NOT EXISTS idx_cognitive_model_version_no ON ecos_cognitive_model(version_no);
CREATE INDEX IF NOT EXISTS idx_cognitive_evidence_version_no ON ecos_cognitive_evidence(version_no);
CREATE INDEX IF NOT EXISTS idx_cognitive_hypothesis_version_no ON ecos_cognitive_hypothesis(version_no);
CREATE INDEX IF NOT EXISTS idx_cognitive_belief_version_no ON ecos_cognitive_belief(version_no);
```

**回滚文件**：`ecos_backend/database/V159__rollback.sql`（R9：只记录，不执行 DROP）

---

## 5. 实施范围（3 Sprints）

### Sprint A — 基础设施（P0，1~2 天）

| 任务 | 类型 | 说明 |
|:--|:--|:--|
| **A1** | 后端 | 创建 `V159__ecos_cognitive_schema_fix.sql`（见 §4.1）+ 回滚文件。执行：`psql -U postgres -d sys_man -f V159__ecos_cognitive_schema_fix.sql` |
| **A2** | 前端 | 重写 `cognitiveEngineApi.ts`：删除全部假端点方法，仅保留 §2 中真实端点。每个方法加 JSDoc 注释标明对应的 Controller + HTTP 方法 |
| **A3** | 前端 | 创建 `src/pages/knowledge/cognition/` 目录 + 7 个页面骨架文件（每个文件含 `export default` 组件 + 路由参数 + 基础布局容器） |
| **A4** | 前端 | 在 `App.tsx` 注册 7 条路由（`/knowledge/cognition/01` ~ `/07`），使用 `React.lazy` 懒加载，包裹 `Suspense` |

**Sprint A 验收**：
- 7 页可通过路由访问（显示骨架占位）
- `npm run build` 通过
- `V159` 执行后 `\d ecos_cognitive_model` 显示 `domain` + `version_no` 列

---

### Sprint B — 核心功能（P1，3~5 天）

| 任务 | 类型 | 说明 |
|:--|:--|:--|
| **B1** | 后端 | `DiagnosisController` 新增 `POST /api/v1/cognitive/diagnose/preflight` 端点 + `DiagnosisService.preflight()` 方法。返回诊断前置条件检查（数据源可达性、本体图谱完整性、知识索引状态） |
| **B2** | 前端 | 重建 `CausalGraph` 组件：节点可点击 → 打开 Drawer（显示节点详情 + 关联证据 + 关联假设 + 数据溯源）；可追溯因果链；反事实检查按钮 |
| **B3** | 前端 | `02-situation-diagnosis.tsx`：5 步向导（选数据源 → 选指标 → preflight 检查 → 执行诊断 → 结果展示），preflight 通过/失败 UI 状态 |
| **B4** | 前端 | `03-causal-analysis.tsx`：因果图（`CausalGraph` 组件）+ 证据节点列表 + 假设面板 + 信念更新触发 |
| **B5** | 前端 | `04-scenario-simulation.tsx`：变量滑块（`input[type=range]`）+ 情景卡片对比 + 敏感性分析表格 + W 决策输入按钮（`POST /decision/record`） |
| **B6** | 前端 | `05-hypothesis.tsx`：列表 + CRUD + 状态工作流（`proposed → valid → invalidated`）+ 证据绑定 + 信念自动更新触发（`POST /beliefs/{variable}/update-by-evidence`） |

**Sprint B 验收**：
- B1：`POST /diagnose/preflight` 返回结构化检查结果（data/metric/index 三项 pass/fail）
- B2：因果图节点点击打开 Drawer，展示 evidence/hypothesis/source 三个 Tab
- B3：5 步向导可完整走通，preflight 失败时阻断下一步
- B4：因果图 + 证据 + 假设 + 信念更新数据流完整
- B5：滑块调参 → 模拟执行 → 卡片对比 → 敏感性表 → 决策记录，全链路可走通
- B6：假设 CRUD + 状态流转 + 证据绑定 + 信念联动，全链路可走通

---

### Sprint C — 完善与体验（P2，2~3 天）

| 任务 | 类型 | 说明 |
|:--|:--|:--|
| **C1** | 前端 | `01-overview.tsx`：4 个指标卡片（模型数/活跃假设数/证据数/最近推理时间）+ 认知链健康度进度条 + 最近活动列表 |
| **C2** | 前端 | `06-cognitive-models.tsx`：模型卡片网格 + 5 态生命周期（`draft → active → deprecated → archived`，共 4 态 + `error` 异常态）CRUD |
| **C3** | 前端 | `07-cognitive-state.tsx`：Belief 表格（变量名/当前值/版本/最后更新）+ 认知演化时间线 + 全量溯源（`GET /provenance`） |
| **C4** | 前端 | i18n 命名空间 `knowledge.cognition.*`，覆盖 `zh-CN` 和 `en` 两个 locale 文件 |
| **C5** | 前端 | 共享组件抽取：`CognitionContextSelector`、`EvidencePanel`、`HypothesisPanel`、`BeliefPanel`、`CausalGraph`、`ScenarioCompare`、`CognitiveTrace`（放入 `src/components/cognition/`） |

**Sprint C 验收**：
- 7 页全部功能完整
- i18n 切换正常（中/英）
- 共享组件在 3+ 个页面复用

---

## 6. 安全合规

| 维度 | 要求 |
|:--|:--|
| **数据库** | V159 新增列遵守 DR06（审计 5 字段不删）/ DR07（`version_no`）/ DR08（`domain`）。回滚文件仅记录，不执行 `DROP COLUMN`（R9） |
| **写操作** | 所有认知写操作（假设创建/失效、信念更新、证据登记）必须通过 ABAC 权限评估 + 发送 Kafka `ecos.audit` 事件（`KafkaTopics.AUDIT`） |
| **Neo4j** | 仅 `@Profile({enterprise, ultimate})` 守卫启用；查询超时 10s；节点上限 1000；只读（禁止 `CREATE`/`MERGE`/`DELETE`） |
| **LLM** | 仅通过 `llm-gateway` 服务调用，禁止 `cognitive-engine` 直连 LLM 提供商 |
| **敏感数据** | Belief 值若含业务敏感字段，响应脱敏（调用 `POST /api/security/cls/columns`） |
| **Token** | 所有 API 调用必须携带 `Authorization: Bearer <token>`，由 `cognitiveEngineApi.ts` 统一从 `ThemeContext` / `AuthContext` 注入 |

---

## 7. 验收标准

### 功能验收

- [ ] 7 页通过路由 `/knowledge/cognition/{01..07}` 均可访问
- [ ] `cognitiveEngineApi.ts` 中所有 API 方法命中真实后端端点（浏览器 Network Tab 验证，无 404 / 无 `console.log` 假数据）
- [ ] 因果图节点可点击，Drawer 打开显示 evidence + hypothesis + source 三个 Tab
- [ ] 假设状态变更（proposed → valid → invalidated）触发 Belief 更新（Network Tab 验证 `POST /beliefs/{var}/update-by-evidence` 调用）
- [ ] 5 步诊断向导中 preflight 失败时阻断后续步骤
- [ ] 情景模拟滑块调参 → 模拟 → 对比 → 决策记录全链路走通
- [ ] Belief 表格展示变量名/当前值/版本号/最后更新时间

### 构建验收

- [ ] `npm run build` 0 errors 0 warnings
- [ ] 新组件使用 `lucide-react` 图标（不引入新图标库）
- [ ] 颜色使用 `ThemeContext` 变量（不硬编码色值）
- [ ] CSS 中无 `!important`
- [ ] 路由使用 `React.lazy` + `Suspense` 懒加载

### 国际化验收

- [ ] `knowledge.cognition.*` 命名空间键在 `zh-CN` 和 `en` locale 文件中均存在
- [ ] 切换语言后 7 页文本同步更新

### 后端验收

- [ ] `V159` SQL 执行成功，`\d ecos_cognitive_model` 等 4 张表显示新增列
- [ ] `POST /diagnose/preflight` 返回结构化 JSON（data/metric/index 三项 pass/fail + 详情）
- [ ] 写操作（假设/信念/证据）触发 Kafka `ecos.audit` 消息（Kafka 消费者日志验证）
- [ ] ABAC 权限校验生效（无权限时返回 403）

### 代码质量

- [ ] 新页面文件 ≤ 300 行（超出必须拆分子组件）
- [ ] 共享组件在 `src/components/cognition/` 下，无重复实现
- [ ] 无 `any` 类型滥用，API 响应有 TypeScript interface
- [ ] 无 `console.log` / `debugger` 残留
- [ ] 新组件有必要的 JSDoc 注释

---

## 8. 不做什么（非目标）

| 排除项 | 理由 |
|:--|:--|
| **新增 Java 算法** | 既有 `CausalReasonerService` / `KnowledgeReasonerService` / `ScenarioSimulatorService` 满足需求，不在本 PRD 范围内 |
| **新增 MCP / Skill / Tool** | 认知工作台是纯前端 + 既有后端 API，不涉及 MCP 插件体系 |
| **新增 Docker 容器** | 复用既有 `cognitive-engine` 容器（:18086，与 kb-engine 共享端口），不新增部署单元 |
| **LLM 依赖** | `cognitive-engine` 不直连 LLM；如需 LLM 辅助，经 `llm-gateway` 服务调用 |
| **修改已有 DB 行** | 仅 `ADD COLUMN`（R9 只加不删），不 `UPDATE` 存量数据、不 `DROP` 任何列/表/索引 |
| **新增表** | 4 张 ADR-8/9 表已存在，本 PRD 仅补列；不新建表 |
| **修改 `kb_cognitive_pipeline`** | `kb_` 前缀属 legacy（R9 不 RENAME），保持原样 |
| **端口变更** | `cognitive-engine` 维持 :18086（与 kb-engine 共享），不改端口映射 |

---

## 9. 风险与缓解

| 风险 | 等级 | 缓解 |
|:--|:--:|:--|
| `App.tsx`（606 行）路由注册冲突 | 中 | A4 前先通读 App.tsx 路由结构，在 `/knowledge` 路由组下追加子路由，不改动既有路由 |
| 原有散落脚本（`CognitiveEngineView.tsx` 384 行等）与新页面功能重叠 | 中 | Sprint C 完成后，旧文件标记 `@deprecated` 注释，下一个 Wave 物理移除（R9 不删） |
| V159 ALTER 在已有数据量大的表上锁表 | 低 | `ALTER TABLE ADD COLUMN ... DEFAULT` 在 PG 16 中为 metadata-only 操作（不重写表），执行时间 < 1s |
| i18n 键遗漏导致页面显示 key 原文 | 低 | C4 完成后跑一遍 7 页中/英切换，终端日志检查 fallback 告警 |

---

## 10. 工作量估算

| Sprint | 任务数 | 预估工时 | 依赖 |
|:--|:--:|:--:|:--|
| **Sprint A**（P0） | 4 | 1~2 天 | 无前置依赖 |
| **Sprint B**（P1） | 6 | 3~5 天 | 依赖 Sprint A 完成 |
| **Sprint C**（P2） | 5 | 2~3 天 | 依赖 Sprint B 完成 |
| **合计** | **15** | **6~10 天** | — |

**关键路径**：A1 → A3/A4 → B1 → B2 → B3~B6 并行 → C1~C5 并行

---

> 版本: v1.0 | 2026-09-22 | 待 PMO 评审批准后进入 Sprint A
