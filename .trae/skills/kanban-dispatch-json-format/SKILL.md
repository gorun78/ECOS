---
name: kanban-dispatch-json-format
description: "PM 任务派发一站式 Skill：从 agents.json 解析 board 和可用 agent，通过 hermes kanban --board swarm 派发任务，主动追踪 worker→verifier→synthesizer 验收结果，自动修复 blocked（3次重试）。看板后端已改为 hermes kanban list --json 实时获取，不再需要生成 kanban.json。"
version: 3.5.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
triggers:
  - 看板
  - kanban
  - 派发
  - 分发
  - swarm
  - 分配任务
  - 任务调度
  - board
  - dispatch
  - 负责任务
  - 交给
  - 指派
metadata:
  hermes:
    tags: [pm, kanban, dispatch, json, task-plan, ui-render, board, agents-json, tracking]
    profiles: [pm-1784271841029]
    auto_trigger: true
    priority: high
---

# Kanban 任务派发一站式 Skill

## 🔴🔴 触发规则（最高优先级）

当用户消息包含以下任一关键词时，本 Skill 自动激活，PM 必须按 swarm 流程派发，禁止自行执行：

| 触发词 | 示例 |
|--------|------|
| 看板、kanban、board | "看一下看板"、"kanban 状态" |
| 派发、分发、dispatch | "派发任务"、"分发一下" |
| swarm | "用 swarm 执行" |
| 分配任务、任务调度 | "把这个分配给 Fullstack" |
| 交给、指派、负责任务 | "交给后端处理"、"指派给 QA" |

**激活后强制行为锁：**

1. 任何代码修改、文件读写、命令执行 —— 必须通过 `hermes kanban --board <board> swarm` 派发，PM 本体禁止直接操作
2. 复杂度评估正常进行，但评估后必须走 swarm，不得因 "L1 很简单我自己来" 跳过
3. 唯一的例外：读取 agents.json、执行 `hermes kanban list/boards` 等派发前置检查命令，PM 可以自行执行

## 🔴 PM 角色红线

1. **PM 只调度，不执行**：PM 禁止自己去查数据、写代码、调 API、搜网页。所有执行工作通过 `hermes kanban --board <board> swarm` 派发给下游 Agent。
2. **派发后必须主动追踪**：持续轮询 worker→verifier→synthesizer，全部 done 后才汇报。禁止回复"任务已派发请稍等"后撒手不管。
3. **board 和 agent 必须从 agents.json 读取**：不得自行猜测 board，不得硬编码 agent profile ID。缺失 agents.json 时立即报错，不自动回退。

---

## 完整派发流程

```
Step 0: 解析 agents.json → 确定 TERMINAL_CWD + board + agent 列表
Step 1: 评估复杂度（L1/L2/L3）
Step 2: 🔴 确认 board：hermes kanban boards（确认 ● 在正确 board 上，不对则 switch）
Step 3: 去重检查 hermes kanban list
Step 4: 执行 hermes kanban --board <board> swarm
Step 5: 从 swarm 返回值提取 root_id + worker_ids + verifier_id + synthesizer_id
Step 6: 🔴 主动追踪 worker → verifier → synthesizer（见"追踪与验收"）
Step 7: 🔴 验证 git diff 确认产出落回项目目录（代码类任务必做）
Step 8: 汇总结果汇报用户
```

---

## Step 0：解析 agents.json（派发前必做）

### 0.1 解析 TERMINAL_CWD

```bash
python3 -B <skill-dir>/scripts/resolve_terminal_cwd.py --env-file <profile-path>/.env
```

> 输出为绝对路径，设为 `<TERMINAL_CWD>`。`<output-dir>` = `<TERMINAL_CWD>/.hermes`

### 0.2 读取 agents.json

```bash
cat <output-dir>/agents.json
```

### 0.3 agents.json 格式

