# C2EOS：基于 Claude Code 的 AI 原生化数智平台 — 总体方案 v2

> 项目代号: **C2EOS** | 日期: 2026-07-28 | 版本: v2.0  
> 核心决策: **CC 源码上扩展 / Python 重写六大引擎 / AI 门户统一入口 / ECOS Java 保留为连接器**

---

## 0. 架构决策（四句话）

1. **CC 是主战场** — fork `claude-code-source` → `c2eos-platform`，在 CC 源码上扩展
2. **Python 重写引擎** — 六大引擎作为独立 MCP Server（`c2eos-engine/`），FastMCP 框架
3. **AI 门户统一入口** — CLI + Web Chat + 企微/飞书 + VS Code → 同一个 Agent 核心
4. **ECOS Java 不动** — 作为数据源连接器，Python MCP Server 通过 JDBC/API 访问

---

## 1. 总体架构

```
┌──────────────────────────────────────────────────────────────┐
│                     AI 门户（统一入口）                        │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌─────────┐          │
│  │Web Chat │ │CLI 终端 │ │企业微信   │ │飞书     │  VS Code │
│  │:3001    │ │c2eos    │ │消息卡片   │ │消息卡片 │  插件    │
│  └────┬────┘ └────┬────┘ └────┬─────┘ └────┬────┘ └───┬─────┘
│       │           │           │            │          │
│       │    ┌──────┴───────────┴────────────┴──────────┘
│       │    │  C2EOS Gateway (Node.js, :3100)
│       │    │  身份验证 → 租户路由 → 会话管理 → 消息路由
│       │    │
│       ▼    ▼
│  ┌─────────────────────────────────────────────────┐
│  │            C2EOS Agent 核心                      │
│  │  (c2eos-platform/ — fork 自 claude-code-source)  │
│  │                                                  │
│  │  LLM 推理 → Tool 选择 → MCP Client → 回复生成     │
│  │                                                  │
│  │  扩展点（改 CC 源码的地方）:                       │
│  │  ├─ src/tools/ — 新增企业级 Tool                  │
│  │  ├─ src/skills/ — L1/L3 业务技能                  │
│  │  ├─ build.ts   — C2EOS feature flags             │
│  │  ├─ src/services/mcp/ — MCP 拦截器                │
│  │  └─ src/entrypoints/ — Web Server 入口（新增）     │
│  └──────────────┬──────────────────────────────────┘
│                 │ MCP 协议 (stdio/HTTP)
│  ┌──────────────▼──────────────────────────────────┐
│  │         C2EOS Python 引擎层                       │
│  │  (c2eos-engine/ — FastMCP + Python 3.12)         │
│  │                                                  │
│  │  ┌────────┐┌────────┐┌────────┐┌────────┐      │
│  │  │Data    ││Ontology││Cogni-  ││Security│      │
│  │  │Engine  ││Engine  ││tive    ││Engine  │      │
│  │  │        ││        ││Engine  ││        │      │
│  │  ├────────┤├────────┤├────────┤├────────┤      │
│  │  │SQL查询 ││对象类型││RAG检索 ││RBAC    │      │
│  │  │数据血缘││关系图  ││KG推理  ││脱敏    │      │
│  │  │DQ规则  ││Function││Agent诊断││审计    │      │
│  │  │Pipeline││导入导出││情景推演││RLS过滤 │      │
│  │  └────────┘└────────┘└────────┘└────────┘      │
│  │  ┌────────┐┌────────┐                          │
│  │  │Rule    ││Workflow│                          │
│  │  │Engine  ││Engine  │                          │
│  │  ├────────┤├────────┤                          │
│  │  │规则执行││Pipeline│                          │
│  │  │合规校验││调度    │                          │
│  │  └────────┘└────────┘                          │
│  │                                                  │
│  │  ┌──────────────────────────────────────────┐   │
│  │  │  连接器层                                  │   │
│  │  │  ├─ PG Connector (asyncpg)                │   │
│  │  │  ├─ Neo4j Connector (neo4j-async)         │   │
│  │  │  ├─ ECOS Connector (JDBC → ECOS Java)     │   │
│  │  │  ├─ MinIO Connector (boto3)               │   │
│  │  │  └─ External API Connector (httpx)        │   │
│  │  └──────────────────────────────────────────┘   │
│  └──────────────┬──────────────────────────────────┘
│                 │
│  ┌──────────────▼──────────────────────────────────┐
│  │              数据层                               │
│  │  PostgreSQL │ Neo4j │ MinIO │ ECOS(Java,可选)    │
│  └─────────────────────────────────────────────────┘
└──────────────────────────────────────────────────────────────┘
```

