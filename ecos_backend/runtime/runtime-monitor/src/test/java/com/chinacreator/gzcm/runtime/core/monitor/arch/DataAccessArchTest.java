package com.chinacreator.gzcm.runtime.core.monitor;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * runtime-monitor 数据访问收口守门（R2-B4c · 架构铁律 §2.5 #1 / IR06 · 防回潮）。
 *
 * <p>架构铁律 §2.5 #1 / IR06：Service / Repository 层<b>禁</b>直接持有
 * {@code org.springframework.jdbc.core.JdbcTemplate}、禁内联 raw-SQL；
 * 监控/告警数据访问一律下沉 {@code ..dao..}——
 * {@code XxxDao extends
 * com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}。
 * B4c 已把告警落库由 {@code WarnLogServiceImpl}（..monitor.service.impl..，原私有
 * {@code JdbcTemplate} 字段）下沉到
 * {@code com.chinacreator.gzcm.runtime.core.monitor.server.warn.dao.WarnLogDao}，
 * {@code WarnLogServiceImpl} 退化为纯内存态门面 + 经 {@code WarnLogDao} 落库。</p>
 *
 * <p>两条规则：规则 1 按类名（{@code *Service}/{@code *ServiceImpl}/{@code *Repository}/
 * {@code *Task}/{@code *Consumer}）兜住未来改名的直持回潮；规则 2 按<b>历史持有过
 * JdbcTemplate 的非 dao 包</b>逐包守门（..monitor.service.impl..；绝不入 ..dao.. 或
 * ..database.support..，后者正是合法收口点）。两条均以
 * {@code haveNameNotMatching(".*Test")} 排除测试类（测试侧 mock JdbcTemplate 属合法测试手段）。</p>
 *
 * <p>注：本模块 {@code AnalyzeClasses} 锁定根包 {@code com.chinacreator.gzcm.runtime.core.monitor}
 * （runtime-monitor 实际根包），DAO 收口点 {@code ..server.warn.dao..} 在该根下自然被规则
 * 白名单…… 但规则 2 仅约束 ..service.impl..，故 DAO 合法持有 JdbcTemplate 不被误判。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.runtime.core.monitor")
class DataAccessArchTest {

    @ArchTest
    static final ArchRule service_repository_task_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .haveNameMatching(".*Service")
                    .or().haveNameMatching(".*ServiceImpl")
                    .or().haveNameMatching(".*Repository")
                    .or().haveNameMatching(".*Task")
                    .or().haveNameMatching(".*Consumer")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule data_access_guarded_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage(
                            "com.chinacreator.gzcm.runtime.core.monitor.service.impl")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
