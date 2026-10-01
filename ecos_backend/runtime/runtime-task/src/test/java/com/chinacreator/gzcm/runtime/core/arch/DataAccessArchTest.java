package com.chinacreator.gzcm.runtime.core.arch;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2 B4c-runtime 数据访问收口门禁（架构铁律 §2.5#1 / IR06）。
 *
 * <p>runtime-task 模块内 {@code runtime.core.ontology} / {@code runtime.core.audit} /
 * {@code runtime.core.task} 三棵子树的生产类（不含 Dao / Test / Support）一律不得依赖
 * JdbcTemplate；raw-SQL 收口到对应 dao 包下的
 * {@code GitArchivePendingDao} / {@code AuditRetryDao} / {@code TaskPersistenceDao}。</p>
 *
 * <p>仅分析这三棵子树（不与 runtime-core 共享的 {@code runtime.core.*} 其他包混扫）。</p>
 */
@AnalyzeClasses(
        packages = {
                "com.chinacreator.gzcm.runtime.core.ontology",
                "com.chinacreator.gzcm.runtime.core.audit",
                "com.chinacreator.gzcm.runtime.core.task"
        },
        importOptions = DoNotIncludeTests.class)
class DataAccessArchTest {

    private static final String[] PKGS = {
            "com.chinacreator.gzcm.runtime.core.ontology..",
            "com.chinacreator.gzcm.runtime.core.audit..",
            "com.chinacreator.gzcm.runtime.core.task..",
    };

    @ArchTest
    static final ArchRule name_based = noClasses().that()
            .resideInAnyPackage(PKGS).and().haveNameNotMatching(".*Dao|.*Test|.*Support")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule prod_classes_do_not_hold_jdbc_field = noClasses().that()
            .resideInAnyPackage(PKGS).and().haveNameNotMatching(".*Dao|.*Test|.*Support")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
