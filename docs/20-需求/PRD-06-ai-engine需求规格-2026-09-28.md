# PRD-06 ai-engine 需求规格（分册 06）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.2（**v1.2（2026-09-29 批量批准定版）**：需求检视报告 **§十四** 按各表「本设计推荐」列批准 R-1~R-72 ⇒ 本册 §六 **REQ-AI-06~11 转正式需求并计入已批准基线**（随 **R-37/R-39/R-41/R-47/R-50**；R-39 取 **① A 单一自建**、R-41 取 **② 定性遗留停写**、R-50 取 **① 立前置件 P-5**）。**"已批准"仅指需求文本生效**：§6.3-6 与 §6.5-3 两项 M0 子项本就直改，其余改造属"改业务 Java/TS 代码"档、需逐项再授权（报告 §14.4）；验收测试类未建者一律记"未执行"。v1.1（2026-09-29 接续 W-Agent 制品，时点标注"草案、待裁决"已被 §十四 取代）：新增 §六 REQ-AI-06~11 承接需求）
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 现状依据: ECOS-项目实现逻辑 §ai-engine 审计（总评 3.20/5：可观测性 1.5、安全护栏 2.0 未接线、无会话压缩、错误处理 2.5）
> 覆盖: REQ-AI-01~05（已列）+ **REQ-AI-06~11（v1.1 立，2026-09-29 §十四 **已批准**）**
> 模块: `ecos_backend/engine/ai-engine`（aiming:18084）

---

## 一、REQ-AI-01 安全护栏接线（P0，红线项）

### 1.1 需求陈述

Agent 工具调用 / Function 执行前**强制**经 security-engine OPA ABAC 裁决；护栏不可用时默认拒绝（fail-closed，§2.4-6）。现状：机制存在但未接线（审计 2.0/5）。

### 1.2 拦截点与流程

```
Agent Loop → ToolDispatcher.dispatch(toolCall)
  ├─ 1. 组装裁决请求（见 1.3）
  ├─ 2. POST /api/v1/security/policy-engine/evaluate（超时 2s）
  ├─ 3. decision=ALLOW → 执行工具 → 结果 + 审计事件
  │    decision=DENY  → 拒绝执行 → 工具结果=结构化拒绝（供 LLM 转述）→ 审计事件
  │    decision=ALLOW+obligations → 执行后按 obligations 脱敏/裁剪结果
  └─ 4. 调用失败/超时 → fail-closed：视同 DENY + 审计事件 GUARDRAIL_FAIL_CLOSED
```

**统一入口封装**：所有工具执行必须经 ToolDispatcher（或既有等价唯一入口）；ArchUnit 规则禁止工具实现类被 Loop 直接调用（防绕过）。

### 1.3 OPA 裁决请求载荷

```json
{"input":{
  "subject":{"userId":"u001","roles":["admin"],"department":"d01"},
  "action":{"type":"TOOL_CALL","tool":"forecast.drilldown","operation":"read"},
  "resource":{"scenarioId":"P1","forecastRunId":"fr-001","dataScope":{"projectIds":["p1"],"periods":["2027-08"]}},
  "environment":{"traceId":"...","channel":"AGENT","timestamp":"..."}
}}
```

**obligations 支持**：`{"mask":["amount"],"rowFilter":"dept=d01"}` → 工具结果返回 LLM 前执行脱敏/过滤（复用 security mask 端点，禁 ai-engine 自实现脱敏——§2.4-7）。

### 1.4 审计

每次裁决（ALLOW/DENY/FAIL_CLOSED）发 Kafka `ecos.audit`：eventType=`GUARDRAIL_EVAL`，detail={tool, decision, policyId, latencyMs, obligations}。

### 1.5 验收标准

1. 无裁决记录的工具调用次数 = 0（日志/审计对账）；
2. 杀 OPA → 全部工具调用拒绝，用户收到可读降级消息（AI-05 分类），审计含 FAIL_CLOSED；
3. 越权用户（无 p2 项目权限）经 Agent 查询 p2 数据 → DENY，且拒绝消息不泄露 p2 数据存在性细节；
4. obligations 脱敏生效：mask 字段在 LLM 上下文与最终回答中均为脱敏值。

