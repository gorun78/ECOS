# refreshStatus NPE 空指针修复

**问题**：`refreshStatus(profile, board)` 方法在某些项目的 kanban.json 上偶发崩溃：

```
Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" 
because the return value of "com.fasterxml.jackson.databind.node.ObjectNode.get(String)" is null
```

**根因**：链式 `.get()` 调用对 null 节点不做判空保护——有些项目的 kanban.json 结构不完整（缺少 `ui_task_list` 或 `ui_task_list.rows` 或 `rows[].status`）。

**出问题的行（修复前）**：

```java
// 危险：root.get("ui_task_list") 可能返回 null，链式 .get("rows") 直接 NPE
JsonNode rows = root.get("ui_task_list").get("rows");

// 危险：r.get("status") 可能为 null，强转 ObjectNode 崩溃
ObjectNode statusObj = (ObjectNode) r.get("status");
```

**修复**：所有链式访问拆开，逐级判空：

```java
JsonNode uiTaskList = root.get("ui_task_list");
if (uiTaskList != null) {
    JsonNode rows = uiTaskList.get("rows");
    if (rows != null && rows.isArray()) {
        for (JsonNode r : rows) {
            JsonNode statusNode = r.get("status");
            if (statusNode != null && statusNode.isObject()) {
                ObjectNode statusObj = (ObjectNode) statusNode;
                // safe to operate
            }
        }
    }
}
```

**防御范围**：`KanbanService.java` 中所有对 `kanban.json` JSON 树的 `.get()` 链式调用都应该做判空保护，尤其是动态数据源（CLI --json）和静态文件降级路径都可能产生不一致的结构。
