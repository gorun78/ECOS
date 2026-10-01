package com.chinacreator.gzcm.engine.cognitive2.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * cognitive-engine 数据访问收口守门（R2-B2b / 架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>架构铁律 §2.5 #1 / 后端开发规范 §三：Service / Controller 层<b>禁</b>
 * {@code private final JdbcTemplate} 直持、禁在方法体内内联 raw-SQL。数据访问一律
 * 下沉到 runtime 的 data-access 收口点——{@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}
 * （本仓 {@code com.chinacreator.gzcm.engine.cognitive2.dao}）。</p>
 *
 * <p>本测试在 {@code mvn install}（含 test-compile + surefire）时立即失败任何：
 * <ol>
 *   <li>名为 {@code *Service}/{@code *ServiceImpl}/{@code *Controller} 的类依赖
 *       {@code org.springframework.jdbc.core.JdbcTemplate}；或</li>
 *   <li>位于 {@code ..service..} / {@code ..controller..} 包的类依赖
 *       {@code org.springframework.jdbc.core.JdbcTemplate}。</li>
 * </ol>
 * 规则的合规落点是 DAO（包名 {@code ..dao..}，不含 {@code ..service..}/{@code ..controller..}，
 * 且类名不以 {@code *Service}/{@code *Controller} 结尾），故不受本规则约束。</p>
 *
 * <p>两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> Service/Controller 的数据访问纪律；测试类持 {@code JdbcTemplate} 作为
 * DAO 层验证的 test double 属合法测试手段，不构成本规则所防的回潮口径。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.cognitive2")
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
                    .resideInAnyPackage("com.chinacreator.gzcm.engine.cognitive2..service..",
                                        "com.chinacreator.gzcm.engine.cognitive2..controller..")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
