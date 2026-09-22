# PMO-A+10: runtime-core / runtime-access 边界修正（44 全局工具回迁 runtime-core）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+9 已完成（runtime-core 被清空，全局工具误迁 runtime-access）

## §背景（架构定位修正）

A+4 的原始设计把「基础工具 37 文件（logging/i18n/mybatis/alert）」一并归入了 runtime-access，后续 A+8/A+9 沿袭此错误，导致 runtime-core 被清空、全局工具全部堆进 runtime-access。

**正确边界（肖总定）**：

| 模块 | 定位 | 内容 |
|------|------|------|
| **runtime-core** | 器·核心 | 全局工具：logging/i18n/alert/mybatis/database/util |
| **runtime-access** | 器·基础设施访问 | Driver/Client 封装：Neo4j/MinIO/Doris/Git/connector |

**判断标准（架构铁律原文）**：归属看类定义什么 Bean——`@Configuration` 定义 Driver/Client（Neo4jConfig 的 neo4jDriver、MinioStorageService 的 MinioClient）才是基础设施访问；`ISystemDatabaseAccess`（JdbcTemplate DAO 支持）、`MyBatisConfig`（SqlSessionFactory）、logging/i18n/alert 都是全局工具，归 runtime-core。

## §迁移三动作铁律

迁移 = 移动不是复制。删原类 + 改消费方 import + 硬门禁 grep 旧 package 0 匹配。

## §回迁清单（44 文件：runtime-access → runtime-core）

| 源 package（runtime.access.*） | 目标 package（runtime.core.*） | 文件数 |
|-------------------------------|-------------------------------|:---:|
| `util/logging/*` | `logging/*` | 25 |
| `util/i18n/*` | `i18n/*` | 5 |
| `util/alert/*` | `alert/*` | 6 |
| `util/mybatis/config/MyBatisConfig` | `mybatis/config/MyBatisConfig` | 1 |
| `util/PageInfo` + `util/Page` + `util/LegacyListInfo` | `util/PageInfo` + `util/Page` + `util/LegacyListInfo` | 3 |
| `database/*` | `database/*` | 4 |

> 注：PageInfo/Page/LegacyListInfo 原历史 package 是 `runtime.core.common.util.*`，本次回迁用干净命名 `runtime.core.util.*`（去 common）。

## §留 runtime-access 的 14 文件（不碰）

`config/Neo4jConfig` + `git/*`(5) + `storage/*`(2) + `olap/*`(1) + `connector/*`(5)。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 回迁 util 40 文件（logging 25 + i18n 5 + alert 6 + mybatis 1 + PageInfo/Page/LegacyListInfo 3）到 `runtime-core/.../runtime/core/`，改 package | 编译通过 |
| T2 | 回迁 database 4 到 `runtime-core/.../runtime/core/database/`，改 package | 编译通过 |
| T3 | 改消费方 import（grep 兜底，约 57 处，见下） | 编译通过 |
| T4 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### T3 消费方改写（grep 兜底）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
# 全部改向 runtime.core.*
grep -rln "runtime.access.util.logging" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"   # → runtime.core.logging
grep -rln "runtime.access.util.i18n"    --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"   # → runtime.core.i18n
grep -rln "runtime.access.util.alert"   --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"   # → runtime.core.alert
grep -rln "runtime.access.util.mybatis" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"   # → runtime.core.mybatis
grep -rln "runtime.access.util.\(PageInfo\|Page\|LegacyListInfo\)" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"  # → runtime.core.util
grep -rln "runtime.access.database"     --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"   # → runtime.core.database
```

已知消费方分布：logging 2(sysman) + i18n 3(engine2/sysman1) + alert 1(runtime) + mybatis 2(gateway1/sysman1) + PageInfo 10(runtime) + Page 10(runtime) + LegacyListInfo 4(runtime) + database 25(engine7/runtime3/sysman15)。

## §禁止清单

1. ❌ 禁止复制——回迁后 runtime-access 的 util/ 和 database/ 目录必须清空（只剩 config/git/connector/olap/storage）
2. ❌ 不改方法体/SQL/业务逻辑——纯 package + import 移动
3. ❌ 不碰 runtime-access 的 14 个 Driver/Client（config/git/connector/olap/storage）
4. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁1: runtime-access 不再有 util/database（只剩 Driver/Client）
ls runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/util 2>/dev/null && echo "残留util" || echo "✅ util已回迁"
ls runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/database 2>/dev/null && echo "残留database" || echo "✅ database已回迁"

# 硬门禁2: 全仓不再 import runtime.access.util/database
grep -rln "runtime.access.\(util\|database\)" --include="*.java" . | grep -v target
# 期望: 0 匹配

# 硬门禁3: runtime-core 恢复全局工具（~44 文件）
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
# 期望: ~44

# runtime-access 只剩 Driver/Client（~14 文件）
find runtime/runtime-access/src/main/java -name "*.java" | wc -l
# 期望: ~14
```

## §工时

0.5-1 天（44 文件回迁 + 57 处消费方改写 + 编译）。

## §风险

- **消费方改写量 57 处**：database 25 处最大（sysman 15 dao），grep 兜底全量改，漏一处编译报错。
- **runtime-core 是空壳了**：A+9 删空了 runtime-core，回迁前确认 runtime-core 的 pom 还在、module 未被移除（若 A+9c 移除了 runtime-core module，需先恢复）。
- **logging 的 datachange 切面**：logging 25 文件含 DataChangeAspect/DataChangeInterceptor，回迁后确认 AOP 切点包路径（若写死 runtime.access，需改 runtime.core）。
- **`.m2` 旧 JAR**：回迁后全量 install，若报旧类冲突，删 `~/.m2/repository/com/chinacreator/gzcm/runtime-core*` 和 `runtime-access*`。
