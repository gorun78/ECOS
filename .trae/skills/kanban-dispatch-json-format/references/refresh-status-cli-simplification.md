# refreshStatus CLI 简化修复

## 问题

`POST /api/kanban/refresh-status` 接口报 500：
```
{"code":500,"msg":"Hermes board API 返回 HTTP 404","data":null}
```

## 根因

`refreshStatus` 的旧实现（80+ 行）调用 `hermesFeign.getKanbanBoard(resolvedBoard)`，这个 Feign 客户端指向 `/api/plugins/kanban/board`——一个在本服务中**不存在的内部端点**，永远返回 404。

之后还有 70+ 行逻辑：解析 board 响应 → 提取状态映射 → 遍历 `ui_task_list.rows` 更新 → 遍历 `tasks` 数组更新 → 写回 kanban.json。这些逻辑依赖一个永远拿不到数据的 API，全部是死代码。

## 修复（v2 简化版，10 行）

```java
public void refreshStatus(String profile, String board) {
    String resolvedBoard = resolveBoard(profile, board);
    // 直接调 CLI 重新获取看板数据，数据本身就是实时的
    List<Map<String, Object>> rows = executeKanbanList(profile, resolvedBoard);
    if (rows == null) {
        // CLI 失败时尝试降级到 kanban.json 缓存
        if (readKanban(profile) != null) return;
        throw new RuntimeException("无法刷新看板状态：hermes kanban list 执行失败，且无本地缓存");
    }
    // CLI 成功即刷新完成——数据本身就是最新的
}
```

## 为什么不需要旧逻辑

| 旧逻辑 | 为什么不需要 |
|--------|-------------|
| `hermesFeign.getKanbanBoard()` | 端点不存在，永远 404 |
| 从 board 响应提取 statusMap | 没有数据源 |
| 逐行更新 `ui_task_list.rows` 状态 | kanban.json 已废弃——前端直接调 CLI |
| 写回 kanban.json | 同上 |
| 更新 statistics | 同上 |

看板数据现在**直接从 `hermes kanban list --json` 实时获取**，不存在"缓存需要刷新"的场景。`refreshStatus` 接口保留仅用于**触发重新加载**——调用一次 CLI 让后续请求拿到最新数据即可。

## 部署注意事项

修改 `refreshStatus` 后，如果报 404 错误仍然出现，说明**旧 jar 进程仍在运行**。检查：

```bash
ps aux | grep "ainative-factory"
# 如果看到多个进程，全部杀掉再重启新 jar
pkill -9 -f "ainative-factory-1.0.0.jar"
java -jar target/ainative-factory-1.0.0.jar --server.port=28081 &
```

## 相关文件

- `backend/src/main/java/com/chinacreator/ai/nativex/factory/kanban/service/KanbanService.java` — `refreshStatus()` 方法
- `backend/src/main/java/com/chinacreator/ai/nativex/factory/kanban/controller/KanbanController.java` — `POST /api/kanban/refresh-status`