```json
{
  "project": "AI-Native-Factory",
  "boards": "ai-native-factory",
  "agents": [
    {"name": "AI-Native-项目经理", "code": "pm-1784271841029"},
    {"name": "AI-Native-测试工程师", "code": "qa-1784271911442"},
    {"name": "AI-Native-全栈工程师", "code": "fullstack-1784098453689"},
    {"name": "AI-Native-架构师", "code": "arch-1785224485752"},
    {"name": "AI-Native-审查者", "code": "reviewer-1786008940272"}
  ]
}
```

> 💡 `code` 只需按前缀匹配即可区分角色：`pm-` / `arch-` / `fullstack-` / `reviewer-` / `qa-`。后缀数字不重要。

| 字段 | 规则 |
|------|------|
| `project` | 非空字符串，填入 `kanban.json.project` |
| `boards` | 非空字符串，作为所有 `hermes kanban` 命令的 `--board` 参数 |
| `agents[].code` | 用于 `--worker`/`--verifier`/`--synthesizer`/`--created-by` 参数和 `tasks[].assignee` |
| `agents[].name` | 用于 UI 展示 `display_name` |

### Agent 角色分配规则

| agent | 角色 | 适用阶段 |
|-------|------|---------|
| `pm-` 开头 | PM 管理/汇总 | synthesizer、created-by |
| `arch-` 开头 | 架构设计 | L2 轻量架构 / L3 完整架构 |
| `fullstack-` 开头 | 前后端开发 | 代码实现、构建部署 |
| `reviewer-` 开头 | Reviewer 审查 | verifier（审核验证）— 有则优先 |
| `qa-` 开头 | QA 测试 | verifier 降级（无 reviewer- 时）、QA 测试 |

> 🔴 **Verifier 优先级**：`reviewer-*` > `qa-*`。绝不可用 `pm-*` 或 `arch-*`。

### 0.4 边界处理

| 情况 | 处理 |
|------|------|
| `agents.json` 不存在 | 立即告知用户"项目缺少 agents.json，无法确定 board" |
| `agents.json` 格式不合法 | 告知具体字段问题 |
| `boards` 指向不存在的 board | 告知用户，列出可用 board 候选 |
| `agents[].code` 不是有效 profile | `hermes profile list` 校验，不存在则告知 |

**绝不自动回退到 default board。**

### 0.5 去重检查（派发前必做）

```bash
hermes kanban list
```

检查规则：
- 若已存在同名或同 feature 的任务且状态为 running/done — **不要重复派发**，直接追踪现有任务
- 若已有任务的 worker block 但 verifier/synthesizer 还是 todo — **unblock worker 重试**
- 若上一条 swarm root 已是 done/archived — 说明当前 wave 已完成，**直接进入下一 wave**

---

## Step 3：Swarm 命令模板

### 执行工具优先级

1. **`terminal` 工具** — 首选，直接执行 hermes CLI
2. **`execute_code`** — terminal 不可用时使用
3. **dry-run 降级** — hermes CLI 完全不可用时生成 t_pre_ 占位

### 命令格式

```bash
# Step 0: 从 agents.json 按前缀匹配 agent code
# verifier 优先 reviewer-*，无则 qa-*
VERIFIER=$(python3 -c "
import json
agents = json.load(open('.hermes/agents.json'))['agents']
codes = [a['code'] for a in agents]
rv = next((c for c in codes if c.startswith('reviewer-')), None)
if not rv: rv = next((c for c in codes if c.startswith('qa-')), 'qa-1784271911442')
print(rv)
")

WORKER=$(python3 -c "
import json
agents = json.load(open('.hermes/agents.json'))['agents']
codes = [a['code'] for a in agents]
print(next((c for c in codes if c.startswith('fullstack-')), 'fullstack-1784098453689'))
")

PM=$(python3 -c "
import json
agents = json.load(open('.hermes/agents.json'))['agents']
codes = [a['code'] for a in agents]
print(next((c for c in codes if c.startswith('pm-')), 'pm-1784271841029'))
")

hermes kanban --board <boards> swarm \
  --worker ${WORKER}:{任务标题} \
  --verifier ${VERIFIER} \
  --synthesizer ${PM} \
  --created-by ${PM} \
  --json \
  "{goal}"
```

