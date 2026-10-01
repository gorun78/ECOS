package com.chinacreator.gzcm.services.agent.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2-B4b agent-service 数据访问收口守门（架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>agent-service 的数据访问/data-holder 类多在 {@code ..runtime..} 子包（copilot /
 * memory / evolution / toolrouter）而非经典 {@code ..service..}，故规则 2 额外纳入
 * {@code ..runtime..} 包，确保散落各处持有 JdbcTemplate 的类都被锁住。数据访问一律
 * 下沉到 {@code com.chinacreator.gzcm.services.agent.dao}（extends
 * {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）。</p>
 *
 * <p>两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> 类的数据访问纪律；测试类持 JdbcTemplate 作为 test double 属合法手段。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.services.agent")
class DataAccessArchTest {

    @ArchTest
    static final ArchRule service_controller_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .haveNameMatching(".*Service")
                    .or().haveNameMatching(".*ServiceImpl")
                    .or().haveNameMatching(".*Controller")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule runtime_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.services.agent..service..",
                                        "com.chinacreator.gzcm.services.agent..controller..",
                                        "com.chinacreator.gzcm.services.agent..runtime..")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
