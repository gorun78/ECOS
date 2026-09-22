# PMO-A+7b: 迁数据源访问簇 + connector + pipeline

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+7a 已完成（engine.data 不再 import datanet）
> **归属决策（肖总定）**: connector（外部数据访问）→ runtime-access；pipeline（数据管道）→ data-engine

## §迁移三动作铁律（同 R1-R3）

迁移 = 移动不是复制。删原类 + 改消费方 import + 硬门禁 grep 旧 package 0 匹配。

## §迁移清单

| 类别 | 源 package | 目标 package | 文件数 |
|------|-----------|-------------|:---:|
| datasource | `runtime.core.datasource.*` | `engine.data.datasource.*` | 5 |
| storage | `runtime.core.dataaccess.storage.*` | `engine.data.datasource.storage.*` | 14 |
| datadescription | `runtime.core.datadescription.*` | `engine.data.datadescription.*` | 19 |
| connector | `datanet.connector.*`（Connector/ConnectorFactory/JdbcConnector/CsvConnector/RestApiConnector） | `runtime.access.connector.*` | 5 |
| pipeline | `datanet.pipeline.*`（api 4 + impl 4） | `engine.data.pipeline.*` | 8 |

> 依赖链（迁入时保持）：`datasource → storage → datadescription`。storage 的 BaseJdbcAdapter/IStorageAdapter 依赖 datadescription（DataSchema），datasource 的 DataSourceServiceImpl 依赖 storage（IStorageAdapter/JdbcAdapterFactory）。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 datadescription 19 到 `engine/data-engine/data-engine-impl/.../engine/data/datadescription/`，改 package | 编译通过 |
| T2 | 迁 storage 14 到 `.../engine/data/datasource/storage/`，改 package + 内部 import（storage→datadescription 改为 engine.data.datadescription） | 编译通过 |
| T3 | 迁 datasource 5 到 `.../engine/data/datasource/`，改 package + 内部 import（datasource→storage 改为 engine.data.datasource.storage） | 编译通过 |
| T4 | 迁 connector 5 到 `runtime/runtime-access/.../runtime/access/connector/`，改 package | 编译通过 |
| T5 | 迁 pipeline 8 到 `.../engine/data/pipeline/`，改 package + 内部 import | 编译通过 |
| T6 | 改消费方 import（grep 兜底，见下） | 编译通过 |
| T7 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### T6 消费方改写（grep 兜底）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
# datasource 消费方（engine/data 5 处 + datanet 已废弃部分）
grep -rln "runtime.core.datasource" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
# → 改向 engine.data.datasource

# connector 消费方（engine/data 的 QualityServiceImpl/DataSourceRegistryService/PipelineExecutionEngine）
grep -rln "datanet.connector" --include="*.java" . | grep -v target | grep -v "/datanet/"
# → 改向 runtime.access.connector

# pipeline 消费方（engine.data.PipelineService 等）
grep -rln "datanet.pipeline" --include="*.java" . | grep -v target | grep -v "/datanet/"
# → 改向 engine.data.pipeline
```

**注意**：datanet 自身的废弃 Controller/impl 也 import 这些包（datasource/storage/datadescription/connector/pipeline），但它们在 A+7c 删除，本指令**不改 datanet 内部的 import**（它们会编译失败，但 A+7c 一并删）。

## §禁止清单

1. ❌ **禁止复制**——迁走后原位置类必须消失
2. ❌ 不改方法体/SQL/业务逻辑——纯 package + import 移动
3. ❌ 不删 datanet 模块（A+7c）
4. ❌ 不改 datanet 内部文件（废弃 Controller/impl 的 import 留着，A+7c 删）
5. ❌ 不建新 Maven 模块
6. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁1: 活跃模块不再 import runtime.core 数据源访问簇
grep -rln "runtime.core.\(datasource\|dataaccess.storage\|datadescription\)" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
# 期望: 0 匹配（datanet 内部除外，A+7c 删）

# 硬门禁2: engine.data 不再 import datanet.connector/pipeline
grep -rln "datanet.\(connector\|pipeline\)" engine/data-engine --include="*.java" | grep -v target
# 期望: 0 匹配

# 迁入类存在
ls engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/datasource/IDataSourceService.java
ls runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/connector/Connector.java
```

## §工时

1.5 天（51 文件迁移 + 消费方改写 + 反复编译，依赖链 datasource→storage→datadescription 要按序迁）。

## §风险

- **迁移顺序**：必须 datadescription → storage → datasource（下层先迁），否则中间态编译失败。
- **datanet 内部会编译失败**：迁走 datasource/storage/datadescription/connector/pipeline 后，datanet 的废弃 Controller/impl 因 import 旧 package 编译失败。这是**预期**（A+7c 删 datanet）。但若 datanet 的编译失败阻塞了全量 install，需临时在 pom 移除 datanet module（本指令可先移除 module 让 datanet 退出构建，A+7c 再删目录）。执行时若遇此情况，优先移除 module 而非纠结 datanet 编译。
- **connector 的 ConnectorFactory 内部引用 JdbcConnector/CsvConnector/RestApiConnector**（反射或 new）：迁 connector 5 时确认 factory 对子类的引用方式，迁入后内部 import 同步改。
- **pipeline 的 PipelineService 双重**：datanet.pipeline.PipelineService 与 engine.data.PipelineService 同名，迁入 engine.data.pipeline 后要确认不冲突（A+7a 已让 engine.data.PipelineService 不再 import datanet.pipeline，本指令把 datanet.pipeline 迁入，两者合并到 engine.data 下）。
