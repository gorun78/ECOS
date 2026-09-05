# 看板任务批量删除 — archive + gc 正确组合

## 单条删除

```bash
hermes kanban --board {board} archive {taskId}
```

不调 gc。用户仍可在 archived 列表中恢复。

## 批量删除

```bash
# 1. 逐条归档
for id in taskIds:
    hermes kanban --board {board} archive {id}

# 2. 全部归档后一次性清理
hermes kanban --board {board} gc
```

## 后端实现

```java
// 单条
@PostMapping("/archive/{taskId}")
public R<Void> archiveTask(@RequestParam String profile, @RequestParam(required = false) String board, @PathVariable String taskId) {
    service.archiveTask(profile, board, taskId);
    return R.ok();
}

// 批量
@PostMapping("/batch-archive")
public R<Map<String,Integer>> batchArchive(@RequestParam String profile, @RequestParam(required = false) String board, @RequestBody List<String> taskIds) {
    int success = 0, fail = 0;
    for (String id : taskIds) {
        try { service.archiveTask(profile, board, id); success++; }
        catch (Exception e) { fail++; }
    }
    if (success > 0) service.gc(profile, board); // 成功归档过才 gc
    return R.ok(Map.of("success", success, "failed", fail));
}
```

## ⚠️ 注意事项

- `archive` + `gc` 不可逆，确认后再操作
- gc 只在批量完成后执行一次，不要每条 archive 后都 gc
- 必须指定 `--board`，否则可能归档到错误的 board
