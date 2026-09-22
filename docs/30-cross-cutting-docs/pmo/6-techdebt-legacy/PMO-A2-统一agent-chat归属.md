# PMO-A2: 统一 agent/chat 归属（删 gateway 重复副本）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-BE
> **铁律**: ①引擎层（ai-engine 火·智慧对象）是 Agent 权威，gateway 是聚合层不该有业务 Controller ②只删副本不改业务逻辑 ③每 Task 独立 commit

## §背景

`gateway/DiagnosticAgentController` 与 `ai-engine/DiagnosticAgentController` **完全重复**（`diff` 空，423 行 copy-paste，仅 package 声明不同）。gateway 版本已被 `GatewayApplication` 的 `excludeFilters` 排除（第 95 行），属**不生效的死副本**。

权威归 ai-engine：`AgentChatController`（`/api/v1/agent/chat`）+ `DiagnosticAgentController`（`/api/v1/agent/tools`、`/call` 等诊断工具）。

> 注：`services/agent-service/AgentRuntimeController` 用 `/api/v1/agent-runtime` 前缀，**不参与**本次冲突，保留不动。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | `git rm gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/DiagnosticAgentController.java` | 文件不存在 |
| T2 | `GatewayApplication.java` excludeFilters 里删除 `com.chinacreator.gzcm.gateway.controller.DiagnosticAgentController.class`（第 95 行） | gateway 编译通过 |
| T3 | 全量 `mvn install -DskipTests` | BUILD SUCCESS |

## §禁止清单

1. ❌ 不碰 ai-engine 的 AgentChatController / DiagnosticAgentController（权威保留）
2. ❌ 不碰 services/agent-service 的 AgentRuntimeController（前缀不同，无冲突）
3. ❌ 不改 excludeFilters 里其他排除项（engine.ai 的 AgentChatController/DiagnosticAgentController/CognitiveController 排除项**保留**——它们由 ai-engine 自己的 boot 启动，gateway 不扫）

## §验证门禁

```bash
# V1: gateway 无 DiagnosticAgentController
grep -rn "DiagnosticAgentController" /home/guorongxiao/ECOS/ecos_backend/gateway/src/ --include="*.java"
# 期望: 0 匹配（excludeFilters 里的也已删）

# V2: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V3: 启动 gateway 确认无 Ambiguous mapping（ai-engine 的诊断工具仍可达）
# 期望: 启动日志无 "Ambiguous mapping" 警告
```

## §工时

0.5 天（删 1 文件 + 删 1 行 excludeFilter + 编译验证）。

## §风险

- **确认 diff 为空再删**：两个 DiagnosticAgentController 已用 `diff` 验证内容一致（仅 package 不同）。若后续有人改了 gateway 版（出现差异），需重新 diff 确认差异是否要迁移到 ai-engine 版后再删。
- **excludeFilters 清理必须精确**：只删第 95 行 gateway 自己的排除，engine.ai 的三个排除（AgentChatController/DiagnosticAgentController/CognitiveController）是 ai-engine boot 架构所需，误删会导致 gateway 启动 Ambiguous mapping。