> ⚠️ `--worker` 格式必须为 `profile:title`，单独 profile code 会报错 `worker must be profile:title`
> 
> 🔴 **Verifier 必须是 Reviewer（`reviewer-*`）不是 PM**。PM 同时是任务派发者和验收者会造成利益冲突。Verifier 应从 agents.json 按 `reviewer-*` 前缀匹配，无则降级 `qa-*`，PM 只负责 synthesizer 和 created-by。

### 复杂度评估

| 总分 | 等级 | 推荐工作流 | worker 分配 |
|------|------|-----------|------------|
| 6-18 | L1 极简 | 直接 swarm 一个 worker | `fullstack-*` |
| 19-36 | L2 标准 | Arch 轻量设计 → 前后端并行 | Wave1: `arch-*`, Wave2: `fullstack-*` |
| 37-60 | L3 复杂 | Arch → Backend+Frontend → QA+Reviewer | Wave1: `arch-*`, Wave2: `fullstack-*`, Wave3: `reviewer-*` 或 `qa-*` |

### Arch 阶段输出产物

架构师 `arch-1785224485752` 拥有 **PlantUML** 技能（支持所有 UML/非 UML 图表类型，可导出 .puml + .svg/.png）。

| 等级 | Arch 输出 | PlantUML 图表 |
|------|----------|--------------|
| L2 轻量设计 | API 规范 + 数据模型 | 时序图（API 调用流）、ER 图（可选） |
| L3 完整架构 | 详细设计文档 + 完整架构方案 | ER 图、组件图、部署图、时序图、模块依赖图等全套 |

> 架构师在 swarm goal 中明确要求输出 PlantUML 图表时，会自动调用 plantuml skill 生成 .puml 源文件并转换为 .svg 图片。

---

## Step 5：🔴 追踪与验收

### 核心原则

1. PM 主动轮询，不等用户催：每 30~60 秒轮询一次
2. 逐层等待：worker → verifier → synthesizer，下一层需等上一层 done
3. 全部完成后才汇报：三层全部 done 后一次性汇报
4. blocked 自动修复：发现 blocked 先尝试修复（见下方），3 次失败再升级人工
5. 🔴 **代码类任务验证 git diff**：worker 完成代码任务后，必须 checked diff 确认产出落回项目目录

### 🔴 swarm CLI worker 标题格式（冒号陷阱——常见事故源）

swarm CLI 解析 `--worker` 参数的格式为 `profile:title[:skill]`。冒号是分隔符。

**正确写法**：
```
--worker "arch-1785224485752:概要设计"
→ profile=arch-1785224485752, title=概要设计
```

**错误写法**：
```
--worker "arch-1785224485752:设计:概要设计"
→ profile=arch-1785224485752, title=设计, skill=概要设计
→ 系统查找名称为 概要设计 的 skill → Unknown skill(s): 概要设计 → exit code 1 crash
```

**规则**：Worker 标题中禁止出现第二个冒号。如果标题需要描述性文字（如"设计:概要设计"），改为破折号或空格（如"概要设计"或"设计-概要设计"）。

**以下是 PM 追踪的完整脚本模板：**

