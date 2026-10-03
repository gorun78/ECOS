package com.chinacreator.gzcm.engine.ontology.repository;

import com.chinacreator.gzcm.engine.ontology.model.MetricDefinition;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 指标定义合规表仓库（表 {@code ecos_ontology.ecos_metric_definition}，V168.1 唯一可写入口，F03-09；
 * 旧 {@code metric_definition} 停写只读，本仓库不碰）。
 */
@Repository
public class MetricDefinitionRepository {

    private static final String COLS =
            "id, code, name, expression, aggregation, entity_code, caliber_id, formula_version, caliber_snapshot, status, " +
            "create_time, update_time, create_by, update_by, is_deleted, version_no, domain";

    private final JdbcTemplate jdbc;
    private final OntologySchemaSupport schema;

    public MetricDefinitionRepository(JdbcTemplate jdbc, OntologySchemaSupport schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    private final RowMapper<MetricDefinition> MAPPER = (rs, rn) -> {
        MetricDefinition m = new MetricDefinition();
        m.setId(rs.getString("id"));
        m.setCode(rs.getString("code"));
        m.setName(rs.getString("name"));
        m.setExpression(rs.getString("expression"));
        m.setAggregation(rs.getString("aggregation"));
        m.setEntityCode(rs.getString("entity_code"));
        m.setCaliberId(rs.getString("caliber_id"));
        m.setFormulaVersion(rs.getString("formula_version"));
        m.setCaliberSnapshot(rs.getString("caliber_snapshot"));
        m.setStatus(rs.getString("status"));
        m.setCreateTime(ts(rs.getTimestamp("create_time")));
        m.setUpdateTime(ts(rs.getTimestamp("update_time")));
        m.setCreateBy(rs.getString("create_by"));
        m.setUpdateBy(rs.getString("update_by"));
        m.setIsDeleted(intOf(rs.getObject("is_deleted")));
        m.setVersionNo(rs.getString("version_no"));
        m.setDomain(rs.getString("domain"));
        return m;
    };

    private static LocalDateTime ts(Timestamp t) { return t != null ? t.toLocalDateTime() : null; }
    private static Integer intOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    public List<MetricDefinition> findAll() {
        String table = schema.t("ecos_metric_definition");
        return jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE is_deleted = 0 ORDER BY code ASC", MAPPER);
    }

    public Optional<MetricDefinition> findByCode(String code) {
        String table = schema.t("ecos_metric_definition");
        List<MetricDefinition> list = jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE code = ? AND is_deleted = 0", MAPPER, code);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(MetricDefinition m) {
        String table = schema.t("ecos_metric_definition");
        return jdbc.update("""
                INSERT INTO %s (id, code, name, expression, aggregation, entity_code, caliber_id, formula_version,
                    caliber_snapshot, status, create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, '1', 'default')
                """.formatted(table),
                m.getId(), m.getCode(), m.getName(), m.getExpression(), m.getAggregation(), m.getEntityCode(),
                m.getCaliberId(), m.getFormulaVersion(), m.getCaliberSnapshot(),
                m.getStatus() != null ? m.getStatus() : "DRAFT",
                tsVal(m.getCreateTime()), tsVal(m.getUpdateTime()), m.getCreateBy(), m.getUpdateBy());
    }

    /** 绑定口径版本（F03-01 规则 2：显式绑版，升版不自动传播）。status 一同前置。 */
    public int bindCaliber(String id, String caliberId, String formulaVersion, String caliberSnapshot,
                           String status, String by) {
        String table = schema.t("ecos_metric_definition");
        return jdbc.update("""
                UPDATE %s SET caliber_id = ?, formula_version = ?, caliber_snapshot = ?, status = ?,
                    update_time = CURRENT_TIMESTAMP, update_by = ?
                WHERE id = ? AND is_deleted = 0
                """.formatted(table),
                caliberId, formulaVersion, caliberSnapshot, status, by, id);
    }

    public int updateDefinition(MetricDefinition m) {
        String table = schema.t("ecos_metric_definition");
        return jdbc.update("""
                UPDATE %s SET name = ?, expression = ?, aggregation = ?, entity_code = ?,
                    update_time = CURRENT_TIMESTAMP, update_by = ?
                WHERE id = ? AND is_deleted = 0
                """.formatted(table),
                m.getName(), m.getExpression(), m.getAggregation(), m.getEntityCode(),
                m.getUpdateBy(), m.getId());
    }

    public int softDelete(String id, String by) {
        String table = schema.t("ecos_metric_definition");
        return jdbc.update(
                "UPDATE " + table + " SET is_deleted = 1, update_time = CURRENT_TIMESTAMP, update_by = ? WHERE id = ? AND is_deleted = 0",
                by, id);
    }

    private static Timestamp tsVal(LocalDateTime t) { return t != null ? Timestamp.valueOf(t) : new Timestamp(System.currentTimeMillis()); }
}
