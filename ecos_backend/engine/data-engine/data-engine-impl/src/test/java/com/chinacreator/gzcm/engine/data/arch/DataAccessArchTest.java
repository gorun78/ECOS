package com.chinacreator.gzcm.engine.data;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * data-engine 数据访问收口守门（R2-B1 / B4a · PMO-74 v2 补记 · 指令 §标准动作 #6·防回潮）。
 *
 * <p>架构铁律 §2.5 #1 / IR06 + 后端开发规范 §三：Service / Controller /
 * Repository / Task（调度）层<b>禁</b> {@code private final JdbcTemplate} 直持、
 * 禁在方法体内内联 raw-SQL。数据访问一律下沉到 runtime 的 data-access
 * 收口点——MyBatis {@code @Mapper}（本仓 {@code com.chinacreator.gzcm.**.dao}）
 * 或 {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}。</p>
 *
 * <p>本测试在 {@code mvn install}（含 test-compile + surefire）时立即失败任何：
 * <ol>
 *   <li>名为 {@code *Service}/{@code *ServiceImpl}/{@code *Controller}/
 *       {@code *Repository}/{@code *Task} 的类依赖
 *       {@code org.springframework.jdbc.core.JdbcTemplate}；或</li>
 *   <li>位于 {@code ..service..} / {@code ..controller..} / {@code ..scheduler..} /
 *       {@code ..pipeline..} / {@code ..quality.repository..} / {@code ..metadata..}
 *       包的类依赖 {@code org.springframework.jdbc.core.JdbcTemplate}。</li>
 * </ol>
 * 规则的合规落点是 DAO / Mapper（包名 {@code ..dao..}，不在上述任何禁入范围），
 * 故不受本规则约束。注：规则 2 覆盖包均为 **生产 JdbcTemplate 持有过** 的历史站点，
 * 逐包列入（B1 已收口 + B4a 残余 5 生产文件），未做整包 *.data.* 粗化以免误伤
 * 合规门面（如 DataLayerService 已走 DataLayerDao 不受限）。</p>
 *
 * <p>注：{@code *ServiceImpl}（如 {@code PipelineTaskServiceImpl}）一并纳入 name 断言，
 * 因本仓 Service 多采用 {@code *ServiceImpl} 命名；仅凭 {@code *Service} 前缀 glob 会漏判，
 * 留下回潮空间——这与指令 "Service/Controller 一律禁" 的口径一致。
 * B4a 进一步把 {@code *Repository}（如 DqRepository 旧版 extends JdbcTemplateDaoSupport）
 * 与 {@code *Task}（如 DqScheduledTask / SchemaChangeDetector 同包 Task 注册形态）
 * 纳入 name 断言，杜绝"改个类名即绕门"。</p>
 *
 * <p>两条规则均以 {@code doNotHaveNameMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> Service/Controller/Repository/Task 的数据访问纪律；测试类持
 * {@code JdbcTemplate} 作为 DAO 层验证的 test double 属合法测试手段，不构成本规则
 * 所防的回潮口径。测试侧对已收口 DAO 的 mock 由所在业务单测用例本身保障，不在架构门禁范围。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.data")
class DataAccessArchTest {

    @ArchTest
    static final ArchRule service_controller_repository_task_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .haveNameMatching(".*Service")
                    .or().haveNameMatching(".*ServiceImpl")
                    .or().haveNameMatching(".*Controller")
                    .or().haveNameMatching(".*Repository")
                    .or().haveNameMatching(".*Task")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule data_access_guarded_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage("com.chinacreator.gzcm.engine.data..service..",
                                        "com.chinacreator.gzcm.engine.data..controller..",
                                        "com.chinacreator.gzcm.engine.data..scheduler..",
                                        "com.chinacreator.gzcm.engine.data..pipeline..",
                                        "com.chinacreator.gzcm.engine.data..metadata..",
                                        "com.chinacreator.gzcm.engine.data..quality.repository..")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
