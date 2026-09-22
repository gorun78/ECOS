# PMO-A+4: 建 runtime-access 收敛基础设施访问

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **铁律**: ①PG/Neo4j/MinIO/Doris/Git 的 Driver/Client 封装统一收敛到 runtime-access（器），引擎层/服务层禁止各自 new Driver/Client ②只迁封装类 + 改写消费方，不改业务逻辑 ③每 Task 独立 commit

## §背景

runtime-access 已在 A1-1 建立（迁入 Neo4jConfig，定义 `neo4jDriver` Bean）。本指令收敛其余基础设施 Driver/Client 封装 + 基础工具，让 runtime-core 逐步退化为纯「器」。

## §收敛清单

| 类别 | 现状（散落位置） | 动作 |
|------|------|------|
| **Git** | runtime-core `git/` 5 文件（GitService/GitServiceImpl/GitRepositoryService/GitRepositoryServiceImpl/entity/GitRepository） | 迁 runtime-access |
| **MinIO** | gateway `MinioStorageService` + workspace `MinioObjectStorageService`（2 个实现）+ common-api `IObjectStorageService`（接口） | 实现迁 runtime-access，接口留 common-api |
| **Doris** | runtime-task `DorisRunner` + gateway `DuckDBQueryService`（Doris 查询） | 迁 runtime-access |
| **基础工具** | runtime-core `logging/` 25 + `i18n/` 5 + `mybatis/` 1 + `alert/` 6 = 37 文件 | 迁 runtime-access |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 Git 5 文件到 `runtime/runtime-access/.../runtime/access/git/`，改 package | `mvn install -DskipTests` 通过 |
| T2 | 迁 MinIO 2 实现到 runtime-access（`runtime/access/storage/`），common-api 接口 `IObjectStorageService` 留原地，实现类实现它 | 同上 |
| T3 | 迁 Doris（DorisRunner + DuckDBQueryService）到 runtime-access（`runtime/access/olap/`） | 同上 |
| T4 | 迁基础工具 37 文件到 runtime-access（`runtime/access/util/` 等），改 package | 同上 |
| T5 | 改写所有消费方 import（grep 兜底，清单见下） | 同上 |

### T5 消费方改写（执行时 grep 兜底）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
# 迁走后，找出所有仍 import 旧 package 的活跃文件
grep -rln "runtime.core.git\|MinioStorageService\|MinioObjectStorageService\|DorisRunner\|DuckDBQueryService\|runtime.core.logging\|runtime.core.i18n\|runtime.core.alert\|runtime.core.mybatis" \
  --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/" | grep -v "/runtime/runtime-access/"
```

已知消费方（迁入前先确认）：
- MinIO：gateway `DataLakeExportService`、`DataLakeController`（用 MinioStorageService）
- Doris：gateway `TaskController`、`DataLakeExportService`、`DataLakeController`、sysman `SysConfigService`、data-engine `DataEngineConfigController`、ontology-engine `CeosCompatController`
- Git：ontology-engine `OntologyGitController` 等（grep `runtime.core.git` 确认）

## §禁止清单

1. ❌ 不改 Driver/Client 封装的内部逻辑（连接池参数、SQL 等），纯 package + import
2. ❌ 不迁 Neo4j 的消费 Service（OntologyKgSyncService/RuleGraphService 等是"用 Driver 的 Service"，不是"封装 Driver"，留在各自引擎）
3. ❌ 不删 runtime-access 里已有的 Neo4jConfig
4. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
# V1: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V2: 引擎层/服务层不再直接 new Driver/Client（Neo4j 的 GraphDatabase.driver、MinIO 的 MinioClient.builder、Doris 的 DriverManager）
grep -rn "GraphDatabase.driver\|MinioClient.builder\|DriverManager.getConnection.*doris" \
  engine/ workspace/ services/ --include="*.java" | grep -v target
# 期望: 0 匹配（封装已收敛到 runtime-access）

# V3: runtime-access 编译通过，迁入类存在
ls runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/GitService.java
```

## §工时

2 天（4 类封装 + 37 基础工具迁移，消费方改写反复编译）。

## §风险

- **MinIO 接口边界**：`IObjectStorageService` 接口在 common-api（下层），实现迁 runtime-access（也是下层），方向正确。但 gateway/workspace 依赖 runtime-access 才能用实现——需确认 gateway/workspace pom 加 runtime-access 依赖。
- **Doris 的 MySQL 协议**：Doris 用 MySQL 协议（mysql-connector-j），runtime-access 迁 DorisRunner 时需带 mysql-connector-j 依赖（runtime-core pom 已有，迁时同步迁移依赖声明）。
- **基础工具 37 文件是"公共依赖"**：logging/i18n/alert 被大量模块 import，迁 runtime-access 后所有消费方都要改 import 指向 `runtime.access.util`，这是本指令最大的 import 改写量——T5 用 grep 兜底，务必全量编译反复调。
- **mybatis 配置特殊**：runtime-core 的 `mybatis/config/MyBatisConfig.java` 在 gateway excludeFilters 里被排除（ASSIGNABLE_TYPE），迁 runtime-access 后该排除项要同步调整。
