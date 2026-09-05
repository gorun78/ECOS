---
name: experience-summary
description: "自动采集项目开发数据（Git提交历史、看板任务记录、各Profile会话历史），由 Agent 自身 LLM 能力生成结构化经验总结文档，写入项目 docs 目录供后续项目参考。当用户请求总结项目开发经验和问题时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [summary, experience, documentation]
    workflow_modes: [L1, L2, L3]
---

# Experience Summary Skill

自动采集项目开发数据 → AI 生成经验总结 → 写入文件，全流程由 Agent 自身能力完成，无需调用外部 LLM API。

## 触发方式

用户通过聊天面板发送 `/experience-summary` 命令，或点击工作区底部「💡经验总结」按钮。

## 执行步骤

### Step 1: 确定项目工作目录

- 读取当前工作目录下的 `.hermes/env.md` 或从当前工作目录推导 projectRoot
- projectRoot 即当前 `pwd` 所在的项目根目录

### Step 2: 采集 Git 提交历史

执行以下命令采集最近 20 条提交：

```bash
git -C "$PROJECT_ROOT" log --oneline -20 --format="%h %s (%an, %ar)"
```

- 失败则标注"无 Git 提交记录"
- 提取内容：提交哈希、提交信息、作者、时间
- 用于分析开发节奏、提交规范、功能演进

### Step 3: 采集看板任务记录

从 `$PROJECT_ROOT/.hermes/agents.json` 的 `boards` 字段读取看板名称：

```bash
BOARD=$(python3 -c "import json; print(json.load(open('$PROJECT_ROOT/.hermes/agents.json'))['boards'])" 2>/dev/null)
```

再获取任务列表（JSON 格式）：

```bash
hermes kanban --board "$BOARD" list --json 2>/dev/null | head -c 10000
```

- board 名从 `agents.json` 的 `boards` 字段获取（唯一权威来源，禁止用 `hermes kanban boards` 的 ● 标记替代）
- 失败则标注"无看板任务记录"
- 提取内容：任务标题、状态（done/blocked/failed）、worker/verifier 结果摘要
- 用于分析协作效率、任务分发问题、审查反馈

### Step 4: 读取 .hermes/agents.json 获取 Profile 列表

读取 `$PROJECT_ROOT/.hermes/agents.json`，解析 `agents[].code` 列表。

```json
{
  "agents": [
    { "name": "pm-pyz", "code": "pm-1784160044022" },
    { "name": "fullstack-pyz", "code": "fullstack-1784160079453" },
    ...
  ]
}
```

- 如果 agents.json 不存在，跳过会话采集，仅用 Git + Kanban 数据

### Step 5: 逐个 Profile 采集会话历史

> ⚠️ **关键修正**：`hermes sessions list` 命令**不支持 `--profile` 参数**，且命令名是 `sessions`（复数）不是 `session`。必须直接读取各 profile 的 SQLite 数据库 `~/.hermes/profiles/{agentCode}/state.db`。

对每个 agentCode，用 Python 读取其 state.db：

```python
import sqlite3, os

db_path = f"/home/hermes/.hermes/profiles/{agentCode}/state.db"
if not os.path.exists(db_path):
    return "(无会话数据库)"

conn = sqlite3.connect(db_path)
cur = conn.cursor()

# 获取最近5条会话
cur.execute("SELECT id FROM sessions ORDER BY rowid DESC LIMIT 5")
session_ids = [r[0] for r in cur.fetchall()]

for sid in session_ids:
    cur.execute(
        "SELECT role, content FROM messages WHERE session_id=? AND role IN ('user','assistant') ORDER BY rowid DESC LIMIT 10",
        (sid,)
    )
    msgs = cur.fetchall()
    for role, content in msgs:
        print(f"[{role}] {(content or '')[:300]}")

conn.close()
```

采集限制：
- 每个 profile 取最近 5 条会话
- 每条会话取最近 10 条 user/assistant 消息
- 单个 profile 会话数据截断至 5000 字符
- 所有 profile 会话数据总长度截断至 20000 字符

覆盖的典型场景：
- Fullstack 多轮对话修复同一个问题（代码无法一次交付，反复调试）
- PM 与下游 Agent 反复沟通需求对齐
- Reviewer 审查打回后 Fullstack 重新修复的来回过程
- Arch 设计调整导致的下游返工
- Commander 协调冲突的沟通过程

### Step 6: 拼接数据，按 Prompt 模板生成总结

