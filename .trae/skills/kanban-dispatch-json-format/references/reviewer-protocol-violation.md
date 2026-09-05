# qa-1784271911442 Protocol Violation 问题记录

## 症状

`hermes kanban show {id}` 输出：
```
[run N] protocol_violation {'pid': N, 'exit_code': 0}
gave_up {'failures': N, 'error': 'worker exited cleanly (rc=0) without calling kanban_complete or kanban_block — protocol violation'}
```

连续 3 次后任务状态变为 `blocked` 或 `archived`（取决于 kanban 版本）。

## 影响范围

| 日期 | Feature | Swarm root | worker | 结果 |
|------|---------|-----------|--------|------|
| 2026-07-27 | FEAT-0015 | t_447337eb | t_64e93548 | 3次 crash → blocked |
| 2026-07-27 | FEAT-0016 | t_e4712533 | t_24fa0c36 | 3次 crash → blocked |
| 2026-07-27 | FEAT-0016 | t_5e81ebe6 | t_70455267 | 3次 crash → blocked |
| 2026-07-27 | FEAT-0016 | t_f0ccf640 | t_cebdbe10 | 3次 crash → blocked/archived |

**结论**：qa-1784271911442 profile 在执行 Reviewer 任务时 100% 触发 protocol_violation。

## 处理策略

1. **不要 unblock 重试**——protocol_violation 是 profile bug，重试只会再 crash
2. **换 profile**：Reviewer 任务改用 `pm-1784271841029` 替代
3. 节点处理：用户可选「跳过 Reviewer」直接交付，或「PM 代审」

## 根因推测

qa-1784271911442 profile 可能缺少 `kanban_complete`/`kanban_block` 工具调用支持，或其 SOUL.md 中的 protocol 配置不完整。需排查 profile 配置。
