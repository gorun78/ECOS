# PMO-A+7a: 断 engine.data 对 datanet 的反向依赖（model/dto + repository 迁入）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+6 已完成
> **目标**: engine.data 不再 import `com.chinacreator.gzcm.datanet.*`，为 A+7c 删 datanet 铺路

## §背景（已勘察，勿重复）

datanet-api 与 engine.data-api 定义了**方法签名一字不差的重复接口**（DataSourceService 5/5、MetadataService 2/2、CategoryService 7/7、CatalogService 10/10），而 engine/data 的 impl 反向 import 了 datanet 的接口 + model/dto + repository。这是下层（data-engine）反向依赖本应废弃的上层旧模块的架构坏味道。

## §迁移三动作铁律（同 R1-R3）

迁移 = 移动不是复制。删原类 + 改消费方 import + 硬门禁 grep 旧 package 0 匹配。

## §迁移清单

| 类别 | 源 | 目标 | 动作 |
|------|----|------|------|
| model 4 | `datanet.model.DataField/DataResource/CatalogItem/DataCategory` | `engine.data.model.*` | 迁 |
| dto 2 | `datanet.dto.DataSourceDTO/CatalogQueryDTO` | `engine.data.dto.*` | 迁 |
| repository 2 | `datanet.repository.DataSourceRepository/DataResourceRepository`（被 MetadataCollectionService 引用） | `engine.data.repository.*` | 迁 |
| 接口 4 | `datanet.service.DataSourceService/MetadataService/CategoryService/CatalogService`（engine.data-api 已有同签名接口） | — | 删 |
| repository 3 | `datanet.repository.DataFieldRepository/CategoryRepository/CatalogItemRepository`（0 引用） | — | 删 |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 model 4 + dto 2 到 `engine/data-engine/data-engine-api/.../engine/data/model|dto/`，改 package | 编译通过 |
| T2 | 迁 repository 2（DataSourceRepository/DataResourceRepository）到 `engine/data-engine/data-engine-impl/.../engine/data/repository/`，改 package | 编译通过 |
| T3 | 改 engine.data-api 5 接口 import：`datanet.model/dto.*` → `engine.data.model/dto.*`（DataSourceService/CategoryService/MetadataService/PipelineService/CatalogService） | 编译通过 |
| T4 | 改 engine/data-impl 全部 import：`datanet.service.DataSourceService/MetadataService/CategoryService/CatalogService` → `engine.data.*`；`datanet.model/dto/repository.*` → `engine.data.*`（grep 兜底） | 编译通过 |
| T5 | 删 datanet 重复接口 4 + 死 repository 3 | 编译通过 |
| T6 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### T3/T4 消费方改写（执行时 `grep -rln "import com.chinacreator.gzcm.datanet" engine/data-engine --include="*.java"` 兜底）

已知 engine/data 里 import datanet 的 15 文件（改向规则）：
- **接口**（`datanet.service.DataSourceService` 等 4 个）→ `engine.data.DataSourceService` 等（engine.data-api 自己的同名接口，签名一致，直接换 import）
- **model/dto**（`datanet.model.DataField` 等 6 个）→ `engine.data.model.DataField` 等（T1 新位置）
- **repository**（`datanet.repository.DataSourceRepository/DataResourceRepository`）→ `engine.data.repository.*`（T2 新位置）

## §禁止清单

1. ❌ **禁止复制**——迁 model/dto/repository 后，datanet 原文件必须消失（git rm/mv）
2. ❌ 不改接口方法签名、不改方法体——纯 package + import 移动
3. ❌ **不碰 datanet 的 connector/pipeline**（那是 A+7b）
4. ❌ **不碰 datanet 的 impl/controller/其他**（那是 A+7c 删）
5. ❌ 不碰 runtime-core 的 datasource/storage/datadescription（那是 A+7b）
6. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁: engine.data 不再 import datanet
grep -rln "import com.chinacreator.gzcm.datanet" engine/data-engine --include="*.java" | grep -v target
# 期望: 0 匹配

# 硬门禁: datanet 重复接口/死 repository 已删
ls datanet/datanet-api/src/main/java/com/chinacreator/gzcm/datanet/service/DataSourceService.java
# 期望: No such file
```

## §工时

1 天（6 model/dto + 2 repository 迁移 + 15 消费方改写 + 删 7 重复/死类 + 编译）。

## §风险

- **接口签名陷阱**：engine.data.DataSourceService 和 datanet.service.DataSourceService 签名一致（5 方法相同），但返回类型 `DataSourceEntity`/`DataSourceDTO` 分别来自 runtime-core 和 datanet——T3 改向 engine.data 接口后，若 engine.data 接口的返回类型 import 还是旧的（`datanet.dto.DataSourceDTO`），需同步改成 `engine.data.dto.DataSourceDTO`（T1 新位置）。这是本指令最容易漏的连带 import。
- **repository 接口方法**：DataSourceRepository/DataResourceRepository 迁 engine.data 后，MetadataCollectionService 注入它们的类型要同步改。
- **PipelineService 双重**：engine.data-api 的 PipelineService 也 import datanet（pipeline 类），但 pipeline 类是 A+7b 才迁——T3 只改 PipelineService 里 import 的 model/dto，**不改** pipeline 类 import（留 A+7b）。