将采集的三路数据（Git log + Kanban tasks + Profile sessions）拼接，按照下方 Prompt 模板，由 Agent 自身的 LLM 能力生成总结（无需调用外部 LLM API）。

原始数据总长度截断至 30000 字符（避免 token 超限）。

#### Prompt 模板

```
你是一名经验丰富的软件项目总结专家。请基于以下项目开发数据，生成结构化的经验总结文档。

要求：
1. 总结必须基于提供的真实数据，不要编造
2. 按两个维度组织：技术经验 和 流程经验
3. 每条经验必须具体、可操作、可复用
4. 输出 Markdown 格式
5. 如果数据不足以支撑某个章节，标注"本期数据不足，暂无总结"
6. 重点关注多轮对话中体现的问题调试过程、代码反复修改的教训、Agent协作中的沟通成本
7. 第一行必须是 '# {经验概要}'，经验概要为4-15个中文字符，不含特殊符号

输出结构：

# {经验概要}

> 生成时间：{datetime}
> 项目：{projectName}
> 数据来源：Git提交({gitCount}条) + 看板任务({kanbanCount}条) + 会话记录({sessionCount}条)

## 一、技术经验

### 1.1 代码模式与最佳实践
（从提交历史和会话中提取的代码模式、架构决策、技术选型经验）

### 1.2 解决方案
（开发过程中解决的具体技术问题及方案）

### 1.3 踩坑记录
（开发过程中遇到的陷阱、错误及避免方法）

## 二、流程经验

### 2.1 协作效率
（任务分发效率、Agent 协作模式、并行/串行选择经验）

### 2.2 任务分发问题
（任务拆分粒度、依赖管理、阻塞处理经验）

### 2.3 审查反馈
（Reviewer 审查发现的共性问题、代码质量趋势）

## 三、改进建议
（基于本次总结，对后续项目开发的 3-5 条可操作建议）
```

### Step 7: 写入文件

- 目录：`{projectRoot}/docs/08经验总结/08-02经验记录/`
- 目录不存在则创建（`mkdir -p`）
- 文件名：`{经验概要}-{YYYYMMDD}.md`
  - 经验概要从总结内容的第一行 H1 标题提取，4-15 个中文字符
  - 只允许中文/英文/数字，正则过滤特殊符号
  - 示例：`编码使用codebase-memory-mcp建立知识库-20260811.md`
- 同一天可生成多个文件（概要不同则不覆盖）
- 文件名冲突时自动追加序号：`{概要}-{年月日}-2.md`

### Step 8: 返回结果

- 在聊天面板展示总结内容的 Markdown
- 告知文件保存路径

## 数据采集脚本

Skill 附带采集脚本 `scripts/collect_data.sh`，可将三路数据采集合并为一步执行：

```bash
bash scripts/collect_data.sh
```

脚本会依次输出：
1. `=== GIT LOG ===` — Git 提交历史
2. `=== KANBAN TASKS ===` — 看板任务记录
3. `=== PROFILE SESSIONS ===` — 各 Profile 会话历史

## 边界处理

| 场景 | 处理策略 |
|------|---------|
| Git 仓库不存在或无提交 | 返回空字符串，总结中标注"无 Git 提交记录" |
| 看板无任务 | 返回空字符串，总结中标注"无看板任务记录" |
| 某个 Profile 无会话 | 该 profile 返回空字符串，其他 profile 正常采集 |
| 所有 Profile 会话为空 | 返回空字符串，总结中标注"无会话记录" |
| 三路数据全部为空 | Agent 返回"开发数据不足，无法生成总结" |
| 文件写入失败 | Agent 返回"文件保存失败，请检查目录权限" |
| 文件名冲突 | 自动追加序号：`{概要}-{年月日}-2.md` |
| agents.json 不存在 | 跳过会话采集，仅用 Git + Kanban 数据 |
| hermes sessions list 不支持 --profile | 中 | 直接读取 `~/.hermes/profiles/{agentCode}/state.db` SQLite 数据库，查 sessions 表和 messages 表 |
| 文件名含非法字符 | 概要只允许中文/英文/数字，正则过滤 |

## 输出文件规范

- 存储路径：`{projectRoot}/docs/08经验总结/08-02经验记录/{经验概要}-{YYYYMMDD}.md`
- 写入前检查目录是否存在，不存在则创建
- 文件命名格式：`{经验概要}-{YYYYMMDD}.md`
- 文件内容为 Markdown 格式，结构见 Prompt 模板中的输出结构
