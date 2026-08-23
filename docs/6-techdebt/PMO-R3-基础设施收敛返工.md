# PMO-R3: 基础设施收敛返工（让 runtime-access 副本转正）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **返工原因**: 上一轮 A+4 只做了"复制"没做"迁移"——Git/基础工具复制到 runtime-access（package 改对），但原类全在 runtime-core（git 5 + logging 25 + i18n 5 + mybatis 1 + alert 6 = 37），MinIO/Doris 原实现也没删，消费方 import 没改。结果 runtime-access 副本零消费方 = 死代码，runtime-core 没瘦下来。

## §迁移三动作铁律（本指令核心）

**迁移 = 移动，不是复制。** 三个动作缺一不可：删原类 + 改消费方 import + 硬门禁 grep 旧 package = 0 匹配。

```bash
# 硬门禁1: 活跃模块不再 import 旧基础工具 package
grep -rln "runtime.core.\(git\|logging\|i18n\|alert\|mybatis\)" \
  --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"
# 期望: 0 匹配

# 硬门禁2: 旧位置的 MinIO/Doris 实现已删
grep -rln "MinioStorageService\|MinioObjectStorageService\|DorisRunner\|DuckDBQueryService" \
  --include="*.java" gateway/ workspace/ runtime/runtime-task/ | grep -v target
# 期望: 0 匹配（这些实现已迁 runtime-access）
```

## §现状（已核实，勿重复勘察）

- **runtime-access 副本已存在且可用**：git 5 + storage 2 + olap 2 + util 37（package 已改对 `runtime.access.*`）。**不要再复制。**
- 原类全在：`runtime-core/git`(5)、`runtime-core/logging`(25)、`runtime-core/i18n`(5)、`runtime-core/mybatis`(1)、`runtime-core/alert`(6)、gateway `MinioStorageService`、workspace `MinioObjectStorageService`、gateway `DuckDBQueryService`、runtime-task `DorisRunner`。
- 消费方 import 未改（见 T5 清单）。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | `git rm` runtime-core/git 5 类（副本在 runtime-access/git） | 编译通过 |
| T2 | `git rm` runtime-core/logging(25)+i18n(5)+mybatis(1)+alert(6) = 37 类（副本在 runtime-access/util） | 编译通过 |
| T3 | `git rm` gateway `MinioStorageService` + workspace `MinioObjectStorageService`（副本在 runtime-access/storage，workspace 版是死副本已核实 0 import） | 编译通过 |
| T4 | `git rm` gateway `DuckDBQueryService` + runtime-task `DorisRunner`（副本在 runtime-access/olap） | 编译通过 |
| T5 | 改消费方 import（grep 兜底，清单见下） | 编译通过 |
| T6 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + 门禁全绿 |

### T5 消费方改写清单（执行时 `grep -rln "旧package" --include="*.java" | grep -v target` 兜底）

| 旧 package/类 | 消费方（已核实） | 改向 |
|------|------|------|
| `runtime.core.logging` | sysman `SysManRuntimeConfig.java`、`log/impl/UserOperationLogServiceImpl.java` | `runtime.access.util.logging` |
| `runtime.core.i18n` | sysman `config/I18nConfig.java` | `runtime.access.util.i18n` |
| `runtime.core.alert` | runtime-task `monitoring/TaskMonitoringServiceImpl.java` | `runtime.access.util.alert` |
| `runtime.core.git` | ontology-engine `OntologyGitController.java`（grep 兜底确认） | `runtime.access.git` |
| `MinioStorageService` | gateway `DataLakeExportService`、`DataLakeController` | `runtime.access.storage.MinioStorageService` |
| `DorisRunner`/`DuckDBQueryService` | gateway `TaskController`/`DataLakeExportService`/`DataLakeController`、sysman `SysConfigService`、data-engine `DataEngineConfigController`、ontology-engine `CeosCompatController` | `runtime.access.olap.*` |

## §禁止清单

1. ❌ **禁止复制**——runtime-access 副本已存在，不要再 cp/mv 任何类进去
2. ❌ 不改 Driver/Client 封装的内部逻辑（连接池参数、SQL），纯 package + import
3. ❌ 不迁 Neo4j 的消费 Service（OntologyKgSyncService/Neo4jGraphService/RuleGraphService/KGWriterService/ObjectKgSyncService/Neo4jQueryService——它们是"用 Driver 的 Service"，不是"封装 Driver"，留各自引擎）
4. ❌ 不删 runtime-access 里已有的 Neo4jConfig、GitService 等已迁类
5. ❌ 不碰 `runtime.core.database` 包（合法保留）
6. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS
```

## §工时

2 天（删 46 类 + 改 ~10 消费方 import + 反复编译调错，消费方改写量最大）。

## §风险

- **mybatis/config/MyBatisConfig 连带**：runtime-core 的 `mybatis/config/MyBatisConfig.java` 在 gateway excludeFilters 里被 ASSIGNABLE_TYPE 排除。迁到 runtime-access 后，gateway 里 `MyBatisConfig.class` 的 import 要同步改（`runtime.core.mybatis.config.MyBatisConfig` → `runtime.access.util.mybatis.config.MyBatisConfig`），否则 gateway 编译报找不到符号。
- **MinIO 接口边界**：`IObjectStorageService` 接口在 common-api（下层），实现迁 runtime-access。gateway/workspace 需依赖 runtime-access 才能用实现——确认 gateway/workspace pom 已加 runtime-access 依赖（A1-1 建 runtime-access 时 gateway 已加，workspace 待确认）。
- **logging 是最大改写量**：25 文件被大量模块 import，T5 必须 grep 兜底全量改，漏一个消费方编译报错。
- **消费方跨模块**：alert 的消费方在 runtime-task，git 的消费方在 ontology-engine，改 import 时确认这些模块 pom 依赖 runtime-access。
