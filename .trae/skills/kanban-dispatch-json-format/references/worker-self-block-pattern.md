# Worker 自 block（review-required）模式处理

## 现象

Worker 完成任务后主动 block 自己，事件 payload：

```json
{
  "kind": "blocked",
  "payload": {
    "reason": "review-required: 代码已完成，npm run build 通过。请人工验证...",
    "kind": "needs_input",
    "recurrences": 1
  }
}
```

## 根因

Fullstack worker（`fullstack-1784098453689`）的 SOUL.md 中可能配置了完成审查工作流，要求完成后等待自然人或 PM 审核。

## 处理流程

```
1. git diff --stat  ← 确认代码是否已在项目目录
2. 代码已产出 → hermes kanban --board <b> unblock <id>
3. 代码未产出 → unblock + 追加归集 Wave
4. unblock 后 worker 可能 protocol_violation (nonzero_exit)
   → 代码已产出时不必再 unblock，直接标记完成
```

## 禁用行为

- ❌ 不验证 git diff 就直接 unblock
- ❌ unblock → 崩溃 → unblock → 崩溃（无限循环）
- ❌ 人工"代码已完成"就信任合成器总结——合成器在 scratch workspace 运行

## 真实案例

**2026-07-28, FEAT-0021**：项目仓库+成员页系统管理菜单补全。Worker 完成后自 block，git diff 显示两文件各 +195/+214 行。Unblock 一次后 worker resume 时 protocol_violation crashed (rc=1)，gave_up after 2 failures。代码已产出，不再尝试 unblock。