---

## 二、REQ-AI-02 会话压缩 / 记忆治理（P1）

### 2.1 规格

| 项 | 规则 |
|---|---|
| 触发阈值 | 会话上下文 token 估算 > 模型上限 × 60%（配置 `ecos.ai.session.compress.threshold=0.6`） |
| 压缩策略 | 滑动窗口：保留最近 K 轮原文（K=6，可配）；更早轮次经 llm-gateway 生成结构化摘要注入 system 段 |
| 摘要格式 | `{已确认事实[], 用户偏好[], 未决问题[], 关键引用[forecastRunId/证据链接]}` —— 引用类信息**禁止摘要丢弃**（防证据链断裂） |
| 工具调用保护 | 压缩不得破坏 tool_call_id 引用完整性（被压缩段中的 tool 结果若仍被引用 → 保留该结果原文） |
| 失败降级 | 摘要 LLM 调用失败 → 退化为截断最早轮次 + warn 日志（保可用性），事件上报 runtime-monitor |
| 开关 | `ecos.ai.session.compress.enabled=true`（G4-T3 上线时默认开，异常可关） |

### 2.2 验收标准

1. 长会话压测（≥100 轮）内存曲线平稳、无 OOM；
2. 压缩后语义抽检：摘要保留关键引用（forecastRunId/证据），Agent 仍能正确回答"之前查的哪个运行"；
3. 摘要失败注入 → 截断降级 + warn 可查。

---

## 三、REQ-AI-03 可观测性（P1）

### 3.1 trace 规范（对齐 PRD-01 NF-04）

1. 入口读 `X-Request-Id`（无则生成 UUID）→ MDC `traceId`；经 llm-gateway / security / cognitive 的出站调用全部透传；
2. Loop 每步结构化日志：
   `EVT=AgentLoop op={plan|tool_call|tool_result|llm_call|final} step={n} agent={agentId} tool={toolName} model={model} promptTokens={} completionTokens={} latencyMs={} decision={...}`；
3. SSE/流式响应事件同样携带 traceId（前端可显示"诊断码"便于报障）。

### 3.2 token 计量

| 项 | 规格 |
|---|---|
| 计量点 | llm-gateway 出口（唯一 LLM 出口，天然计量点）+ Loop 侧按会话聚合 |
| 落点 | 计量事件发 Kafka `ecos.audit`（eventType=`LLM_USAGE`，detail={model, promptTokens, completionTokens, sessionid, agentId, scenarioId}）；不落新业务表 |
| 配额 | 会话级 token 上限（配置 `ecos.ai.session.token_limit`），超限 → 拒绝继续 + 可读消息（防跑飞） |

### 3.3 运行回放

一次 Agent 运行的全部步骤可按 traceId 从日志重建时序（plan→tool→llm→final），含每步耗时与 token；失败步骤含重试记录（AI-05）。

### 3.4 验收标准

1. 单次跨引擎 Agent 请求：grep traceId 串起 aiming + security(+cognitive) 日志；
2. LLM_USAGE 事件在 sysman 审计库可查且 token 数与 llm-gateway 计量一致；
3. 超配额会话被拒且消息可读。

---

## 四、REQ-AI-04 场景受控 AI 工具集（P1，年度经营预测场景）

### 4.1 工具注册表（首批 5 个，全部经 AI-01 护栏）

