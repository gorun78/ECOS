# PMO-A1-3: 移除 dccheng 模块 + 清理 gateway 引用 + 删目录

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **前置（硬门禁）**: A1-1（迁移底层类）已完成 + A1-2（ArchUnit 断言）已转绿。**断言不绿，禁止执行本指令。**
> **铁律**: ①只删不迁——本指令让 dccheng 退出构建，不做任何代码迁移 ②每 Task 独立 commit ③分两步：先移除 pom module（软删除，dccheng 退出构建、目录保留可回退），全量验证 + 肖总确认后再 `git rm -r` 物理删除（硬删除）

## §背景

A1-1 已把 dccheng 的 22 个底层类迁到引擎层（+ Neo4jConfig 迁 runtime-access），A1-2 断言已确认"引擎层不依赖 dccheng"转绿。dccheng 剩余物 = 24 个 Controller（引擎层全有对应）+ 2 个 pom（dccheng-api/dccheng-impl 壳）。

本指令把 dccheng 从构建中彻底移除，并清理 gateway 的 25 处 `excludeFilters` 引用（否则删目录后 gateway 编译失败）。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | `pom.xml` 移除 4 处 `<module>dccheng</module>`（主 modules 413 行 + standard 454 行 + enterprise 483 行 + ultimate 511 行）+ `gateway/pom.xml` 移除 `dccheng-impl` 依赖（第 115-119 行） | `mvn validate` 通过 |
| T2 | `gateway/.../GatewayApplication.java` 清理 excludeFilters 里 25 处 `com.chinacreator.gzcm.dccheng.*` 引用（清单见下） | gateway 编译通过 |
| T3 | 全量 `mvn install -DskipTests` + 三版本 profile validate（dccheng 已退出构建，**目录保留不删**＝软删除） | BUILD SUCCESS，dccheng 目录仍在 |
| T4（后置可选） | 全量回归 + 肖总确认引擎层稳定后，`git rm -r dccheng/` 物理删除 | 目录不存在 |

### T2 清理清单（25 处，GatewayApplication.java excludeFilters 内）

**17 处 ontology**（`com.chinacreator.gzcm.dccheng.ontology.controller.` 前缀）：
OntologyController、OntologyActionController、OntologyActionApiController、OntologyPropertyController、OntologyRelationshipController、OntologyDomainController、OntologyDomainApiController、OntologyVersionController、OntologyVersionSimpleController、OntologyRuleController、OntologyProposalController、OntologyMappingController、OntologyExportController、OntologyDataController、CeosCompatController、AutoDiscoverController、LineageController

**8 处非 ontology**：
- `com.chinacreator.gzcm.dccheng.knowledgegraph.controller.KnowledgeGraphController`
- `com.chinacreator.gzcm.dccheng.knowledgegraph.controller.GraphSyncController`
- `com.chinacreator.gzcm.dccheng.knowledge.KnowledgeApiController`
- `com.chinacreator.gzcm.dccheng.knowledge.KnowledgeSettingsController`
- `com.chinacreator.gzcm.dccheng.guardrails.GuardrailsApiController`
- `com.chinacreator.gzcm.dccheng.glossary.controller.GlossaryController`
- `com.chinacreator.gzcm.dccheng.classification.controller.ClassificationController`
- `com.chinacreator.gzcm.dccheng.cognitive.impl.CognitiveController`

**同时删除** `@ComponentScan` basePackages 里的 `"com.chinacreator.gzcm.dccheng"`（第 36 行），因为 dccheng 已删，留着无害但应清理干净。

## §禁止清单

1. ❌ 不迁任何 dccheng 代码（A1-1 已迁完，本指令是纯删除）
2. ❌ 不碰 gateway excludeFilters 里**非 dccheng** 的引用（buszhi/aimod/sysman/datanet/portal/market/worldmodel/engine 等排除项一律保留）
3. ❌ 不用 `rm -rf` 手动删（用 `git rm -r` 保历史）
4. ❌ 不跳过三版本 profile 验证（standard/enterprise/ultimate 都要能 validate）

## §验证门禁

```bash
# V1: 主 modules 无 dccheng
grep -n "dccheng" /home/guorongxiao/ECOS/ecos_backend/pom.xml
# 期望: 0 匹配

# V2: gateway 无 dccheng 引用
grep -rn "dccheng" /home/guorongxiao/ECOS/ecos_backend/gateway/src/ --include="*.java"
# 期望: 0 匹配

# V3: 全量编译（default profile）
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V4: 三版本 profile validate（模块裁剪不炸）
cd /home/guorongxiao/ECOS/ecos_backend && mvn validate -Pstandard -q && mvn validate -Penterprise -q && mvn validate -Pultimate -q
# 期望: 均成功

# V5: 活跃模块（非 dccheng 目录自身）无 dccheng 引用
grep -rn "com.chinacreator.gzcm.dccheng" /home/guorongxiao/ECOS/ecos_backend --include="*.java" | grep -v target | grep -v "/dccheng/"
# 期望: 0 匹配（dccheng 目录自身的死代码除外）
```

## §工时

0.5 天（pom 4 处 + gateway 25 处 + 删目录 + 编译验证）。

## §风险

- **gateway excludeFilters 是最大坑**：25 处 dccheng `.class` 引用若漏删，gateway 编译直接报"找不到符号"。T2 必须逐条对照清单删干净。
- **软删除后目录是死代码**：移除 module 后 dccheng 目录保留，但里面 24 Controller 因 A1-1 迁走了底层类而编译不过（死代码）。目录保留是为了可回退，T4 物理删除前别误改这些死代码。
- **`.m2` 旧 JAR 陷阱**：dccheng 的旧 jar 若残留在 `~/.m2`，gateway 可能仍扫到旧类。移除 module + 全量 install 后，如仍报 dccheng 相关错误，删 `~/.m2/repository/com/chinacreator/gzcm/dccheng*`。
- **三版本 profile**：dccheng 在三个 profile 的 modules 里都显式列出，漏删任何一个会导致该 profile 构建时找不到目录。
- **T4 物理删除不可逆**：软删除（T1-T3）可回退（目录还在）；T4 `git rm` 后 dccheng 源码只存在于 git 历史。执行 T4 前必须确认引擎层全量回归通过。