```python
import subprocess, json, time, re, os

board = "<boards>"  # 从 agents.json 读取

# ===== 第 1 层：追踪 Worker =====
print("▶ 追踪 Worker...")
worker_done = False
for i in range(40):
    time.sleep(30)
    r = subprocess.run(["hermes", "kanban", "--board", board, "show", worker_id, "--json"],
                      capture_output=True, text=True)
    try:
        d = json.loads(r.stdout)
        s = d.get("task", {}).get("status", "unknown")
        print(f"  [{i*30+30}s] worker → {s}")
        if s == "done":
            worker_done = True
            break
        if s == "blocked":
            # 自动修复（见 blocked 修复策略表）
            subprocess.run(["hermes", "kanban", "--board", board, "unblock", worker_id], capture_output=True)
    except json.JSONDecodeError:
        print(f"  archived = done"); worker_done = True; break

if not worker_done:
    print("⚠ worker 超时，继续...")

# ===== 第 2 层：追踪 Verifier =====
print("▶ 追踪 Verifier...")
for i in range(10):
    time.sleep(30)
    r = subprocess.run(["hermes", "kanban", "--board", board, "show", verifier_id, "--json"],
                      capture_output=True, text=True)
    try:
        d = json.loads(r.stdout)
        s = d.get("task", {}).get("status", "unknown")
        print(f"  [{i*30+30}s] verifier → {s}")
        if s == "done": break
    except: break

# ===== 第 3 层：追踪 Synthesizer =====
print("▶ 追踪 Synthesizer...")
for i in range(10):
    time.sleep(30)
    r = subprocess.run(["hermes", "kanban", "--board", board, "show", synthesizer_id, "--json"],
                      capture_output=True, text=True)
    try:
        d = json.loads(r.stdout)
        s = d.get("task", {}).get("status", "unknown")
        print(f"  [{i*30+30}s] synthesizer → {s}")
        if s == "done": break
    except: break

# ===== 🔴 追踪完成后直接汇报（kanban.json 已废弃）=====
# 看板后端已改为 hermes kanban list --json 实时获取数据，
# 不再依赖 .hermes/kanban.json 文件，无需生成。
```

---

```python
import subprocess, json, time

board = "<boards>"
for layer, tid in [("Worker", worker_id), ("Verifier", verifier_id), ("Synthesizer", synth_id)]:
    for attempt in range(40):
        r = subprocess.run(["hermes", "kanban", "--board", board, "show", tid, "--json"],
                          capture_output=True, text=True)
        try:
            d = json.loads(r.stdout)
            status = d.get("task", {}).get("status", "unknown")
            print(f"[{attempt*30}s] {layer} {tid} → {status}")
            if status == "done":
                for evt in d.get("events", []):
                    if evt.get("kind") == "completed":
                        print(f"Summary: {evt.get('payload', {}).get('summary','')[:500]}")
                break
            elif status == "blocked":
                # 进入自动修复流程
                break
        except json.JSONDecodeError:
            print(f"[{attempt*30}s] GC'd = archived")
            break
        time.sleep(30)
```

> ⚠️ 已被 GC 清理的任务 `hermes kanban show` 返回空字符串，需 `try/except JSONDecodeError` 兜底，fallback 为 archived（= done）。

### 🔴 blocked 自动修复（3 次重试后升级）

```
第1次：提取原因 → unblock / 等待重试 → 等 30s → 检查
第2次：换策略（备用数据源/延长超时）→ 等 60s → 检查
第3次：激进修复（换 worker / 拆分任务）→ 等 60s → 检查
仍 blocked → 🚨 升级人工
```

| 阻塞类型 | 第1次 | 第2次 | 第3次 |
|---------|-------|-------|-------|
| 依赖未完成 | 等 60s unblock | 等 120s unblock | `hermes kanban --board <b> unblock {id}` |
| 外部服务超时 | unblock 重试 | 换备用数据源 | 换 worker profile |
| API 错误 | unblock + 等 30s | 换备用 API | 换 worker profile |
| protocol_violation | 直接换 worker profile | 换 worker + 不同 prompt | **升级人工** |
| review-required (自block) | git diff 验证代码落地 → unblock | 代码未落地 → 派发归集 swarm | **升级人工** |

### 超时降级

| 层级 | 阈值 | 超时动作 |
|------|------|---------|
| worker | 5min | 报告用户，继续轮询 |
| verifier | 3min | 跳过，直接进 synthesizer |
| synthesizer | 3min | 手动汇总 worker + verifier 结果 |

### 🔴 Worker 自 block（review-required）

