# PRD-06 ai-engine 需求规格（分册 06）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 现状依据: ECOS-项目实现逻辑 §ai-engine 审计（总评 3.20/5：可观测性 1.5、安全护栏 2.0 未接线、无会话压缩、错误处理 2.5）
> 覆盖: REQ-AI-01~05
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

## 六、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| AI-01 | security OPA evaluate 可用 | AI-04 全部工具、NF-01 | **PMO-73 G4**（红线，未过整批 FAIL） |
| AI-02 | llm-gateway 摘要能力 | — | PMO-73 G4 |
| AI-03 | X-Request-Id 全链透传（NF-04） | G3 可观测断言 | PMO-73 G4 |
| AI-04 | PRD-09 FC-02 运行服务、PRD-05 COG-02/03 | 演练 6 | 场景批次 B（M2） |
| AI-05 | AI-01 分类 | — | PMO-73 G4 尾批 |

<!-- PRD-06-ai-engine需求规格 / 2026-09-28 / v1.0 -->
