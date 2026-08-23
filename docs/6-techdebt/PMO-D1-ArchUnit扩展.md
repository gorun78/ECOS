# PMO指令: D1 ArchUnit 架构守护扩展到六引擎

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①只加测试断言，不改任何生产代码 ②新增断言若发现既有违规，只报告不修（修复另开指令）③禁止跨 Task 预创建文件

## 零、现状摸底（已核实）

已有 5 个 ArchUnit 测试文件，但 **engine 层六引擎零覆盖**：

| 已有测试 | 模块 | 覆盖 |
|----------|------|------|
| ArchitectureTest | sysman | 包结构/依赖/命名规范（8 断言） |
| ArchitectureTest | runtime-core | 同上（8 断言） |
| ArchitectureTest | common-api | 分层（D不依赖K/W、Controller只调自己Service、禁止新增模块/容器、Controller不JdbcTemplate） |
| DcchengRemovalGuardTest | gateway | 引擎层不依赖 dccheng |
| ArchitectureGuardTest | common-api | workspace不依赖buszhi/worldmodel、禁止下层依赖上层、标准版不引用Doris |

## 一、目标状态

六引擎（data/ontology/kb/cognitive/ai/security）各建 ArchUnit 测试 + 全局 API 路径不重复断言。

## 二、分阶段执行计划

| Task | 文件/路径 | 操作 |
|:-----|----------|------|
| P1-1 | `engine/{data,ontology,kb,cognitive,ai,security}-engine/*-impl/src/test/java/.../ArchitectureTest.java` | 六引擎各建基础 ArchitectureTest，参照 `sysman/ArchitectureTest.java` 模板，断言：①所有类在 `engine.xxx` 包下 ②service 接口以 I 开头、impl 在 impl 包 ③controller 在 controller 包 ④entity 不依赖 service 层 |
| P1-2 | `engine/*-engine/*-impl/src/test/java/.../EngineDependencyTest.java`（或并入 P1-1） | 引擎间依赖边界断言：六引擎 `-impl` 不得互相依赖（data 不依赖 ontology、ai 不依赖 kb 等），只能依赖 `runtime-access`/`runtime-core`/`common-api` |
| P1-3 | `gateway/src/test/java/.../ApiPathUniquenessTest.java` | 全局 API 路径不重复断言：扫描所有模块 Controller 的 `@RequestMapping`（类级+方法级拼接），断言无重复路径（防止 `Ambiguous mapping` 启动失败） |

**实现顺序**：P1-1 → P1-2 → P1-3。

## 三、禁止清单

- ❌ 改生产代码让断言「通过」（断言发现的违规是真实债，报告即可，修复另开指令）
- ❌ 断言写得太严导致现有代码编译不过就删测试（应报告违规清单，让肖总决策）
- ❌ 新建测试引入重依赖（ArchUnit 已在 sysman/common-api 有依赖，复用）
- ❌ 引入新 Maven 模块

## 四、风险与回滚

- **断言误伤**：引擎间依赖边界可能比预想复杂（如 cognitive-engine 依赖 kb-engine 的 service 是合理的），写断言前先 `grep import` 摸清真实依赖，避免写一个「一跑就红」的断言。若发现合理依赖，断言里加白名单并注明理由。
- **回滚**：纯测试代码，`git revert` 即可，不影响生产。

## 五、验证门禁

```bash
# V1: 运行新测试（以 data-engine 为例，六引擎同法）
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn test -pl engine/data-engine/data-engine-impl -am -DskipTests=false 2>&1 | grep -E "Tests run|BUILD SUCCESS|BUILD FAILURE"'

# V2: 六引擎测试文件齐全
find engine -path '*src/test*ArchitectureTest.java' -o -path '*src/test*ApiPathUniquenessTest.java' | sort
# 期望: 6 引擎 ArchitectureTest + 1 ApiPathUniquenessTest

# V3: 违规清单报告（若有）
# 断言若发现违规，交付报告列出违规类/路径/依赖，不修
```

## 六、工时估算

P1-1（3h）+ P1-2（2h）+ P1-3（2h）≈ **7h**
