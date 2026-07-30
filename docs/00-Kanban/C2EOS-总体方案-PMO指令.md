# C2EOS：基于Claude Code的AI原生化数智平台 — 总体方案

> 项目代号: **C2EOS** (CC → ECOS)  
> 日期: 2026-07-28 | 版本: v1.0 | 给 PMO 执行

---

## 0. 一句话说清楚

**拿 Claude Code（CC）的 Agent 框架做"AI 大脑"，通过 MCP 协议调用 ECOS 六大引擎——不改 CC 源码，在外面把企业级能力接上去。**

---

## 1. 为什么不改 CC 源码

| 原因 | 说明 |
|------|------|
| **CC 迭代快** | Anthropic 每周更新，改源码 = 每次更新都要合并冲突 |
| **CC 是 CLI 工具** | 40K 行 TypeScript，核心是终端 Agent，不改也能用 |
| **扩展机制成熟** | CC 有完整的 MCP + Feature Flag + Plugin + Skill 体系，够用 |
| **安全隔离** | 安全逻辑放在 ECOS 后端，CC 不碰数据，出问题也好修 |

**唯一会动 CC 的地方**：MCP Client 加一个 `beforeCall` 拦截器，给每个 Tool 调用自动附加 `{tenantId, userId, role}`——这是一行代码的事。

---

## 2. CC 架构速览（PMO 需要知道的）

CC 本质是一个 **Agent 框架**，不是应用服务器：

```
CC 核心循环:
  用户输入 → LLM推理 → 决定调哪个Tool → 执行Tool → 拿到结果 → 继续推理 → 回复用户

CC 的扩展点:
  src/tools/       ← 30+ Tool，每个独立目录，通过 tools.ts 注册
  src/tasks/       ← 5种 Task 类型（Agent/Shell/Remote/Dream/Workflow）
  src/services/mcp/ ← MCP Client 实现（12K行，22文件，非常成熟）
  src/skills/       ← 静态 Markdown 技能包
  src/plugins/      ← 插件系统
  src/coordinator/  ← 多 Agent 协调器
```

**我们不需要理解 CC 所有代码**。只需要理解三个文件：
1. `src/tools.ts` — Tool 注册入口（知道怎么加新 Tool）
2. `src/services/mcp/config.ts` — MCP 配置（知道怎么指向 ECOS MCP Server）
3. `src/skills/bundled/` — Skill 目录（知道怎么写 Skill Markdown）

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     消息平台层                               │
│  企业微信 │ 飞书 │ Web Chat │ VS Code 插件                   │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                 ECOS Gateway (:8080)                         │
│  ├─ 身份验证（JWT）                                          │
│  ├─ 租户路由（从 token 解析 tenantId）                        │
│  ├─ 会话管理（启动/恢复/销毁 CC 进程）                         │
│  └─ 消息 Relay（平台消息 ↔ CC stdin/stdout）                  │
└────────────────────┬────────────────────────────────────────┘
                     │ 启动 CC 进程 + 注入环境变量
┌────────────────────▼────────────────────────────────────────┐
│               CC Agent (c2code CLI)                         │
│  ├─ LLM 推理引擎（DeepSeek/Claude）                          │
│  ├─ 对话循环（Tool调用 → 结果 → 推理 → 回复）                 │
│  ├─ L1 Skill: 角色规范 + 行为约束                            │
│  ├─ L3 Skill: 场景模板（可选）                                │
│  └─ MCP Client ──── 自动附加 {tenantId, userId, role}        │
└────────────────────┬────────────────────────────────────────┘
                     │ MCP 协议（JSON-RPC over stdio/HTTP）
