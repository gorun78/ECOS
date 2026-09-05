# FEAT-0015 自动驾驰模式 — Swarm 派发案例

## 会话概要

- 功能：FEAT-0015 自动驾驰模式
- 工作流：L2 标准 (Arch → Backend+Frontend 并行 → Reviewer)
- 日期：2026-07-27
- 结果：3/4 任务完成，Reviewer blocked（workspace 不一致）

## DAG 依赖图

```
t_pre_001 (Arch设计)
  ├── t_pre_002 (Backend) ──┐
  └── t_pre_003 (Frontend) ─┤
                             └── t_pre_004 (Reviewer)
```

## 三波派发详情

### Wave 1: Arch 轻量设计
```bash
hermes kanban swarm \
  --worker "fullstack-1784098453689:[Arch] 自动驾驰模式-状态机与API规范设计" \
  --verifier pm-1784271841029 --synthesizer pm-1784271841029 \
  --created-by pm-1784271841029 --priority 0 --json \
  "功能编号: FEAT-0015。..."
```
- root: t_008f1db3, worker: t_f697e19e
- Synthesizer 产出路径: `/home/hermes/prj/AI-Native-Factory/48/docs/arch/auto-drive-spec.md`

### Wave 2: Backend + Frontend 并行
```bash
hermes kanban swarm \
  --worker "fullstack-1784098453689:[Backend] ..." \
  --worker "fullstack-1784098453689:[Frontend] ..." \
  --verifier pm-1784271841029 --synthesizer pm-1784271841029 \
  --created-by pm-1784271841029 --priority 0 --json "...."
```
- root: t_a726c390, workers: t_0fd187f3(BE), t_6ed03ed8(FE)
- Synthesizer 产出路径: `/home/hermes/prj/AI-Native-Factory/48/`

### Wave 3: Reviewer → BLOCKED
```bash
hermes kanban swarm \
  --worker "qa-1784271911442:[Reviewer] FEAT-0015 自动驾驰模式-代码审查" \
  ... --json "..."
```
- root: t_447337eb, worker: t_64e93548
- **阻塞原因**：Reviewer 在 workspace_path=/home/hermes/prj/AI-Native-Factory/3 中查找源文件
- **实际代码位置**：/home/hermes/prj/AI-Native-Factory/48/ （swarm 自建 workspace）
- **package 名差异**：生成代码用 `com.ainative.factory`，项目3用 `com.chinacreator.ai.nativex.factory`

## 根本原因

`hermes kanban swarm` 创建独立编号 workspace（如 48），不写入 kanban task 中指定的 workspace_path（项目3）。这导致：
1. Reviewer 在项目 workspace 找不到源文件 → blocked
2. Synthesizer 的 completion summary 包含实际路径，但下游任务不会自动使用

## 修复方案

方案 A（推荐）：全部 swarm 完成后，从 synthesizer summary 提取实际路径，用 cp 迁移代码：
```bash
# 从 synthesizer 事件中提取产出路径
SYNTH_OUTPUT=$(hermes kanban show {synth_id} --json | python3 -c "
import json,sys
d=json.load(sys.stdin)
for e in d.get('events',[]):
    if e['kind']=='completed':
        print(e['payload']['summary'])
")
# 迁移到项目 workspace
cp -r /path/to/swarm/output/backend/src/* {project}/backend/src/
cp -r /path/to/swarm/output/frontend/src/* {project}/frontend/src/
```

方案 B：更新后续任务的 workspace_path 指向 swarm 实际 workspace（需要 kanban update）

## 追踪耗时统计

| Wave | Worker | 总耗时 |
|------|--------|--------|
| Wave 1 (Arch) | t_f697e19e | ~225s ready→done |
| Wave 2 (BE+FE) | t_0fd187f3, t_6ed03ed8 | ~450s ready→done |
| Wave 3 (Reviewer) | t_64e93548 | ~600s 后 blocked |