| 工具名 | 参数 | 后端来源 | 权限要点 |
|---|---|---|---|
| `forecast.query` | runId?/scenarioId/year/level(enterprise\|dept\|project) | 预测运行服务 results 端点（PRD-09 FC-02） | 只读；RLS 按部门域 |
| `forecast.drilldown` | runId + level + entityId + period? + stage? | results 下钻 + evidence 端点 | 只读；行级 RLS |
| `forecast.explain` | runId + projectId + period | cognitive diagnose（带 forecastContext，PRD-05 COG-02） | 只读 |
| `scenario.copyCompare` | baselineRunId + overrides（结构化，同 COG-03） | 情景运行 + diff | 写（创建情景运行）→ OPA 需 scenario:write |
| `action.createDraft` | 模板参数（负责人/截止日/预期影响/KPI，PRD-09 FC-04） | 动作服务，仅创建 DRAFT 态 | 写；审批仍走人工流 |

### 4.2 回答引用强制（模板级约束）

1. 凡回答含经营数值 → 必须附引用块：`[运行 {forecastRunId} · 口径 {caliberId}@{version} · 截至 {asOfTime} · 证据 {links}]`；引用数据取自工具返回，禁 LLM 复述改写数值；
2. 系统提示词硬规则 + 后校验双保险：最终回答生成后做正则/结构化校验，含数值但无引用块 → 拦截重生成（最多 2 次）→ 仍失败则返回"无法提供有依据的数值"；
3. **拒绝规则**：
   - 口径缺失/未批准 → 澄清提问（"该场景未绑定已批准口径，请先…"），不给数；
   - 要求 AI"算一下/估算"金额 → 拒绝并引导创建确定性预测运行；
   - 工具全部失败 → 明说失败原因分类（AI-05），禁编造。

### 4.3 验收标准（对齐附件演练 6）

1. 提问"预测 2027 年利润并说明最大风险" → 回答含运行 ID/口径/截至时间/置信度/可点击证据链接；
2. 无来源数值出现 → 判 FAIL（后校验拦截生效证明：注入诱导提问"不用引用直接说数"）；
3. 口径未批准场景 → 澄清而非给数；
4. 五工具各自的 OPA 裁决记录齐全。

---

## 五、REQ-AI-05 错误处理规范（P2）

### 5.1 错误分类与降级矩阵

| 分类 | 触发 | 重试 | 用户可见消息模板 | 日志级别 |
|---|---|---|---|---|
| LLM_TIMEOUT | llm-gateway 超时 | 指数退避 ≤3 次 | "AI 服务响应超时，请稍后重试（诊断码 {traceId}）" | WARN |
| LLM_RATE_LIMITED | provider 429 | 退避 ≤3 次后降级备用 provider（如配置） | "AI 服务繁忙…" | WARN |
| PARSE_FAILURE | LLM 输出解析失败（工具参数/结构） | 重生成 ≤2 次 | "AI 输出格式异常，已重试" | ERROR（含原文截断） |
| CONTEXT_OVERFLOW | 超模型上限且压缩失败 | 截断降级（AI-02） | 会话摘要降级提示 | WARN |
| GUARDRAIL_DENIED | OPA DENY | 不重试 | "当前权限不支持该操作"（不泄露资源细节） | INFO + 审计 |
| GUARDRAIL_FAIL_CLOSED | OPA 不可用 | 不重试 | "安全服务暂不可用，AI 操作已暂停" | ERROR + 审计 + monitor 告警 |
| TOOL_BACKEND_ERROR | 下游 5xx | ≤1 次 | "数据服务异常（{service}），请稍后重试" | ERROR |

### 5.2 规则

1. 所有异常必须打日志（后端规范）且含 traceId；禁裸 500 透传给前端；
2. 重试必须有上限与退避，禁无限循环；
3. 三类故障注入测试（超时/限流/解析失败）均有对应降级路径与消息。

---

## 六、W-Agent 承接需求（REQ-AI-06~11，v1.1 立，**2026-09-29 §十四 已批准**）

