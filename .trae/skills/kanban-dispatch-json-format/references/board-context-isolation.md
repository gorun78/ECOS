# Board 上下文隔离 — 不同项目看板数据泄漏问题

## 问题

同一个 profile（如 `pm-1784271841029`）被多个项目的工作空间共用。该 profile 的 `TERMINAL_CWD` 固定指向一个项目目录，`agents.json` 的 `boards` 字段只有一个值。

**现象**：打开项目 A 的工作空间 → 看板显示项目 A 的 `ai-native-factory` board 任务。打开项目 B 的工作空间（也用同一 profile）→ 看板仍显示同一个 board 的任务。

**根因**：所有 kanban API 路径固定从 profile 的 `TERMINAL_CWD` 读取 `agents.json` → 永远读到同一个 boards 值。

## 修复

### 后端

所有 kanban Controller 接口新增可选 `board` 参数：

```java
@GetMapping("/tasks")
public R<List<Map<String, Object>>> listTasks(
    @RequestParam String profile,
    @RequestParam(required = false) String board,  // 新增
    ...
)
```

KanbanService 中 board 获取逻辑：

```java
private String getBoard(String profile, String overrideBoard) {
    if (overrideBoard != null && !overrideBoard.isBlank()) return overrideBoard;
    return getBoardFromAgentsConfig(cwd); // fallback
}
```

### 前端

1. workspace/index.vue 在初始化时从 agents.json 读取 board：

```javascript
const kanbanBoard = ref('')
// 在 fetchProjectData 或类似方法中
const agentsRes = await projectApi.getAgents(projectId)
kanbanBoard.value = agentsRes.data.boards || ''
```

2. KanbanPanel 组件增加 props：

```html
<KanbanPanel :profile="agentCode" :board="kanbanBoard" />
```

3. kanban.js 所有方法透传 board 参数：

```javascript
listTasks(profile, board, params) {
  return request.get('/kanban/tasks', { params: { profile, board, ...params } })
}
```
