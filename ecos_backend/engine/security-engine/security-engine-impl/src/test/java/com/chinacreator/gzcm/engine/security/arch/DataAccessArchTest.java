package com.chinacreator.gzcm.engine.security.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 数据访问收口 ArchUnit 守门（R2-B3b security-engine-impl JdbcTemplate 收口）。
 *
 * <p>铁律 §2.5 / IR06：{@code *Service*} / {@code *ServiceImpl} / {@code *Controller}
 * 禁止直接依赖 {@code org.springframework.jdbc.core.JdbcTemplate} —— SQL 一律收敛
 * 到 {@code ..dao..} 子包（{@code JdbcTemplateDaoSupport} 底座）。</p>
 *
 * <p>规则：</p>
 * <ol>
 *   <li>任何 Service/ServiceImpl/Controller（非 Test）不得直接依赖 JdbcTemplate；</li>
 *   <li>service / controller 包内的一切非 Test 类
 *       不得直接依赖 JdbcTemplate（DAO 只允许出现在 {@code ..dao..}）。</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.security")
class DataAccessArchTest {

    @ArchTest
    static final ArchRule rule1 = noClasses().that()
            .haveNameMatching(".*Service").or().haveNameMatching(".*ServiceImpl")
            .or().haveNameMatching(".*Controller")
            .and().haveNameNotMatching(".*Test")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule rule2 = noClasses().that()
            .resideInAnyPackage("com.chinacreator.gzcm.engine.security..service..",
                                "com.chinacreator.gzcm.engine.security..controller..")
            .and().haveNameNotMatching(".*Test")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