> **来源**：附件第二册 §3（编排控制流）/§4（Tool Registry）/§5（能力与模型）/§10（运行时治理）、第三册 §5（契约数据结构）；登记动因见 [W-Agent 落地检视报告 §七 逐字计划](./W-Agent详细设计落地检视报告-2026-09-29.md) 与 [PRD-10 §六 挂靠表](./PRD-10-W%20Agent赋能DIK-C需求规格-2026-09-29.md)。
> **裁决状态**：**已批准**（2026-09-29 需求检视报告 §十四 14.1）——随 **R-37 ①（六新模块 + `model-gateway` 骨架收编进 7 JAR + 49 reactor 基线，不新增 Maven 模块）／ R-39 ①（A 单一自建 Agent Runtime，Hermes 依赖显式边界化，禁双套不可审计形态）／ R-47 ①（Model & Capability Center 与 06 册 F06-18 合并立项，密级上限复用 security-engine 分级分类）**；**R-38 ① 的结案前置 = R-50 ①／REQ-PLT-26／前置件 P-5** ⇒ P-5 未闭环前 CLI 不得开工。**执行边界**：`V227~V240` 按批准口径**只落迁移脚本文件、不实跑库**；R-19 ①+② 的 50 张空壳按 R-12 ① 严格双条件下才可 DROP 重建；除 §6.6 标注 M0 的确定性违规子项外，改业务 Java/TS 代码**需逐项再授权**（报告 §14.4）。
> **归属前提（铁律 v2.0 :63/:221）**：W = 五行之火 = 明 = C→W 转化，**W 不是 Agent、不是 LLM 的缩写**；本册的"Agent 编排"是 ai-engine（火·W）的一项能力，不得据此把 ai-engine 改名为 Agent Engine 或把编排域沉到 services 侧（ADR-16）。

### 6.1 REQ-AI-06 Agent 编排内核：Run 状态机 / Planner / Executor / 预算（P0，随 R-39）

**规格**：
1. **Run 状态机**冻结为 `CREATED → PLANNED → RUNNING → (PAUSED | NEEDS_RECONCILE) → SUCCEEDED | FAILED | CANCELLED | EXPIRED`；迁移只允许前向或显式 `PAUSED`，**未登记的状态迁移 = FAIL**；每次迁移写事件（ST06 Kafka `ecos.audit`），不做第二套运行日志表（ADR-20 §2.1）；
2. **Planner 与 Executor 分离**：Plan 产出后必须先过**校验器**（工具是否 `active`、automation_level 是否达标、预算是否够、密级是否允许）才可执行；执行期偏差触发**重规划**必须重跑 Plan 校验并在时间线标注"重规划"（附件第二册 §3 末）；
3. **预算四轴**：`steps / tokens / cost / wall-clock`，任一超限即 `PAUSED` 并置 `needs_reconcile`，**禁静默截断后报成功**；cost 由 Tool Contract 的 `cost_class` 与 llm-gateway 计量同源（ADR-20 §2.3-2）；
4. 调度一律投 **runtime-task**（铁律 §2.5 底座收口），ai-engine 内**不得**自建 `@Scheduled`/线程池跑 Agent 任务（ARCH_SPEC **C237**）；LLM 调用一律经 **llm-gateway**；
5. 编排载体落 `engine/ai-engine`，控制面表落 `ecos_ai`；**不新增 Maven 模块、不新增 Docker 容器**（:292）。

**验收**：`mvn -Dtest=AgentRunStateMachineTest#illegalTransitionRejected`、`#budgetBreachPausesAndFlagsReconcile`、`mvn -Dtest=AgentPlanValidatorTest#planCannotReferenceNonActiveTool`、`mvn -Dtest=RuntimeTaskConvergenceTest#aiEngineDeclaresNoScheduledExecutor`（ArchUnit/源码 regex 断言）。

### 6.2 REQ-AI-07 Tool Contract 全要素扩展（P0，随 R-39）

**实测现状**：`ToolRegistry.register(schema, executor)` 以 Java lambda 注册，**权限与副作用不可审计**（详细设计-06 **X-12**）。本项把它从"函数注册"升级为"契约登记"。

