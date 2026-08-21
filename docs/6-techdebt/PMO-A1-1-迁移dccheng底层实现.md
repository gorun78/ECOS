# PMO-A1-1: 迁移 dccheng ontology 底层实现到引擎层

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **铁律**: ①对象代码归引擎层（五行），转化逻辑归服务层（格致诚明）②只迁移不重写——纯 package 移动 + import 改写，不改任何方法体 ③每 Task 独立 commit + 全量编译验证

## §背景

dccheng 模块把"引擎层对象管理"（ontology 的 Service/Repository/领域模型）和"服务层转化职责"混在一起。ontology-engine 的 18 个 Controller 反向 import dccheng 的底层类，形成跨模块反向依赖。

本指令把 dccheng 的 ontology 底层实现**迁移**到 ontology-engine（金·信息本体），把 Neo4jConfig 迁移到 kb-engine（水·知识）。**只迁不删**——dccheng 目录和 Controller 在 A1-3 指令处理。

## §迁移清单（22 个类）

| 类 | 源包 | 目标包 | 行数 |
|----|------|--------|-----:|
| OntologyEntity | dccheng.ontology | engine.ontology.model | 52 |
| OntologyProperty | dccheng.ontology | engine.ontology.model | 92 |
| OntologyRelationship | dccheng.ontology | engine.ontology.model | 40 |
| OntologyRule | dccheng.ontology | engine.ontology.model | 60 |
| OntologyAction | dccheng.ontology | engine.ontology.model | 64 |
| OntologyDomain | dccheng.ontology | engine.ontology.model | 48 |
| OntologyVersion | dccheng.ontology | engine.ontology.model | 48 |
| OntologyRepository | dccheng.ontology | engine.ontology.repository | 357 |
| OntologyRuleRepository | dccheng.ontology | engine.ontology.repository | 94 |
| OntologyDomainRepository | dccheng.ontology | engine.ontology.repository | 94 |
| OntologyVersionRepository | dccheng.ontology | engine.ontology.repository | 98 |
| OntologyMappingStore | dccheng.ontology | engine.ontology.repository | 14 |
| OntologyService | dccheng.ontology | engine.ontology.service | 378 |
| OntologyRuleService | dccheng.ontology | engine.ontology.service | 143 |
| OntologyDomainService | dccheng.ontology | engine.ontology.service | 133 |
| OntologyActionService | dccheng.ontology | engine.ontology.service | 183 |
| OntologyVersionService | dccheng.ontology | engine.ontology.service | 275 |
| ActionHookExecutor | dccheng.ontology | engine.ontology.engine | 234 |
| FunctionEvaluator | dccheng.ontology | engine.ontology.engine | 555 |
| GlossaryEntity | dccheng.glossary（api） | engine.ontology.glossary | — |
| GlossaryRepository | dccheng.glossary（impl） | engine.ontology.glossary | 104 |
| Neo4jConfig | dccheng.knowledgegraph | engine.kb.config | 47 |

## §Task

| Task | 文件 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 7 个领域模型类 | 迁移到 `engine/ontology-engine/ontology-engine-impl/.../engine/ontology/model/`，改 package 声明 + 所有 import | `mvn install -DskipTests` 编译通过 |
| T2 | 6 个 Repository + GlossaryEntity + GlossaryRepository | 迁移到 `engine/ontology/.../repository/` 和 `glossary/`，改 package + import | 同上 |
| T3 | 5 个 Service | 迁移到 `engine/ontology/.../service/`，改 package + import（含内部对 Repository/Model 的 import） | 同上 |
| T4 | ActionHookExecutor + FunctionEvaluator | 迁移到 `engine/ontology/.../engine/`，改 package + import | 同上 |
| T5 | Neo4jConfig | 迁移到 `engine/kb-engine/kb-engine-impl/.../engine/kb/config/`，改 package + import | 同上 |

**迁移后必须改写的 import 消费方（关键）**：
- `engine/ontology-engine/.../controller/` 下 18 个 Controller（GlossaryController、LineageController、OntologyDataController、OntologyActionController、OntologyActionApiController、OntologyController、OntologyDomainController、OntologyDomainApiController、OntologyExportController、OntologyMappingController、OntologyPropertyController、OntologyProposalController、OntologyRelationshipController、OntologyRuleController、OntologyVersionController、OntologyVersionSimpleController 等）
- `workspace/workspace-impl/.../controller/ObjectController.java` + `ObjectActionController.java`（import ActionHookExecutor + FunctionEvaluator）
- 迁移类之间的内部 import（Service→Repository、Repository→Model）

## §禁止清单

1. ❌ 不删 dccheng 目录、不删 dccheng 的 24 个 Controller（那是 A1-3）
2. ❌ 不改任何方法体、不改任何 SQL、不改任何业务逻辑——纯 package + import 移动
3. ❌ 不改类名、不改注解、不改构造器签名
4. ❌ 不改 ontology-engine 已有代码（FunctionSandboxEngine 等 2,950 行新能力），只追加迁移的类
5. ❌ 不碰 kb-engine/cognitive-engine 的其他文件（除 Neo4jConfig 迁移目标包外）
6. ❌ 不用 `mvn compile` 替代 `mvn install`（.m2 旧 JAR 不覆盖）

## §验证门禁

```bash
# V1: 全量编译（必须 install）
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V2: 引擎层不再 import dccheng（除 dccheng 自身和 boot ComponentScan 外）
grep -r "import com.chinacreator.gzcm.dccheng" engine/ workspace/ services/ \
  --include="*.java" | grep -v target
# 期望: 0 匹配（boot 的 ComponentScan 字符串除外，那个 A1-3 处理）

# V3: 迁移后的类存在且 package 正确
ls engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/model/OntologyEntity.java
ls engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/config/Neo4jConfig.java
# 期望: 均存在
```

## §工时

T1-T5 每个 0.5-1 天，共 3-4 天（含 import 改写的反复编译调错）。

## §风险

- **迁移顺序必须 Model → Repository → Service → 执行器**（下层先迁），否则中间态编译失败。
- **Neo4jConfig 的 `neo4jDriver` Bean 被 4 模块消费**（kb-engine/runtime/workspace/ontology-engine），迁到 kb-engine 后必须确认 @Bean 仍被 ComponentScan 扫到（kb-engine-impl 已被 gateway 扫描，理论无影响，但需 V1 编译 + 启动验证）。
- **workspace 反向依赖**：workspace import ActionHookExecutor/FunctionEvaluator，迁到 ontology-engine 后 workspace 需依赖 ontology-engine-api。若 workspace pom 未引入 ontology-engine-api，需补依赖（这是允许的跨模块依赖，方向正确）。
