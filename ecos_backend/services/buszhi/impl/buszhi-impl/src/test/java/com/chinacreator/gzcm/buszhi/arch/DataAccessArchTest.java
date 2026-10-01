package com.chinacreator.gzcm.buszhi.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2-B5 buszhi 数据访问收口守门（架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>工作流域（{@code com.chinacreator.gzcm.buszhi.workflow} 本体域）的生产类<b>禁</b>
 * 直持 {@code org.springframework.jdbc.core.JdbcTemplate}。数据访问一律下沉到
 * {@code com.chinacreator.gzcm.buszhi.workflow.dao} 下的
 * {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}（SQL 原样搬运，
 * 门面 {@code Workflow*Repository}/{@code TimelineRepository} 保留 public API 原样委托）。</p>
 *
 * <p>落点 / 范围说明（与 B1 data-engine ground-truth、agent-service、ontology-engine 同款）：
 * <ol>
 *   <li>规则 1（按类名）：名为 {@code *Service}/{@code *ServiceImpl}/{@code *Repository}/
 *       {@code *Task}/{@code *Consumer}/{@code *Store}/{@code *Filter}/{@code *Interceptor}/
 *       {@code *Aspect}/{@code *Aggregator} 的类不得依赖 JdbcTemplate。类名断言<b>后缀锚定</b>
 *       （{@code .*Service} 等，而非 {@code *Service*}），以免误判 {@code WorkflowTaskDao}/
 *       {@code WorkflowTaskEntity} 等以中缀含 Task 的合规落点。</li>
 *   <li>规则 2（按包）：工作流<b>本体包</b> {@code ...buszhi.workflow} 与
 *       {@code ...buszhi.workflow.controller} 的类不得依赖 JdbcTemplate。规则 2 刻意只覆盖
 *       持有过 JdbcTemplate 的<b>非 DAO</b> 站点（门面 / 领域服务 / Controller）；合规落点
 *       {@code ...buszhi.workflow.dao}（extends JdbcTemplateDaoSupport，构造器需 JdbcTemplate）
 *       <b>不在</b>上述禁入包内，故不受约束——这与「数据访问收口到 runtime 底座 DAO」的口径一致，
 *       也避免「整包 *workflow.* 粗化」误伤合规门面与 DAO。</li>
 * </ol>
 * 两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> 领域层的数据访问纪律；测试类持 {@code JdbcTemplate} mock（如
 * {@code WorkflowSoftDeleteTest} 对 {@code WorkflowDao} 的软删断言）属合法测试手段。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.buszhi")
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
                    .resideInAnyPackage("com.chinacreator.gzcm.buszhi.workflow",
                                        "com.chinacreator.gzcm.buszhi.workflow.controller")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
