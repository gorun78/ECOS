package com.chinacreator.gzcm.engine.ai.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ai-engine 数据访问收口守门（R2-B4-ai / 架构铁律 §2.5 #1 · IR06 · 防回潮）。
 *
 * <p>架构铁律 §2.5 #1 / IR06 + 后端开发规范 §三：Service / Controller / Repository /
 * Config 层<b>禁</b> {@code JdbcTemplate} 直持。数据访问一律下沉到
 * {@code com.chinacreator.gzcm.engine.ai.dao} 下的
 * {@code XxxDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}
 * （B1 data-engine / B3-a ontology-engine ground-truth 同款 pattern）。</p>
 *
 * <p>本测试在 {@code mvn install}（含 test-compile + surefire）时立即失败任何：
 * <ol>
 *   <li>rule1 — 名为 {@code *Service}/{@code *ServiceImpl}/{@code *Controller}/{@code *Repository}
 *       的<b>生产</b>类依赖 {@code org.springframework.jdbc.core.JdbcTemplate}（按类名匹配，
 *       不受包名偏移影响）。</li>
 *   <li>rule2 — 位于以下任一业务/配置/控制/仓储/辅助包中的<b>生产</b>类依赖
 *       {@code org.springframework.jdbc.core.JdbcTemplate}：
 *       {@code ai..service..}、{@code ai..controller..}、{@code ai..repository..}、
 *       {@code ai..config..}、{@code ai..oag..}、{@code ai..audit..}、{@code ai..security..}、
 *       {@code ai..nlq..}、{@code ai..agent..} 。</li>
 * </ol>
 *
 * <p>两条规则均以 {@code haveNameNotMatching(".*Test")} 排除测试类：规则约束的是
 * <b>生产</b> Service/Controller/Repository/Config 层的数据访问纪律；测试类持
 * {@code JdbcTemplate} 或 DAO 作为 test double 属合法测试手段。</p>
 *
 * <p><b>红线</b>：未来如需在 {@code ai..} 下新增任何持有 JdbcTemplate 的包，必须在
 * {@link #BANNED_PACKAGES} 中<b>追加</b>；缩小本列表需架构官签发。</p>
 */
@AnalyzeClasses(packages = "com.chinacreator.gzcm.engine.ai")
class DataAccessArchTest {

    /** 禁止持有 JdbcTemplate 的包（rule2 的范围清单，只增不缩）。 */
    private static final String[] BANNED_PACKAGES = {
            "com.chinacreator.gzcm.engine.ai..service..",
            "com.chinacreator.gzcm.engine.ai..controller..",
            "com.chinacreator.gzcm.engine.ai..repository..",
            "com.chinacreator.gzcm.engine.ai..config..",
            "com.chinacreator.gzcm.engine.ai..oag..",
            "com.chinacreator.gzcm.engine.ai..audit..",
            "com.chinacreator.gzcm.engine.ai..security..",
            "com.chinacreator.gzcm.engine.ai..nlq..",
            "com.chinacreator.gzcm.engine.ai..agent..",
            // F06-11：受控工具声明/注册层同样禁自持 JdbcTemplate（表读只经 ..dao.. 门面）
            "com.chinacreator.gzcm.engine.ai..tool..",
    };

    @ArchTest
    static final ArchRule rule1_service_controller_repository_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .haveNameMatching(".*Service")
                    .or().haveNameMatching(".*ServiceImpl")
                    .or().haveNameMatching(".*Controller")
                    .or().haveNameMatching(".*Repository")
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule rule2_business_packages_must_not_depend_on_jdbc_template =
            noClasses().that()
                    .resideInAnyPackage(BANNED_PACKAGES)
                    .and().haveNameNotMatching(".*Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}
