package com.chinacreator.gzcm.services.datanet.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2-B5 datanet 数据访问收口守门（架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>数据服务域（{@code com.chinacreator.gzcm.services.datanet}）的生产类<b>禁</b>
 * 直持 {@code org.springframework.jdbc.core.JdbcTemplate}。数据访问一律下沉到
 * {@code com.chinacreator.gzcm.services.datanet.dao} 下的
 * {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}
 * （SQL / 参数原样搬运，门面 {@code DqDashboardService}/{@code GitQueryService} 保留 public API
 * 原样委托；{@code DataLakeExportService} 直接拆除死代码 {@code pgJdbc}）。</p>
 *
 * <p>落点 / 范围说明（与 B1 data-engine ground-truth、agent-service、ontology-engine、buszhi
 * 同款）：
 * <ol>
 *   <li>规则 1（按类名）：名为 {@code *Service}/{@code *ServiceImpl}/{@code *Repository}/
 *       {@code *Task}/{@code *Consumer}/{@code *Store}/{@code *Filter}/{@code *Interceptor}/
 *       {@code *Aspect}/{@code *Aggregator} 的类不得依赖 JdbcTemplate。类名断言<b>后缀锚定</b>
 *       （{@code .*Service} 等），以免误判 {@code GitQueryDao}/{@code DqDashboardDao} 等
 *       合规数据落点。</li>
 *   <li>规则 2（按包）：{@code ...datanet.service} 与 {@code ...datanet.controller} 的类不得依赖
 *       JdbcTemplate。规则 2 刻意只覆盖部署于域内生产持 JdbcTemplate 的<b>非 DAO</b> 站点
 *       （Service / Controller）；合规落点 {@code ...datanet.dao}（extends JdbcTemplateDaoSupport）
 *       <b>不在</b>禁入包内，不受约束。「{@code ...datanet..}」整包粗化会误伤合规 DAO，故不应写入。</li>
 * </ol>
 * 两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> 层的数据访问纪律；测试类持 {@code JdbcTemplate} mock 属合法测试手段。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.services.datanet")
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
    static final ArchRule datanet_domain_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.services.datanet.service",
                                        "com.chinacreator.gzcm.services.datanet.controller")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
