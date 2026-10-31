package com.chinacreator.gzcm.engine.ontology.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;

/**
 * #147B 规则数据面契约门禁 —— {@code OntologyRuleDao} 对 live 表
 * {@code ecos_ontology.ecos_ontology_rule} 的读写面（42703 取证订正）。
 *
 * <p>判据打在<b>捕获的 SQL 实参</b>上，不打源文本（类 Javadoc 有意提及
 * created_at/updated_at 订正缘由——源文本断言会假绿）。三腿：</p>
 * <ul>
 *   <li>读腿：{@code findByEntity/findAll/findById} 一律 {@code AND is_deleted = 0}
 *       （逻辑删读面收口），排序用 {@code create_time}；</li>
 *   <li>写腿：INSERT/UPDATE 时间戳列 = {@code create_time/update_time}（live 实列），
 *       禁 {@code created_at/updated_at}；双维+就绪件套
 *       （rule_carrier/rule_layer/scope/version_no/git_ref/status/domain）承载；</li>
 *   <li>RowMapper：新列与 create_time/update_time 可读回模型。</li>
 * </ul>
 */
class OntologyRuleContractDaoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private OntologyRuleDao dao;

    @BeforeEach
    void injectMockJdbcTemplate() throws Exception {
        dao = new OntologyRuleDao();
        Field f = dao.getClass().getSuperclass().getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(dao, jdbc);
    }

    /**
     * 捕获 query 腿 SQL。varargs 用 {@code ArgumentMatchers.<Object>any()} 展开。
     */
    private String captureQuerySql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), ArgumentMatchers.any(RowMapper.class),
            ArgumentMatchers.<Object>any());
        return sql.getValue();
    }

    /** findAll 走 query(String, RowMapper) 两参重载（零 varargs 实参）。 */
    private String captureQuerySqlNoArgs() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), ArgumentMatchers.any(RowMapper.class));
        return sql.getValue();
    }

    // ═══════════════ 读腿：is_deleted 过滤 ═══════════════

    @Test
    @DisplayName("findByEntity 读腿带 AND is_deleted = 0 且排序用 create_time")
    void findByEntityFiltersDeleted() {
        dao.findByEntity("ent1");
        String sql = captureQuerySql();
        assertTrue(sql.contains("is_deleted = 0"), sql);
        assertTrue(sql.contains("create_time DESC"), sql);
        assertFalse(sql.contains("created_at"), "live 无 created_at 列：" + sql);
    }

    @Test
    @DisplayName("findAll 读腿带 AND is_deleted = 0")
    void findAllFiltersDeleted() {
        dao.findAll();
        assertTrue(captureQuerySqlNoArgs().contains("is_deleted = 0"));
    }

    @Test
    @DisplayName("findById 读腿带 id = ? AND is_deleted = 0（已删行不可见）")
    void findByIdFiltersDeleted() {
        dao.findById("rule1001");
        String sql = captureQuerySql();
        assertTrue(sql.contains("id = ?"), sql);
        assertTrue(sql.contains("is_deleted = 0"), sql);
    }

    // ═══════════════ 写腿：INSERT 列面 ═══════════════

    @Test
    @DisplayName("INSERT 用 create_time/update_time 并承载双维+就绪件套+status/domain")
    void insertCarriesDualDimensionAndLiveTimestamps() {
        OntologyRule r = new OntologyRule();
        r.setId("rule1001");
        r.setEntityId("ent1");
        r.setCode("R-001");
        r.setName("n");
        r.setRuleType("VALIDATION");
        r.setExpression("age > 0");
        r.setAction("");
        r.setPriority(100);
        r.setEnabled(1);
        r.setDescription("");
        r.setRuleCarrier("ONTOLOGY_ELEMENT");
        r.setRuleLayer("VALIDATION");
        r.setScope("entity");
        r.setVersionNo("2");
        r.setGitRef("abc1234");
        r.setStatus("PUBLISHED");
        r.setDomain("default");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        dao.insert(r);
        verify(jdbc).update(sql.capture(), args.capture());

        String statement = sql.getValue();
        assertTrue(statement.contains("create_time"), statement);
        assertTrue(statement.contains("update_time"), statement);
        assertFalse(statement.contains("created_at"), "42703 断裂列禁回潮：" + statement);
        assertFalse(statement.contains("updated_at"), "42703 断裂列禁回潮：" + statement);
        for (String col : new String[]{"rule_carrier", "rule_layer", "scope", "version_no", "git_ref", "status", "domain"}) {
            assertTrue(statement.contains(col), "INSERT 缺列 " + col + "：" + statement);
        }
        Object[] params = args.getValue();
        assertEquals(17, params.length, "17 个占位参（NOW() 直书不入参）");
        assertEquals("ONTOLOGY_ELEMENT", params[10]);
        assertEquals("VALIDATION", params[11]);
        assertEquals("entity", params[12]);
        assertEquals("2", params[13]);
        assertEquals("abc1234", params[14]);
        assertEquals("PUBLISHED", params[15]);
        assertEquals("default", params[16]);
    }

    // ═══════════════ 写腿：UPDATE 列面（COALESCE null=不更新） ═══════════════

    @Test
    @DisplayName("UPDATE 承载双维+就绪件套+status 的 COALESCE 段，时间戳用 update_time")
    void updateCarriesCoalesceForNewColumns() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        dao.update("rule1001", null, null, "VALIDATION", "age > 0", null, null, null, null,
            "RULE_OBJECT", "BUSINESS", "domain", "3", "deadbeef", "PUBLISHED");
        verify(jdbc).update(sql.capture(), args.capture());

        String statement = sql.getValue();
        assertTrue(statement.contains("rule_carrier = COALESCE(?, rule_carrier)"), statement);
        assertTrue(statement.contains("rule_layer = COALESCE(?, rule_layer)"), statement);
        assertTrue(statement.contains("scope = COALESCE(?, scope)"), statement);
        assertTrue(statement.contains("version_no = COALESCE(?, version_no)"), statement);
        assertTrue(statement.contains("git_ref = COALESCE(?, git_ref)"), statement);
        assertTrue(statement.contains("status = COALESCE(?, status)"), statement);
        assertTrue(statement.contains("update_time = NOW()"), statement);
        assertFalse(statement.contains("updated_at"), "42703 断裂列禁回潮：" + statement);

        Object[] params = args.getValue();
        assertEquals(15, params.length, "14 段 COALESCE + WHERE id");
        assertEquals("RULE_OBJECT", params[8]);
        assertEquals("BUSINESS", params[9]);
        assertEquals("domain", params[10]);
        assertEquals("3", params[11]);
        assertEquals("deadbeef", params[12]);
        assertEquals("PUBLISHED", params[13]);
        assertEquals("rule1001", params[14]);
    }

    // ═══════════════ RowMapper：新列读回 ═══════════════

    @Test
    @DisplayName("ROW_MAPPER 读回双维+就绪件套与 create_time/update_time（非 created_at）")
    @SuppressWarnings("unchecked")
    void rowMapperReadsNewColumns() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("rule1001");
        when(rs.getString("rule_carrier")).thenReturn("KNOWLEDGE_ASSET");
        when(rs.getString("rule_layer")).thenReturn("POLICY");
        when(rs.getString("scope")).thenReturn("tenant");
        when(rs.getString("version_no")).thenReturn("v1");
        when(rs.getString("git_ref")).thenReturn("cafe123");
        when(rs.getString("status")).thenReturn("ACTIVE");
        when(rs.getString("domain")).thenReturn("default");
        when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 31, 9, 0)));
        when(rs.getTimestamp("update_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 31, 9, 30)));

        Field field = OntologyRuleDao.class.getDeclaredField("ROW_MAPPER");
        field.setAccessible(true);
        RowMapper<OntologyRule> mapper = (RowMapper<OntologyRule>) field.get(dao);
        OntologyRule r = mapper.mapRow(rs, 0);

        assertEquals("KNOWLEDGE_ASSET", r.getRuleCarrier());
        assertEquals("POLICY", r.getRuleLayer());
        assertEquals("tenant", r.getScope());
        assertEquals("v1", r.getVersionNo());
        assertEquals("cafe123", r.getGitRef());
        assertEquals("ACTIVE", r.getStatus());
        assertEquals("default", r.getDomain());
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), r.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 30), r.getUpdatedAt());
    }
}