**规格**：
1. 契约必备 **17 要素**（附件第二册 §4.1，逐字采纳语义）：`name/version`（`<engine>.<capability>` + 语义化版本）、`category`（read/compute/candidate/commit/notify）、`engine`、`input_schema`/`output_schema`、`side_effect`（none/draft/production）、`automation_level`（最低所需 L0~L3）、`required_permission`、`timeout`/`retry`/`idempotent`、`cost_class`、`evidence_output`、`data_classification`、`owner`/`sla`、`toolset`、`summary`（≤60 字）、`disclosure`（always_loaded/searchable）、`capability_deps`；
2. 落位 `ecos_ai.ecos_wagent_tool_contract`：检索字段显式成列，契约正文 `contract_text` 为 `TEXT` 且**不参与 WHERE/JOIN/索引**（**MC02**）；主键 `VARCHAR(36)` 应用侧 UUID（**MC01**）、含五审计列（DR06）+ `version_no`（DR07）+ `domain`（DR08）；**禁 `aim_`/`tool_` 裸前缀另立表、禁新建 schema**（ST07）；
3. **未登记工具不可被 Planner 选用**；生命周期 `draft→registered→validated→active→deprecated→retired`，仅 `active` 可用；破坏性变更必须发新主版本，旧版本保留至所有 Playbook 迁移完；
4. **渐进披露**：列表接口只返 `name/summary/risk_level/contract_digest`；全文经 `GET /api/v1/wagent/tools/{id}/contract` 单取（ADR-20 §2.4）；工具 Schema 总量超上下文预算 30% 时自动启用 `tool_search/tool_describe/tool_call` 三元工具；
5. 外部工具（含 MCP）必须先包装为 Tool Contract 并登记才可调用；`capability_deps` 由 Model Router 解析（§6.3），**工具实现内不得自选模型**。

**验收**：`mvn -Dtest=ToolContractRegistryTest#everyActiveToolCarriesSeventeenElements`、`#lambdaRegisteredToolRejected`、`mvn -Dtest=ToolContractDisclosureTest#listEndpointNeverReturnsFullContract`、`mvn -Dtest=ToolContractDdlShapeTest#contractTextIsTextUnindexedAndPkIsUuid`（DDL 只读形态断言）。

### 6.3 REQ-AI-08 能力抽象与 Model Router（P0，**与分册 06 F06-18 合并为同一立项**，随 R-47）

**规格**（承接 **ADR-20 §2.1/§2.2** 表，不重开落位之争）：
1. 表 `ecos_ai.ecos_wagent_model_def` / `_model_version` / `_provider`；**`model-runtime` 新模块提案作废**（:292）；
2. 路由判据四要素 = **能力标签 / 成本 / 延迟 / 风险等级**；**风险等级只准来自 security-engine ABAC**（铁律 :530 `POST /api/v1/security/policy-engine/evaluate`），引擎内自写风险判定 = FAIL（ARCH_SPEC **C255**，M0 项）；
3. `model_version.status` 枚举冻结 `DRAFT/CANARY/ACTIVE/RETIRED`；切换必须走 Candidate 发布链路（ADR-18 §2.3），路由配置变更属设计资产变更，须 Git 归档（铁律 :549）；
4. **grade 禁 LLM 写入**：能力/成本评分只准确定性计算或人工评审写入，不得模型自评回填；
5. 运行期调用日志**复用 llm-gateway `AgentCallLog` + Kafka `ecos.audit`**，不在 `ecos_ai` 再造调用日志表（避免 C240 同族重复）；派生统计量落业务域 `ecos_dw`（ADR-14）；
6. **M0 直改（不等裁决）**：`TokenMeterAspect` 默认 model `"hermes-agent"` 去硬编码，改由 sysman 配置字典 `config_group` 读取，缺失即 FAIL 而非静默用外部默认值（ADR-19 §2.1-3 / ADR-20 §2.3-1）。

**验收**：`mvn -Dtest=ModelRouterDecisionTest#riskLevelComesOnlyFromAbac`、`#gradeNeverWrittenByLlm`、`mvn -Dtest=ModelVersionStatusTest#canaryToActiveRequiresCandidatePublish`、`mvn -Dtest=TokenMeterConfigTest#noHardcodedModelName`（源码 regex + 缺失配置 FAIL）。