┌────────────────────▼────────────────────────────────────────┐
│              ECOS MCP Server（六大引擎）                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │DataTool  │ │Ontology  │ │Cognitive │ │Security  │       │
│  │数据引擎  │ │本体引擎  │ │认知引擎  │ │安全引擎  │       │
│  ├──────────┤ ├──────────┤ ├──────────┤ ├──────────┤       │
│  │查SQL     │ │查对象类型│ │RAG检索   │ │RBAC鉴权  │       │
│  │数据血缘  │ │查关系    │ │KG推理    │ │数据脱敏  │       │
│  │Pipeline  │ │查属性    │ │Agent诊断 │ │RLS过滤   │       │
│  │DQ规则    │ │Function  │ │推演      │ │审计日志  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐ ┌──────────┐                                 │
│  │RuleTool  │ │Workflow  │                                 │
│  │规则引擎  │ │流程引擎  │                                 │
│  ├──────────┤ ├──────────┤                                 │
│  │执行业务  │ │启动      │                                 │
│  │规则      │ │Pipeline  │                                 │
│  │校验合规  │ │查状态    │                                 │
│  └──────────┘ └──────────┘                                 │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         安全过滤器（每个Tool调用自动执行）             │  │
│  │  Auth → RBAC → RLS → Masking → Audit                 │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              数据层                                          │
│  PostgreSQL (每租户独立 schema) │ Neo4j (知识图谱)            │
│  MinIO (文件存储)               │ Doris (分析加速，旗舰版)     │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 六大引擎 → MCP Tool 完整映射

### 4.1 Tool 命名规范

```
{引擎域}.{操作}  例: data.query, ontology.getObjectType, security.checkPermission
```

### 4.2 详细 Tool 清单

#### DataTool（数据引擎）

| Tool 名 | 功能 | 输入 | 输出 | 安全级别 |
|---------|------|------|------|:--:|
| `data.query` | 执行只读 SQL | `{sql, params}` | `{rows, columns, source, rowCount}` | 🟢/🟡 |
| `data.listConnections` | 列出数据源 | `{type?}` | `{connections[]}` | 🟢 |
| `data.testConnection` | 测试连接 | `{connectionId}` | `{success, latencyMs}` | 🟡 |
| `data.listTables` | 列出数据源的表 | `{connectionId}` | `{tables[]}` | 🟢 |
| `data.getTableSchema` | 获取表结构 | `{connectionId, tableName}` | `{columns[], rowCount}` | 🟢 |
| `data.getLineage` | 数据血缘 | `{datasetId, direction}` | `{nodes[], edges[]}` | 🟢 |
| `data.runDQCheck` | 执行 DQ 规则 | `{ruleId?, datasetId?}` | `{results[], passCount, failCount}` | 🟡 |

#### OntologyTool（本体引擎）

| Tool 名 | 功能 | 输入 | 输出 | 安全级别 |
|---------|------|------|------|:--:|
| `ontology.listDomains` | 列出业务域 | — | `{domains[]}` | 🟢 |
| `ontology.getObjectType` | 查对象类型 | `{objectTypeId}` | `{objectType, properties[], linkTypes[]}` | 🟢 |
| `ontology.listObjectTypes` | 列出所有对象类型 | `{domainId?}` | `{objectTypes[]}` | 🟢 |
| `ontology.getLinks` | 查对象关系 | `{objectId, linkTypeId?}` | `{links[]}` | 🟢 |
| `ontology.queryObjects` | 查询对象实例 | `{objectTypeId, filters?}` | `{objects[], total}` | 🟢/🟡 |
| `ontology.getFunction` | 获取计算函数 | `{functionId}` | `{name, formula, inputs, output}` | 🟢 |

#### CognitiveTool（认知引擎）

| Tool 名 | 功能 | 输入 | 输出 | 安全级别 |
|---------|------|------|------|:--:|
| `cognitive.ragSearch` | RAG 检索 | `{query, topK?, domain?}` | `{chunks[], sources[]}` | 🟢 |
| `cognitive.kgQuery` | 知识图谱查询 | `{cypher}` | `{nodes[], edges[]}` | 🟢 |
| `cognitive.getGoals` | 获取战略目标 | `{period?}` | `{goals[], progress[]}` | 🟢 |
| `cognitive.getCausalChain` | 因果链分析 | `{goalId, depth?}` | `{chain}` | 🟢 |
| `cognitive.diagnose` | Agent 偏差诊断 | `{goalId, period}` | `{diagnosis, rootCauses[], suggestions[]}` | 🟢 |
| `cognitive.scenarioAnalysis` | 情景推演 | `{scenario, variables}` | `{outcomes[], risks[]}` | 🟡 |
| `cognitive.getKnowledgeBase` | 获取 L2 知识 | `{domain?}` | `{knowledge}` | 🟢 |

