# Swarm 工作空间隔离：真实案例与验证流程

## 核心结论

**Swarm worker 的修改不会自动归集到项目目录。每次代码类任务完成后必须 git diff 验证。这是本会话中最高频的失败模式。**

## 真实案例

### 案例 1：FEAT-0017 系统管理菜单修复（2026-07-28）

- Wave 1：Worker 在 scratch workspace 完成 frontend/src/views/project/workspace/index.vue 的修改
- 结果：git diff 为空——worker 产物在 `/home/hermes/.hermes/kanban/boards/ai-native-factory/workspaces/t_xxxx/`，不在项目目录
- 追加 Wave 2：派发新 swarm 明确要求将修复应用到项目目录 → +136 行
- 额外发现：repo/index.vue 和 member/index.vue 同类页面也未修 → Wave 3 追加

### 案例 2：FEAT-0019 构建部署（2026-07-28）

- Worker 完成了 npm run build + java -jar 启动，端口 21574/28081
- 项目进程实际已运行，但合成器报告在 scratch workspace 中

## 验证流程（每次 swarm 完成后必做）

```bash
# 1. 确认当前 board
hermes kanban boards

# 2. 查看 git 改动
git diff --stat

# 3. 检查 swarm workspace（如果 git diff 为空）
ls -la /home/hermes/.hermes/kanban/boards/<board>/workspaces/<worker_id>/

# 4. 如果 workspace 有产物但 git diff 为空 → 追加归集 Wave
```

## 根本原因

`hermes kanban swarm` 为每个 worker 创建独立的 scratch workspace，worker 的 cwd 指向该目录。除非 worker prompt 中明确要求写回项目绝对路径，否则产物不会落回 GP 目录。

## 预防措施

1. Swarm goal 中显式写入项目绝对路径：`/home/hermes/prj/AI-Native-Factory/3/frontend/src/xxx`
2. 代码修复任务在 goal 末尾加："所有修改必须写入项目目录，不要仅写入 scratch workspace"
3. Swarm 完成后立即 git diff 验证