**症状**：worker 完成任务后主动 block 自己，reason 为 `review-required: ... 需人工审核/验证 ...`，kind 为 `needs_input`。本会话中 fullstack-1784098453689 多次出现此模式——代码已产出并写入 git working tree，但 worker 仍自 block 等人工确认。

**处理**：
1. 先 `git diff --stat` 确认代码是否已落地到项目目录
2. 代码已产出且 diff 验证通过 → `hermes kanban --board <board> unblock {id}` 继续流程
3. 代码未落地（swarm workspace 隔离导致）→ 先 unblock，再派发新 swarm 将产物合并到项目目录
4. unblock 后 worker 可能崩溃（protocol_violation: nonzero_exit）→ 代码已产出则标记 done，不重复 unblock

**禁止**：不验证 git diff 就直接 unblock 让 worker 继续跑——它可能已完成工作在 scratch workspace，继续跑只会重复崩溃（gave_up after N failures）。

---

## 🔴 Swarm 工作空间隔离（关键陷阱 ⚠️ 最高优先级）

1. **`terminal`** — 首选。直接执行所有 hermes CLI 命令
2. **`execute_code`** — 用于追踪脚本
3. **dry-run 降级** — hermes CLI 不可用时生成 t_pre_ 占位

---

## 🔴 Swarm 工作空间隔离（关键陷阱 ⚠️ 最高优先级）

`hermes kanban swarm` 在每个 Wave 创建一个隔离的 scratch workspace（如 `/home/hermes/prj/AI-Native-Factory/48/`），**不是用户项目目录**。所有 worker 产生的代码、文档都落在这个 workspace。

**核心结论：swarm worker 的修改不会自动归集到项目目录。GP（当前项目）目录的修改需要显式的第二次 swarm 或手动操作。**

**真实案例（2026-07-28）**：Wave 1 修复系统管理菜单，worker 在 scratch workspace 完成代码但产物未落回 GP 目录。Git diff 为空。必须追加 Wave 2 将代码应用到项目路径。

**影响**：
- Arch 产出的 ARCH_SPEC 在 workspace 的 `docs/arch/`，不在用户项目的 `docs/arch/`
- Reviewer 派发时，其 scratch workspace 是另一个隔离目录，默认**看不到前面 worker 产出的代码**
- 用户项目的 `kanban.json` 和 `features_v2.json` 在用户项目目录，不在 swarm workspace
- **代码修复类任务必须显式给出项目绝对路径，并在 prompt 中要求 worker 写回项目目录**

**必须做的事**：
1. 每波 swarm 完成后，**立即检查 git diff** 确认产出是否落在项目目录
2. 派发 Reviewer 时，在 prompt 中**显式给出前面 worker 产出的绝对路径**
3. 如果 git diff 为空，说明 worker 产出在 scratch workspace 没回来 → 追加 Wave 应用回项目
4. 全部 Wave 完成后，汇总产出物路径列表给用户
5. 多 Wave 之间不存在同一 workspace——每波 swarm 是独立隔离的

---

## 常见陷阱

