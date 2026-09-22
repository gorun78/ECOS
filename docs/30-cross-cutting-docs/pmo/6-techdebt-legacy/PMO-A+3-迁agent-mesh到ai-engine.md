# PMO-A+3: 迁 agent.mesh → ai-engine

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **Commit**: `3961bb6`
> **前置**: PMO-A+1, A+2

## §背景

`runtime-core` 的 `agent/mesh`（14 文件）被 ai-engine 反向 import，形成跨模块反向依赖。迁入 ai-engine 打破该依赖。

## §已迁移（14 文件）

| 文件 | 目标路径 |
|------|---------|
| AgentMessageBus, MissionExecutionEngine | `engine.ai.agent.mesh` |
| entity: AgentRegistryEntity, MissionEntity, MissionTaskEntity | `engine.ai.agent.mesh.entity` |
| knowledge: KnowledgeGraphService, Neo4jQueryService | `engine.ai.agent.mesh.knowledge` |
| knowledge/entity: KnowledgeEdge, KnowledgeNode | `engine.ai.agent.mesh.knowledge.entity` |
| knowledge/repository: KnowledgeEdgeRepository, KnowledgeNodeRepository | `engine.ai.agent.mesh.knowledge.repository` |
| repository: AgentRegistryRepository, MissionRepository, MissionTaskRepository | `engine.ai.agent.mesh.repository` |

## §消费者改写（8 个 ai-engine 文件）

DataInitializer, AIPAgentController, AgentMeshController, AgentConfigResolver, AgentLoopConfig, AgentMeshServiceImpl, AgentStudioService, AgentTemplateService 的 import 已从 `runtime.core.agent.mesh` → `engine.ai.agent.mesh`。

**注意**: aimod/AgentMeshController **保持** `runtime.core.agent.mesh`（A+1 Path A: runtime-crypto 保留 canonical；ai-engine ↔ aimod-impl 循环依赖未解）。

## §MapperScan 更新

GatewayApplication + SysManApplication 的 `@MapperScan` 已更新:
- `runtime.core.agent.mesh.repository` → `engine.ai.agent.mesh.repository`
- `runtime.core.agent.mesh.knowledge.repository` → `engine.ai.agent.mesh.knowledge.repository`

## §Bean 冲突修复

**问题**: `MissionExecutionEngine` 同时存在于 runtime-core JAR 和 ai-engine-impl JAR，ConflictingBeanDefinitionException。

**修复**: GatewayApplication 添加 regex excludeFilter:
```java
@ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.agent\\.mesh\\..*")
```

## §验证

```bash
# V1: 全量编译
cd /home/guorongxiao/ECOS/ecos_backend && unset HOME && \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  mvn install -DskipTests -Dmaven.test.skip=true -q
# 期望: BUILD SUCCESS

# V2: 无旧 import
grep -rln "runtime.core.agent.mesh" ecos_backend --include="*.java" \
  | grep -v target | grep -v '/runtime/runtime-core/'
# 期望: 0 匹配（除 aimod-impl）

# V3: 端点验证
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health
# → 403 (需认证，正常)

curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/cognitive/health
# → 200 OK
```

## §未完成项

- aimod/AgentMeshController 仍依赖 `runtime.core.agent.mesh`（循环依赖阻塞，待 ARCH 决策解除 ai-engine ↔ aimod-impl 循环后迁移）
