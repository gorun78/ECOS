# PMO-A+9c: agent 归 ai-engine + aimod 收敛（解 ai-engine↔aimod 循环依赖）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+9a（删死代码）、A+9b（迁 database）已完成

## §背景（已勘察，勿重复）

1. **aimod 是 ai-engine 的旧模块**：9 个 Controller 已被 gateway excludeFilter 用 REGEX 整体排除（`aimod.controller.*`，ai-engine 有同名副本），是死副本；只有 3 个 service 被 ai-engine 反向 import（ObjectQLParser/SemanticQueryService ← NLQController，AgentConfigService ← AgentConfigController）。
2. **runtime-core 的 agent/mesh(14)** 是 ai-engine 的重复副本（A+3 已迁 14 类到 engine.ai.agent.mesh），唯一依赖它的 aimod/AgentMeshController 是死副本（已被 excludeFilter 排除）。
3. **runtime-core 的 agent 顶层 AgentRuntime/AgentResult** 被 ai-engine 的 `MissionExecutionEngine` 反向 import（agent 顶层和 mesh 是一体的）。

**解环方案**：把 aimod 的 3 个活跃 service 迁到 ai-engine（断 ai-engine→aimod 依赖），删 aimod 死代码 + 模块，删 runtime-core 的 agent/mesh + agent 顶层副本（A+3 补完）。

## §迁移三动作铁律

迁移 = 移动不是复制。删原类 + 改消费方 import + 硬门禁 grep 旧 package 0 匹配。

## §迁移清单

| 类别 | 源 | 目标 | 动作 |
|------|----|------|------|
| agent 顶层 2 | `runtime.core.agent.AgentRuntime` + `AgentResult` | `engine.ai.agent.*` | 迁 |
| aimod service 3 | `aimod.ObjectQLParser` + `SemanticQueryService` + `AgentConfigService` | `engine.ai.nlq.*`（前2）+ `engine.ai.config.*`（后1） | 迁 |
| agent/mesh 14 | `runtime.core.agent.mesh.*`（ai-engine 已有完整副本 engine.ai.agent.mesh） | — | 删 |
| aimod 死代码 13 | 9 Controller + ObjectQLException/AgentRepository/AgentEntity/AgentProfileServiceImpl | — | 删 |
| aimod 模块 | pom module + 目录 | — | 删 |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 AgentRuntime/AgentResult 到 `engine/ai-engine/.../engine/ai/agent/`，改 ai-engine `MissionExecutionEngine` 的 import（`runtime.core.agent.*` → `engine.ai.agent.*`） | 编译通过 |
| T2 | 迁 aimod 3 service 到 ai-engine（ObjectQLParser/SemanticQueryService→engine.ai.nlq，AgentConfigService→engine.ai.config），改 ai-engine `NLQController`/`AgentConfigController` 的 import（`aimod.*` → `engine.ai.*`） | 编译通过 |
| T3 | 删 aimod 死代码 13（9 Controller + ObjectQLException/AgentRepository/AgentEntity/AgentProfileServiceImpl），`git rm -r aimod/`，pom 移除 aimod module（主+三 profile），gateway 清理 aimod 的 excludeFilter REGEX + `@ComponentScan` basePackage `"com.chinacreator.gzcm.aimod"` | 编译通过 |
| T4 | 删 runtime-core 的 agent/mesh 14（`git rm -r`，ai-engine 已有完整副本） | 编译通过 |
| T5 | 全量编译 + 三版本 profile validate + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### T3 消费方改写（grep 兜底）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
# 删 aimod 前，确认无其他模块 import aimod（除 ai-engine，T2 已改）
grep -rln "import com.chinacreator.gzcm.aimod" --include="*.java" . | grep -v target | grep -v "/aimod/"
# 期望: 0 匹配（T2 改完后 ai-engine 不再 import aimod）
```

## §禁止清单

1. ❌ 禁止复制——AgentRuntime/AgentResult/aimod 3 service 迁走后原位置类必须消失
2. ❌ 不改方法体/SQL/业务逻辑——纯 package + import 移动
3. ❌ 不删 ai-engine 的 engine.ai.agent.mesh 副本（那是权威，保留）
4. ❌ 不碰 ai-engine 的其他 Controller/Service（NLQController/AgentConfigController 只改 import，不改逻辑）
5. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁1: 全仓无 aimod 引用
grep -rn "com.chinacreator.gzcm.aimod" --include="*.java" . | grep -v target | grep -v "/aimod/"
# 期望: 0 匹配

# 硬门禁2: runtime-core 无 agent.mesh + agent 顶层残留
grep -rln "runtime.core.agent" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
# 期望: 0 匹配

# 硬门禁3: aimod 目录已删
test -d aimod && echo EXISTS || echo GONE
# 期望: GONE

# runtime-core 文件数（A+9a/b 后 ~22，本指令后应 ~0）
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
```

## §工时

1 天（2 + 3 文件迁移 + aimod 模块删除 + agent/mesh 删除 + 编译）。

## §风险

- **aimod 的 pom 依赖面广**：aimod 被 sysman/gateway/common-api/services 的 pom 依赖（8 处），删 module 前 grep pom 确认这些依赖是死依赖（aimod 源码删后无 import）。
- **ObjectQLParser/SemanticQueryService 的依赖**：这两个类可能 import 了 aimod 的其他类（ObjectQLException 等），迁到 ai-engine 时要确认连带依赖（若 import ObjectQLException，需一起迁或删）。
- **gateway 的 aimod 清理**：excludeFilter 的 REGEX `aimod.controller.*` + @ComponentScan basePackage + @MapperScan（若 aimod 有 mapper），漏一处编译报错。
- **`.m2` 旧 JAR**：删 module 后全量 install，若报旧类冲突，删 `~/.m2/repository/com/chinacreator/gzcm/aimod*`。
- **runtime-core 归零**：本指令后 runtime-core 的 src/main/java 无 .java 文件（剩空壳 pom），这是预期——后续 A+10 把 runtime-core 从 pom 移除（像 dccheng/datanet）。