1. 不读 agents.json 直接切 default board
2. swarm 漏 `--board`
3. 🔴 **Verifier 用了 PM 而非 Reviewer**：`--verifier` 必须从 agents.json 按前缀 `reviewer-*` 匹配，无则降级 `qa-*`，绝不能用 `pm-*`。
3. 🔴 **`--worker` 标题冒号陷阱（本会话累计 9 次崩溃的根因）** —— `--worker` 格式为 `profile:title`，swarm CLI 将 `profile:title:anything` 解析为 `profile:title:skill`，`anything` 被当成 skill 名去匹配 → `Unknown skill(s): anything` 错误 → worker exit code 1 → crash。**正确写法** `arch-1785224485752:概要设计`（只有一个冒号），**错误写法** `arch-1785224485752:设计:概要设计`。title 中绝对不能含 `:`。详见 `references/worker-title-colon-trap.md`。
4. 派发后不追踪到全部 done
5. blocked 不处理/不修复
6. 覆盖 agents.json
9. 🔴 **Worker 标题禁止冒号** —— `arch-1785224485752:概要设计` 正确，`arch-1785224485752:设计:概要设计` 的第二个冒号被 swarm CLI 解析为 `title:skill` 分隔符 → `Unknown skill(s): 概要设计`。标题中只能有一个冒号，冒号后就是 worker 标题，不含冒号。
6. 🔴 **Swarm worker 产出的前端代码必须 build 验证** —— worker 改 `workspace/index.vue` 可能产生 `Unexpected token` 等语法错误，导致 `npm run build` 失败。部署前必须先 build，失败则 `git checkout HEAD --` 回退。
7. 🔴 **refreshStatus 不要调 Hermes Board API** —— 该端点已下线（404），应直接用 `executeKanbanList` 刷新。详见 `references/refresh-status-cli-simplification.md`。
8. 🔴 **Board 上下文隔离** —— 不同项目不能共用 board。后端 `resolveBoard` 从 TERMINAL_CWD/.hermes/agents.json 读 `boards` 字段；前端用 `workspaceApi.getFile(projectId, agentId, '.hermes/agents.json')` 读项目自己的 board。
9. 忽略 swarm workspace 隔离
10. 不检查 board 就派发（重复 swarm）
11. cross feature 混淆 task ID
12. 🔴 **skill 跨 profile 不可用** —— swarm worker 的 skill 仅在其所属 profile 下生效。如果 goal 中指定了某个 skill 名称，必须先确认该 skill 存在于目标 worker profile 的 `skills/` 目录。如果只有 arch profile 有 `design-software-architecture` 而 fullstack profile 没有，切换到 fullstack 时 worker 会崩溃（exit code 1，报错 `Unknown skill(s)`）。解决：将 skill 同步到所有可能需要执行该类任务的 worker profile
12. **board 被意外切换**：某些操作会导致 board 回退到 default。每次派发前 `hermes kanban boards` 确认当前 board，不对则 `switch`
13. **worker 自 block 循环**：worker 完成后自 block（`review-required`）。先 `git diff` 验证代码已落地再 unblock。unblock 一次后仍 block 且代码已有产出 → 直接视为完成，不要无限 unblock
14. **protocol_violation 后代码可能已落地**：worker 进程退出但代码已通过 git 落到项目目录。先 `git diff --stat` 检查再决定
15. **同类多页面修复遗漏**：用户说"一样的改法"时先全局搜索所有同类文件再派发
16. **patch() 工具在 .vue 文件中正则双重转义**：在 .vue 文件 `<script>` 块中用 patch() 修改含正则或模板字符串的代码时，JSON 序列化会引入额外转义。修复：不用模板字符串，改用字符串拼接；大改动直接用 write_file 重写整个函数块
17. **Git diff 显示改动但 search_files 搜不到关键词**：search_files 在 scoped style 等区域可能漏搜，直接 grep 验证
18. **board 漂移到 default**：kanban gc、其他会话 swarm 等操作可能切换 board。派发前必须 `hermes kanban boards` 确认 ● 在正确 board 上。本会话中 3 次因 board 切换导致任务派到错误 board。
19. **hermes kanban list --json 一次性获取全量数据**：不要逐条 `show`。`hermes kanban --board <board> list --json` 返回完整 JSON 数组，包含 body/priority/created_at/completed_at 等全部字段。逐条 `show` 会导致 O(n) 网络调用，100+ 任务时超时。kanban.json 生成和前端看板后端都应使用此方法。
20. 🔴 **Board 上下文隔离（完整修复链路）**：所有项目共用同一 profile 时，看板展示同一 board 任务。修复分三步：(1) `resolveBoard(profile, board)` 传空时不能返回 "default"，必须从 `TERMINAL_CWD/.hermes/agents.json` 读取真实 board；(2) `refreshStatus` 的 `hermesFeign.getKanbanBoard(null)` 是 Bug——null 导致 API 返回默认 board 而非目标 board，必须传 `resolveBoard(profile, board)` 的结果；(3) 前端 workspace 从项目 agents.json 读 board 传给 KanbanPanel。详见 `references/board-context-isolation-root-cause.md` 和 `references/refresh-status-npe.md`。
21. **批量删除正确方式**：`hermes kanban --board {board} archive {taskId}` 逐条归档，全部完成后执行 `hermes kanban --board {board} gc` 清理。gc 只在批量删除完成后执行一次，单条删除只 archive 不 gc。不可直接调用不属于 hermes kanban 的删除命令。
22. **前端 build 失败后 git revert**：worker 产出代码在 scratch workspace 可能通过构建，但项目目录 npm run build 失败。先 git checkout HEAD -- <files> 回退所有前端文件，npm run build 确认干净版本通过，再最小化重新派发。详见 `references/frontend-build-failure-recovery.md`。
23. **refreshStatus NPE**：`root.get("ui_task_list").get("rows")` 链式调用在 kanban.json 结构不完整时 NPE。所有 JSON 树枚举访问必须逐级判空。详见 `references/refresh-status-npe.md`。
25. 🔴 **vite.config.js skip-worktree 拉取冲突**：该文件已设 `git update-index --skip-worktree` 永久跳过提交，但 `git pull` 时若远端有该文件的更新，Git 仍会报 `Your local changes to the following files would be overwritten by merge: frontend/vite.config.js`。原因是 skip-worktree 不阻止 Git 检测到本地文件与远端版本差异。解决方法：(1) `git update-index --no-skip-worktree frontend/vite.config.js` 取消保护，(2) `git fetch origin && git reset --hard origin/user/zhaohui` 强制同步远端，(3) `git update-index --skip-worktree frontend/vite.config.js` 恢复保护。拉取完成后端口值需手动改回 21574/28081。
26. 🔴 **patch() 工具在 .vue 文件中留下死代码**：多次 patch 同一个函数可能导致旧代码块残留（如函数体外的孤儿 fallback 循环），引发 `Unexpected token` 构建错误。出现此类错误时，用 `read_file` 完整读取目标区域，清理所有不在函数体内的代码行。详见 `references/frontend-build-failure-recovery.md`。
27. 🔴 **mvn clean package 必须在 backend/ 目录执行**：项目根目录没有 pom.xml。命令：`cd backend && mvn clean package -DskipTests`。启动 jar 路径也相应调整为 `backend/target/ainative-factory-1.0.0.jar`。
28. 🔴 **Board 不存在校验**：agents.json 中的 boards 字段可能被其他人改成不存在的值。后端 `executeKanbanList` 中应调用 `fetchAvailableBoards(cwd)` 执行 `hermes kanban boards list` 获取所有可用 board，校验 resolvedBoard 存在。不存在时明确报错并列出可用 board，而不是默默返回 null 导致 404。
29. 🔴 **refreshStatus 不要调 Hermes Board API（已下线）**：`hermesFeign.getKanbanBoard()` 调 `/api/plugins/kanban/board` 返回 404。应直接用 `executeKanbanList(profile, board)` 重新获取 CLI 数据即可刷新状态，不依赖任何内部 REST API。旧代码 80+ 行全部删除，新实现仅 10 行。
30. 🔴 **Java 进程强杀必须 `pkill -9 -f`**：`pkill -f 'ainative-factory-1.0.0.jar'` 可能杀不掉所有旧进程（同一 jar 多个实例、子进程未响应 TERM），导致旧代码继续处理请求、新代码不生效。部署脚本必须用 `pkill -9 -f 'ainative-factory-1.0.0.jar'` 然后 `sleep 2` 确保端口释放。验证方法：`lsof -i :28081` 确认只有一个新 PID 监听。
31. 🔴 **features_v4.json 写回模式**：功能开发任务完成后，按功能编号更新 `.hermes/features/features_v4.json` 的 status 字段（done/failed）。features 文件结构：`{ "features": [{ "id": "F-02", "status": "doing", ... }], "version": "v4" }`。swarm goal 中必须写明此要求，PM 在追踪完成后主动执行写回。

