# PMO-A+6: 死代码清理收尾（60 数据工程死代码 + DorisRunner + 孤儿测试）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: R1/R2/R3 已完成（runtime-core 287 文件）

## §背景

R1-R3 迁移完成后，勘察确认 runtime-core 还剩一批死代码 + 遗留物（依赖链已逐条 grep 核实）：

1. **format(15)/metadata(10)/lineage(3)/bigdataengine(8)/modelaccess(5) = 41 文件**：全仓 0 引用（datadescription 实测不 import 它们）。纯死代码。
2. **dataaccess 的 CRUD 部分(19 文件，不含 storage/)**：`DataAccess.java` + `impl/` + `model/`(10) + `service/`(2) + `exception/`(5)。全仓 0 引用——datasource 的 `DataSourceServiceImpl` 只 import `dataaccess.storage.*`（IStorageAdapter/JdbcAdapterFactory），**不 import CRUD**。死代码。
3. **DorisRunner(1)**：R3 时因"task.model 依赖"回退保留，但实测全仓 0 引用 + 无 @Component/@Service 注解 + 无 @Bean 注册（`registerExecutor` 无人调用它）。死代码。
4. **alert 2 个孤儿测试**：`AlertServiceImplTest`/`NotificationServiceImplTest` import 旧的 `runtime.core.alert`（类已迁 runtime-access），主编译跳过 test 没暴露，`mvn test` 会失败。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 删 format/metadata/lineage/bigdataengine/modelaccess 5 包 41 文件（`git rm -r`） | 编译通过 |
| T2 | 删 dataaccess 的 CRUD 19 文件（`DataAccess.java` + `impl/` + `model/` + `service/` + `exception/`，**保留 storage/**） | 编译通过 |
| T3 | 删 DorisRunner（删前 grep 确认 0 引用 + 无人调用 `registerExecutor` 注册它） | 编译通过 |
| T4 | 删 alert 2 个孤儿测试 | 编译通过 |
| T5 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### T2 删除清单（dataaccess CRUD 19 文件，精确）

```
dataaccess/DataAccess.java
dataaccess/impl/DataAccessImpl.java
dataaccess/exception/  (5: QueryException/InsertException/DeleteException/UpdateException/DataAccessErrorCode)
dataaccess/model/      (10: Pagination/QueryResult/DeleteRequest/BatchRequest/SortCondition/BatchResult/InsertOptions/QueryRequest/FilterCondition/UpdateRequest)
dataaccess/service/    (2: IDataProductService + impl/DataProductServiceImpl)
```

**保留**：`dataaccess/storage/`（14 文件，被 datasource 活跃引用，归 A+7）。

### T3 删前确认命令

```bash
cd /home/guorongxiao/ECOS/ecos_backend
grep -rln "DorisRunner" --include="*.java" . | grep -v target
# 期望: 只有 executor/DorisRunner.java 自身
grep -rn "registerExecutor" --include="*.java" . | grep -v target
# 期望: 无注册 DorisRunner 的调用（若发现则停，汇报）
```

## §禁止清单

1. ❌ **不删 datadescription(19)** —— 被 dataaccess.storage 的 `BaseJdbcAdapter`/`IStorageAdapter` 引用，归 A+7
2. ❌ **不删 datasource(5)/dataaccess.storage(14)** —— 活跃（datasource 被 data-engine+datanet 引用，storage 被 datasource 引用），归 A+7
3. ❌ 删 CRUD 时**不要误删 storage/**（T2 用精确清单，不要 `git rm -r dataaccess/` 整包删）
4. ❌ 删 format 等 41 + CRUD 19 前，`grep -rln "runtime.core.<pkg>" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"` 确认 0 外部消费方（勘察已确认，删前再验防漂移）
5. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁: 已删包 0 残留
grep -rln "runtime.core.\(format\|metadata\|lineage\|bigdataengine\|modelaccess\)" --include="*.java" . | grep -v target
grep -rln "runtime.core.dataaccess.\(impl\|model\|service\|exception\|DataAccess\)" --include="*.java" . | grep -v target
# 期望: 均 0 匹配

# runtime-core 文件数（R1-R3 后 287，本指令后应 ~224）
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
```

## §工时

0.5 天（60 死代码 + 1 类 + 2 测试，删前 grep 确认 + 编译）。

## §风险

- **format/metadata 等可能被 datadescription 内某文件用注解/反射引用**（非 import 形式）：T1 删前 `grep -rn "Format\|Metadata\|Lineage\|BigData\|ModelAccess"` 粗扫 datadescription 目录，确认无类名字符串引用。
- **CRUD 与 storage 边界**：T2 删 CRUD 时若误删 storage 的 `IStorageAdapter`，datasource 编译会断。删前 `find dataaccess -name '*.java' | grep -v storage` 核对恰好 19 文件。
- **`.m2` 旧 JAR**：删包后全量 install，若报旧类冲突，删 `~/.m2/repository/com/chinacreator/gzcm/runtime-core*` 重建。
- **DorisRunner 若被 Spring SPI 激活**：无 @Component 注解不会被扫描，但删前确认 runtime-task 无 `@ComponentScan` 显式扫 executor 包。
