package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * F05-13 三层纪律（设计-05 冻结验收名）：认知 Service 层禁直持
 * {@code JdbcTemplate} —— SQL 一律下沉 {@code cognitive2.dao}（与
 * {@code DataAccessArchTest} 双门禁互锁，防回潮）。
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.cognitive2")
class CognitiveLayerDisciplineTest {

    @ArchTest
    static final ArchRule noJdbcTemplateInCognitiveServiceLayer =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.engine.cognitive2..service..")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                    .because("F05-13：Service 删除 JdbcTemplate，数据访问收口 DAO 层");
}