---

## 2. 项目目录结构

```
/home/guorongxiao/
├── c2eos-platform/          # CC fork → C2EOS Agent核心 (TypeScript)
│   ├── src/                 # 40K行，CC源码 + 扩展
│   │   ├── tools/           # 30+ Tool，新增 c2eos/ 子目录
│   │   ├── skills/          # L1/L3 Skill 包
│   │   ├── services/mcp/    # MCP Client + 拦截器
│   │   ├── entrypoints/     # cli.tsx + server.tsx(新增)
│   │   ├── portal/          # 新增: AI门户 Web UI
│   │   └── ...
│   ├── build.ts             # 构建脚本 + C2EOS feature flags
│   ├── package.json         # 改名 c2eos
│   └── .mcp.json            # MCP Server 配置
│
├── c2eos-engine/            # Python MCP Server (六大引擎)
│   ├── pyproject.toml       # Python 项目配置
│   ├── src/
│   │   ├── server.py        # MCP Server 入口 (FastMCP)
│   │   ├── engines/         # 六大引擎实现
│   │   │   ├── data/        # 数据引擎
│   │   │   ├── ontology/    # 本体引擎
│   │   │   ├── cognitive/   # 认知引擎
│   │   │   ├── security/    # 安全引擎
│   │   │   ├── rule/        # 规则引擎
│   │   │   └── workflow/    # 流程引擎
│   │   └── connectors/      # 连接器层
│   ├── tests/
│   └── skills/              # L2 知识 (JSON Schema)
│
├── c2eos-gateway/           # AI门户 Gateway (Node.js/TypeScript)
│   ├── src/
│   │   ├── server.ts        # Web Server (:3100)
│   │   ├── auth/            # JWT 认证
│   │   ├── session/         # 会话管理 + CC 进程池
│   │   ├── chat/            # Web Chat WebSocket
│   │   └── platforms/       # 企微/飞书 Bot 适配
│   └── web/                 # Web Chat 前端 (React)
│
└── ECOS/                    # 保留不动
    └── ecos_backend/        # Java Spring Boot (数据源之一)
```

---

## 3. CC 源码扩展点（具体改哪里）

### 3.1 Feature Flag 注入 (build.ts)

在 `build.ts` 的 `featureFlags` 中新增 C2EOS 开关:

```typescript
// C2EOS feature flags (追加到已有 flags)
C2EOS_DATA_ENGINE: true,       // 数据引擎 Tool
C2EOS_ONTOLOGY_ENGINE: true,   // 本体引擎 Tool
C2EOS_COGNITIVE_ENGINE: true,  // 认知引擎 Tool
C2EOS_SECURITY_ENGINE: true,   // 安全引擎 Tool
C2EOS_RULE_ENGINE: true,       // 规则引擎 Tool
C2EOS_WORKFLOW_ENGINE: true,   // 流程引擎 Tool
C2EOS_PORTAL_SERVER: true,     // AI门户 Web Server
C2EOS_MULTI_TENANT: true,      // 多租户支持
```

### 3.2 Tool 注册 (src/tools.ts)

```typescript
// 在 getAllTools() 末尾追加 C2EOS Tools
const c2eosTools = feature('C2EOS_DATA_ENGINE')
  ? [require('./tools/c2eos/DataTool').DataTool] : []
const c2eosOntologyTools = feature('C2EOS_ONTOLOGY_ENGINE')
  ? [require('./tools/c2eos/OntologyTool').OntologyTool] : []
// ... 其他引擎同理

return [
  ...existingTools,
  ...c2eosTools,
  ...c2eosOntologyTools,
  // ...
]
```