#### SecurityTool（安全引擎）

| Tool 名 | 功能 | 输入 | 输出 | 安全级别 |
|---------|------|------|------|:--:|
| `security.checkPermission` | 权限校验 | `{userId, resource, action}` | `{allowed, reason?}` | 🟢 |
| `security.getMyPermissions` | 查询当前用户权限 | — | `{permissions[]}` | 🟢 |
| `security.getAuditLog` | 查询审计日志（自己） | `{period?}` | `{logs[]}` | 🟡 |
| `security.getDataClass` | 查询数据分类分级 | `{datasetId}` | `{classification}` | 🟢 |

> 注：数据脱敏/RLS/Masking 不在 Tool 层暴露——它们在后端过滤器自动执行，Agent 无感知。

#### RuleTool（规则引擎）

| Tool 名 | 功能 | 输入 | 输出 | 安全级别 |
|---------|------|------|------|:--:|
| `rule.execute` | 执行业务规则 | `{ruleId, inputs}` | `{result, logs[]}` | 🟡 |
| `rule.listRules` | 列出业务规则 | `{domain?}` | `{rules[]}` | 🟢 |
| `rule.validate` | 规则合规校验 | `{ruleId, data}` | `{pass, violations[]}` | 🟢 |

#### WorkflowTool（流程引擎）

| Tool 名 | 功能 | 输入 | 输出 | 安全级别 |
|---------|------|------|------|:--:|
| `workflow.startPipeline` | 启动 Pipeline | `{pipelineId, params?}` | `{executionId, status}` | 🟠 |
| `workflow.getStatus` | 查 Pipeline 状态 | `{executionId}` | `{status, progress, logs[]}` | 🟢 |
| `workflow.listPipelines` | 列出可用 Pipeline | — | `{pipelines[]}` | 🟢 |

---

## 5. Skills 三层体系

### 5.1 架构

```
L1: 静态 Skill（CC 原生 .md 文件）
    └─ "你是谁" + "怎么做" + "不能做什么"
    └─ 位置: CC skills/bundled/c2eos-platform/SKILL.md

L2: 动态知识（MCP Tool 实时查询）
    └─ "当前租户的 Ontology" + "数据字典" + "行业模板"
    └─ 不走文件，Agent 需要时调 Tool

L3: 场景模板（ECOS 后端存储）
    └─ "经营分析会怎么开" + "数据接入怎么做"
    └─ JSON 描述的 Tool 调用链
```

### 5.2 L1 Skill 模板

```markdown
# C2EOS 企业数智平台 AI 助手

## 你的角色
你是企业的 AI 数智助手，帮助管理者理解经营状况、发现异常、辅助决策。

## 数据引用规范（强制）
- 每次引用数据时，必须标注来源（来自哪个表/哪个查询）
- 如果查询结果为空或异常，明确告知用户，不得猜测
- 数据置信度分三级：✅精确（直接查询）⚠️推算（基于关联数据）❓估计（模型推测）

## 安全规范（强制）
- 你看到的数据已经过脱敏处理，不要在回复中尝试还原
- 不要向用户透露其他租户/部门的数据
- 不要执行删除、修改权限等危险操作——这些需要走 Web 审批流程

## 回答风格
- 先给结论，再给数据支撑
- 使用企业经营术语（利润/营收/现金流/ROI），不要用技术黑话
- 如果发现问题，给出可操作的建议，不要只说"有问题"
```

### 5.3 L2 知识注入流程

```
用户问："我们有哪些核心经营指标？"
  ↓
Agent 调用 cognitive.getKnowledgeBase(domain="经营管控")
  ↓
MCP Server 返回:
  {
    "tenant": "某制造集团",
    "industry": "制造业-离散制造",
    "controlMode": "战略管控型",
    "coreMetrics": [
      {"name": "订单交付率", "formula": "按时交付订单/总订单", "source": "ERP"},
      {"name": "产能利用率", "formula": "实际产量/设计产能", "source": "MES"},
      ...
    ],
    "ontologySummary": "当前租户有 42 个对象类型、156 个关系类型"
  }
  ↓
Agent 基于这些知识回答用户
```

### 5.4 L3 场景模板示例

