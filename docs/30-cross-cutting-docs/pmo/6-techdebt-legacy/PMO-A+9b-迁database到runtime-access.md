# PMO-A+9b: 迁 database + common.util 到 runtime-access

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+9a 已完成（死代码已删）

## §背景

runtime-core 剩余活跃文件里，有两块是"基础设施访问"性质，按架构铁律应归 runtime-access（器）：

1. **database(4)**：`ISystemDatabaseAccess`/`SystemDatabaseAccessImpl`/`JdbcTemplateDaoSupport`/`PageResult`。被 30 处引用（sysman 15 + engine 7 + runtime 8）。
2. **common/util/LegacyListInfo + PageInfo(2)**：分页/列表工具，被 runtime-monitor 15 处引用。

## §迁移三动作铁律

迁移 = 移动不是复制。删原类 + 改消费方 import + 硬门禁 grep 旧 package 0 匹配。

## §迁移清单

| 源 package | 目标 package | 文件数 |
|-----------|-------------|:---:|
| `runtime.core.database.*` | `runtime.access.database.*` | 4 |
| `runtime.core.common.util.LegacyListInfo` + `PageInfo` | `runtime.access.util.*` | 2 |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 database 4 到 `runtime/runtime-access/.../runtime/access/database/`，改 package | 编译通过 |
| T2 | 迁 LegacyListInfo/PageInfo 到 `runtime/access/util/`，改 package | 编译通过 |
| T3 | 改消费方 import（grep 兜底，30 + 15 处） | 编译通过 |
| T4 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### T3 消费方改写（grep 兜底）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
# database 消费方（sysman 15 + engine 7 + runtime 8 = 30 处）
grep -rln "runtime.core.database" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/" | grep -v "/runtime/runtime-access/"
# → 改向 runtime.access.database

# LegacyListInfo/PageInfo 消费方（runtime-monitor 15 处）
grep -rln "runtime.core.common.util.\(LegacyListInfo\|PageInfo\)" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
# → 改向 runtime.access.util
```

## §禁止清单

1. ❌ 禁止复制——迁走后原位置类必须消失
2. ❌ 不改方法体/SQL——纯 package + import 移动
3. ❌ 不迁 agent/mesh、AgentRuntime/AgentResult（归 A+9c）
4. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁: 活跃模块不再 import 旧 package
grep -rln "runtime.core.database\|runtime.core.common.util.\(LegacyListInfo\|PageInfo\)" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"
# 期望: 0 匹配

# 迁入类存在
ls runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/database/ISystemDatabaseAccess.java
```

## §工时

0.5 天（6 文件迁移 + 45 处消费方改写 + 编译）。

## §风险

- **database 消费方量大（30 处）**：sysman 的 15 个 dao + engine 的 7 个 + runtime 的 8 个，改写要 grep 兜底全量改，漏一处编译报错。
- **runtime-monitor 依赖 PageInfo/LegacyListInfo**：runtime-monitor 在 runtime 目录内，迁走后它的 import 也要改（T3 grep 已含）。
- **runtime-access pom 依赖**：database 若用 JdbcTemplate，runtime-access 需有 spring-jdbc 依赖（检查现有 pom）。
