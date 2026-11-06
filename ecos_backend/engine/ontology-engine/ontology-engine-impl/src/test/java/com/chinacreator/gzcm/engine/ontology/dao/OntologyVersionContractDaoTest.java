package com.chinacreator.gzcm.engine.ontology.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;

/**
 * B28 版本数据面契约门禁 —— {@code OntologyVersionDao} 对 live 表
 * {@code ecos_ontology_version} 的读写面（42703 取证订正）。
 *
 * <p>判据打在捕获的 SQL 实参与 RowMapper 读列上，不打源文本
 * （类 Javadoc 有意提及 created_at 订正缘由——源文本断言会假绿）。三腿：</p>
 * <ul>
 *   <li>读腿：排序/比较一律 {@code create_time}，禁 {@code created_at}；
 *       列投影不再 {@code SELECT *}，快照用 {@code COALESCE(snapshot_json, snapshot)}
 *       兼容 V170.1 停写前存量行；</li>
 *   <li>写腿：INSERT 落 {@code snapshot_json}（V170.1 新写列），时间戳 {@code create_time}，
 *       禁 {@code ?::jsonb}（live snapshot/snapshot_json 均为 TEXT）；</li>
 *   <li>RowMapper：{@code create_time → createdAt}、{@code snapshot → snapshot} 可读回模型。</li>
 * </ul>
 */
class OntologyVersionContractDaoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private OntologyVersionDao dao;

    @BeforeEach
    void injectMockJdbcTemplate() throws Exception {
        dao = new OntologyVersionDao();
        Field f = dao.getClass().getSuperclass().getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(dao, jdbc);
    }

    private String captureQuerySql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), ArgumentMatchers.any(RowMapper.class),
            ArgumentMatchers.<Object>any());
        return sql.getValue();
    }

    private String captureQuerySqlTwoArgs() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), ArgumentMatchers.any(RowMapper.class),
            ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any());
        return sql.getValue();
    }

    private String captureQuerySqlNoArgs() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), ArgumentMatchers.any(RowMapper.class));
        return sql.getValue();
    }

    @Test
    @DisplayName("findByOntology 读腿：排 create_time DESC，无 created_at，不用 SELECT *")
    void findByOntologyUsesLiveColumns() {
        dao.findByOntology("ont_entmgmt");
        String sql = captureQuerySql();
        assertTrue(sql.contains("create_time DESC"), sql);
        assertFalse(sql.contains("created_at"), "live 无 created_at 列：" + sql);
        assertFalse(sql.contains("SELECT *"), "列投影须显式（快照需 COALESCE 双列过渡）：" + sql);
        assertTrue(sql.contains("COALESCE(snapshot_json, snapshot)"), sql);
    }

    @Test
    @DisplayName("findPreviousVersion 读腿：create_time 比较而非 created_at")
    void findPreviousVersionUsesCreateTime() {
        dao.findPreviousVersion("ont_entmgmt", LocalDateTime.now());
        String sql = captureQuerySqlTwoArgs();
        assertTrue(sql.contains("create_time < ?"), sql);
        assertFalse(sql.contains("created_at"), sql);
    }

    @Test
    @DisplayName("findAll 读腿：排序用 create_time")
    void findAllUsesCreateTime() {
        dao.findAll();
        String sql = captureQuerySqlNoArgs();
        assertTrue(sql.contains("ORDER BY create_time DESC"), sql);
        assertFalse(sql.contains("created_at"), sql);
    }

    @Test
    @DisplayName("insert 写腿：落 snapshot_json + create_time，禁 ::jsonb")
    void insertWritesLiveColumns() {
        OntologyVersion v = new OntologyVersion();
        v.setId("ver5001");
        v.setOntologyId("ont_entmgmt");
        v.setVersionNo("1.0.0");
        v.setStatus("Draft");
        v.setSnapshot("{\"entities\":[]}");
        v.setChangeLog("c");
        v.setPublisher("admin");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        dao.insert(v);
        verify(jdbc).update(sql.capture(),
            ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any(),
            ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any(),
            ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any(),
            ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any());
        String statement = sql.getValue();
        assertTrue(statement.contains("snapshot_json"), statement);
        assertTrue(statement.contains("create_time"), statement);
        assertFalse(statement.contains("created_at"), "42703 断裂列禁回潮：" + statement);
        assertFalse(statement.contains("::jsonb"), "live 快照列是 TEXT：" + statement);
    }

    @Test
    @DisplayName("RowMapper 读回 create_time → createdAt 与快照")
    void rowMapperReadsLiveColumns() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getString("id")).thenReturn("ver5001");
        when(rs.getString("ontology_id")).thenReturn("ont_entmgmt");
        when(rs.getString("version_no")).thenReturn("1.0.0");
        when(rs.getString("status")).thenReturn("Published");
        when(rs.getString("snapshot")).thenReturn("{\"entities\":[]}");
        when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf("2026-11-04 10:00:00"));

        when(jdbc.query(anyString(), ArgumentMatchers.any(RowMapper.class),
                ArgumentMatchers.<Object>any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<OntologyVersion> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<OntologyVersion> rows = dao.findByOntology("ont_entmgmt");
        OntologyVersion v = rows.get(0);
        assertEquals(LocalDateTime.of(2026, 11, 4, 10, 0, 0), v.getCreatedAt());
        assertEquals("{\"entities\":[]}", v.getSnapshot());
        assertEquals("Published", v.getStatus());
    }

    @Test
    @DisplayName("空结果集返回空列表（非 null）")
    void emptyResultIsEmptyList() {
        when(jdbc.query(anyString(), ArgumentMatchers.any(RowMapper.class),
                ArgumentMatchers.<Object>any()))
                .thenReturn(Collections.emptyList());
        assertTrue(dao.findByOntology("ont_entmgmt").isEmpty());
    }
}
