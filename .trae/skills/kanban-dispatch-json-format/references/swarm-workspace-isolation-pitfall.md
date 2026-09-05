# Swarm 工作空间隔离排查指南

## 核心问题

`hermes kanban swarm` 的 worker 在隔离的 scratch workspace 中运行，不是用户项目目录。worker 修改的文件**不会自动写回项目目录**。

## 两个路径

| 路径 | 谁在用 |
|------|--------|
| `/home/hermes/prj/AI-Native-Factory/3/` | 项目实际目录，用户可见 |
| `/home/hermes/.hermes/kanban/boards/.../workspaces/t_xxxxxxxx/` | worker 沙箱，每波迭代独立，会被 GC |

## 判断 worker 是否改到了项目目录

```bash
# swarm 完成后立即检查
git diff --stat

# 如果 workspace/index.vue 有改动 → worker 写到了项目目录 ✅
# 如果无改动 → worker 只改了沙箱，需要复制或重派 ❌
```

## protocol_violation 与代码落地的关系

worker 可能 `protocol_violation`（rc=0 退出但未调 kanban_complete），但代码**已经通过 git 写到了项目目录**。

处理步骤：
1. `git diff --stat` 先看改动
2. 若有改动 → 任务实质完成，不必重派，直接 unblock 继续 verifier/synthesizer
3. 若无改动 → 换 worker profile 重派

## 实战案例

FEAT-0017 系统管理菜单修复：
- Wave 1: worker 在沙箱改了代码，git diff 无改动 → 代码未落地
- Wave 2: 重派时在 prompt 中显式给出项目绝对路径，worker 成功写入项目目录
- Wave 3: worker protocol_violation 但 git diff 显示 +195/+214 → 代码已落地

## .hermes 目录文件

`kanban.json`、`agents.json` 在项目目录 `.hermes/` 下，不在 swarm workspace。所有 swarm 生成 kanban.json 必须由 PM 的 Step 6 手动写回。
