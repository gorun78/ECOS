package com.chinacreator.gzcm.common.arch;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2 B4c-runtime 数据访问收口门禁（架构铁律 §2.5#1 / IR06）。
 *
 * <p>生产类（不含 Dao / Test / Support）一律不得依赖 JdbcTemplate；raw-SQL 收口到
 * {@code common.dao.ObjectRuntimeDao} / {@code common.dao.StateMachineDao}。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.common", importOptions = DoNotIncludeTests.class)
class DataAccessArchTest {

    @ArchTest
    static final ArchRule name_based = noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.common..").and().haveNameNotMatching(".*Dao|.*Test|.*Support")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule prod_classes_do_not_hold_jdbc_field = noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.common..").and().haveNameNotMatching(".*Dao|.*Test|.*Support")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