## 全自动模式（auto_pipeline）

当 PM `config.yaml` 的 `auto_pipeline.enabled: true` 且用户消息命中触发词「零干预 / 全自动执行 / 自动编排」时，本 Skill 进入全自动模式，按 `references/auto-pipeline-contract.md` 编排：

| 环节 | 标准模式 | 全自动模式 |
|------|---------|-----------|
| 复杂度评估 | 输出 + 等待确认 | 跳过确认，直接派发 |
| 派发追踪 | 手动触发追踪 | 自动 dispatch_and_track |
| Synthesizer 崩溃 | 手动汇合 | crash 2 次自动降级汇合 |
| P0 修复 | 手动决策 | 自动派发修复 swarm（最多 3 轮） |
| 部署验证 | 用户说「需要」 | 按 `phase` 分级（phase≥2 自动） |
| Git 提交 | 用户说「提交」 | 按 `phase` 分级（phase=3 自动） |

**全自动模式红线**：

1. 分支白名单校验不变：主干 `main/master/develop/test` 仍拒绝直推，走 MR/PR
2. 部署前端口归属校验不变：端口被其他项目占用时拒绝操作
3. 分级放行 `phase` 是权威门禁，默认 phase=1（仅评估→审查自动，部署/提交仍人工）
4. 用户 out-of-band 指令可随时中断自动流程

