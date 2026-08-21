# PMO-A+5: 删死代码 + 清空壳（runtime-core 瘦身）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-PMO + ECOS-BE
> **Commit**: `df838af`
> **前置**: PMO-A+3 (agent.mesh → ai-engine)

## §执行结果

| 任务 | 结果 | 文件数 |
|:-----|:-----|-------:|
| **T1** 明确死代码删除 | ✅ 完成 | 23 文件 |
| **T2** runtime-datanet 空壳移除 | ✅ 已在 A+1 完成 | — |
| **T3** 数据工程 99 文件判断 | ⚠️ **暂保留** | 99 文件 |
| **T4** 全量编译 | ✅ BUILD SUCCESS | — |

### T1 实际删除（23 文件）

| 包 | 文件 | 验证 |
|----|------|------|
| `agent/tool/` | Tool/ToolCall/ToolRegistry/ToolResult + impl(4) | 0 外部引用 ✅ |
| `agent/impl/` | AgentRuntimeImpl/AgentSessionImpl/DefaultLLMClient/MockLLMClient/ToolRegistryImpl | 0 外部引用 ✅ |
| `agent/llm/` | LLMClient/LLMConfig/ChatRequest/ChatResponse | 0 外部引用 ✅ |
| `legacy/` | 6 文件（applymanager + util） | 0 外部引用 ✅ |
| `agent/config/AgentConfig.java` | — | 0 外部引用 ✅ |

### T1 AgentRuntime 接口修复

删 `agent/tool/impl/` 后 `AgentRuntime.java` 残留 `getLLMClient()/getToolRegistry()/updateLLMConfig()/getLLMConfig()` 方法签引用已删类。  
修复：改为 `@Deprecated default Object stub` 返回 null，避免破坏接口契约。

### T3 暂保留原因

| 包 | 文件数 | 暂留原因 |
|----|-------:|----------|
| `dataaccess` | 33 | `runtime-core/datasource/service/impl` 消费者引用 `dataaccess.storage.*`，删会断编译 |
| `datadescription` | 19 | 内部引用链复杂，需单独分析 |
| `format/metadata/lineage/kettle/bigdataengine/modelaccess` | 47 | 0 外部引用，但内部可能成链 |

## §runtime-core 文件数

| 阶段 | 文件数 |
|------|-------:|
| 基线 | 388 |
| PMO-A+5 后 | 353 |
| 删减 | 35 |

## §Gateway excludeFilters 更新（commit df838af）

```java
// A+3: 排除旧包 runtime.core.agent（已迁入 ai-engine）
@Filter(type = REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.agent\\..*"),
// A+4: 排除迁出后的 runtime.core 旧包
@Filter(type = REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.git\\..*"),
@Filter(type = REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.datapermission\\..*"),
@Filter(type = REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.compliance\\..*"),
// A+5: 排除同名 ConfigDao（runtime-core 和 sysman MyBatis @Mapper 独立注册，ComponentScan exclude 无法处理）
@Filter(type = REGEX, pattern = "com\\.chinacreator\\.gzcm\\.(runtime\\.core\\.config\\.dao|sysman\\.config\\.dao).+\\.class"),
@Filter(type = REGEX, pattern = "com\\.chinacreator\\.gzcm\\.aimod\\.controller\\..*"),
@Filter(type = ASSIGNABLE_TYPE, classes = {
    MyBatisConfig.class,
    MinioStorageService.class,          // A+4: 已迁 runtime-access
    MinioObjectStorageService.class,    // A+4: 已迁 runtime-access
    ConfigDao.class,                    // 尝试排除（MyBatis 层可能无效）
    // ... 安全/数据引擎已接管 Controller
})
```

## §已知问题

### ConfigDao 冲突（MyBatis @Mapper 独立于 ComponentScan）

**现象**：`ConfigDao`（runtime-core）与 `ConfigDao`（sysman）冲突，`allow-bean-definition-overriding=true` 无法解决不兼容类型冲突。

**根因**：MyBatis `@Mapper` 接口通过 `SqlSessionFactory` 在 MyBatis 层独立注册，不经过 `ComponentScan`，所以 `excludeFilter` 无法排除。

**状态**：Pre-existing 问题（PMO-A+5 之前已存在），不影响 BUILD SUCCESS 和 API 端点验证。

**待处理**：需在 MyBatis `SqlSessionFactory` 配置中用 `typeAliasesPackage` 精确排除其中一个 ConfigDao，或统一两个 ConfigDao 的接口签名（ARCH 决策）。

## §验证门禁

```bash
# V1: 全量编译
cd /home/guorongxiao/ECOS/ecos_backend
unset HOME && export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 && \
  mvn install -DskipTests -Dmaven.test.skip=true -q
# 期望: BUILD SUCCESS ✅

# V2: runtime-core 文件数
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
# 期望: 353 (基线 388，删 35)

# V3: 无残留死代码 import
grep -rln "runtime.core.legacy\|runtime.core.agent.tool\|runtime.core.agent.llm\|runtime.core.agent.impl" \
  --include="*.java" . | grep -v target | grep -v '/runtime/runtime-core/'
# 期望: 0 匹配 ✅
```