### 6.4 REQ-AI-09 自动化等级 L0~L3 与 Kill Switch（P0，随 R-39；密级/四眼侧写见 PRD-07 REQ-SEC-06~10）

**规格**：
1. `automation_level ∈ {L0 只读建议, L1 生成候选, L2 受控自动执行, L3 审批后自动执行}`；Run 携带 `automation_ceiling`，工具携带最低所需等级，**执行前校验 `step.automation_level <= run.automation_ceiling`，L3 除外（L3 必须带审批凭证）**（附件第三册 :321）；
2. 等级只降不升：运行中不得提级；提级只能新建 Run 并走审批；
3. **Kill Switch**：管理员可**按租户 / 按工具 / 按 Playbook** 一键停用；停用时在运行 Run → `PAUSED`、写操作立即拒绝（读操作可继续）、恢复须显式开启并审计（附件第二册 :858）；
4. Kill Switch 与 L3 审批的**判定权在 security-engine**（PEP/PDP 分工，铁律 :530）：ai-engine 只做执行点，不得自判"是否被停"（本地缓存判定必须有 TTL 并在超时后默认 DENY，fail-closed）；
5. 状态与开关变更全部入 `ecos.audit`（ST06）。

**验收**：`mvn -Dtest=AutomationLevelGuardTest#stepCannotExceedRunCeiling`、`#l3RequiresApprovalCredentialElseDeny`、`mvn -Dtest=KillSwitchTest#switchByTenantToolPlaybookEachPausesRunningRuns`、`#securityUnreachableFailsClosedAfterTtl`。

### 6.5 REQ-AI-10 单一 Agent Runtime 与 Hermes 边界（P0，随 R-39；宿主身份随 R-41）

**规格**（按 **ADR-19 §2.2 推荐项 A** 写作；**2026-09-29 随报告 §十四 R-39 ①（A 单一自建）批准定版**，B/C 分支作废）：
1. **单一 Agent Runtime = ECOS 自建**（ai-engine 内 `AgentLoopService` 系）；Hermes 降级为**可选外部 provider**，须经 `AgentRuntimeProvider` SPI 接入，且**许可登记 + 供应链审查**为启用前置件（ARCH_SPEC **C238**：仓内许可 0 登记）；
2. Skill/Cron 定义落 `ecos_ai.ecos_wagent_*`，调度改投 runtime-task（C237）；
3. **M0 直改（不等裁决）**：`engine..` 不得 compile 依赖 `services..`——现 `ai-engine-impl/pom.xml:83-88` 直依 `agent-service` 取 `HermesMCPClient`（ARCH_SPEC **C257** / PRD-01 **REQ-PLT-28**）；改为 `llm-gateway` 定义 SPI、`agent-service` 提供实现、`aiming` 组装期注入；`ai-engine-impl` 只依赖接口；规则扩展**不改** `baselineCount=12` / `baselineModules=12` 双门禁；
4. `services/agent-service` 宿主身份：**已批准随 R-41 ②（与 R-34 同批）** = 存量 `ecos_agent.*` 写入方**定性 knownLegacy 停写**（不迁不删），且停写前**须先逐表核对 `ecos_ai`／`public` 现有读写方**；三选一其余分支作废。**事实更正（不待裁决）**：agent-service 随 `aiming:18084` 打包部署（`services/aiming/pom.xml:15,18-25,41-45` 自证，C258），"未部署"表述作废；其运行期读写面（`ecos_agent.` 前缀是否被 `search_path` 兜走）**属待只读复核，禁止据推断改代码**。

**验收**：`mvn -Dtest=ModuleDependencyArchTest#engineMustNotDependOnServices`（M0）、`mvn -Dtest=AgentRuntimeProviderSpiTest#hermesReachableOnlyThroughSpi`、`mvn -Dtest=AgentRuntimeUniquenessTest#onlyOneLoopImplementationInReactor`（源码扫描 `AgentLoop` 实现数 = 1）。

