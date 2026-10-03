package com.chinacreator.gzcm.engine.ontology.repository;

import com.chinacreator.gzcm.engine.ontology.model.Caliber;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 口径主表仓库（表 {@code ecos_ontology.ecos_caliber}，V167.1；schema 经 {@link OntologySchemaSupport} 注入）。
 * SQL 纪律：显式列清单（禁 SELECT *）、参数化（禁拼接）、无 PG 私有转型（禁 ::）、schema 限定（禁裸名）。
 */
@Repository
public class CaliberRepository {

    private static final String COLS =
            "id, code, name, status, unit, currency, owner_role, dimension_json, " +
            "create_time, update_time, create_by, update_by, is_deleted, version_no, domain";

    private final JdbcTemplate jdbc;
    private final OntologySchemaSupport schema;

    public CaliberRepository(JdbcTemplate jdbc, OntologySchemaSupport schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    private final RowMapper<Caliber> MAPPER = (rs, rn) -> {
        Caliber c = new Caliber();
        c.setId(rs.getString("id"));
        c.setCode(rs.getString("code"));
        c.setName(rs.getString("name"));
        c.setStatus(rs.getString("status"));
        c.setUnit(rs.getString("unit"));
        c.setCurrency(rs.getString("currency"));
        c.setOwnerRole(rs.getString("owner_role"));
        c.setDimensionJson(rs.getString("dimension_json"));
        c.setCreateTime(ts(rs.getTimestamp("create_time")));
        c.setUpdateTime(ts(rs.getTimestamp("update_time")));
        c.setCreateBy(rs.getString("create_by"));
        c.setUpdateBy(rs.getString("update_by"));
        c.setIsDeleted(intOf(rs.getObject("is_deleted")));
        c.setVersionNo(rs.getString("version_no"));
        c.setDomain(rs.getString("domain"));
        return c;
    };

    private static LocalDateTime ts(Timestamp t) { return t != null ? t.toLocalDateTime() : null; }

    private static Integer intOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    public List<Caliber> findAll() {
        String table = schema.t("ecos_caliber");
        return jdbc.query("SELECT " + COLS + " FROM " + table + " WHERE is_deleted = 0 ORDER BY create_time DESC", MAPPER);
    }

    public Optional<Caliber> findByCode(String code) {
        String table = schema.t("ecos_caliber");
        List<Caliber> list = jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE code = ? AND is_deleted = 0", MAPPER, code);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Caliber> findById(String id) {
        String table = schema.t("ecos_caliber");
        List<Caliber> list = jdbc.query(
                "SELECT " + COLS + " FROM " + table + " WHERE id = ? AND is_deleted = 0", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(Caliber c) {
        String table = schema.t("ecos_caliber");
        return jdbc.update("""
                INSERT INTO %s (id, code, name, status, unit, currency, owner_role, dimension_json,
                    create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, '1', 'default')
                """.formatted(table),
                c.getId(), c.getCode(), c.getName(),
                c.getStatus() != null ? c.getStatus() : "ACTIVE",
                c.getUnit(), c.getCurrency(), c.getOwnerRole(), c.getDimensionJson(),
                tsVal(c.getCreateTime()), tsVal(c.getUpdateTime()), c.getCreateBy(), c.getUpdateBy());
    }

    /** 仅允许改可变列；APPROVED 版本行不可变由 service 层守卫，本方法不触碰 caliber_version。 */
    public int update(Caliber c) {
        String table = schema.t("ecos_caliber");
        return jdbc.update("""
                UPDATE %s SET name = ?, status = ?, unit = ?, currency = ?, owner_role = ?, dimension_json = ?,
                    update_time = CURRENT_TIMESTAMP, update_by = ?
                WHERE id = ? AND is_deleted = 0
                """.formatted(table),
                c.getName(), c.getStatus(), c.getUnit(), c.getCurrency(), c.getOwnerRole(),
                c.getDimensionJson(), c.getUpdateBy(), c.getId());
    }

    /** 逻辑删除（DR05）。 */
    public int softDelete(String id, String by) {
        String table = schema.t("ecos_caliber");
        return jdbc.update(
                "UPDATE " + table + " SET is_deleted = 1, update_time = CURRENT_TIMESTAMP, update_by = ? WHERE id = ? AND is_deleted = 0",
                by, id);
    }

    private static Timestamp tsVal(LocalDateTime t) { return t != null ? Timestamp.valueOf(t) : new Timestamp(System.currentTimeMillis()); }
}
