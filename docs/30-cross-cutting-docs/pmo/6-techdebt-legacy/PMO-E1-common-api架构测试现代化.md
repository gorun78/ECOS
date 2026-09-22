# PMO指令: E1 common-api ArchitectureTest 架构测试现代化

> **来源**: 肖国荣 | **日期**: 2026-08-24
> **协同**: ECOS-ARCH
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①只改测试文件，不改任何 Java 生产代码 ②禁止动六引擎 ArchitectureTest（D1 已交付）③铁律5 的 JdbcTemplate 下沉不属于本次（另开 PMO-E2）④禁止改 pom.xml 的 modules 结构

## 零、现状摸底（已核实）

common-api 的 `ArchitectureTest`（5 条铁律全局守护）停留在旧 DIKW 架构，全量 `mvn test -am` 在 common-api 模块 **3 个失败**：

| # | 失败 | 根因 |
|---|------|------|
| 铁律2 | `Controller只能调自己的Service` — datanet 规则 `failed to check any classes`（:154） | `..datanet..controller..` 包已随 A+7c 删 datanet 消失 |
| 铁律4 | `禁止新增Docker容器`（:323） | compose 已迁 `ecos-docker/`，测试仍查 `ecos_backend/` 根下旧文件（flagship 6 image 超基线 5） |
| 铁律5 | `Controller必须通过Service访问数据库`（:337） | 158 次 JdbcTemplate 违规（33 个 Controller 文件）——**真生产债务** |

**关键判断**：铁律1/2 的职责已被 D1 的六引擎 ArchitectureTest（30 断言：包结构/命名/分层/引擎间依赖边界）取代，旧 DIKW 模块清单（datanet/dccheng/buszhi/aimod/worldmodel/cognitive/portal）已过时。

**当前实际模块结构**（供 T1/T4 参考）：
- 默认 modules 块 11 个：common/engine/runtime/sysman/buszhi/workspace/services×4(api-gateway/identity/ontology/agent)/gateway
- engine 六引擎：data/ontology/kb/cognitive/ai/security-engine
- runtime 5 子模块：runtime-core/runtime-access/runtime-task/runtime-monitor/llm-gateway（runtime-security/runtime-crypto/runtime-datanet 目录残留但已出构建）
- 目录残留（不在 pom modules）：dccheng/、database/、runtime-security/、runtime-crypto/、runtime-datanet/

## 一、目标状态

common-api `ArchitectureTest` 恢复 BUILD SUCCESS。铁律5 的 158 次 JdbcTemplate 是生产代码债务，本次用 `@Disabled` 挂起并标注指向 PMO-E2，不修。

## 二、分阶段执行计划

| Task | 操作 | 改文件 |
|:-----|------|--------|
| T1 | `importClasses()` 的 modules 数组（第 58-83 行）更新为当前实际模块清单：删 datanet/dccheng/aimod/worldmodel/cognitive/portal/market/runtime-security/runtime-crypto，加 engine 六引擎 impl、runtime-access、services 4 子服务 | `common/common-api/src/test/java/com/chinacreator/gzcm/common/ArchitectureTest.java` |
| T2 | 删除铁律1 `D层不能依赖K层_W层`（第 114-130 行）——旧 DIKW 分层废弃，D1 已覆盖引擎间依赖边界 | 同上 |
| T3 | 删除铁律2 `Controller只能调自己的Service`（第 136-238 行）——旧模块清单过时，datanet 空 should 直接失败；D1 已覆盖引擎级 Controller 归属 | 同上 |
| T4 | 铁律3 baseline `13` → `11`（第 275 行，当前默认 modules 块实际数） | 同上 |
| T5 | 铁律4 baselineImages（第 292-296 行）从 4 个旧文件名（standard/enterprise/flagship/doris，指向 ecos_backend/ 根）改为 `ecos-docker/` 下新文件，按三版本实际容器数重定基线（standard=PG，enterprise=PG+Neo4j+MinIO+OPA，ultimate=PG+Neo4j+MinIO+OPA+Doris）。先读 ecos-docker/ 各 compose 实际 image 数再定基线，基线数字+理由写进交付报告 | 同上 |
| T6 | 铁律5 `Controller必须通过Service访问数据库_不能直接JdbcTemplate`（第 330-338 行）加 `@Disabled("158处JdbcTemplate违规(33 Controller)，下沉见 PMO-E2")` | 同上 |
| T7 | 清理 `ecos_backend/` 根下 4 个过时 compose 文件（docker-compose-standard/enterprise/flagship/doris.yml）——先 `grep -rn` 确认无脚本/文档引用后 `git rm` | 4 个 yml 文件 |

**实现顺序**：T1 → T2/T3/T4/T5/T6（互不依赖可并行）→ T7（最后，先确认无引用）。

## 三、禁止清单

- ❌ 改任何 Java 生产代码（本次只动 `common/common-api/src/test/` 下的测试文件 + 4 个过时 yml）
- ❌ 动六引擎的 ArchitectureTest（D1 已交付，独立守护）
- ❌ 把铁律5 的 JdbcTemplate 下沉混进本次（那是 PMO-E2，生产代码重构）
- ❌ 改 pom.xml 的 modules 结构（铁律3 只改 baseline 数字，不增删 module）
- ❌ 改 ecos-docker/ 下 compose 文件内容（铁律4 只改测试指向和基线，不动 compose 本身）

## 四、风险与回滚

- **T1 误删正在用的模块**：modules 数组列出 `target/classes` 目录，删错模块会导致断言"漏扫"（假绿）。写清单前先 `ls -d */` 对照默认 modules 块 11 个聚合模块确认。
- **T5 基线定错**：ecos-docker 用 base + overlay 结构，image 数 ≠ 容器数。定基线前必须读 ecos-docker/ 的 compose 结构，写清楚"为什么是这几个数"。
- **T7 误删被引用的旧 compose**：删前 `grep -rn "docker-compose-flagship\|docker-compose-doris"` 全仓确认无引用。
- **回滚**：纯测试代码 + 4 个 yml，`git revert` 即可，不影响生产。

## 五、验证门禁

```bash
# V1: common-api 测试恢复绿色
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn test -pl common/common-api 2>&1 | grep -E "Tests run|BUILD SUCCESS|BUILD FAILURE|Imported"'
# 期望: Imported N>0，Tests run: N, Failures: 0，BUILD SUCCESS

# V2: 铁律5 为 Skipped 非 Failed
# 上述输出里铁律5 显示 Skipped: 1

# V3: 测试类无旧方法残留
grep -n "D层不能依赖K层_W层\|Controller只能调自己的Service" common/common-api/src/test/java/com/chinacreator/gzcm/common/ArchitectureTest.java
# 期望: 0 匹配

# V4: 全量 -am 不再被 common-api 阻断（可选，验证完整链路）
# mvn test -am 时 common-api 模块不再 BUILD FAILURE
```

## 六、工时估算

T1(0.5h) + T2(0.25h) + T3(0.25h) + T4(0.25h) + T5(1h) + T6(0.25h) + T7(0.5h) ≈ **3h**

## 七、一句话给 PMO

common-api 的架构测试还停在旧 DIKW 架构，把过时的断言删掉、基线更新到五引擎现状；铁律5（158 处 JdbcTemplate）先 @Disabled 挂起，下沉另开 PMO-E2。