### 3.3 MCP 拦截器 (src/services/mcp/)

在 MCP Client 的 `callTool()` 方法前加拦截:

```typescript
// 每个 Tool 调用自动附加身份上下文
mcpClient.onBeforeCall((toolName, params) => ({
  ...params,
  _ctx: {
    tenantId: process.env.C2EOS_TENANT_ID,
    userId: process.env.C2EOS_USER_ID,
    role: process.env.C2EOS_USER_ROLE,
    sessionId: process.env.C2EOS_SESSION_ID,
  }
}))
```

### 3.4 新增 Web Server 入口 (src/entrypoints/server.tsx)

CC 原本只有 CLI 入口 (`cli.tsx`)。新增 Web Server 入口，让 AI 门户的 Web Chat 能调用 CC Agent:

```typescript
// c2eos-platform/src/entrypoints/server.tsx
// 启动 HTTP Server + WebSocket，接收 Web Chat 的消息
// 复用 CC 的 Agent 对话循环
```

### 3.5 宏常量替换

```typescript
// build.ts define 块中
'MACRO.VERSION': JSON.stringify('2.0.0'),
'MACRO.PRODUCT_NAME': JSON.stringify('C2EOS'),
'MACRO.PACKAGE_URL': JSON.stringify('https://github.com/gorun78/c2eos'),
```

---

## 4. Python MCP Server 详细设计

### 4.1 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| MCP 框架 | **FastMCP** (fastmcp>=2.0) | 装饰器定义 Tool，自动生成 JSON Schema |
| 数据库 | asyncpg (PG) + neo4j-async (Neo4j) | 异步驱动，匹配 MCP 异步调用 |
| Web 框架 | FastAPI（内嵌在 FastMCP） | MCP Server 同时暴露 HTTP 健康检查 |
| 向量检索 | ChromaDB 或 pgvector | 轻量，Python 原生 |
| LLM 调用 | httpx + DeepSeek SDK | CC 做 LLM 推理，Engine 只管数据 |

### 4.2 FastMCP Tool 定义示例

```python
# c2eos-engine/src/engines/data/tools.py
from fastmcp import FastMCP

mcp = FastMCP("C2EOS Data Engine")

@mcp.tool()
async def query(
    sql: str,
    params: dict | None = None,
    ctx: dict | None = None,  # 自动从拦截器注入
) -> dict:
    """
    执行只读 SQL 查询。返回脱敏后的数据。

    Args:
        sql: SQL 查询语句（只允许 SELECT）
        params: 查询参数
        ctx: 调用上下文（tenantId, userId, role）— 由 MCP 拦截器自动注入
    """
    tenant_id = ctx["tenantId"] if ctx else "default"
    # 1. 安全校验
    if not sql.strip().upper().startswith("SELECT"):
        raise PermissionError("只允许 SELECT 查询")

    # 2. Schema 隔离
    sql = sql.replace("FROM ", f"FROM {tenant_id}.")

    # 3. RLS 注入
    sql = inject_rls(sql, ctx)

    # 4. 执行查询
    rows = await pg_pool.fetch(sql, *(params or []))

    # 5. 脱敏
    rows = apply_masking(rows, ctx)

    return {
        "rows": rows,
        "columns": [k for k in rows[0].keys()] if rows else [],
        "rowCount": len(rows),
        "source": f"{tenant_id}.{extract_table(sql)}",
        "query": sql,
        "maskingApplied": True if masking_rules else False,
    }
```

### 4.3 六大引擎 Tool 清单

#### DataEngine (数据引擎) — 7 Tools