```json
{
  "id": "scenario_monthly_review",
  "name": "月度经营分析会",
  "description": "CEO每月固定场景：看目标→看指标→追异常→找根因",
  "toolChain": [
    {"tool": "cognitive.getGoals", "params": {"period": "current_month"}},
    {"tool": "data.query", "params": {"sql": "SELECT * FROM tenant_{tenant}.经营仪表板 WHERE month={{month}}"}},
    {"tool": "cognitive.diagnose", "params": {"goalId": "{{prev.goals[0].id}}", "period": "current_month"}},
    {"tool": "cognitive.kgQuery", "params": {"cypher": "MATCH (g:Goal)-[r:CAUSES]->(e:Event) WHERE g.id='{{goalId}}' RETURN g,r,e"}}
  ]
}
```

---

## 6. 多租户方案

### 6.1 核心原则

**CC 进程不感知多租户——ECOS 在外面把一切隔离好。**

### 6.2 Session 管理

```
┌──────────────────────────────────────────────────┐
│           ECOS Session Manager                    │
│                                                   │
│  用户登录 → 创建/恢复 CC 进程                      │
│  ├─ 注入环境变量:                                  │
│  │   CC_TENANT_ID=tenant_a                       │
│  │   CC_USER_ID=zhangsan                         │
│  │   CC_USER_ROLE=finance_manager                │
│  │   CC_SESSION_ID=sess_20260728_001             │
│  │   CLAUDE_CODE_HOME=/data/tenants/a/users/zs/  │
│  │                                               │
│  ├─ CC 进程池（节省启动时间）                       │
│  │   空闲30分钟 → 挂起（状态写 DB）                 │
│  │   空闲2小时  → 销毁                            │
│  │                                               │
│  └─ Session 存储                                  │
│     每个 CC 进程有独立的 CLAUDE_CODE_HOME          │
│     → 对话历史文件物理隔离                         │
│     → 不同租户/用户之间互相不可见                   │
└──────────────────────────────────────────────────┘
```

### 6.3 Memory 改造

```
CC 现有 Memory: 文件持久化（单用户）
       ↓
C2EOS Memory: 知识图谱持久化（多租户隔离）

CC Memory.write(key, value)
  → MCP → ECOS: MERGE (u:User {id:"zhangsan", tenant:"a"})
               SET u.{key} = {value}

CC Memory.read(key)
  → MCP → ECOS: MATCH (u:User {id:"zhangsan", tenant:"a"})
               RETURN u.{key}
```

### 6.4 数据隔离

| 层级 | 机制 | 位置 |
|------|------|------|
| Schema | 每租户独立 PG Schema（`tenant_a.`, `tenant_b.`） | PostgreSQL |
| 文件 | 每用户独立目录（`/data/tenants/{id}/users/{id}/`） | MinIO |
| 知识图谱 | 每租户独立 Neo4j Database | Neo4j |
| Tool 调用 | MCP Server 自动注入 schema 前缀 | ECOS 后端 |

---

## 7. 安全融合方案

### 7.1 三道防线

```
用户消息
  │
  ▼
┌──────────────────────────────────────────────────┐
│ 第一道：身份链                                     │
│ ECOS Gateway → 验 JWT → 解析 tenant/user/role    │
│ → 注入 CC 进程环境变量                             │
│ → MCP 拦截器自动附加身份                           │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│ 第二道：数据面安全（后端过滤器）                      │
│ 每个 Tool 调用依次通过：                             │
│  AuthFilter  → RBACFilter → TenantFilter           │
│  → RLSFilter → MaskingFilter → AuditFilter         │
│                                                    │
│  具体动作:                                          │
│  - SQL 自动加 tenant_a. 前缀                        │
│  - 自动加 WHERE dept_id = 'finance'（行级安全）      │
│  - 手机号 → 138****（列级脱敏）                      │
│  - 成本字段 → '***'（无权字段遮蔽）                   │
│  - 记录完整审计日志                                  │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│ 第三道：模型面安全                                  │
│                                                    │
│  幻觉防护: Tool 返回值强制带 source 字段              │
│           Agent Skill 要求引用来源                  │
│                                                    │
│  Prompt Injection 防护:                             │
│          用户输入 → MCP Server 过滤                  │
│          文件读取 → 先脱敏再传 Agent                 │
│                                                    │
│  用户确认分级:                                      │
│          🟢自动 🟡通知 🟠确认 🔴禁止                │
└──────────────────────────────────────────────────┘
```

