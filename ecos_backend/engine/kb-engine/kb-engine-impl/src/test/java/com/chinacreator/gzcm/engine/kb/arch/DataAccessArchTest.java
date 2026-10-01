package com.chinacreator.gzcm.engine.kb.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2 / B2-kb 数据访问收口守门（架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>kb-engine 业务门面类（Service / Store / Task / Consumer / Aspect / Filter / Interceptor /
 * Aggregator / Repository）<b>禁</b>直持 {@code org.springframework.jdbc.core.JdbcTemplate}。
 * 数据访问一律下沉到 {@code com.chinacreator.gzcm.engine.kb.dao} 下的
 * {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}
 * （SQL 原样搬运，门面保留 public API 原样委托）。</p>
 *
 * <p>范围说明：
 * <ol>
 *   <li>规则一（命名后缀锚定）：任意后缀为业务门面命名且非 {@code *Test} 的类禁依赖 JdbcTemplate；</li>
 *   <li>规则二（原持有点包精确枚举）：kb 业务包（cognition / event / repo / service 及其子包 /
 *       task）禁依赖 JdbcTemplate。<b>刻意排除</b> {@code ...kb.dao} — 合规 DAO 继承
 *       {@code JdbcTemplateDaoSupport}（其字段/构造引用 JdbcTemplate），属白名单落点。</li>
 * </ol>
 * </p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.kb")
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
    static final ArchRule workflow_domain_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage(
                            "com.chinacreator.gzcm.engine.kb.cognition",
                            "com.chinacreator.gzcm.engine.kb.event",
                            "com.chinacreator.gzcm.engine.kb.repo",
                            "com.chinacreator.gzcm.engine.kb.service",
                            "com.chinacreator.gzcm.engine.kb.service.assumption",
                            "com.chinacreator.gzcm.engine.kb.service.kb",
                            "com.chinacreator.gzcm.engine.kb.service.nav",
                            "com.chinacreator.gzcm.engine.kb.service.profile",
                            "com.chinacreator.gzcm.engine.kb.task")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
