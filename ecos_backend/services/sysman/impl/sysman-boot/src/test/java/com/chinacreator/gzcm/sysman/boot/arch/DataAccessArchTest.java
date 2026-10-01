package com.chinacreator.gzcm.sysman.boot.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DataAccessArchTest — sysman-boot 数据访问收口门禁（架构铁律 §2.5 / IR06 · 防回潮）。
 *
 * <p>sysman-boot 的 {@code AuthService} 门面<b>禁</b>直持
 * {@code org.springframework.jdbc.core.JdbcTemplate}。数据访问一律下沉到
 * {@code com.chinacreator.gzcm.sysman.boot.dao} 下
 * {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}（SQL 原样搬运，
 * 门面 {@code AuthService} 保留 public API 原样委托）。</p>
 *
 * <ol>
 *   <li>规则 1（按类名）：名为 {@code *Service}/{@code *ServiceImpl}/{@code *Repository}/
 *       {@code *Task}/{@code *Consumer}/{@code *Store}/{@code *Filter}/{@code *Interceptor}/
 *       {@code *Aspect}/{@code *Aggregator} 的类不得依赖 JdbcTemplate（后缀锚定）。</li>
 *   <li>规则 2（按包）：曾持有 JdbcTemplate 的<b>非 DAO</b> 包 {@code ...sysman.boot.service}
 *       不得依赖 JdbcTemplate。合规落点 {@code ...sysman.boot.dao}（extends
 *       JdbcTemplateDaoSupport）<b>不在</b>上述禁入包内，故不受约束。</li>
 * </ol>
 * 两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类。
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.sysman.boot")
class DataAccessArchTest {

    @ArchTest
    static final ArchRule name_based_data_access_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .haveNameMatching(".*Service")
                    .or().haveNameMatching(".*ServiceImpl")
                    .or().haveNameMatching(".*Repository")
                    .or().haveNameMatching(".*Task")
                    .or().haveNameMatching(".*Consumer")
                    .or().haveNameMatching(".*Store")
                    .or().haveNameMatching(".*Filter")
                    .or().haveNameMatching(".*Interceptor")
                    .or().haveNameMatching(".*Aspect")
                    .or().haveNameMatching(".*Aggregator")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule boot_service_package_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.sysman.boot.service..")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