| Tool | 输入 | 输出 |
|------|------|------|
| `data.query` | `{sql, params}` | `{rows, columns, rowCount, source}` |
| `data.list_connections` | — | `{connections[]}` |
| `data.get_table_schema` | `{connectionId, tableName}` | `{columns[], rowCount}` |
| `data.get_lineage` | `{datasetId, direction}` | `{nodes[], edges[]}` |
| `data.run_dq_check` | `{ruleId?}` | `{results[], passes, fails}` |
| `data.get_metrics` | `{metricIds[]}` | `{metrics[]}` |
| `data.search_catalog` | `{keyword}` | `{datasets[]}` |

#### OntologyEngine (本体引擎) — 8 Tools

| Tool | 输入 | 输出 |
|------|------|------|
| `ontology.list_domains` | — | `{domains[]}` |
| `ontology.get_object_type` | `{id}` | `{type, properties[], links[]}` |
| `ontology.list_object_types` | `{domainId?}` | `{types[]}` |
| `ontology.get_links` | `{objectId}` | `{links[]}` |
| `ontology.query_objects` | `{typeId, filters}` | `{objects[], total}` |
| `ontology.get_functions` | `{typeId?}` | `{functions[]}` |
| `ontology.import_template` | `{industry}` | `{template}` |
| `ontology.search` | `{keyword}` | `{results[]}` |

#### CognitiveEngine (认知引擎) — 7 Tools

| Tool | 输入 | 输出 |
|------|------|------|
| `cognitive.rag_search` | `{query, topK, domain}` | `{chunks[], sources[]}` |
| `cognitive.kg_query` | `{cypher}` | `{nodes[], edges[]}` |
| `cognitive.get_goals` | `{period?}` | `{goals[], progress[]}` |
| `cognitive.get_causal_chain` | `{goalId, depth}` | `{chain[]}` |
| `cognitive.diagnose` | `{goalId, period}` | `{diagnosis, causes[], suggestions[]}` |
| `cognitive.scenario_analysis` | `{scenario, vars}` | `{outcomes[], risks[]}` |
| `cognitive.get_knowledge` | `{domain?}` | `{ontology, metrics, rules}` |

#### SecurityEngine (安全引擎) — 4 Tools

| Tool | 输入 | 输出 |
|------|------|------|
| `security.check_permission` | `{resource, action}` | `{allowed, reason}` |
| `security.my_permissions` | — | `{permissions[]}` |
| `security.my_audit_log` | `{period?}` | `{logs[]}` |
| `security.data_class` | `{datasetId}` | `{classification}` |

> **核心原则**: 脱敏/RLS/Masking 在 Tool 内部自动执行，不暴露给 Agent。安全引擎的 Tool 用于查询权限状态，不用于执行安全策略。

#### RuleEngine (规则引擎) — 3 Tools

| Tool | 输入 | 输出 |
|------|------|------|
| `rule.execute` | `{ruleId, inputs}` | `{result, logs}` |
| `rule.list_rules` | `{domain?}` | `{rules[]}` |
| `rule.validate` | `{ruleId, data}` | `{pass, violations[]}` |

#### WorkflowEngine (流程引擎) — 3 Tools

| Tool | 输入 | 输出 |
|------|------|------|
| `workflow.start` | `{pipelineId, params}` | `{executionId, status}` |
| `workflow.status` | `{executionId}` | `{status, progress, logs}` |
| `workflow.list_pipelines` | — | `{pipelines[]}` |

---

## 5. AI 门户详细设计

### 5.1 四大入口

```
                    ┌──────────────────┐
                    │   C2EOS Gateway   │
                    │   (:3100)          │
                    │                    │
  Web Chat ────────→│ /chat/ws          │──→ CC Agent
  (:3001)           │ (WebSocket)       │    进程池
                    │                    │
  CLI ─────────────→│ 本地 stdin/stdout │──→ CC Agent
  (c2eos)           │                    │    直接进程
                    │                    │
  企微/飞书 ───────→│ /platform/wecom   │──→ CC Agent
  (Bot消息)         │ /platform/feishu  │    进程池
                    │                    │
  VS Code ─────────→│ /vscode/ws        │──→ CC Agent
  (插件)            │ (WebSocket)       │    进程池
                    └──────────────────┘
```

