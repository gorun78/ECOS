# PMO-A1-2: ArchUnit 守护断言 + dccheng Controller 对应确认

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A1-1（迁移 dccheng 底层类）完成后，本指令断言才能转绿
> **铁律**: ①断言是"删前门禁"——A1-3 删 dccheng 目录前，本断言必须通过 ②不改任何业务代码，只加测试 ③每 Task 独立 commit

## §背景

dccheng 的 24 个 Controller 已核实**全部在引擎层有对应**（gateway 的 `@ComponentScan excludeFilters` 已屏蔽 dccheng 版本，改用引擎层版本）。A1-1 迁移底层类后，引擎层 Controller 的 import 从 dccheng 改写为 engine.*，引擎层不再依赖 dccheng。

本指令写一条 ArchUnit 断言，把"引擎层不依赖 dccheng"固化为可执行门禁，作为 A1-3 删目录的安全网。

## §24 Controller 对应确认表（已核实，A1-3 删前对照用）

| dccheng Controller | 引擎层对应 |
|------|------|
| OntologyController / OntologyActionController / OntologyActionApiController / OntologyPropertyController / OntologyRelationshipController / OntologyDomainController / OntologyDomainApiController / OntologyVersionController / OntologyVersionSimpleController / OntologyRuleController / OntologyProposalController / OntologyMappingController / OntologyExportController / OntologyDataController / CeosCompatController / AutoDiscoverController / LineageController（17 个） | ontology-engine/controller 同名 17 个（金） |
| KnowledgeSettingsController / KnowledgeApiController | kb-engine/controller 同名（水） |
| KnowledgeGraphController / GraphSyncController | kb-engine/controller 同名（水） |
| GlossaryController | ontology-engine/controller/GlossaryController（金） |
| ClassificationController | ai-engine/controller/ClassificationController（火） |
| GuardrailsApiController | ai-engine/controller/GuardrailsApiController（火） |

**结论：24/24 有对应，无需迁移 Controller，A1-3 直接删。**

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | gateway `pom.xml` 加 archunit-junit5 test 依赖（`com.tngtech.archunit:archunit-junit5`，version 由父 pom dependencyManagement 管，已定义 1.2.1） | `mvn install -pl gateway` 编译通过 |
| T2 | gateway 新增 `src/test/java/com/chinacreator/gzcm/gateway/DcchengRemovalGuardTest.java`，断言 engine 不依赖 dccheng | `mvn test -pl gateway` 该断言通过 |

### T2 断言代码（完整）

```java
package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * A1 拆解 dccheng 的删前门禁。
 * 断言：引擎层(com.chinacreator.gzcm.engine..) 不得依赖 dccheng(com.chinacreator.gzcm.dccheng..)。
 * A1-1 迁移底层类后此断言必须为绿；A1-3 删 dccheng 目录前必须通过。
 */
public class DcchengRemovalGuardTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinacreator.gzcm.engine", "com.chinacreator.gzcm.dccheng");

    @Test
    void 引擎层不得依赖dccheng() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.chinacreator.gzcm.engine..")
                .should().dependOnClassesThat().resideInAPackage("com.chinacreator.gzcm.dccheng..");
        rule.check(classes);
    }
}
```

**说明**：
- 断言只扫 `engine` + `dccheng` 两个包，避免扫全仓拖慢。
- `importPackages` 从 classpath 找类，gateway 依赖 engine/dccheng 的 jar，故能扫到。
- A1-1 完成后，engine 里所有 `import com.chinacreator.gzcm.dccheng` 已改写为 `com.chinacreator.gzcm.engine`，断言变绿。

## §禁止清单

1. ❌ 不改任何业务代码（Controller/Service/Repository 全不碰），只加 pom 依赖 + 测试类
2. ❌ 不在本指令里删 dccheng 或改 pom module（那是 A1-3）
3. ❌ 不用 `mvn compile` 替代 `mvn test`（断言必须真跑）

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn test -pl gateway -Dtest=DcchengRemovalGuardTest -q'
# 期望: BUILD SUCCESS（A1-1 完成后）

# 若 A1-1 未完成，此断言应失败并列出 engine 里残留的 dccheng import（这是预期的"红"）
```

## §工时

0.5 天（pom 依赖 + 测试类 + 跑一次）。

## §风险

- **断言"红"是预期**：A1-1 完成前，引擎层 18 个 Controller 还在 import dccheng 底层类，断言会失败。这是 TDD 的正常状态，不是 Bug。A1-1 完成后重跑应转绿。
- **archunit 扫 classpath 陷阱**：`importPackages` 依赖 gateway classpath 里有 engine/dccheng 的 jar。若 gateway pom 未依赖 engine（只靠 ComponentScan 字符串），需先确认 gateway pom 有 engine-api/engine-impl 依赖。若没有，改用 `importPackages("com.chinacreator.gzcm")` 扫全 classpath。