### 7.2 操作风险分级

| 风险 | 操作示例 | 策略 | 实现方式 |
|:--:|------|------|------|
| 🟢 | 查聚合指标、查数据字典、RAG检索 | 自动执行 | CC PermissionMode=default |
| 🟡 | 查明细数据、执行DQ规则、情景推演 | 执行后通知 | 推送到企业微信 |
| 🟠 | 启动Pipeline、修改配置、批量操作 | Agent提议→用户确认 | CC PermissionMode=accept-edits |
| 🔴 | 删数据、改权限、导出原始数据 | 禁止，走Web审批 | Tool定义 allow=false |

### 7.3 审计日志格式

```
{
  "timestamp": "2026-07-28T11:30:01.000Z",
  "tenantId": "tenant_a",
  "userId": "zhangsan",
  "sessionId": "sess_abc123",
  "toolName": "data.query",
  "toolParams": {"sql": "SELECT profit FROM ... WHERE month=6"},
  "agentReasoning": "用户问了上个月利润，需要查询财务报表",
  "backendSql": "SELECT profit FROM tenant_a.财务报表 WHERE month='2026-06'",
  "rlsApplied": "dept_id='finance'",
  "maskingApplied": false,
  "rowsReturned": 1,
  "durationMs": 45
}
```

---

## 8. 分阶段实施计划

### P0: 最小可行验证（2周）

**目标**：CC 能通过 MCP 查询 ECOS 数据库，验证全链路。

| Task | 内容 | 文件 | 验收 |
|------|------|------|------|
| P0-1 | ECOS MCP Server 骨架 | `ecos_backend/services/mcp-server/` (新建) | `curl http://localhost:8080/mcp/health` → 200 |
| P0-2 | DataTool.query 实现 | MCP Server 内 | Agent 说"查营收"→ 返回真实数据 |
| P0-3 | CC MCP 配置 + 启动脚本 | CC `.mcp.json` + `start-c2eos.sh` | `bun dist/cli.js` → 调 DataTool 成功 |
| P0-4 | 身份注入验证 | CC 环境变量 + MCP 拦截器 | `CC_TENANT_ID=test` → Tool 调用带 tenant |
| P0-5 | L1 Skill 编写 | `cc_skills/c2eos-platform/SKILL.md` | Agent 回答时引用数据来源 |

**P0 成功标准**：
```
$ CC_TENANT_ID=tenant_a CC_USER_ID=admin bun dist/cli.js
> 帮我查一下上个月的经营数据

Agent: 查询结果显示，2026年6月经营数据如下：
  - 营收：1,423.5万元（来源：tenant_a.财务报表.月度利润表，2026-07-28 11:30）
  - 利润：218.3万元
  ...
```

### P1: 六大引擎 Tool 完整接入（4周）

| Task | 内容 | 验收 |
|------|------|------|
| P1-1 | OntologyTool 全部 6 个 Tool | Agent 能回答"我们有哪些业务对象" |
| P1-2 | CognitiveTool 全部 7 个 Tool | Agent 能做偏差诊断和因果分析 |
| P1-3 | SecurityTool 全部 4 个 Tool | Agent 能查权限但不能越权 |
| P1-4 | RuleTool + WorkflowTool | Agent 能执行业务规则、启动 Pipeline |
| P1-5 | 安全过滤器链实现 | 每条 Tool 调用的数据已脱敏、已过滤 |
| P1-6 | 审计日志完整记录 | 每条 Tool 调用有完整审计记录 |
| P1-7 | L2 知识注入实现 | Agent 需要时自动获取业务知识 |

### P2: 多租户 + 消息平台（4周）

| Task | 内容 | 验收 |
|------|------|------|
| P2-1 | ECOS Session Manager | 多用户同时在线，session 隔离 |
| P2-2 | CC Memory → KG 持久化 | 跨 session 记忆保留 |
| P2-3 | 企业微信接入 | 老板在企微发消息 → Agent 回复 |
| P2-4 | 用户确认交互卡片 | 🟠操作在企微弹出确认卡片 |
| P2-5 | L3 场景模板 | "月度经营分析会"一键触发 |
| P2-6 | CC 进程池 | 用户登录 < 3 秒响应 |