### 5.2 Web Chat 前端

基于 CC 现有 Ink TUI 组件改造为 Web 版本：

- **框架**: React 19 + Vite + Tailwind
- **消息渲染**: 复用 CC 的 message 组件逻辑，替换为 DOM 渲染
- **权限交互**: 🟠操作弹出确认对话框（替代 CLI 的键盘确认）
- **数据可视化**: 图表组件（ECharts）展示查询结果
- **端口**: `:3001`

### 5.3 Gateway 核心功能

```
C2EOS Gateway (Node.js, :3100)
├─ 认证模块
│   ├─ JWT 签发/验证
│   ├─ 企微 OAuth / 飞书 OAuth
│   └─ API Key 管理
├─ 租户路由
│   ├─ 从 token 解析 tenantId
│   └─ 路由到对应 CC 进程池
├─ 会话管理
│   ├─ CC 进程池（每用户最多1个活跃进程）
│   ├─ 空闲 30min → 挂起（状态持久化）
│   └─ 空闲 2h → 销毁
├─ 消息路由
│   ├─ Web Chat ↔ CC Agent（WebSocket ↔ stdin/stdout）
│   ├─ 企微/飞书 ↔ CC Agent（Bot API ↔ stdin/stdout）
│   └─ 消息格式转换（富文本/卡片 ↔ Markdown）
└─ 监控
    ├─ Agent 健康检查
    ├─ Token 使用量追踪
    └─ 审计日志收集
```

---

## 6. Skills 三层体系

### 6.1 L1: 静态角色 Skill（CC 原生 Markdown）

位置: `c2eos-platform/skills/bundled/c2eos-platform/SKILL.md`

内容: 角色定义 + 行为规范 + 数据引用强制 + 安全边界

### 6.2 L2: 动态知识 Skill（Python MCP Tool 实时查询）

不走文件。Agent 需要时调 `cognitive.get_knowledge()` 实时获取:

```json
// 返回示例
{
  "tenant": {"id": "t_001", "name": "某制造集团", "industry": "离散制造"},
  "controlMode": "战略管控型",
  "ontology": {
    "domains": ["财务", "运营", "人力", "供应链"],
    "objectTypes": 42,
    "linkTypes": 156
  },
  "coreMetrics": [
    {"id": "m_001", "name": "订单交付率", "formula": "按时交付/总订单", "source": "ERP"},
    {"id": "m_002", "name": "产能利用率", "formula": "实际产量/设计产能", "source": "MES"}
  ],
  "businessGlossary": {
    "GC": "集团管控 (Group Control)",
    "OTD": "准时交付率 (On-Time Delivery)"
  }
}
```

### 6.3 L3: 场景 Skill（Tool 调用链模板）

位置: `c2eos-platform/skills/bundled/c2eos-scenarios/`

```markdown
# SCENARIO: 月度经营分析会

## 触发条件
用户提到"月度分析"、"经营会"、"本月经营"、"月度报告"

## Tool 调用链
1. cognitive.get_goals(period="current_month")
2. data.get_metrics(metricIds=上月关注的指标)
3. cognitive.diagnose(goalId=偏差最大的目标)
4. 生成 Markdown 格式的月度分析报告

## 输出模板
## {月份} 经营分析简报
### 一、目标达成
{goals 表格}
### 二、异常指标
{diagnose 结果}
### 三、建议
{diagnose.suggestions}
```

---

## 7. 多租户方案

### 7.1 数据隔离

| 层级 | 机制 | 实现 |
|------|------|------|
| PostgreSQL | 每租户独立 Schema | `tenant_a.`, `tenant_b.` — Python 连接器自动前缀 |
| Neo4j | 每租户独立 Database | `CREATE DATABASE tenant_a` — neo4j-async 路由 |
| MinIO | 每租户独立 Bucket | `c2eos-tenant-a`, `c2eos-tenant-b` |
| 文件系统 | 每用户独立目录 | `/data/c2eos/{tenantId}/users/{userId}/` |

