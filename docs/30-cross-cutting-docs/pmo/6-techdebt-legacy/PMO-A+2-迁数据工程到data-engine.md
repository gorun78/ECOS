# PMO-A+2: 迁数据工程活引用 → data-engine（土）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **铁律**: ①数据工程代码归 data-engine（土），不留在 runtime（器）②只迁 data-engine 活引用的包，0 引用包归 A+5 判断删/迁 ③纯 package 移动 + import 改写 ④每 Task 独立 commit

## §背景

runtime-core 的数据工程包共 118 文件，但已核实**只有 2 个包被 data-engine 活引用**（共 19 文件）：

| 包 | 文件数 | 活引用方 |
|------|-----:|------|
| `transform` | 15 | data-engine `UdfServiceImpl`（TransformStep）、`PipelineExecutionEngine`（TransformChain/TransformStep/DataFrame/TransformResult/step.*） |
| `quality` | 4 | data-engine `QualityServiceImpl`（QualityResult/QualityRule/QualityRuleProvider） |

其余 99 文件（dataaccess 33 / datadescription 19 / format 15 / metadata 10 / lineage 3 / kettle 6 / bigdataengine 8 / modelaccess 5）**0 外部引用**，归 A+5 判断删或迁，本指令不碰。

## §迁移清单（19 文件）

**源** `com.chinacreator.gzcm.runtime.core.transform.*` → **目标** `com.chinacreator.gzcm.engine.data.transform.*`（15 文件）
**源** `com.chinacreator.gzcm.runtime.core.quality.*` → **目标** `com.chinacreator.gzcm.engine.data.quality.*`（4 文件）

> 注：transform 本质是 D→I 转化逻辑，最终归服务层 ge-service（格）。但 A+2 阶段 ge-service 未建（阶段 D4 才收敛四转化服务），且 data-engine 活引用它，故先归 data-engine（土），D4 再归位。指令里需在文件头注释标注 `// TODO D4: 归位 ge-service（格）`。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 quality 4 文件到 `engine/data-engine/data-engine-impl/.../engine/data/quality/`，改 package + 内部 import | `mvn install -DskipTests` 通过 |
| T2 | 迁 transform 15 文件到 `engine/data-engine/data-engine-impl/.../engine/data/transform/`，改 package + 内部 import | 同上 |
| T3 | 改写消费方 3 文件：`UdfServiceImpl`、`QualityServiceImpl`、`PipelineExecutionEngine` 的 import 指向 `engine.data.*` | 同上 |
| T4 | 全量编译 + 三版本 profile validate | BUILD SUCCESS |

## §禁止清单

1. ❌ 不改方法体、SQL、业务逻辑——纯 package + import
2. ❌ 不碰 0 引用的数据工程包（dataaccess/datadescription/format/metadata/lineage/kettle/bigdataengine/modelaccess），那是 A+5
3. ❌ 不建 ge-service 模块（那是阶段 D4）
4. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
# V1: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V2: data-engine 不再 import runtime.core.transform/quality
grep -rln "runtime.core.\(transform\|quality\)" engine/data-engine --include="*.java" | grep -v target
# 期望: 0 匹配

# V3: 迁入类存在
ls engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/TransformChain.java
ls engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/QualityResult.java
```

## §工时

1 天（19 文件迁移 + 3 文件 import 改写）。

## §风险

- **transform 的 step.* 通配**：`PipelineExecutionEngine` import 了 `transform.step.*`（多个 step 实现），迁入 data-engine 后该通配 import 也要改，注意 step 子包完整迁移。
- **quality.spi 子包**：quality 4 文件含 `spi.QualityRuleProvider`，迁入后 data-engine 的 SPI 边界要保持（`QualityRuleProvider` 是 SPI，data-engine 其他模块可能实现它）。
- **D4 二次归位**：transform 迁 data-engine 是临时方案（D4 才归 ge-service），T2 必须加 `// TODO D4` 注释标记，否则 D4 时找不到这些待归位的类。
