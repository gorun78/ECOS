# C2EOS：AI 原生化数智平台 — 详细方案 v3

> 项目代号: **C2EOS** | 日期: 2026-07-28 | 版本: v3.0  
> 定位: **可直接交付 PMO 执行的工程方案，不是概要设计**

---

## 目录

0. [名词约定](#0-名词约定)
1. [架构总览](#1-架构总览)
2. [P0: 骨架贯通（2周，5个Task）](#2-p0-骨架贯通2周5个task)
3. [P1: 六大引擎全覆盖（4周，7个Task）](#3-p1-六大引擎全覆盖4周7个task)
4. [P2: 门户+多租户（4周，8个Task）](#4-p2-门户多租户4周8个task)
5. [P3: 产品化（长期）](#5-p3-产品化长期)
6. [禁止清单](#6-禁止清单)
7. [附录: 代码模板](#7-附录-代码模板)

---

## 0. 名词约定

| 名词 | 全称 | 路径 | 技术栈 |
|------|------|------|--------|
| **CC** | Claude Code 原始源码 | `/home/guorongxiao/claude-code-source/` | TypeScript, Bun |
| **C2EOS平台** | CC fork + 扩展后的 Agent 核心 | `/home/guorongxiao/c2eos-platform/` | TypeScript, Bun, React/Ink |
| **Engine** | Python MCP Server（六大引擎） | `/home/guorongxiao/c2eos-engine/` | Python 3.12, FastMCP |
| **Gateway** | AI 门户网关 | `/home/guorongxiao/c2eos-gateway/` | Node.js, TypeScript, Express |
| **ECOS** | 现有 Java 后端（保留为数据源） | `/home/guorongxiao/ECOS/` | Java 17, Spring Boot |
| **Tool** | MCP 工具——Agent 可调用的原子能力 | — | Python async 函数 |
| **ctx** | 调用上下文 `{tenantId, userId, role, sessionId}` | — | 由 MCP 拦截器自动注入 |

---

## 1. 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                     AI 门户（P2 实现）                         │
│  Web Chat (:3001) │ CLI (c2eos) │ 企业微信 │ 飞书 │ VS Code  │
└────────────┬─────────────────────────────────────────────────┘
             │                 ┌──────────────────┐
             │                 │   用户请求          │
             └─────────────────┤ JWT/Token 验证     │
                               │ 解析 {t,u,r}       │
                               │ 路由到 Agent 进程   │
                               └──────┬───────────┘
                                      │
┌─────────────────────────────────────▼────────────────────────┐
│                C2EOS 平台 (CC fork)                           │
│  /home/guorongxiao/c2eos-platform/                            │
│                                                               │
│  三个入口:                                                     │
│  ├─ CLI:    entrypoints/cli.tsx    → 终端交互                 │
│  ├─ Server: entrypoints/server.tsx → Web Chat (P2)           │
│  └─ MCP:    entrypoints/mcp.ts     → 内部 MCP Hub (P3)       │
│                                                               │
│  改动的 CC 文件:                                               │
│  ├─ build.ts          → C2EOS feature flags + MACRO 常量      │
│  ├─ tools.ts          → 注册 C2EOS 企业 Tool                  │
│  ├─ tools/c2eos/      → 新增: 企业 Tool 目录                  │
│  ├─ skills/bundled/   → 新增: L1/L3 Skill                   │
│  ├─ services/mcp/     → MCP Client 拦截器(自动注入 ctx)        │
│  └─ entrypoints/server.tsx → 新增: Web Server 入口            │
│                                                               │
│  不改动的 CC 文件: 除以上7个点，其余 469 个文件不动              │
└──────────────┬───────────────────────────────────────────────┘
               │ MCP 协议 (JSON-RPC over HTTP/stdio)
               │ 每个 Tool 调用携带 _ctx: {tenantId, userId, role, sessionId}
┌──────────────▼───────────────────────────────────────────────┐
│              C2EOS Engine (Python MCP Server)                  │
│  /home/guorongxiao/c2eos-engine/                               │
│                                                               │
│  入口: src/server.py         → FastMCP                        │
│                                                               │
│  src/engines/                 → 六大引擎                       │
│  ├─ data/        (data.py, lineage.py, dq.py)                │
│  ├─ ontology/    (ontology.py, functions.py, search.py)      │
│  ├─ cognitive/   (rag.py, kg.py, diagnose.py, scenario.py)   │
│  ├─ security/    (auth.py, masking.py, audit.py)             │
│  ├─ rule/        (rule.py)                                   │
│  └─ workflow/    (workflow.py)                                │
│                                                               │
│  src/connectors/              → 数据源连接器                    │
│  ├─ pg_connector.py   → asyncpg → PostgreSQL                 │
│  ├─ neo4j_connector.py→ neo4j-async → Neo4j                  │
│  ├─ minio_connector.py→ minio-py → MinIO                      │
│  └─ ecos_connector.py  → httpx → ECOS Java (可选)             │
│                                                               │
│  src/filters/                 → 安全过滤器链                    │
│  ├─ auth_filter.py    → ctx 有效性校验                         │
│  ├─ rbac_filter.py    → 角色权限矩阵                           │
│  ├─ tenant_filter.py  → Schema 前缀注入                        │
│  ├─ rls_filter.py     → 行级 WHERE 条件                       │
│  ├─ masking_filter.py → 列级脱敏                               │
│  └─ audit_filter.py   → 审计日志                              │
│                                                               │
│  skills/                      → L2 动态知识                    │
│  └─ {tenant_id}/              → 每个租户的知识库 JSON           │
│      ├─ ontology.json         → 对象类型/关系/属性             │
│      ├─ metrics.json          → 核心经营指标定义                │
│      ├─ glossary.json         → 业务术语表                     │
│      └─ rules.json            → 业务规则定义                   │
└──────────────┬───────────────────────────────────────────────┘
               │ 数据库驱动
┌──────────────▼───────────────────────────────────────────────┐
│                   数据层                                       │
│  PostgreSQL (:5432, sys_man) │ Neo4j (:7687)                  │
│  MinIO (:9000)               │ ECOS Java (可选, :8080)        │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. P0: 骨架贯通（2周，5个Task）

> **目标**: CC Agent 能通过 MCP 调用 Python Engine，查询真实数据库并返回脱敏数据。  
> **成功标志**: 用户在 CC 终端输入"帮我查经营数据"→ Agent 调用 `data.query` 返回脱敏结果并标注来源。

### 2.0 环境准备（PMO 执行前一次性操作）

```bash
# ─── 1. 安装 Python 3.12 + 依赖 ───
# 检查 Python 版本
python3 --version  # 期望: 3.12+

# 安装 uv (Python 包管理器，替代 pip)
curl -LsSf https://astral.sh/uv/install.sh | sh
source ~/.bashrc

# ─── 2. 确认 Node.js + Bun ───
node --version  # 期望: v18+
bun --version   # 期望: v1.3+

# ─── 3. 确认 PostgreSQL 可用 ───
psql -h localhost -U postgres -d sys_man -c "SELECT 1"
# 期望: 返回 1

# ─── 4. 克隆 CC 源码 → C2EOS 平台 ───
cp -r /home/guorongxiao/claude-code-source /home/guorongxiao/c2eos-platform
cd /home/guorongxiao/c2eos-platform
git init && git add -A && git commit -m "init: fork from claude-code-source v2.1.88"
```

### Task P0-1: Python Engine 骨架 + DataEngine.query (3天)

#### P0-1.1 创建项目结构

```bash
mkdir -p /home/guorongxiao/c2eos-engine
cd /home/guorongxiao/c2eos-engine

# 目录结构
mkdir -p src/engines/data
mkdir -p src/engines/ontology
mkdir -p src/engines/cognitive
mkdir -p src/engines/security
mkdir -p src/engines/rule
mkdir -p src/engines/workflow
mkdir -p src/connectors
mkdir -p src/filters
mkdir -p skills
mkdir -p tests
```

#### P0-1.2 pyproject.toml

**文件**: `/home/guorongxiao/c2eos-engine/pyproject.toml`
**操作**: 新建

```toml
[project]
name = "c2eos-engine"
version = "0.1.0"
description = "C2EOS 六大引擎 — Python MCP Server"
requires-python = ">=3.12"
dependencies = [
    "fastmcp>=2.0.0",
    "asyncpg>=0.30.0",
    "pydantic>=2.0.0",
]

[project.optional-dependencies]
all = [
    "neo4j>=5.0.0",
    "minio>=7.0.0",
    "httpx>=0.28.0",
    "chromadb>=0.5.0",
]

[tool.uv]
dev-dependencies = [
    "pytest>=8.0.0",
    "pytest-asyncio>=0.25.0",
]
```

**安装验证**:
```bash
cd /home/guorongxiao/c2eos-engine
uv sync
# 期望: "Resolved 42 packages in ..."
```

#### P0-1.3 PG 连接器

**文件**: `/home/guorongxiao/c2eos-engine/src/connectors/pg_connector.py`
**操作**: 新建

```python
"""PostgreSQL 连接器 — 连接池管理 + Schema 隔离"""
import asyncpg
from typing import Any

# 连接配置（后续从环境变量读取）
PG_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "user": "postgres",
    "password": "postgres",
    "database": "sys_man",
}

_pool: asyncpg.Pool | None = None


async def get_pool() -> asyncpg.Pool:
    global _pool
    if _pool is None:
        _pool = await asyncpg.create_pool(**PG_CONFIG, min_size=2, max_size=10)
    return _pool


async def query(tenant_id: str, sql: str, params: list[Any] | None = None) -> list[dict]:
    """执行只读查询，自动加 schema 前缀"""
    pool = await get_pool()
    # Schema 隔离: 替换 FROM table → FROM tenant_id.table
    import re
    sql = re.sub(r"\bFROM\s+(\w+)", rf"FROM {tenant_id}.\1", sql, flags=re.IGNORECASE)
    async with pool.acquire() as conn:
        rows = await conn.fetch(sql, *(params or []))
        return [dict(r) for r in rows]
```

**验证**:
```bash
cd /home/guorongxiao/c2eos-engine && python3 -c "
import asyncio
from src.connectors.pg_connector import query
async def t():
    rows = await query('public', 'SELECT 1 AS test')
    print(rows)
asyncio.run(t())
"
# 期望: [{'test': 1}]
```

#### P0-1.4 安全过滤器

**文件**: `/home/guorongxiao/c2eos-engine/src/filters/__init__.py`
**操作**: 新建

```python
"""安全过滤器链 — 每个 Tool 调用自动执行"""
from dataclasses import dataclass, field


@dataclass
class FilterContext:
    tenant_id: str
    user_id: str
    role: str
    session_id: str


# 简化版权限矩阵 (P0 用硬编码，P1 改为数据库读取)
ROLE_PERMISSIONS = {
    "admin":    {"*": "*"},                              # 管理员: 全部
    "analyst":  {"data": "read", "ontology": "read"},    # 分析师: 可读数据和本体
    "viewer":   {"data": "read"},                        # 观察者: 只读数据
}


async def filter_chain(ctx: FilterContext, tool_name: str, params: dict) -> dict:
    """
    过滤器链入口。返回 (allowed: bool, reason: str, params: dict)
    P0 简化实现: 只做 RBAC 校验 + Schema 注入
    """
    # 1. RBAC 校验
    engine = tool_name.split(".")[0]  # "data.query" → "data"
    perms = ROLE_PERMISSIONS.get(ctx.role, {})
    if engine not in perms and "*" not in perms:
        raise PermissionError(f"用户 {ctx.user_id}({ctx.role}) 无权访问 {engine} 引擎")

    # 2. 注入 ctx（传递到 Tool 内部）
    params["_ctx"] = {
        "tenantId": ctx.tenant_id,
        "userId": ctx.user_id,
        "role": ctx.role,
        "sessionId": ctx.session_id,
    }
    return params


# 脱敏规则 (P0 简化)
MASKING_RULES = [
    {"column_pattern": r"(?i).*phone.*", "fn": lambda v: v[:3] + "****" + v[-4:] if v and len(v) > 7 else "***"},
    {"column_pattern": r"(?i).*email.*", "fn": lambda v: v.split("@")[0][:2] + "***@" + v.split("@")[-1] if v and "@" in v else "***"},
    {"column_pattern": r"(?i).*id_card.*|.*身份证.*", "fn": lambda v: v[:3] + "**********" + v[-3:] if v and len(v) > 10 else "***"},
]


def apply_masking(rows: list[dict]) -> tuple[list[dict], bool]:
    """对查询结果应用脱敏规则"""
    import re
    if not rows:
        return rows, False
    masked = False
    for row in rows:
        for col in list(row.keys()):
            for rule in MASKING_RULES:
                if re.match(rule["column_pattern"], col):
                    if row[col] is not None:
                        row[col] = rule["fn"](str(row[col]))
                        masked = True
    return rows, masked
```

#### P0-1.5 DataEngine.query Tool

**文件**: `/home/guorongxiao/c2eos-engine/src/engines/data/data.py`
**操作**: 新建

```python
"""Data Engine — 数据查询 Tool"""
from fastmcp import FastMCP
from src.connectors.pg_connector import query as pg_query
from src.filters import apply_masking
import re

mcp = FastMCP("C2EOS Data Engine")


@mcp.tool()
async def query(sql: str, params: list | None = None, _ctx: dict | None = None) -> dict:
    """
    执行只读 SQL 查询。返回脱敏后的数据。

    使用场景:
    - 用户问"上个月营收多少"→ SELECT SUM(revenue) FROM 财务报表 WHERE month=6
    - 用户问"最近订单状态"→ SELECT * FROM 订单 WHERE created_at > '2026-06-01'
    - 用户问"员工总数"→ SELECT COUNT(*) FROM 员工

    Args:
        sql: SQL 查询语句。只允许 SELECT 开头。表名不需要加 schema 前缀，系统自动根据租户隔离。
        params: SQL 参数列表，用于参数化查询防止注入。
        _ctx: 调用上下文，由 MCP 拦截器自动注入。包含 tenantId, userId, role, sessionId。

    Returns:
        rows: 查询结果行列表
        columns: 列名列表
        rowCount: 行数
        source: 数据来源标识 (schema.table形式)
        maskApplied: 是否应用了脱敏
        query: 实际执行的 SQL（去参数化后）
        error: 错误信息（仅查询失败时返回）
    """
    # 1. 安全检查: 只允许 SELECT
    sql_stripped = sql.strip()
    if not sql_stripped.upper().startswith("SELECT"):
        return {
            "rows": [],
            "columns": [],
            "rowCount": 0,
            "source": "",
            "maskApplied": False,
            "query": sql,
            "error": "只允许 SELECT 查询。如需写入/修改/删除，请走 Web 审批流程。"
        }

    # 2. 禁止关键字检查
    forbidden = ["DROP", "ALTER", "CREATE", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "GRANT", "REVOKE"]
    for kw in forbidden:
        if re.search(rf"\b{kw}\b", sql_stripped, re.IGNORECASE):
            return {
                "rows": [], "columns": [], "rowCount": 0,
                "source": "", "maskApplied": False, "query": sql,
                "error": f"禁止 '{kw}' 操作。此 Tool 只支持只读查询。"
            }

    # 3. 获取租户上下文
    tenant_id = _ctx.get("tenantId", "public") if _ctx else "public"

    # 4. 执行查询（pg_query 自动加 schema 前缀）
    try:
        rows = await pg_query(tenant_id, sql, params or [])
    except Exception as e:
        return {
            "rows": [], "columns": [], "rowCount": 0,
            "source": "", "maskApplied": False, "query": sql,
            "error": f"查询执行失败: {str(e)}"
        }

    # 5. 脱敏
    rows, masked = apply_masking(rows)

    # 6. 提取列名 + 来源
    columns = list(rows[0].keys()) if rows else []
    # 从 SQL 提取表名作为来源
    table_match = re.search(r"FROM\s+(\w+\.?\w+)", sql, re.IGNORECASE)
    source = f"{tenant_id}.{table_match.group(1)}" if table_match else tenant_id

    return {
        "rows": rows,
        "columns": columns,
        "rowCount": len(rows),
        "source": source,
        "maskApplied": masked,
        "query": sql,
    }
```

#### P0-1.6 MCP Server 入口

**文件**: `/home/guorongxiao/c2eos-engine/src/server.py`
**操作**: 新建

```python
"""C2EOS Engine — MCP Server 入口"""
import sys
from pathlib import Path

# 确保项目根目录在 Python path
sys.path.insert(0, str(Path(__file__).parent.parent))

from fastmcp import FastMCP

# 创建 MCP Server
server = FastMCP(
    "C2EOS Engine",
    description="C2EOS 六大引擎 MCP Server — 企业数智平台核心能力",
    version="0.1.0",
)

# 注册引擎
from src.engines.data.data import mcp as data_mcp
server.mount("data", data_mcp)

# P1 阶段注册更多引擎
# server.mount("ontology", ontology_mcp)
# server.mount("cognitive", cognitive_mcp)
# server.mount("security", security_mcp)
# server.mount("rule", rule_mcp)
# server.mount("workflow", workflow_mcp)

if __name__ == "__main__":
    server.run(transport="stdio")
    # HTTP 模式: server.run(transport="http", port=8765)
```

#### P0-1 验收

```bash
# 1. Server 启动 (stdio 模式)
cd /home/guorongxiao/c2eos-engine
python3 src/server.py --help
# 期望: 显示 FastMCP CLI 帮助

# 2. Python 单元测试
cd /home/guorongxiao/c2eos-engine
python3 -c "
import asyncio
from src.connectors.pg_connector import query as pg_query
async def t():
    rows = await pg_query('public', 'SELECT current_database() AS db, current_schema() AS schema')
    print('PG 连接:', rows)
asyncio.run(t())
"
# 期望: PG 连接: [{'db': 'sys_man', 'schema': 'public'}]

# 3. 脱敏验证
cd /home/guorongxiao/c2eos-engine
python3 -c "
from src.filters import apply_masking
rows = [{'name': '张三', 'phone': '13812345678', 'email': 'zhang@test.com'}]
masked_rows, applied = apply_masking(rows)
print('脱敏结果:', masked_rows, '已脱敏:', applied)
"
# 期望: 脱敏结果: [{'name': '张三', 'phone': '138****5678', 'email': 'zh***@test.com'}] 已脱敏: True

# 4. Git commit
cd /home/guorongxiao/c2eos-engine
git init && git add -A && git commit -m "P0-1: Python Engine 骨架 + DataEngine.query + 安全过滤器"
```

---

### Task P0-2: CC 平台改造 — Feature Flags + MACRO 替换 (1天)

#### P0-2.1 修改 build.ts

**文件**: `/home/guorongxiao/c2eos-platform/build.ts`
**操作**: 修改

**改 1**: 在 `featureFlags` 对象末尾追加 C2EOS flags (在第 100 行 `}` 之前):

```typescript
  // ── C2EOS Feature Flags ──────────────────────────────────
  C2EOS_DATA_ENGINE: true,
  C2EOS_ONTOLOGY_ENGINE: false,     // P1 开启
  C2EOS_COGNITIVE_ENGINE: false,    // P1 开启
  C2EOS_SECURITY_ENGINE: false,     // P1 开启
  C2EOS_RULE_ENGINE: false,         // P1 开启
  C2EOS_WORKFLOW_ENGINE: false,     // P1 开启
  C2EOS_PORTAL_SERVER: false,       // P2 开启
  C2EOS_MULTI_TENANT: true,
}
```

**改 2**: 修改 MACRO 常量 (第 111-117 行):

```typescript
define: {
    'MACRO.VERSION': JSON.stringify('2.0.0'),
    'MACRO.BUILD_TIME': JSON.stringify(BUILD_TIME),
    'MACRO.PRODUCT_NAME': JSON.stringify('C2EOS'),
    'MACRO.ISSUES_EXPLAINER': JSON.stringify('报告给 C2EOS 团队'),
    'MACRO.FEEDBACK_CHANNEL': JSON.stringify('内部反馈'),
    'MACRO.PACKAGE_URL': JSON.stringify(''),
    'MACRO.VERSION_CHANGELOG': JSON.stringify('C2EOS v2.0.0 — 企业数智平台'),
}
```

#### P0-2.2 修改 package.json

**文件**: `/home/guorongxiao/c2eos-platform/package.json`
**操作**: 修改第 2-3 行

```json
"name": "c2eos",
"version": "2.0.0",
```

#### P0-2.3 修改 cli.tsx 版本输出

**文件**: `/home/guorongxiao/c2eos-platform/src/entrypoints/cli.tsx`
**操作**: 修改第 40 行

```typescript
// 改为:
console.log(`${MACRO.VERSION} (C2EOS — 企业数智平台)`);
```

#### P0-2 验收

```bash
# 1. 构建
cd /home/guorongxiao/c2eos-platform
bun run build.ts
# 期望: 无错误，dist/ 目录生成

# 2. 版本验证
bun dist/cli.js --version
# 期望: 2.0.0 (C2EOS — 企业数智平台)

# 3. 检查 feature flags 是否生效
bun dist/cli.js --help
# 期望: 正常显示帮助信息（flags 不影响基本功能）

# 4. Git commit
cd /home/guorongxiao/c2eos-platform
git add -A && git commit -m "P0-2: C2EOS feature flags + MACRO 替换 + package 改名"
```

---

### Task P0-3: CC 连接 Python Engine — MCP 配置 + 拦截器 (1.5天)

#### P0-3.1 创建 MCP 配置文件

**文件**: `/home/guorongxiao/c2eos-platform/.mcp.json`
**操作**: 新建

```json
{
  "mcpServers": {
    "c2eos-data": {
      "command": "python3",
      "args": ["-m", "src.server"],
      "cwd": "/home/guorongxiao/c2eos-engine",
      "env": {
        "C2EOS_TENANT_ID": "${C2EOS_TENANT_ID}",
        "C2EOS_USER_ID": "${C2EOS_USER_ID}",
        "C2EOS_USER_ROLE": "${C2EOS_USER_ROLE}"
      }
    }
  }
}
```

#### P0-3.2 MCP 拦截器 — 自动注入 ctx

**文件**: `/home/guorongxiao/c2eos-platform/src/services/mcp/interceptor.ts`
**操作**: 新建（不改动现有 MCP 文件）

```typescript
/**
 * C2EOS MCP 拦截器
 * 在每个 MCP Tool 调用前自动注入调用上下文
 *
 * 使用方式: 在 mcp 初始化时调用 setupInterceptor(mcpClient)
 */

export interface C2EOSContext {
  tenantId: string;
  userId: string;
  role: string;
  sessionId?: string;
}

/**
 * 从环境变量读取当前用户上下文
 */
export function getC2EOSContext(): C2EOSContext {
  return {
    tenantId: process.env.C2EOS_TENANT_ID || "default",
    userId: process.env.C2EOS_USER_ID || "anonymous",
    role: process.env.C2EOS_USER_ROLE || "viewer",
    sessionId: process.env.C2EOS_SESSION_ID || "unknown",
  };
}

/**
 * 包装 Tool 参数，自动注入 _ctx
 */
export function injectContext(params: Record<string, unknown>): Record<string, unknown> {
  return {
    ...params,
    _ctx: getC2EOSContext(),
  };
}
```

**集成点**: 找到 CC 中实际调用 MCP Tool 的位置（`src/services/mcp/client.ts` 约 200 行），在 `callTool()` 方法中调用 `injectContext(params)` 包裹参数。

具体改动: 查找 `callTool` 方法:
```bash
cd /home/guorongxiao/c2eos-platform
grep -n "async callTool\|callTool(" src/services/mcp/client.ts
```

在 `callTool` 中 `params` 传给实际调用之前，加一行:
```typescript
params = injectContext(params);
```

#### P0-3.3 创建 C2EOS 启动脚本

**文件**: `/home/guorongxiao/c2eos-platform/start.sh`
**操作**: 新建，设置为可执行 (`chmod +x`)

```bash
#!/bin/bash
# C2EOS 启动脚本
# 用法: ./start.sh [--tenant <id>] [--user <id>] [--role <role>]

# 默认值
export C2EOS_TENANT_ID="${C2EOS_TENANT_ID:-public}"
export C2EOS_USER_ID="${C2EOS_USER_ID:-admin}"
export C2EOS_USER_ROLE="${C2EOS_USER_ROLE:-admin}"

# 解析参数
while [ $# -gt 0 ]; do
  case $1 in
    --tenant) export C2EOS_TENANT_ID="$2"; shift 2 ;;
    --user)   export C2EOS_USER_ID="$2"; shift 2 ;;
    --role)   export C2EOS_USER_ROLE="$2"; shift 2 ;;
    *) break ;;
  esac
done

echo "🚀 C2EOS 启动"
echo "   租户: $C2EOS_TENANT_ID | 用户: $C2EOS_USER_ID | 角色: $C2EOS_USER_ROLE"

# 构建（如未构建）
if [ ! -f dist/cli.js ]; then
  echo "🔨 首次构建..."
  bun run build.ts
fi

# 启动 CC Agent
exec bun dist/cli.js "$@"
```

#### P0-3 验收

```bash
# 1. 启动脚本验证
cd /home/guorongxiao/c2eos-platform
chmod +x start.sh
./start.sh --version
# 期望: 2.0.0 (C2EOS — 企业数智平台)

# 2. 环境变量注入验证
C2EOS_TENANT_ID=test C2EOS_USER_ID=zhangsan C2EOS_USER_ROLE=analyst ./start.sh --help
# 期望: 正常显示帮助，无错误

# 3. MCP 拦截器单元验证 (写一个简单脚本)
cd /home/guorongxiao/c2eos-platform
node -e "
const { injectContext, getC2EOSContext } = require('./src/services/mcp/interceptor.ts');
process.env.C2EOS_TENANT_ID = 'test_tenant';
process.env.C2EOS_USER_ID = 'test_user';
const ctx = getC2EOSContext();
console.assert(ctx.tenantId === 'test_tenant', 'tenantId error');
console.assert(ctx.userId === 'test_user', 'userId error');
console.log('拦截器验证通过:', ctx);
"
# 期望: 拦截器验证通过: { tenantId: 'test_tenant', userId: 'test_user', role: 'viewer' }

# 4. Git commit
cd /home/guorongxiao/c2eos-platform
git add -A && git commit -m "P0-3: MCP 配置 + 拦截器 + 启动脚本"
```

---

### Task P0-4: L1 Skill — Agent 行为规范 (0.5天)

#### P0-4.1 创建 Skill 文件

**文件**: `/home/guorongxiao/c2eos-platform/skills/bundled/c2eos-platform/SKILL.md`
**操作**: 新建

```markdown
---
name: c2eos-platform
description: C2EOS 企业数智平台 AI 助手行为规范
version: 1.0.0
---

# C2EOS 企业数智平台 AI 助手

## 你的角色

你是企业的 AI 数智助手，运行在 C2EOS 平台上。帮助企业管理者理解经营状况、发现异常指标、辅助经营决策。

## 回答规范（强制遵守）

### 1. 数据引用规范

每次引用数据时，必须标注来源。使用以下格式:

```
📊 {数据描述}: {数值}
   来源: {source字段值} | 行数: {rowCount} | 脱敏: {maskApplied ? '是' : '否'}
```

示例:
```
📊 6月营收: 1,423.5万元
   来源: test_tenant.财务报表 | 行数: 1 | 脱敏: 否
```

### 2. 空数据处理

如果 Tool 返回 `rowCount: 0` 或 `error` 字段存在:
- 明确告知用户"查询结果为空"或显示具体错误
- **不得猜测数据**。不要编造任何数值。

### 3. 安全边界

- 你看到的数据已经过脱敏处理。不要尝试还原脱敏数据。
- 不要向用户透露技术细节（SQL语句/Tool名称/架构建模信息），除非用户明确问。
- 使用企业经营术语（营收/利润/现金流/ROI/交付率），不要用技术黑话。

### 4. 回答风格

- 先给结论，再给数据支撑
- 如果发现问题，给出可操作的建议
- 不确定的事情说"不确定"，列出需要哪些数据才能判断

### 5. 可用能力（告知用户你能做什么）

你可以帮用户:
- 📈 查询经营指标（营收、利润、成本、交付率等）
- 📋 查询数据目录和表结构
- (即将上线) 偏差诊断和根因分析
- (即将上线) 情景推演和模拟分析
```

#### P0-4 验收

```bash
# Skill 文件存在性
ls -la /home/guorongxiao/c2eos-platform/skills/bundled/c2eos-platform/SKILL.md
# 期望: 文件存在

# 格式验证
head -5 /home/guorongxiao/c2eos-platform/skills/bundled/c2eos-platform/SKILL.md
# 期望: 显示 YAML frontmatter (--- 包围)

# Git commit
cd /home/guorongxiao/c2eos-platform
git add -A && git commit -m "P0-4: L1 Skill — Agent 行为规范"
```

---

### Task P0-5: 端到端集成验证 (1.5天)

#### P0-5.1 测试数据准备

```bash
# 在 PostgreSQL 中创建 test schema 和测试数据
psql -h localhost -U postgres -d sys_man << 'EOF'
-- 创建测试租户 schema
CREATE SCHEMA IF NOT EXISTS test_tenant;

-- 创建经营数据表
CREATE TABLE IF NOT EXISTS test_tenant.经营数据 (
  id SERIAL PRIMARY KEY,
  月份 VARCHAR(10),
  营收 NUMERIC(15,2),
  成本 NUMERIC(15,2),
  利润 NUMERIC(15,2),
  订单数 INT,
  交付率 NUMERIC(5,2)
);

-- 插入测试数据
INSERT INTO test_tenant.经营数据 (月份, 营收, 成本, 利润, 订单数, 交付率) VALUES
  ('2026-05', 1350.50, 1080.20, 270.30, 45, 91.5),
  ('2026-06', 1423.50, 1105.80, 317.70, 52, 94.2),
  ('2026-07', 1298.00, 1050.00, 248.00, 40, 88.7);

-- 创建员工表（含敏感字段，用于测试脱敏）
CREATE TABLE IF NOT EXISTS test_tenant.员工 (
  id SERIAL PRIMARY KEY,
  姓名 VARCHAR(50),
  手机号 VARCHAR(20),
  邮箱 VARCHAR(100),
  部门 VARCHAR(50)
);

INSERT INTO test_tenant.员工 (姓名, 手机号, 邮箱, 部门) VALUES
  ('张三', '13812345678', 'zhangsan@company.com', '财务部'),
  ('李四', '13987654321', 'lisi@company.com', '运营部');
EOF
# 期望: CREATE SCHEMA / CREATE TABLE / INSERT 0 3 / INSERT 0 2
```

#### P0-5.2 Python Engine 独立测试

```bash
# 启动 Python Engine (HTTP 模式，方便测试)
cd /home/guorongxiao/c2eos-engine
# 先验证 Tool 注册
python3 -c "
from src.engines.data.data import mcp
tools = mcp.list_tools()
print('注册的 Tools:', [t.name for t in tools])
"
# 期望: 注册的 Tools: ['query']

# 测试 query Tool (模拟 CC 调用)
python3 -c "
import asyncio
from src.engines.data.data import query

async def test():
    # 测试1: 查经营数据
    result = await query(
        sql='SELECT * FROM 经营数据 ORDER BY 月份',
        _ctx={'tenantId': 'test_tenant', 'userId': 'admin', 'role': 'admin', 'sessionId': 'test'}
    )
    print('测试1 - 经营数据:')
    print(f'  行数: {result[\"rowCount\"]}')
    print(f'  来源: {result[\"source\"]}')
    print(f'  首行: {result[\"rows\"][0] if result[\"rows\"] else \"无数据\"}')

    # 测试2: 查员工（验证脱敏）
    result2 = await query(
        sql='SELECT * FROM 员工',
        _ctx={'tenantId': 'test_tenant', 'userId': 'admin', 'role': 'admin', 'sessionId': 'test'}
    )
    print('测试2 - 员工数据 (脱敏后):')
    print(f'  脱敏: {result2[\"maskApplied\"]}')
    for row in result2['rows']:
        print(f'  {row}')

    # 测试3: 禁止 INSERT 验证
    result3 = await query(
        sql='INSERT INTO 员工 VALUES (3, \"黑客\", \"111\", \"h@h.com\", \"IT\")',
        _ctx={'tenantId': 'test_tenant', 'userId': 'hacker', 'role': 'admin', 'sessionId': 'test'}
    )
    print(f'测试3 - INSERT 拦截: {result3[\"error\"]}')

asyncio.run(test())
"
# 期望:
# 测试1: 行数: 3 | 来源: test_tenant.经营数据 | 首行带数据
# 测试2: 脱敏: True | 手机号: 138****5678
# 测试3: INSERT 拦截: 只允许 SELECT 查询
```

#### P0-5.3 CC 端到端对话测试

```bash
# 启动 C2EOS 终端
cd /home/guorongxiao/c2eos-platform

# 测试1: 查经营数据（预期 CC 调 data.query Tool）
echo '帮我查一下 test_tenant 的经营数据' | C2EOS_TENANT_ID=test_tenant C2EOS_USER_ID=admin C2EOS_USER_ROLE=admin bun dist/cli.js -p "你是 C2EOS 助手" --mcp-config .mcp.json 2>&1 | head -30

# 测试2: 查员工数据（预期手机号被脱敏）
echo '查一下 test_tenant 的员工信息' | C2EOS_TENANT_ID=test_tenant C2EOS_USER_ID=admin C2EOS_USER_ROLE=admin bun dist/cli.js -p "你是 C2EOS 助手" --mcp-config .mcp.json 2>&1 | head -30

# 测试3: 技能说明
echo '你能帮我做什么？' | C2EOS_TENANT_ID=test_tenant C2EOS_USER_ID=admin C2EOS_USER_ROLE=admin bun dist/cli.js -p "你是 C2EOS 助手" --mcp-config .mcp.json 2>&1 | head -20
```

#### P0-5.4 P0 验收脚本

**文件**: `/home/guorongxiao/c2eos-engine/tests/p0_verify.py`
**操作**: 新建

```python
"""P0 验收脚本 — 一键验证全链路"""
import asyncio
import sys

sys.path.insert(0, "/home/guorongxiao/c2eos-engine")

from src.connectors.pg_connector import query as pg_query
from src.engines.data.data import query as data_query
from src.filters import apply_masking


async def main():
    passed = 0
    failed = 0
    ctx = {"tenantId": "test_tenant", "userId": "admin", "role": "admin", "sessionId": "verify"}

    # Test 1: PG 连接
    try:
        rows = await pg_query("public", "SELECT 1 AS ok")
        assert rows == [{"ok": 1}], f"PG 连接失败: {rows}"
        passed += 1
        print("✅ Test 1: PG 连接")
    except Exception as e:
        failed += 1
        print(f"❌ Test 1: PG 连接 — {e}")

    # Test 2: 查询经营数据
    try:
        result = await data_query("SELECT * FROM 经营数据 ORDER BY 月份", _ctx=ctx)
        assert result["rowCount"] == 3, f"期望 3 行, 实际 {result['rowCount']}"
        assert result["source"] == "test_tenant.经营数据"
        assert "error" not in result
        passed += 1
        print(f"✅ Test 2: 经营数据查询 — {result['rowCount']} 行")
    except Exception as e:
        failed += 1
        print(f"❌ Test 2: 经营数据查询 — {e}")

    # Test 3: 员工脱敏
    try:
        result = await data_query("SELECT * FROM 员工", _ctx=ctx)
        assert result["maskApplied"] is True, f"脱敏未生效"
        phone = result["rows"][0].get("手机号", "")
        assert "*" in str(phone), f"手机号未脱敏: {phone}"
        passed += 1
        print(f"✅ Test 3: 员工脱敏 — 手机号: {phone}")
    except Exception as e:
        failed += 1
        print(f"❌ Test 3: 员工脱敏 — {e}")

    # Test 4: SQL 注入拦截
    try:
        result = await data_query("DROP TABLE 员工", _ctx=ctx)
        assert "error" in result
        passed += 1
        print(f"✅ Test 4: DROP 拦截 — {result['error']}")
    except Exception as e:
        failed += 1
        print(f"❌ Test 4: DROP 拦截 — {e}")

    # Test 5: INSERT 拦截
    try:
        result = await data_query("INSERT INTO 员工 VALUES (99)", _ctx=ctx)
        assert "error" in result
        passed += 1
        print(f"✅ Test 5: INSERT 拦截 — {result['error']}")
    except Exception as e:
        failed += 1
        print(f"❌ Test 5: INSERT 拦截 — {e}")

    print(f"\n{'='*40}")
    print(f"通过: {passed}/{passed+failed}  |  失败: {failed}/{passed+failed}")
    return failed == 0


if __name__ == "__main__":
    ok = asyncio.run(main())
    sys.exit(0 if ok else 1)
```

**执行验收**:
```bash
cd /home/guorongxiao/c2eos-engine
python3 tests/p0_verify.py
# 期望: ✅✅✅✅✅ 全部通过
```

#### P0-5 验收完成

```bash
# Git commit (Engine)
cd /home/guorongxiao/c2eos-engine
git add -A && git commit -m "P0-5: 测试数据 + 端到端验证脚本"

# Git commit (Platform)
cd /home/guorongxiao/c2eos-platform
git add -A && git commit -m "P0-5: 端到端验证完成"
```

---

## 3. P1: 六大引擎全覆盖（4周，7个Task）

> **前置**: P0 全部通过  
> **目标**: 32个 Tool 全部可用，安全过滤器链完整

### Task P1-1: OntologyEngine (3天)

**文件**: `/home/guorongxiao/c2eos-engine/src/engines/ontology/`
**操作**: 新建

```python
# ontology.py — 8个 Tool
# 核心: get_object_type, list_object_types, query_objects, get_links, get_functions, search, import_template, list_domains

# functions.py — Function 计算引擎
# 核心: 表达式解析 + 动态计算
```

**提交**: `git commit -m "P1-1: OntologyEngine 8 Tools"`

### Task P1-2: CognitiveEngine (4天)

**文件**: `/home/guorongxiao/c2eos-engine/src/engines/cognitive/`
**操作**: 新建

```python
# rag.py — RAG 检索
#   依赖: ChromaDB 或 pgvector
#   功能: 语义搜索企业文档/报告

# kg.py — 知识图谱查询
#   依赖: Neo4j
#   功能: Cypher 查询 + 图遍历

# diagnose.py — Agent 偏差诊断
#   功能: 对比 Goal vs Actual → 因果链分析 → 根因定位

# scenario.py — 情景推演
#   功能: What-if 分析 → 多变量模拟
```

**提交**: `git commit -m "P1-2: CognitiveEngine 7 Tools"`

### Task P1-3: SecurityEngine (2天)

**文件**: `/home/guorongxiao/c2eos-engine/src/engines/security/`
**操作**: 新建

**注意**: 安全过滤器（脱敏/RLS/RBAC）已在 `src/filters/` 中实现。SecurityEngine 的 Tool 用于查询权限状态，不用于执行安全策略。

```python
# auth.py — 权限查询 Tool
#   check_permission: 查询用户对资源的权限
#   my_permissions: 当前用户权限列表
#   my_audit_log: 当前用户审计日志
#   data_class: 数据分类分级查询
```

**提交**: `git commit -m "P1-3: SecurityEngine 4 Tools"`

### Task P1-4: RuleEngine + WorkflowEngine (2天)

**文件**: `/home/guorongxiao/c2eos-engine/src/engines/rule/`、`workflow/`
**操作**: 新建

```python
# rule.py — 3 Tools
#   execute: 执行业务规则
#   list_rules: 列出可用规则
#   validate: 规则合规校验

# workflow.py — 3 Tools
#   start: 启动 Pipeline
#   status: 查询 Pipeline 状态
#   list_pipelines: 列出可用 Pipeline
```

**提交**: `git commit -m "P1-4: RuleEngine + WorkflowEngine 6 Tools"`

### Task P1-5: 安全过滤器链完善 (2天)

**文件**: `/home/guorongxiao/c2eos-engine/src/filters/`
**操作**: 完善 P0 的简化版 → 生产版

```python
# 每个 filter 独立文件
# auth_filter.py       → JWT/token 验证 + 会话有效性
# rbac_filter.py       → 从数据库加载角色权限矩阵
# tenant_filter.py     → Schema 前缀注入 (PG/Neo4j/MinIO)
# rls_filter.py        → 行级安全 WHERE 条件
# masking_filter.py    → 列级脱敏规则（从数据库加载）
# audit_filter.py      → 审计日志写入数据库
# chain.py             → 过滤器链编排 (顺序执行)
```

**提交**: `git commit -m "P1-5: 安全过滤器链生产版"`

### Task P1-6: L2 动态知识注入 (2天)

**文件**: `/home/guorongxiao/c2eos-engine/skills/{tenant_id}/`
**操作**: 新建 + 实现

```python
# 新增 cognitive.get_knowledge Tool
# 根据 tenant_id 读取 skills/{tenant_id}/ 下的 JSON
# 返回: ontology + metrics + glossary + rules 的摘要
```

**提交**: `git commit -m "P1-6: L2 动态知识注入"`

### Task P1-7: P1 集成验证 (2天)

- 32 个 Tool 逐一 curl 验收
- 安全过滤器链完整回归测试
- CC 对话覆盖 6 大引擎
- 审计日志完整记录

**提交**: `git commit -m "P1-7: P1 集成验证完成"`

---

## 4. P2: 门户+多租户（4周，8个Task）

### Task P2-1: C2EOS Gateway (Node.js) (3天)

**文件**: `/home/guorongxiao/c2eos-gateway/`
**操作**: 新建项目

```
c2eos-gateway/
├── package.json
├── tsconfig.json
├── src/
│   ├── server.ts          # Express + WebSocket (:3100)
│   ├── auth/              # JWT 签发/验证
│   │   └── jwt.ts
│   ├── session/           # 会话管理
│   │   ├── manager.ts     # CC 进程池
│   │   └── pool.ts        # 空闲回收
│   ├── chat/              # Web Chat 路由
│   │   └── ws.ts          # WebSocket handler
│   └── platforms/         # 消息平台适配
│       ├── wecom.ts       # 企业微信 Bot
│       └── feishu.ts      # 飞书 Bot
└── web/                   # Web Chat 前端
    └── ... (P2-2)
```

**提交**: `git commit -m "P2-1: C2EOS Gateway 骨架"`

### Task P2-2: Web Chat 前端 (3天)

**框架**: React 19 + Vite + Tailwind + WebSocket

**核心组件**:
- `ChatWindow.tsx` — 对话界面
- `MessageBubble.tsx` — 消息气泡（支持 Markdown + 数据表格 + 图表）
- `ConfirmDialog.tsx` — 🟠操作的确认弹窗
- `ToolCallCard.tsx` — Tool 调用过程展示
- `DataSourceBadge.tsx` — 数据来源标注

**提交**: `git commit -m "P2-2: Web Chat 前端"`

### Task P2-3~P2-8 (略)

详细程度由 PMO 在进入 P2 阶段前根据 P0/P1 经验调整。

---

## 5. P3: 产品化（长期）

| Task | 说明 |
|------|------|
| P3-1: Agent Builder | 低代码配置 Agent 的 Tool/Knowledge/Skill |
| P3-2: 飞书接入 | 飞书 Bot + 卡片交互 |
| P3-3: 数据可视化 | ECharts 集成到 Web Chat |
| P3-4: 多 Agent 协作 | 财务 Agent + 运营 Agent 联合分析 |
| P3-5: ECOS 连接器 | 通过 JDBC 访问 ECOS Java 后端的 PG |
| P3-6: Feature Flag 编译注入 | 高频 Tool 编译进 CC 内核，零 MCP 延迟 |

---

## 6. 禁止清单

### 6.1 绝对不能做

1. ❌ **禁止修改 ECOS Java 后端**
2. ❌ **禁止在 CC 里写业务逻辑** — 业务逻辑在 Python Engine
3. ❌ **禁止 Tool 返回未脱敏数据** — 每个 Tool 必须过 masking_filter
4. ❌ **禁止跨租户数据访问** — tenant_id 必须在 Tool 内部强制注入
5. ❌ **禁止 Agent 直接执行 DELETE/DROP/ALTER/TRUNCATE**
6. ❌ **禁止在 Agent prompt 里硬编码企业数据**
7. ❌ **禁止新增 Docker 容器**（用现有 PG/Neo4j/MinIO）

### 6.2 安全红线

- 脱敏规则由安全管理员配置，Agent 不感知
- 🟠及以上操作必须过用户确认流程
- 每条 Tool 调用必须记录审计日志
- 用户密码/Token 不写入日志

---

## 7. 附录: 代码模板

### 7.1 新增 Tool 模板 (Python)

```python
"""{引擎名} — {Tool名} Tool"""
from fastmcp import FastMCP

mcp = FastMCP("C2EOS {Engine Name}")


@mcp.tool()
async def {tool_name}(param1: str, param2: int = 10, _ctx: dict | None = None) -> dict:
    """
    {Tool 功能描述}

    Args:
        param1: {参数1说明}
        param2: {参数2说明，默认 10}
        _ctx: 调用上下文，由 MCP 拦截器自动注入。包含 tenantId, userId, role, sessionId。

    Returns:
        result: {返回结果说明}
        source: 数据来源标识
    """
    # 1. 解包上下文
    tenant_id = _ctx.get("tenantId", "default") if _ctx else "default"
    user_id = _ctx.get("userId", "anonymous") if _ctx else "anonymous"

    # 2. 业务逻辑
    # ...

    # 3. 返回标准格式
    return {
        "result": ...,
        "source": f"{tenant_id}.{数据来源}",
    }
```

### 7.2 注册引擎到 MCP Server

```python
# 在 src/server.py 中追加
from src.engines.{engine_name}.{module_name} import mcp as {name}_mcp
server.mount("{name}", {name}_mcp)
```

### 7.3 新增 CC Feature Flag

```typescript
// 在 build.ts featureFlags 中追加
C2EOS_{ENGINE}_ENGINE: true,
```

### 7.4 新增 CC Tool (如需要编译进 CC 内核)

```typescript
// src/tools/c2eos/{ToolName}Tool.ts
// 参照 CC 现有 Tool 格式:
export const {ToolName}Tool = {
  name: '{tool_name}',
  description: '...',
  inputSchema: { ... },
  handler: async (params, ctx) => { ... }
}

// 在 src/tools.ts 中注册:
const c2eos{ToolName} = feature('C2EOS_{ENGINE}_ENGINE')
  ? [require('./tools/c2eos/{ToolName}Tool').{ToolName}Tool] : []
```
