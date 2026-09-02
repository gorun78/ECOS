# Board 上下文隔离模式

## 问题

多个项目共用同一个 profile 时，`resolveBoard` 读取 TERMINAL_CWD/.hermes/agents.json
固定返回同一个 board，导致所有项目看板展示相同任务。

## 解决方案

### 后端 resolveBoard

```java
private String resolveBoard(String profile, String board) {
    if (board != null && !board.isBlank()) return board;
    String cwd = getTerminalCwd(profile);
    if (cwd == null) return "default";
    String fromAgents = getBoardFromAgentsConfig(Path.of(cwd));
    return fromAgents != null ? fromAgents : "default";
}
```

### 前端 board 加载

```javascript
// workspace/index.vue - checkKanbanAvailable()
// 从项目 .hermes/agents.json 读取（项目隔离），不走 profile 全局 boards list
const agentId = selectedAgent.value?.id
if (!agentId) { kanbanBoard.value = ''; return }
const fileRes = await workspaceApi.getFile(projectId.value, agentId, '.hermes/agents.json')
const agents = JSON.parse(fileRes.data || fileRes || '{}')
if (agents.boards) kanbanBoard.value = agents.boards
```

### 陷阱

- `kanbanApi.getBoards(profile)` 调的是 `hermes kanban boards list`，返回 profile 下所有 board，不适合做项目隔离
- `workspaceApi.getFile` 的 agentId 不能写死为 1，不同项目 agentId 不同
- agentId 为 null 时不调用 API，直接 return
