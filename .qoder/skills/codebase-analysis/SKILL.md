---
name: codebase-analysis
description: "使用 codebase-memory-mcp 分析项目代码结构、调用链和功能模块的标准工作流。覆盖索引检查、多模式搜索、调用链追踪、源码读取的完整链路，包含工具已知缺陷的规避策略。当用户要求'分析代码'、'分析功能'、'分析模块'、'追踪调用链'、'代码结构分析'时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux]
triggers:
  - 分析代码
  - 分析功能
  - 分析模块
  - 追踪调用链
  - 代码结构分析
  - codebase
  - 调用链
metadata:
  hermes:
    tags: [codebase, analysis, mcp, architecture, trace]
    auto_trigger: true
    priority: high
---

# 代码结构分析工作流（codebase-memory-mcp）

## 触发规则

当用户要求分析项目代码、功能模块、调用链、架构结构时，使用 codebase-memory-mcp 工具链进行结构化分析。禁止直接 grep/read 逐文件扫描。

## 完整流程

```
Step 0: 确认索引 → 搜索是否有匹配结果
Step 0.5: 查设计文档 → docs/ 目录中是否有同名功能文档
Step 1: 多模式搜索 → BM25 + name_pattern 定位目标函数
Step 2: 读取源码 → get_code_snippet + read_file 交叉验证
Step 3: 追踪调用链 → trace_path（需传完整 qualified_name）
Step 4: 搜索调用方/被调用方 → 补全上下游
Step 5: 汇总输出 → 调用链图 + 涉及文件清单 + 核心代码
```

---

## Step 0：确认索引

直接执行搜索，若无结果说明项目未索引或索引过时：

```python
mcp_codebase_memory_search_graph(
    project="<project-name>",
    query="关键词",
    limit=30
)
```

- 有结果 → 索引存在，继续
- 无结果 → 需先执行 `mcp_codebase_memory_index_repository`（mode 建议用 "moderate"）

> project-name 通常是项目目录名，如 `ai-navtive-software-factory-pyz`

---

## Step 0.5：查设计文档（功能分析必做）

当用户用**功能名称**要求分析时（如"分析系统自检功能"、"分析信息对齐功能"），先查 `docs/` 目录中是否有对应设计文档，再搜索代码。

**原因**：codebase-memory 的 BM25 搜索可能命中**名称相近但不同的功能**。例如搜"系统自检"可能命中 workspace 模块的 `checkHealth`（工作空间健康检查），而非真正的"系统自检"（`SystemSelfCheckService`）。设计文档能精确定位目标功能的包名、类名、接口路径。

**查找方法**：

```python
# 1. 用 search_files 在 docs/ 中搜索功能名称
search_files(path="<project-root>/docs", pattern="系统自检")

# 2. 或用 execute_code + find/grep 定位
subprocess.run("find <project-root>/docs -name '*.md' | xargs grep -l '系统自检'", shell=True)

# 3. 读取设计文档，提取关键信息：
#    - 功能对应的包名/类名（如 systemcheck）
#    - API 路径（如 /api/system/self-check）
#    - 涉及文件清单
```

**设计文档常见位置**：
- `docs/06运维支持/` — 运维相关功能
- `docs/02设计阶段/` — 架构设计、API 设计
- `docs/01需求分析/` — 需求规格说明

> 如果设计文档存在，先读文档提取类名/路径作为 Step 1 搜索的精准关键词；如果不存在，再回退到模糊搜索。

---

## Step 1：多模式搜索定位

**同时发起两种搜索提高命中率**：

1. **BM25 全文搜索**（自然语言/关键词）：
   ```python
   search_graph(project=..., query="系统自检 health check", limit=30)
   ```

2. **名称模式匹配**（精确函数名）：
   ```python
   search_graph(project=..., name_pattern=".*[Cc]heck.*", limit=30)
   ```

**过滤技巧**：搜索结果常混入 template/ 等无关目录，用 `path_filter` 缩小范围：
```python
search_code(project=..., pattern="checkUrlOnline", path_filter="^backend/src/")
```

---

## Step 2：读取源码（交叉验证）

### 已知缺陷：get_code_snippet 行号偏移

`get_code_snippet` 可能返回**邻近函数**而非目标函数（行号偏移），尤其是大文件中的方法。

**规避策略 — 交叉验证**：
1. 先用 `get_code_snippet` 获取片段和声明的 `start_line`
2. 用 `read_file(path, offset=start_line, limit=50)` 读取确认
3. 若 `get_code_snippet` 返回的 source 与 start_line 不匹配 → 以 `read_file` 为准

```python
# 步骤1：MCP 获取行号
snippet = get_code_snippet(project=..., qualified_name="...ServiceImpl.checkHealth")

# 步骤2：read_file 确认（用 snippet 返回的 start_line）
content = read_file(path=".../ServiceImpl.java", offset=1075, limit=30)
```

### include_neighbors 参数

`get_code_snippet(include_neighbors=true)` 会返回调用方/被调用方信息（caller_names, callee_names），用于快速了解上下游，但源码仍可能有偏移。

