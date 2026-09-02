# Board 上下文隔离根因分析与完整修复

## 问题现象

不同项目打开工作空间的「任务看板」，展示的是同一个 board（如 `ai-native-factory`）的任务。

## 根因（两处隐患）

### 隐患 1：`resolveBoard` 降级到 "default"

```java
// ❌ 错误写法
private String resolveBoard(String profile, String board) {
    if (board != null && !board.isBlank()) return board;
    return "default";  // 所有项目都降级到 default
}
```

### 隐患 2：`refreshStatus` 传 null 给 `getKanbanBoard`

```java
// ❌ 错误写法
try (Response resp = hermesFeign.getKanbanBoard(null)) {
    // null → API 返回默认 board 而非目标 board
}
```

两处叠加：前端不传 board → resolveBoard 返回 "default" → list 用 "default" 但 refreshStatus 传 null → 不一致。

## 完整修复

### 修复 1：`resolveBoard` 从 agents.json 读取

```java
// ✅ 正确写法
private String resolveBoard(String profile, String board) {
    if (board != null && !board.isBlank()) return board;
    String cwd = getTerminalCwd(profile);
    if (cwd == null) return "default";
    String fromAgents = getBoardFromAgentsConfig(Path.of(cwd));
    return fromAgents != null ? fromAgents : "default";
}
```

### 修复 2：`refreshStatus` 传真实 board

```java
// ✅ 正确写法
public void refreshStatus(String profile, String board) {
    String resolvedBoard = resolveBoard(profile, board);
    try (Response resp = hermesFeign.getKanbanBoard(resolvedBoard)) {
        // resolvedBoard 而非 null
    }
}
```

Controller 也要加 `@RequestParam(required=false) String board` 参数。

### 修复 3：refreshStatus NPE 防御

`root.get("ui_task_list").get("rows")` 链式调用在 kanban.json 结构不完整时 NPE：

```java
// ✅ 逐级判空
JsonNode uiTaskList = root.get("ui_task_list");
if (uiTaskList != null) {
    JsonNode rows = uiTaskList.get("rows");
    if (rows != null && rows.isArray()) {
        // ...
        JsonNode statusNode = r.get("status");
        if (statusNode != null && statusNode.isObject()) {
            ObjectNode statusObj = (ObjectNode) statusNode;
            // ...
        }
    }
}
```

### 前端配合

1. workspace 初始化时调 `kanbanApi.getBoards(profile)` 获取可用 board 列表（后端执行 `hermes kanban boards list`）
2. 传给 `<KanbanPanel :board="kanbanBoard" />`
3. KanbanPanel 所有 API 调用带上 board 参数

## 验证方法

不同项目的工作空间 → 看板 → 展示的任务列表不同。
