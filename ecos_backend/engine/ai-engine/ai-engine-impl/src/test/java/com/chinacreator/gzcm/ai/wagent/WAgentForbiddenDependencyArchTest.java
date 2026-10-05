package com.chinacreator.gzcm.ai.wagent;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分册10 F10-03 · W Agent 编排三红线 ArchUnit 门禁（PMO-C 系列；等价 C257 W275 的 wagent 侧前锋）。
 *
 * <p>三条红线：</p>
 * <ol>
 *   <li>{@code wagent..} 不依赖任何 {@code engine..impl..} 或 JDBC/Driver 直接引用
 *    （工具调用必走 {@code EngineEndpointInvoker}，即 REST，禁 {@code JdbcTemplate}）。</li>
 *   <li>W Agent 控制器必须在 controller 子包，禁散发（避免 V233 落 hub 集散落字面量）。</li>
 *   <li>{@code wagent.o..} 不错过 {@code ScheduledExecutorService}（调度必走 runtime-task；PMU 4-Circuit）。</li>
 * </ol>
 *
 * <p>P-5 契约同源（R-50）：本规则为静态层——controller 存在性 presence 另由
 * {@code WagentApiParityGuard} 在 gateway 侧承担（24 端点在一处声明，一命同现）。</p>
 */
class WAgentForbiddenDependencyArchTest {

    private JavaClasses classes() {
        return new ClassFileImporter()
                .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES)
                .importPackages("com.chinacreator.gzcm.ai.wagent");
    }

    @Test
    void wAgentPackageNeverImportsEngineImplOrJdbc() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.chinacreator.gzcm.ai.wagent..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.chinacreator.gzcm.engine..impl..",
                        "javax.sql..",
                        "java.sql..driver..")
                .because("F10-03 / PMO-C C257：wagent 禁 import 引擎 impl 与 JDBC（铁律 §0.6 编排/生产分离）");
        rule.check(classes());
    }

    @Test
    void wAgentControllersMustBeInControllerSubpackage() {
        // §4.1 组件落位：wagent 子树里凡以 "Controller" 结尾的简单名类，
        // 必须落 wagent.web 子包，禁止散发到 orchestrator/readiness/tool 等域包
        // （V233 落 hub 集散落字面量防御）。ArchUnit 1.2.1 DSL 不支持
        // haveSimpleNameMatching(regex)，直接按 JavaClass 遍历断言。
        java.util.List<String> offenders = classes().stream()
                .filter(c -> c.getSimpleName().endsWith("Controller"))
                .filter(c -> !c.getPackageName().equals("com.chinacreator.gzcm.ai.wagent.web")
                        && !c.getPackageName().startsWith("com.chinacreator.gzcm.ai.wagent.web."))
                .map(com.tngtech.archunit.core.domain.JavaClass::getName)
                .sorted()
                .toList();
        org.junit.jupiter.api.Assertions.assertTrue(
                offenders.isEmpty(),
                "wagent 控制器必须落 wagent.web 子包（§4.1），下列违例须改包名：" + offenders);
    }

    @Test
    void wAgentServicesMustNotDirectlyInstantiateScheduler() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.chinacreator.gzcm.ai.wagent..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "java.util.concurrent.ScheduledExecutorService")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "java.util.concurrent.ScheduledThreadPoolExecutor")
                .because("F10-15 + 铁律 §2.5：调度一律经 runtime-task ITaskManagementService，禁自建");
        rule.check(classes());
    }
}