### 7.2 会话隔离

- CC 进程按用户启动，环境变量注入 `C2EOS_TENANT_ID` / `C2EOS_USER_ID`
- CC 的 `CLAUDE_CODE_HOME` 设为 `/data/c2eos/{tenantId}/users/{userId}/`
- 对话历史文件天然物理隔离
- Gateway 的 Session Manager 管理进程生命周期

### 7.3 Memory 改造

```
CC 原有 Memory: 文件持久化
C2EOS Memory:   Neo4j 知识图谱节点

每个用户记忆:
  MERGE (u:User {id: $userId, tenant: $tenantId})
  SET u.prefers_chart_type = $value

隔离保证: {tenantId} 在查询时自动过滤
```

---

## 8. 安全方案

### 8.1 身份链

```
用户 → Gateway 验签 → 解析 tenant/user/role → 
  启动 CC 进程(注入环境变量) → MCP 拦截器附加 ctx →
  Python Engine 使用 ctx 做 RBAC/RLS/Masking
```

### 8.2 三道防线（在 Python Engine 内执行）

```
Tool 调用进入 Python Engine
  → AuthFilter:  ctx 有效?
  → RBACFilter:  用户有权限?
  → TenantFilter: Schema 前缀注入
  → RLSFilter:    行级 WHERE 条件
  → MaskingFilter: 列级脱敏
  → AuditFilter:   记录审计日志
  → 执行查询
  → 返回脱敏后数据
```

### 8.3 操作风险分级

| 等级 | 示例 | 策略 | CC 实现 |
|:--:|------|------|------|
| 🟢 | 查聚合指标、RAG 检索 | 自动执行 | PermissionMode=default |
| 🟡 | 查明细、DQ 检查 | 执行后通知 | 推送消息到用户 |
| 🟠 | 启动 Pipeline、修改配置 | 用户确认 | PermissionMode=accept-edits |
| 🔴 | 删数据、改权限 | 禁止 | Tool 定义 allow=false |

---

## 9. 分阶段实施计划

### P0: 骨架贯通（2周）— 跑通全链路

| Task | 内容 | 交付物 |
|------|------|--------|
| P0-1 | CC 源码 fork → c2eos-platform | `c2eos-platform/` 目录，build.ts 已改，MACRO 替换 |
| P0-2 | Python MCP Server 骨架 + DataEngine.query | `c2eos-engine/` 目录，FastMCP 跑通 |
| P0-3 | CC MCP 配置 → 连接 Python Engine | `.mcp.json`，Agent 能调 `data.query` |
| P0-4 | 身份注入验证 | MCP 拦截器 + `C2EOS_TENANT_ID` 环境变量验证 |
| P0-5 | L1 Skill 编写 | `SKILL.md`，Agent 行为规范生效 |

**P0 成功标准**:
```bash
$ C2EOS_TENANT_ID=t_001 C2EOS_USER_ID=admin c2eos
> 帮我查经营数据
# Agent 通过 MCP 调用 Python DataEngine → 返回真实数据 → 回复带来源标注
```

### P1: 引擎全覆盖（4周）— 32个 Tool 就位

| Task | 内容 |
|------|------|
| P1-1 | OntologyEngine 8 Tools |
| P1-2 | CognitiveEngine 7 Tools |
| P1-3 | SecurityEngine 4 Tools |
| P1-4 | RuleEngine + WorkflowEngine (6 Tools) |
| P1-5 | 安全过滤器链实现（Auth→RBAC→RLS→Masking→Audit） |
| P1-6 | L2 动态知识注入 |
| P1-7 | 审计日志完整记录 |

### P2: 门户 + 多租户（4周）

| Task | 内容 |
|------|------|
| P2-1 | C2EOS Gateway (Node.js) |
| P2-2 | Web Chat 前端 (React + WebSocket) |
| P2-3 | Gateway Session Manager + CC 进程池 |
| P2-4 | 多租户 Schema 隔离 |
| P2-5 | CC Memory → Neo4j 持久化 |
| P2-6 | 企微 Bot 接入 |
| P2-7 | L3 场景 Skill（月度分析会、数据接入向导） |
| P2-8 | VS Code 插件适配 |

