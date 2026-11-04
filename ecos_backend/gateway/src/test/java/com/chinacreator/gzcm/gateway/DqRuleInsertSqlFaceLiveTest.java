package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.quality.dao.DqRuleLifecycleDao;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 质量规则创建 SQL 的<b>活体</b>腿（#147AU）。
 *
 * <p><b>它证明什么</b>：{@code DqRuleLifecycleDao#insertRule} 的占位符数量与绑定值数量一致，
 * 在真 PG 上能真的插进 {@code ecos_data.dq_rule}（status 落 DRAFT）、查得回、再被删干净。</p>
 *
 * <p><b>为什么必须真库</b>：这条 INSERT 曾有 <b>20 个占位符对 19 个参数</b>的错位——单元测试里
 * {@code jdbcTemplate} 是 mock，参数个数不进 SQL 解析，永远绿；只有真驱动才会报
 * 「未设定参数值 20 的内容」。同批还有第二处：{@code source_type} 是 NOT NULL 列（DDL 默认
 * 'MANUAL'），而服务层会把 {@code null} 原样绑定成<b>显式 NULL</b>（显式 NULL 绕开列默认值），
 * 该半在 {@code DqRuleLifecycleTest#createDraft_defaultsSourceTypeToManual} 里钉。本腿给的是
 * 与修复后服务层相同的取值（sourceType=MANUAL、parameters={@code {}}），只钉 SQL 面本身。</p>
 *
 * <p><b>为什么带标志</b>：与 {@code CacheInvalidationOutboxLiveTest} 同口径——没有 PG 的机器默认跳过；
 * 跳过不等于已验。库口令一律由运行侧供给（{@code -Decos.live.db.password} 或环境变量
 * {@code ECOS_DB_PASSWORD}），仓库不留默认值。测试行用随机 id，跑完<b>物理删除</b>（不是逻辑删除），
 * 不会给开发库留探针。</p>
 *
 * @author ECOS gateway #147AU
 */
@EnabledIfSystemProperty(named = "ecos.verify.live", matches = "true")
class DqRuleInsertSqlFaceLiveTest {

    @Test
    @DisplayName("insertRule 占位符与绑定值等量：真 PG 插入一行、查得到、删除后归零")
    void insertRuleBindsEveryPlaceholder() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName("127.0.0.1");
        ds.setPortNumber(5432);
        ds.setDatabaseName("sys_man");
        ds.setUser(System.getProperty("ecos.live.db.user", "postgres"));
        String dbPass = System.getProperty("ecos.live.db.password", System.getenv("ECOS_DB_PASSWORD"));
        assertNotNull(dbPass, "活体腿需显式给数据库口令：-Decos.live.db.password=… 或环境变量 ECOS_DB_PASSWORD");
        ds.setPassword(dbPass);
        // 与生产连接串同口径（gateway application.yml 的 ...&stringtype=unspecified）：
        // 本 INSERT 把 parameters 以 String 绑到 jsonb 列，缺这一项会被 PG 拒绝
        // （column "parameters" is of type jsonb but expression is of type character varying）。
        ds.setStringType("unspecified");

        JdbcTemplate jdbc = new JdbcTemplate(ds);

        DqRuleLifecycleDao dao = new DqRuleLifecycleDao();
        Field field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(dao, jdbc);

        String ruleId = "probe-147au-" + UUID.randomUUID();
        try {
            dao.insertRule(ruleId, "probe-147au", "PROBE_147AU", "VALIDITY", "ecos_dw",
                    "COMPLETENESS", "LOW", "TABLE", null, "probe_table", null, null,
                    "{}", null, "MANUAL", null, "147AU 活体探针（跑完即物理删除）", "junit-147au");

            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM ecos_data.dq_rule WHERE id = ?", Integer.class, ruleId);
            assertEquals(1, rows, "插入后应能在 ecos_data.dq_rule 查到该行（占位符错位时会在这里之前就抛 PG 异常）");

            String status = jdbc.queryForObject(
                    "SELECT status FROM ecos_data.dq_rule WHERE id = ?", String.class, ruleId);
            assertEquals("DRAFT", status, "创建落库状态固定 DRAFT");
            Integer version = jdbc.queryForObject(
                    "SELECT version FROM ecos_data.dq_rule WHERE id = ?", Integer.class, ruleId);
            assertEquals(1, version, "创建落库版本固定 1");
        } finally {
            jdbc.update("DELETE FROM ecos_data.dq_rule WHERE id = ?", ruleId);
        }

        Integer residue = jdbc.queryForObject(
                "SELECT count(*) FROM ecos_data.dq_rule WHERE id = ?", Integer.class, ruleId);
        assertEquals(0, residue, "探针行必须物理删除，开发库不留残留");
    }
}