### P3: 产品化（长期）

| Task | 内容 |
|------|------|
| P3-1 | Web Chat UI（非 CLI） |
| P3-2 | Agent Builder（低代码配置 Agent） |
| P3-3 | Dashboard → Agent 联动（从看板直接问 AI） |
| P3-4 | 多 Agent 协作（财务 Agent + 运营 Agent 联合分析） |
| P3-5 | CC Feature Flag 编译注入（高频 Tool 零延迟） |

---

## 9. PMO 铁律

### 9.1 必须遵守

1. **不改 CC 源码**（除 MCP Client 拦截器一行代码）
2. **安全逻辑只在 ECOS 后端**——Agent 看到的数据已脱敏
3. **每个 Tool 返回必须带 `source` 字段**——防幻觉
4. **每个 Tool 调用必须过安全过滤器链**
5. **每阶段有独立 git commit + curl 验收**

### 9.2 禁止清单

1. ❌ 禁止在 CC 里写业务逻辑
2. ❌ 禁止在 MCP Tool 里直接暴露原始 SQL（所有 SQL 后端生成）
3. ❌ 禁止 Tool 返回未脱敏的数据
4. ❌ 禁止跨租户数据访问
5. ❌ 禁止在 Agent prompt 里硬编码企业数据
6. ❌ 禁止新增 Maven 模块（MCP Server 放在现有 ecos_backend 内）
7. ❌ 禁止新增 Docker 容器（复用现有 PG/Neo4j/MinIO）

---

## 10. 验收标准（P0 阶段）

```bash
# 1. MCP Server 启动
curl http://localhost:8080/mcp/health
# 期望: {"status":"UP","tools":7,"tenants":0}

# 2. DataTool.query 返回脱敏数据
curl -X POST http://localhost:8080/mcp/tools/data.query/call \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: test" \
  -H "X-User-Id: admin" \
  -d '{"sql":"SELECT * FROM 员工"}'
# 期望: 手机号字段已脱敏，带 source 和 maskingApplied 字段

# 3. CC 启动并连接 MCP
cd /home/guorongxiao/claude-code-source
CC_TENANT_ID=test CC_USER_ID=admin bun dist/cli.js --mcp-config .mcp.json
# 期望: 启动日志显示 "Connected to ECOS MCP Server (7 tools)"

# 4. CC 对话查询
# > 帮我查一下经营数据
# 期望: Agent 返回带数据来源标注的回答

# 5. 安全过滤器链
curl -X POST http://localhost:8080/mcp/tools/data.query/call \
  -H "X-Tenant-Id: test" -H "X-User-Id: guest" \
  -d '{"sql":"SELECT * FROM 财务报表"}'
# 期望: 403 Forbidden（guest 无权限）

# 6. 审计日志
curl http://localhost:8080/api/v1/security/audit-logs?userId=admin&limit=1
# 期望: 返回上一步调用的完整审计记录
```

---

## 11. 关键文件路径

| 用途 | 路径 |
|------|------|
| CC 源码 | `/home/guorongxiao/claude-code-source/` |
| ECOS 后端 | `/home/guorongxiao/ECOS/ecos_backend/` |
| MCP Server 新建 | `ecos_backend/services/mcp-server/` |
| MCP Server 入口 | `MCPApplication.java` (Spring Boot) |
| CC MCP 配置 | `claude-code-source/.mcp.json` |
| CC 启动脚本 | `claude-code-source/start-c2eos.sh` |
| L1 Skill | `claude-code-source/skills/bundled/c2eos-platform/SKILL.md` |
| L3 场景模板 | `ecos_backend/services/mcp-server/src/main/resources/scenarios/` |
| 安全过滤器 | `ecos_backend/common/common-security/src/main/java/.../filter/` |
| Session Manager | `ecos_backend/gateway/src/main/java/.../session/` |
| 审计日志表 | `ecos_backend/gateway/src/main/resources/db/migration/Vxx__c2eos_audit.sql` |
| PMO 验收脚本 | `ECOS/docs/00-Kanban/C2EOS-p0-verify.sh` |
