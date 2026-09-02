# Worker 崩溃模式参考

## arch-1785224485752 反复崩溃

**日期**: 2026-07-29
**会话**: 概要设计重试

**症状**:
- spawned → crashed (exit code 1) → gave_up
- unblock 重试后再次 crashed → blocked
- 连续 5 次失败（arch 3 次 + fullstack 2 次）

**排查过程**:
1. 首次怀疑 protocol_violation，按协议 unblock 重试
2. 3 次后升级人工，换 fullstack profile
3. Fullstack 也崩溃 — 发现是 skill 跨 profile 不可用
4. 将 `design-software-architecture` skill 从 arch profile 同步到 fullstack/PM profile 后仍崩溃
5. 根本原因：退出码 1 无具体错误信息，可能与全量代码扫描内存/超时相关

**最终处理**:
- 首次成功的 `t_7da9c916`（arch 第一次执行）已产出 42KB 完整文档
- 后续重试全部崩溃，但产出已存在
- 按协议升级人工

**排查要点（下次遇到）**:
1. 检查 worker profile 是否有目标 skill（最常见被忽略的原因）
2. 检查 goal 长度是否过大导致命令行截断
3. 检查 worker 的 TERMINAL_CWD 是否正确
4. 读取 `hermes kanban show {tid}` 的金丝雀日志获取确切错误
