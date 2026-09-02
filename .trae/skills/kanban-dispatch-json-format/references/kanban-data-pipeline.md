# Kanban 端到端数据管道

## 架构：CLI → 后端 → 前端

```
hermes kanban --board <board> list --json
    ↓
KanbanService.executeKanbanList()
    ↓ JSON 数组解析
KanbanController /api/kanban + /api/kanban/tasks
    ↓ HTTP
KanbanPanel (Vue 3) fetchTasks() → filteredRows
    ↓
UI 渲染（列表/看板/详情）
```

## 关键决策

### 使用 `list --json` 而非逐条 `show`

`hermes kanban --board <board> list --json` 返回完整 JSON 数组，每个元素包含：
- `id`, `title`, `body`, `assignee`, `status`, `priority`, `created_at`, `completed_at`

逐条 `show` 会导致 O(n) 网络调用，100+ 任务时后端超时（axios timeout 10s → 30s）。

### 超时设置

- 前端: `api/index.js` axios timeout: 10000 → 30000（允许后端执行 CLI + 解析）
- 后端: CLI 执行添加 5 秒缓存，避免高频刷新重复调 CLI

### 字段映射

| CLI --json 字段 | 看板 UI 字段 |
|----------------|-------------|
| `priority` (0-3) | 紧急(P0) / 高(P1) / 中(P2) / 低(P3) |
| `status` | 待办 / 进行中 / 已完成 / 暂停 |
| `created_at` (Unix timestamp) | `toLocaleString('zh-CN')` |
| `body` | 卡片描述 + 详情弹窗 |
| `assignee` (code) | display_name 从 agents.json 映射 |

## 排序

创建时间倒序：`filteredRows.sort((a, b) => (b.created_at || 0) - (a.created_at || 0))`

## 日志功能

`hermes kanban --board <board> log {taskId}` 返回执行日志文本。
- 后端: `GET /api/kanban/log/{taskId}?profile=xxx&lines=500`
- 前端: 黑底白字 pre，行数可选 500/1000/3000/5000