### P3: 产品化（长期）

| Task | 内容 |
|------|------|
| P3-1 | Agent Builder（低代码配置 Agent） |
| P3-2 | 飞书接入 |
| P3-3 | 数据可视化（ECharts 集成到 Web Chat） |
| P3-4 | 多 Agent 协作 |
| P3-5 | ECOS Java 作为可选数据源连接器 |
| P3-6 | 性能优化（CC Feature Flag 编译注入高频 Tool） |

---

## 10. 与 ECOS 的关系

```
ECOS Java 后端      C2EOS
    │                  │
    │  ── JDBC ──→  Python Engine (数据源连接器)
    │                  │
    │               CC Agent
    │                  │
    │              AI 门户
```

**ECOS 的角色**: 保留为数据源之一。Python Engine 的连接器层通过 JDBC/API 访问 ECOS 的 PG 数据库。不依赖 ECOS 的 Java 代码逻辑。

**为什么要保留 ECOS**: 
- ECOS 已有的 443 模型/31699 表是宝贵资产
- 数据工作台/本体设计器等 Web UI 可逐步迁移到 C2EOS 门户
- 过渡期 ECOS 和 C2EOS 并行运行

---

## 11. PMO 铁律

### 必须
1. **CC 源码可改**——但要标记所有改动点（`// C2EOS:` 注释）
2. **Python Engine 独立部署**——不依赖 ECOS Java
3. **每个 Tool 返回带 source 字段**——防幻觉的生命线
4. **安全过滤器链在每个 Tool 自动执行**
5. **每阶段独立 git commit + 验证**

### 禁止
1. ❌ 禁止在 CC 里写业务逻辑（业务逻辑在 Python Engine）
2. ❌ 禁止 Tool 返回未脱敏数据
3. ❌ 禁止跨租户数据访问
4. ❌ 禁止在 Agent prompt 里硬编码企业数据
5. ❌ 禁止修改 ECOS Java 后端代码
6. ❌ 禁止 Agent 直接执行 DELETE/DROP/ALTER

---

## 12. P0 验收命令

```bash
# 1. Python MCP Server 启动
cd c2eos-engine && python -m src.server
# 期望: "FastMCP server running on :8765 (32 tools)"

# 2. MCP 健康检查
curl http://localhost:8765/health
# 期望: {"status":"UP","tools":32}

# 3. DataTool.query 脱敏验证
curl -X POST http://localhost:8765/tools/data.query/call \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT * FROM 员工 WHERE 1=1","_ctx":{"tenantId":"test","userId":"admin"}}'
# 期望: 手机号已脱敏，含 source 和 maskingApplied

# 4. CC 连接 MCP 验证
cd c2eos-platform
C2EOS_TENANT_ID=test C2EOS_USER_ID=admin bun dist/cli.js --mcp-config .mcp.json
# > /tools → 期望显示 32 个 Tool

# 5. 端到端对话
# > 帮我查一下经营数据
# 期望: 返回带数据来源标注的回答

# 6. 权限校验
curl -X POST http://localhost:8765/tools/data.query/call \
  -d '{"sql":"SELECT * FROM 财务报表","_ctx":{"tenantId":"test","userId":"guest"}}'
# 期望: 403 或 false（guest 无权限）
```

---

## 13. 关键路径速查

| 用途 | 路径 |
|------|------|
| C2EOS Agent 核心 | `/home/guorongxiao/c2eos-platform/` |
| Python MCP Server | `/home/guorongxiao/c2eos-engine/` |
| AI 门户 Gateway | `/home/guorongxiao/c2eos-gateway/` |
| CC 原始源码 | `/home/guorongxiao/claude-code-source/` |
| ECOS (保留) | `/home/guorongxiao/ECOS/` |
| P0 验收脚本 | `c2eos-engine/tests/p0_verify.py` |
