# FEAT-0016 派发案例：WebSocket全事件推送与降级策略

## 背景

- 功能编号：FEAT-0016
- 复杂度：L2（6维度22分）
- DAG：Arch → [Backend, Frontend] → Reviewer
- 日期：2026-07-27

## 派发过程

### Wave 1：Arch 轻量设计（单 worker）
```
t_8a2b7bc5: ready → running → done（约225s）
产出：ARCH_SPEC，731行，覆盖8事件Schema+重连策略+降级API+心跳+前端状态机
Verifier/Synthesizer：均 done
```

### Wave 2：Backend + Frontend 并行（双 worker）
```
t_bf3c124c (Backend): ready → running → blocked → done（about 450s）
  产出：10源文件+6测试，83用例全过
  中间被 blocked 过一次（review-required），自行恢复

t_0297f455 (Frontend): ready → running → done（about 540s）
  产出：useWebSocket composable + 8事件监听 + 状态指示灯 + 断线横幅 + 降级轮询

Verifier/Synthesizer：均 done
```

### Wave 3：Reviewer（单 worker）
```
t_70455267: ready → running → blocked（约160s后）
原因：代码在 swarm workspace /home/hermes/prj/AI-Native-Factory/48/ 生成，
      Reviewer 在另一个隔离 workspace 找不到源文件
状态：blocked，等待迁移后重派
```

## 关键观察

1. **Backend 在 Wave 2 中途自行 blocked 又恢复**：`review-required` 类型，unblock 后继续完成。说明 swarm 内有自动修复机制。
2. **Wave 2 并行耗时约 540s（9分钟）**：比单 worker 的 Wave 1（225s）长，因为两个 worker 都要执行大任务。
3. **Reviewer blocked 与 FEAT-0015 完全相同的根因**：workspace 隔离导致 Reviewer 找不到前面 worker 的产出物。
4. **Arch worker 在 60s 后被 GC（archived）**：追踪脚本遇到 archived 时需正确处理（try/except JSONDecodeError → 视为 done）。

## 修复建议（Reviewer 重派）

重派 Reviewer 时必须在 swarm prompt 中显式给出源码绝对路径：
```
所有待审查代码在 /home/hermes/prj/AI-Native-Factory/48/ 目录：
- docs/arch/websocket-spec.md
- backend/src/main/java/com/ainative/factory/websocket/*
- backend/src/main/java/com/ainative/factory/config/WebSocketConfig.java
- sourcecode/hermes-chat-panel/src/composables/useWebSocket.js
- sourcecode/hermes-chat-panel/src/components/websocket/*
```
