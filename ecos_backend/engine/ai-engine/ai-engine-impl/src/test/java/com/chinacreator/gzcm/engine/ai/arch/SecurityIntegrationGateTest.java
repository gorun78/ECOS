package com.chinacreator.gzcm.engine.ai.arch;

import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全集成强制卡 —— SQL 执行直连面守门（详细设计-06 F06-17 要点 4-③ · §2.4-8 安全集成强制卡 · X-28）。
 *
 * <p>设计原文：{@code engine.ai..} 中除咽喉/DAO  sanctioned 路径外，任何类不得直接执行 SQL
 * （LLM 文本 → SQL 执行是 X-28 红线形态）。本规则是 {@link DataAccessArchTest}（仅覆盖
 * service/controller 等包 + 类名匹配）的<b>全引擎更严格版本</b>：按"调用 SQL 执行方法"判定，
 * 唯一 sanctioned 位置 = {@code ..engine.ai.dao..} 包与咽喉类
 * {@link ToolExecutorService}（其 executeSql 只经 {@code toolDao.queryForList(..)} 走 DAO，
 * 不持 JdbcTemplate —— 若本规则失败即为真实违规，报告 file:line 后修生产代码，禁放宽规则）。</p>
 *
 * <p>覆盖三类直连形态：</p>
 * <ol>
 *   <li>{@code org.springframework.jdbc.core.JdbcTemplate} 的
 *       {@code query*} / {@code update*} / {@code execute*} / {@code batchUpdate*} 调用；</li>
 *   <li>{@code java.sql.Statement}（含 PreparedStatement/CallableStatement 族）的
 *       {@code execute*} 调用；</li>
 *   <li>{@code java.sql.Connection#prepareStatement} 调用。</li>
 * </ol>
 *
 * <p><b>正对照（防空转）</b>：{@link #toolDaoCallsJdbcTemplateQueryForList()} 断言
 * {@code dao.ToolDao} 确实调用 {@code JdbcTemplate#queryForList}；失败 = DAO sanctioned
 * 形态漂移，本规则可能已判不到任何东西。</p>
 *
 * <p>分析域 = {@code com.chinacreator.gzcm.engine.ai} 的<b>生产</b>类
 * （{@code ImportOption.DoNotIncludeTests}；测试类持 JdbcTemplate/DAO 作 test double
 * 属合法手段，与 {@link DataAccessArchTest} 排除口径同源）。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.ai",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SecurityIntegrationGateTest {

    /** 咽喉类 FQN（豁免名单与 ToolChokepointArchTest 同步，只增不缩）。 */
    private static final String CHOKEPOINT_FQN = ToolExecutorService.class.getName();

    /** sanctioned 数据访问包（DAO 层，对齐 DataAccessArchTest / JdbcTemplateDaoSupport 模式）。 */
    private static final String DAO_PACKAGE = "com.chinacreator.gzcm.engine.ai..dao..";

    /** JdbcTemplate 的 SQL 执行方法调用。 */
    private static final DescribedPredicate<JavaCall<?>> CALLS_JDBC_TEMPLATE_EXECUTION =
            target(owner(simpleName("JdbcTemplate")))
                    .and(target(nameMatching("(query|update|execute|batchUpdate).*")));

    /** java.sql.Statement 族（Statement/PreparedStatement/CallableStatement）的 execute* 调用。 */
    private static final DescribedPredicate<JavaCall<?>> CALLS_SQL_STATEMENT_EXECUTE =
            target(owner(nameMatching("java\\.sql\\..*Statement")))
                    .and(target(nameMatching("execute.*")));

    /** java.sql.Connection#prepareStatement 调用。 */
    private static final DescribedPredicate<JavaCall<?>> CALLS_CONNECTION_PREPARE_STATEMENT =
            target(owner(name("java.sql.Connection")))
                    .and(target(name("prepareStatement")));

    @ArchTest
    static final ArchRule noDirectSqlExecutionOutsideChokepoint =
            noClasses().that()
                    .resideOutsideOfPackage(DAO_PACKAGE)
                    .and().doNotHaveFullyQualifiedName(CHOKEPOINT_FQN)
                    .should().callMethodWhere(CALLS_JDBC_TEMPLATE_EXECUTION
                            .or(CALLS_SQL_STATEMENT_EXECUTE)
                            .or(CALLS_CONNECTION_PREPARE_STATEMENT))
                    .as("F06-17/§2.4-8 安全集成强制卡：..engine.ai.. 中除 " + DAO_PACKAGE
                            + " 与咽喉 " + CHOKEPOINT_FQN + " 外，任何类不得直接执行 SQL"
                            + "（JdbcTemplate query*/update*/execute*/batchUpdate*、"
                            + "java.sql.Statement#execute*、java.sql.Connection#prepareStatement）")
                    .because("SQL 执行必须经 DAO 收口 + 咽喉裁决（RLS/列过滤/OPA 只在 security 侧生效），"
                            + "否则即 X-28 的 LLM 文本→SQL 直执行红线");

    /**
     * 正对照：{@code com.chinacreator.gzcm.engine.ai.dao.ToolDao} 必须调用
     * {@code JdbcTemplate#queryForList(..)}（现网 file:line = ToolDao.java:44/50/52）。
     * 失败 = DAO sanctioned 调用形态漂移（如换 JdbcTemplateDaoSupport 模板方法），
     * 需连同本规则一起修订，禁止只删正对照保规则"通过"。
     */
    @Test
    void toolDaoCallsJdbcTemplateQueryForList() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.chinacreator.gzcm.engine.ai");
        JavaClass toolDao = classes.get("com.chinacreator.gzcm.engine.ai.dao.ToolDao");
        DescribedPredicate<JavaCall<?>> callsQueryForList =
                target(owner(simpleName("JdbcTemplate")))
                        .and(target(name("queryForList")));
        boolean hit = toolDao.getMethodCallsFromSelf().stream().anyMatch(callsQueryForList::test);
        assertTrue(hit,
                "正对照失败：dao.ToolDao 不再调用 JdbcTemplate#queryForList —— DAO sanctioned 形态已漂移，"
                        + "noDirectSqlExecutionOutsideChokepoint 规则可能空转，请连同本测试一起修订");
    }

    /**
     * 正对照：ToolExecutorService 自身在豁免名单内，但必须证明其"通过"不是因为
     * 根本不碰 SQL —— 其 executeSql 经 {@code toolDao.queryForList(..)}（owner = ToolDao，
     * 不触发上方禁令）。此处仅断言咽喉类确实存在对 ToolDao 的调用，防豁免面被误用。
     */
    @Test
    void chokepointExecutesSqlOnlyThroughToolDao() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.chinacreator.gzcm.engine.ai");
        JavaClass chokepoint = classes.get(CHOKEPOINT_FQN);
        DescribedPredicate<JavaCall<?>> callsToolDaoQuery =
                target(owner(name("com.chinacreator.gzcm.engine.ai.dao.ToolDao")))
                        .and(target(name("queryForList")));
        boolean hit = chokepoint.getMethodCallsFromSelf().stream().anyMatch(callsToolDaoQuery::test);
        assertTrue(hit,
                "ToolExecutorService.executeSql 应经 toolDao.queryForList(..) 走 DAO 收口"
                        + "（现网 ToolExecutorService.java:632）—— 形态漂移需连同 §2.4-8 卡口一起复核");
    }
}