详细契约见 `references/auto-pipeline-contract.md`。

## 脚本清单

| 脚本 | 用途 |
|------|------|
| `scripts/resolve_terminal_cwd.py` | 从 profile .env 解析 TERMINAL_CWD |

## 参考文档

| 文档 | 内容 |
|------|------|
| `references/collaboration-contract.md` | PM 跨 profile 协作契约（分发/追踪/汇合门禁/修复分发/人工审核/异常处理） |
| `references/auto-pipeline-contract.md` | PM 全自动流程编排契约（触发条件/phase 分级放行/主流程/超时权威值/异常处理） |
| `references/dispatch-values-spec.md` | dispatch-values.json 写法规范 |
| `references/hermes-dispatch-rules.md` | Hermes swarm 派发规则 |
| `references/farmmall-dispatch-reference.md` | 已验证的完整派发命令模板 |
| `assets/hermes-kanban.schema.json` | kanban.json 权威 Schema |
| `assets/dry-run-values.example.json` | dry-run 示例 |
| `references/worker-title-colon-trap.md` | Worker 标题冒号错误导致的 Unknown skill 陷阱 |
| `references/plantuml-huffman-encoding.md` | PlantUML SVG Huffman 编码 ~1 前缀修复 |
| `references/worker-self-block-pattern.md` | Worker 自 block（review-required）模式处理 |
| `references/swarm-workspace-isolation-lesson.md` | 工作空间隔离真实案例与验证流程 |
| `references/kanban-data-pipeline.md` | Kanban 端到端数据管道：CLI→后端→前端链路、超时与缓存 |
| `references/detailed-design-directory-template.md` | 详细设计文档目录结构、标题编号规范与合并方法 |
| `references/swarm-batch-module-design.md` | 架构设计 Wave D3 逐模块批量处理模式与中断恢复 |
| `references/board-context-isolation-root-cause.md` | Board 隔离根因：resolveBoard 降级 default 导致所有项目看板相同 |
| `references/frontend-build-failure-recovery.md` | 前端 Worker 产出 npm build 失败：git revert 恢复流程 |
| `references/refresh-status-npe.md` | refreshStatus 链式 .get() NPE 空指针修复 |
| `references/detailed-design-merge-pattern.md` | 详细设计文档合并为系统详细设计.md的正确方法 |
| `references/markdown-toc-scroll-pattern.md` | Markdown TOC 目录导航 scrollIntoView 多次迭代修复 |
