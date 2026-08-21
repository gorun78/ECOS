# PMO-A+3: 迁 agent.mesh → ai-engine（火）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **铁律**: ①agent.mesh（Agent 网格/Mission 执行）是 ai-engine（火·智慧对象）的职责，runtime-core 是历史遗留 ②纯 package 移动 + import 改写 ③每 Task 独立 commit

## §背景

`runtime-core` 的 `agent/mesh`（14 文件：AgentMessageBus/MissionExecutionEngine/AgentRegistry/Mission 等）被 ai-engine 反向 import（8 文件 12 处），形成跨模块反向依赖。这是 A+3 要消除的——agent.mesh 迁入 ai-engine，改写反向依赖。

## §迁移清单（14 文件）

**源** `com.chinacreator.gzcm.runtime.core.agent.mesh.*` → **目标** `com.chinacreator.gzcm.engine.ai.agent.mesh.*`：

| 文件 | 目标子包 |
|------|------|
| AgentMessageBus | engine.ai.agent.mesh |
| MissionExecutionEngine | engine.ai.agent.mesh |
| entity/AgentRegistryEntity、MissionEntity、MissionTaskEntity | engine.ai.agent.mesh.entity |
| knowledge/KnowledgeGraphService、Neo4jQueryService | engine.ai.agent.mesh.knowledge |
| knowledge/entity/KnowledgeEdge、KnowledgeNode | engine.ai.agent.mesh.knowledge.entity |
| knowledge/repository/KnowledgeEdgeRepository、KnowledgeNodeRepository | engine.ai.agent.mesh.knowledge.repository |
| repository/AgentRegistryRepository、MissionRepository、MissionTaskRepository | engine.ai.agent.mesh.repository |

## §消费方改写清单（已核实）

| 文件 | 处理 |
|------|------|
| ai-engine：DataInitializer、AIPAgentController、AgentMeshController、AgentConfigResolver、AgentLoopConfig、AgentMeshServiceImpl、AgentStudioService、AgentTemplateService（8 文件） | import 改 `runtime.core.agent.mesh` → `engine.ai.agent.mesh` |
| aimod/AgentMeshController | import 改指向 engine.ai.agent.mesh（或评估 aimod 是否死模块，死则删） |
| gateway/GatewayApplication `@MapperScan` | `runtime.core.agent.mesh.repository` → `engine.ai.agent.mesh.repository` 等 |
| sysman-boot/SysManApplication | 同上（@MapperScan/ComponentScan 字符串） |
| dccheng/KnowledgeGraphController | A1-3 已删，无需处理 |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 agent.mesh 14 文件到 `engine/ai-engine/ai-engine-impl/.../engine/ai/agent/mesh/`，改 package 声明 + 内部 import | `mvn install -DskipTests` 通过 |
| T2 | 改写 ai-engine 8 文件的 import + aimod/AgentMeshController | 同上 |
| T3 | 改 gateway/sysman 的 @MapperScan / ComponentScan 字符串指向新 package | gateway 启动正常 |
| T4 | 全量编译 + 三版本 profile validate | BUILD SUCCESS |

## §禁止清单

1. ❌ 不改方法体、SQL、业务逻辑——纯 package + import
2. ❌ 不碰 ai-engine 已有 Agent 运行时（AgentLoopService/ToolRouter 等），只改它们的 import
3. ❌ 不删 agent.mesh 目录（软删除，先移走代码；物理删除等全量验证后）
4. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
# V1: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V2: 活跃模块不再 import runtime.core.agent.mesh
grep -rln "runtime.core.agent.mesh" /home/guorongxiao/ECOS/ecos_backend --include="*.java" \
  | grep -v target | grep -v "/runtime/runtime-core/"
# 期望: 0 匹配

# V3: ai-engine 迁入类存在
ls engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/agent/mesh/MissionExecutionEngine.java
```

## §工时

1 天（14 文件迁移 + 8 文件 import 改写 + @MapperScan 调整）。

## §风险

- **@MapperScan 是最大坑**：gateway 的 `@MapperScan` 显式列了 `com.chinacreator.gzcm.runtime.core.agent.mesh.repository` 和 `.knowledge.repository`（两处），迁走后必须改成 `engine.ai.agent.mesh.repository` 等，否则 MyBatis Mapper 扫不到 → Agent 相关功能 500。
- **aimod 归属**：aimod/AgentMeshController 是旧模块（aimod 已在 gateway excludeFilters 整体排除）。若 aimod 是死模块，T2 可直接删 aimod/AgentMeshController 而非改 import——执行时先 `grep -r aimod` 确认 aimod 是否还有活引用。
- **Neo4jQueryService 连带**：agent.mesh 里有 `Neo4jQueryService`（Neo4j 封装），迁入 ai-engine 后它与 A+4 的 runtime-access 收敛有重叠——A+3 先按原样迁（改 package），A+4 再统一收敛 Neo4j 封装到 runtime-access。