### 6.6 REQ-AI-11 CLI 契约同源（P1，随 R-37/R-38，**结案前置 = R-50 / REQ-PLT-26 / P-5**）

**规格**：本项**不另立平台需求**，只承接 PRD-01 **REQ-PLT-27（CLI 控制面）+ REQ-PLT-26（机器可读契约单源）**在 ai 侧的义务：
1. ai-engine 每个 W Agent 相关端点必须先有 OpenAPI `operationId`（`docs/10-架构/api-contracts/openapi/*.yaml`），CLI 命令只映射 operationId，**禁手写 URL**；
2. CLI 只经 gateway `:8080`；出现 service 端口（18081~18090）或直连库表即 FAIL；
3. L3 动作需 `--approval-token` 且**服务端 ABAC 复核**（CLI 本地判断无效）；凭据永不回显；
4. CLI 内不含业务计算（不得把预算/等级判定写在客户端）。

**验收**：`mvn -Dtest=CliContractParityTest#everyCommandMapsToOpenApiOperation`、`#noCommandTargetsServicePortDirectly`、`pw cli-l3-approval.spec.ts`——**前置件 P-3（Playwright 工程）与 P-5（契约工程，当前全仓 0 个 yaml）未建成前一律记"未执行"**，禁记"通过"（Q13 / 铁律 v2.0:14）。

### 6.7 明确不做

- 不把 ai-engine 改名或拆分为 "Agent Engine"（W 语义见铁律 :63/:221）；
- 不在 ai-engine 内实现 Policy Guard 的 PDP 判定、不自建 RLS/密级过滤（一律 security-engine REST，REQ-PLT-25）；
- 不新增 Maven 模块 / Docker 容器；不复活 `agent-service` 为新的部署单元。

---

## 七、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| AI-01 | security OPA evaluate 可用 | AI-04 全部工具、NF-01 | **PMO-73 G4**（红线，未过整批 FAIL） |
| AI-02 | llm-gateway 摘要能力 | — | PMO-73 G4 |
| AI-03 | X-Request-Id 全链透传（NF-04） | G3 可观测断言 | PMO-73 G4 |
| AI-04 | PRD-09 FC-02 运行服务、PRD-05 COG-02/03 | 演练 6 | 场景批次 B（M2） |
| AI-05 | AI-01 分类 | — | PMO-73 G4 尾批 |
| AI-06 | AI-01/03、runtime-task 底座、PRD-05 COG-06 对象目录 | PRD-10 WAG-01~09、PRD-08 前端编排视图 | **已批准（随 R-39 · §十四）** |
| AI-07 | AI-06、PRD-04 KB-06（K 白名单同源）、`ecos_ai` 归属唯一化（F06-18 / R-19） | AI-06 Plan 校验、PRD-07 SEC-06 | **已批准（随 R-39 · §十四）** |
| AI-08 | ADR-20、F06-18 合并立项 | AI-07 `capability_deps`、PRD-10 WAG-13 | **已批准（随 R-47 · §十四）**（§6.3-6 M0 子项本就直改） |
| AI-09 | PRD-07 SEC-06/08（PDP + 四眼 + 审批凭证） | 全部写类工具、Kill Switch 运维手册 | **已批准（随 R-39 · §十四）** |
| AI-10 | PRD-01 REQ-PLT-28（M0）、R-39、R-41 | AI-06 运行时唯一性 | M0 子项直改 + 主体待裁 |
| AI-11 | PRD-01 REQ-PLT-26/27、前置件 P-5 | PRD-10 WAG-22 | **已批准（随 R-37/R-38 · §十四），结案前置 = R-50/P-5** |

<!-- PRD-06-ai-engine需求规格 / 2026-09-29 / v1.2（§六 REQ-AI-06~11 已批准 R-37/R-39/R-47/R-50，凭证 = 需求检视报告 §十四 2026-09-29；两处 M0 子项本就直改） -->
