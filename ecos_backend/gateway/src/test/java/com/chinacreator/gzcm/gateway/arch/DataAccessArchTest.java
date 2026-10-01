package com.chinacreator.gzcm.gateway.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * gateway 数据访问收口守门（R2-B4b / 架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>规则约束的是 <b>生产</b> 层（service / telemetry / filter / twin / controller）
 * 的数据访问纪律：<b>禁</b>直持/内联 {@code org.springframework.jdbc.core.JdbcTemplate}。
 * 数据访问一律下沉到 gateway 的 data-access 收口点 ——
 * {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}
 * （本仓 {@code com.chinacreator.gzcm.gateway.dao}，9 个 DAO 全部走继承的
 * {@code protected JdbcTemplate jdbcTemplate}，不 new、不 private 直持）。</p>
 *
 * <p>落点 / 范围说明（与 buszhi / data-engine ground-truth 同款）：
 * <ol>
 *   <li>规则 1（按类名，后缀锚定 {@code .*Service} 等）：名为 {@code *Service}/
 *       {@code *ServiceImpl}/{@code *Repository}/{@code *Task}/{@code *Consumer}/
 *       {@code *Store}/{@code *Filter}/{@code *Interceptor}/{@code *Aspect}/
 *       {@code *Aggregator} 的类不得依赖 JdbcTemplate。后缀锚定避免误判
 *       {@code QuotaDao}/{@code DeviceTelemetryDao} 等以中缀含 Task/Service 的
 *       合规落点。</li>
 *   <li>规则 2（按包，<b>精确包名</b>）：仅覆盖持有过 JdbcTemplate 的<b>非 DAO</b>
 *       站点包 {@code ...gateway.service} / {@code ...gateway.telemetry} /
 *       {@code ...gateway.filter} / {@code ...gateway.twin} / {@code ...gateway.controller}。
 *       刻意<b>不使用</b> {@code ..} 子树通配，也<b>不得</b>列入 {@code ...gateway.dao}：
 *       合规 DAO 继承 {@code JdbcTemplateDaoSupport}（其构造/字段引用
 *       {@code JdbcTemplate} Bean），子树规则会误伤 DAO 本身、永远不绿。</li>
 * </ol>
 * 两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类：测试类持
 * {@code JdbcTemplate} mock / test-double 属合法测试手段，不构成本规则所防的回潮口径。</p>
 *
 * <p>{@code gateway.config.JdbcConfig}（JdbcTemplate Bean 工厂）与
 * {@code gateway.jdbc.TenantAwareJdbcTemplate}（租户感知包装）属受批准基础设施，
 * 落在 {@code ..config..} / {@code ..jdbc..} 包，天然不在两条规则命中范围内。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.gateway")
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
    static final ArchRule gateway_non_dao_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.gateway.service",
                                        "com.chinacreator.gzcm.gateway.telemetry",
                                        "com.chinacreator.gzcm.gateway.filter",
                                        "com.chinacreator.gzcm.gateway.twin",
                                        "com.chinacreator.gzcm.gateway.controller")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
