# PMO-R2: 数据工程迁移返工（transform/quality → data-engine）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **返工原因**: 上一轮 A+2「迁数据工程到 data-engine」**完全未执行**——transform(15)+quality(4) 仍留在 runtime-core，3 个消费方仍 import 旧 package，git 无对应 commit。本指令从头真迁移。

## §迁移三动作铁律（本指令核心）

**迁移 = 移动，不是复制。**

1. **移原类**（`git mv` 或删原 + 建新，最终原位置类必须消失）
2. **改消费方 import**（data-engine 不再 import 旧 package）
3. **硬门禁 grep 旧 package = 0 匹配**

```bash
# 硬门禁: 全仓不再出现 runtime.core.transform/quality
grep -rln "runtime.core.\(transform\|quality\)" --include="*.java" . | grep -v target
# 期望: 0 匹配（含 runtime-core 自身，迁移后原位置类已消失）
```

## §迁移清单（19 类，已核实）

| 源 package | 目标 package | 文件数 |
|------|------|-----:|
| `runtime.core.transform.*`（含 impl/model/step 子包） | `engine.data.transform.*` | 15 |
| `runtime.core.quality.*`（含 spi 子包） | `engine.data.quality.*` | 4 |

**源位置**：`runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/{transform,quality}/`
**目标位置**：`engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/{transform,quality}/`

> 注：transform 本质是 D→I 转化，最终归 ge-service（格），阶段 D4 再归位。迁入 data-engine 时每个文件头加 `// TODO D4: 归位 ge-service（格）`。

## §消费方（3 文件，已核实）

| 消费方 | 用的包 | 改向 |
|------|------|------|
| `data-engine/.../service/QualityServiceImpl.java` | quality（QualityResult/QualityRule/spi.QualityRuleProvider） | `engine.data.quality.*` |
| `data-engine/.../service/UdfServiceImpl.java` | transform（TransformStep） | `engine.data.transform.*` |
| `data-engine/.../service/PipelineExecutionEngine.java` | transform（TransformChain/TransformStep/impl/TransformChainImpl/model.DataFrame/model.TransformResult/step.*） | `engine.data.transform.*` |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 quality 4 类到 `engine/data/quality/`，改 package 声明 + 内部 import | 编译通过 |
| T2 | 迁 transform 15 类到 `engine/data/transform/`（含 impl/model/step 子包完整迁移），改 package + 内部 import + `// TODO D4` 标记 | 编译通过 |
| T3 | 改 3 个消费方 import → `engine.data.*` | 编译通过 |
| T4 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

## §禁止清单

1. ❌ **禁止复制**——`git mv` 或删原+建新，迁移后 runtime-core 里不能再有 transform/quality 包
2. ❌ 不改方法体/SQL/业务逻辑——纯 package + import 移动
3. ❌ 不碰 0 引用的数据工程包（dataaccess/datadescription/format/metadata/lineage/kettle/bigdataengine/modelaccess），那是 A+5 后续判断
4. ❌ 不建 ge-service 模块（阶段 D4）
5. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 迁入类存在
ls engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/TransformChain.java
ls engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/QualityResult.java

# 原位置类消失
ls runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/transform/TransformChain.java
# 期望: No such file
```

## §工时

1 天（19 类迁移 + 3 消费方 import 改写 + 反复编译调错）。

## §风险

- **transform.step.* 通配 import**：`PipelineExecutionEngine` import 了 `transform.step.*`，step 子包必须完整迁移，漏一个 step 实现编译报错。
- **quality.spi 边界**：quality 4 类含 `spi.QualityRuleProvider`（SPI），迁入后保持 SPI 边界，确认无其他模块实现该 SPI（grep `implements QualityRuleProvider` 兜底）。
- **内部 import 链**：transform 内部 impl→model→step 互相 import，迁入后所有内部 import 都要同步改 package，漏一个编译报错。
