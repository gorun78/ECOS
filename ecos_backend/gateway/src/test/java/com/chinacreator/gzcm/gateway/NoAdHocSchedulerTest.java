package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 详细设计-00 F00-08 — 业务引擎/服务禁自建调度（唯一出口 = runtime-task）。
 *
 * <p>是 {@link ModuleDependencyArchTest#businessMustNotSelfSchedule()} 的<b>命名验收投影</b>：
 * F00-08 的验收命令显式要求 {@code -Dtest='NoAdHocSchedulerTest'}。本类以相同口径
 * （业务包内禁 {@code org.springframework.scheduling.annotation.Scheduled} 注解依赖，
 * 禁 {@code java.util.concurrent.ScheduledExecutorService} 池）提供该命名入口，
 * 白名单例外（runtime 底座 / gateway facade / engine.security TTL 清理 / services.apigateway）
 * 与母规则完全一致，改动任一侧都会同步红。
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='NoAdHocSchedulerTest'}</p>
 */
class NoAdHocSchedulerTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void setUp() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.chinacreator.gzcm");
        System.out.println("[NoAdHocScheduler] imported " + allClasses.size() + " classes");
    }

    /** 业务包白名单：与 ModuleDependencyArchTest.businessPackagesForScheduling() 语义一致。 */
    private static String[] businessPackages() {
        return new String[]{
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.services.sysman..",
                "com.chinacreator.gzcm.services.datanet..",
                "com.chinacreator.gzcm.services.buszhi..",
                "com.chinacreator.gzcm.services.dccheng..",
                "com.chinacreator.gzcm.services.aiming..",
                "com.chinacreator.gzcm.workspace.."
        };
    }

    @Test
    @DisplayName("业务包禁用 Spring @Scheduled（自建触发器）")
    void noScheduledAnnotationInBusiness() {
        DescribedPredicate<JavaClass> scheduledAnno =
                new DescribedPredicate<JavaClass>("@Scheduled 注解") {
                    @Override
                    public boolean test(JavaClass input) {
                        return "org.springframework.scheduling.annotation.Scheduled".equals(input.getName());
                    }
                };
        noClasses().that().resideInAnyPackage(businessPackages())
                .should().dependOnClassesThat(scheduledAnno)
                .because("铁律 §2.5-3 / F00-08：业务定时任务唯一经 runtime-task 注册，禁 @Scheduled")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("业务包禁用 ScheduledExecutorService 自建调度池")
    void noScheduledExecutorServiceInBusiness() {
        DescribedPredicate<JavaClass> scheduledPool =
                new DescribedPredicate<JavaClass>("ScheduledExecutorService") {
                    @Override
                    public boolean test(JavaClass input) {
                        return "java.util.concurrent.ScheduledExecutorService".equals(input.getName());
                    }
                };
        noClasses().that().resideInAnyPackage(businessPackages())
                .should().dependOnClassesThat(scheduledPool)
                .because("铁律 §2.5-3 / F00-08：禁 Executors.newScheduledThreadPool 自建调度池")
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
