# Board 不存在校验

## 问题

`agents.json` 的 `boards` 字段可能被其他人修改为不存在的 board 名称，
导致 `hermes kanban --board {bad-board} list --json` 执行失败或返回空数据，
但错误被静默吞掉。

## 修复

在 `executeKanbanList` 中新增 `fetchAvailableBoards(cwd)` 调用，
校验 `resolvedBoard` 确实在可用 board 列表中。

```java
private Set<String> fetchAvailableBoards(String cwd) {
    // 执行 hermes kanban boards list 获取所有可用 board slug
    ProcessBuilder pb = new ProcessBuilder("hermes", "kanban", "boards", "list");
    pb.directory(Path.of(cwd).toFile());
    Process p = pb.start();
    String output = new String(p.getInputStream().readAllBytes(), UTF_8);
    // 解析输出：提取 ● 和 ● 后的 board slug
    Set<String> boards = new HashSet<>();
    for (String line : output.split("\n")) {
        // 格式: "●   ai-native-factory         Ai Native Factory"
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("Board:")) continue;
        String[] parts = trimmed.split("\\s+", 2);
        String slug = parts[0].replace("●","").trim();
        if (!slug.isEmpty()) boards.add(slug);
    }
    return boards;
}
```

在 `executeKanbanList` 的 `resolveBoard` 之后：

```java
Set<String> available = fetchAvailableBoards(cwd);
if (!available.isEmpty() && !available.contains(resolvedBoard)) {
    throw new RuntimeException("Board '" + resolvedBoard + "' 不存在，可用 board: " + available);
}
```

用户看到明确错误信息而不是 404/500。
