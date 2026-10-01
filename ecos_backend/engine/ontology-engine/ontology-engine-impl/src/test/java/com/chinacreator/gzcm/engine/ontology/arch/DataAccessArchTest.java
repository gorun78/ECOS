package com.chinacreator.gzcm.engine.ontology.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ontology-engine 数据访问收口守门（B3-a / R2-B3 / 架构铁律 §2.5 #1 · 防回潮）。
 *
 * <p>架构铁律 §2.5 #1 / IR06 + 后端开发规范 §三：Service / Controller 层<b>禁</b>
 * {@code private final JdbcTemplate} 直持。数据访问一律下沉到
 * {@code com.chinacreator.gzcm.engine.ontology.dao} 下的
 * {@code XxxDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}
 * （B1 data-engine ground-truth 同款 pattern）。engine / glossary / repository 包内的
 * 领域类（*Repository / FunctionSandboxEngine 等）不在本规则覆盖范围，列入 B3c 预留批次。</p>
 *
 * <p>本测试在 {@code mvn install}（含 test-compile + surefire）时立即失败任何：
 * <ol>
 *   <li>名为 {@code *Service}/{@code *ServiceImpl}/{@code *Controller} 的类依赖
 *       {@code org.springframework.jdbc.core.JdbcTemplate}；或</li>
 *   <li>位于 {@code ..service..} / {@code ..controller..} 包的类依赖
 *       {@code org.springframework.jdbc.core.JdbcTemplate}。</li>
 * </ol>
 *
 * <p>两条规则均以 {@code doNotHaveNameMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> Service/Controller 的数据访问纪律；测试类持 {@code JdbcTemplate} 作为
 * 预留批领域类（如 FunctionCacheManager）的 test double 属合法测试手段。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.ontology")
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
    static final ArchRule service_controller_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.engine.ontology..service..",
                                        "com.chinacreator.gzcm.engine.ontology..controller..")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