---

## Step 3：追踪调用链

### 已知缺陷：trace_path 对同名函数返回 ambiguous

`trace_path(function_name="checkHealth")` 当项目中有多个同名函数时返回 `status: ambiguous`，拒绝执行。

**规避策略 — 传完整 qualified_name**：

从 Step 1 的搜索结果中获取 `qualified_name`，传给 trace_path：

```python
# 错误 — 会 ambiguous
trace_path(function_name="checkHealth", project=...)

# 正确 — 但 trace_path 只接受 function_name，不接受 qualified_name
# 所以需要先用 search_graph 缩小到唯一函数
search_graph(project=..., name_pattern=".*WorkspaceController.*checkHealth")
# 然后用 search_graph 的 include_connected=true 查看关联节点
```

> 如果 trace_path 始终 ambiguous，改用 `search_graph(include_connected=true)` + `get_code_snippet(include_neighbors=true)` 手动构建调用链。

### trace_path 三种模式

| mode | 用途 | edge_types |
|------|------|-----------|
| calls | 调用关系（默认） | CALLS |
| data_flow | 数据流追踪 | CALLS + DATA_FLOWS |
| cross_service | 跨服务追踪 | HTTP_CALLS + ASYNC_CALLS + CROSS_* |

---

## Step 4：补全上下游

### 搜索调用方

```python
search_graph(project=..., query="checkHealth", include_connected=true)
```

### 搜索前端调用

前端调用点搜索需要 regex=true（含 | 或特殊字符）：

```python
search_code(
    project=...,
    pattern="checkHealth|自检",
    path_filter="^frontend/src/",
    regex=true
)
```

> **坑**：`search_code` 的 `pattern` 参数默认 `regex=false`，含 `|` 的模式会被当做字面量匹配。必须显式传 `regex=true`。

### 前端组件中的调用点定位

找到 API 定义后，用 `search_files`（非 MCP）搜索组件中的调用：

```python
search_files(path=".../frontend/src", pattern="workspaceApi.checkHealth")
```

再用 `execute_code` + `grep -n` 获取行号上下文：
```python
subprocess.run("grep -n 'healthStatus\\\\|healthTimer\\\\|doHealthCheck' .../index.vue", shell=True)
```

---

## Step 5：汇总输出

分析报告结构：

```
功能概述
  一句话描述功能用途

完整调用链
  前端 UI → 前端 API → 后端 Controller → 后端 Service → 底层方法
  （标注文件路径和行号）

核心代码详解
  按层级展开关键函数源码

数据结构
  请求/响应 DTO 字段说明

涉及文件清单
  前端文件列表 + 后端文件列表（含行号范围）

特点与局限
  设计特点 + 已知限制
```

---

## 工具速查表

| 工具 | 用途 | 关键参数 | 已知缺陷 |
|------|------|---------|---------|
| search_graph | BM25/名称搜索 | query, name_pattern, path_filter, include_connected | 结果混入 template/ 等无关目录 |
| search_code | grep 式代码搜索 | pattern, regex, path_filter, file_pattern | pattern 默认 regex=false |
| get_code_snippet | 读取函数源码 | qualified_name, include_neighbors | 行号偏移，可能返回邻近函数 |
| trace_path | 调用链追踪 | function_name, mode, direction | 同名函数 ambiguous |
| get_architecture | 架构概览 | aspects, path | — |
| query_graph | Cypher 查询 | query (Cypher) | — |

---

## 常见陷阱

1. **功能名称歧义 → 先查设计文档**：用户说"分析系统自检功能"时，BM25 搜索可能命中名称相近但不同的功能（如 workspace `checkHealth` 而非 `SystemSelfCheckService`）。**必须先在 `docs/` 中搜索功能名称对应的设计文档**，提取精确的类名/包名/API 路径后再搜索代码。详见 Step 0.5。
2. **get_code_snippet 行号偏移**：返回的 source 可能不是 start_line 对应的代码。始终用 `read_file` 交叉验证。
3. **trace_path ambiguous**：同名函数需先用 `search_graph(name_pattern=...)` 缩小到唯一结果。trace_path 只接受 `function_name`，不接受 `qualified_name`，所以名称必须唯一。
4. **search_code regex=false 默认**：含 `|` 的模式需显式传 `regex=true`，否则被当做字面量。
5. **template/ 目录噪音**：搜索结果常包含项目内 template/ 目录的模板代码。用 `path_filter="^backend/src/"` 或 `path_filter="^frontend/src/"` 过滤。
6. **前端组件调用点**：MCP 索引可能不含 Vue 模板中的调用。找到 API 定义后，用 `search_files` 搜索组件中的调用，再用 `execute_code` + `grep -n` 获取行号。
7. **大文件截断**：`read_file` 默认 limit=500，大文件需指定 offset 分段读取。

---

## 参考文档

| 文档 | 内容 |
|------|------|
| `references/codebase-memory-tool-quirks.md` | 工具已知缺陷与规避策略详细记录 |
