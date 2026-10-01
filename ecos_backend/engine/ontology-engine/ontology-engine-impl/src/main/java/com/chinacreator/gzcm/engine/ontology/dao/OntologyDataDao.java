package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code OntologyDataService}
 * （本体数据实例表 {@code ecos_ontology.ecos_ontology_data}）的 JDBC 落点。
 *
 * <p><b>P0 前修保护（B0 V200 B4，commit 69e7742e）</b>：SQL 一律携带
 * {@code ecos_ontology.} schema 限定，搬运中逐字保留，禁止回落到 public schema。</p>
 */
@Repository
public class OntologyDataDao extends JdbcTemplateDaoSupport {

    /** 数据行统一列选择（保持列顺序与既有契约一致）。 */
    static final String SELECT_COLUMNS =
            "SELECT id, ontology_id, object_type, record_key, payload, status, " +
            "       create_time, update_time, create_by, update_by ";

    /**
     * 数据记录总数（可选按归属域过滤）。
     *
     * @param filterType 可选，对象类型 ID（对应 ontology_id 列），空/空白 = 全量
     * @return 匹配 is_deleted=0 的行数
     */
    public Integer countRecords(String filterType) {
        boolean hasFilter = filterType != null && !filterType.isBlank();
        final String where = hasFilter
                ? "WHERE ontology_id = ? AND is_deleted = 0"
                : "WHERE is_deleted = 0";
        return hasFilter
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology.ecos_ontology_data " + where, Integer.class, filterType)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology.ecos_ontology_data " + where, Integer.class);
    }

    /**
     * 数据记录分页查询（可选按归属域过滤，按 create_time 倒序）。
     */
    public List<Map<String, Object>> listRecords(String filterType, int limit, int offset) {
        boolean hasFilter = filterType != null && !filterType.isBlank();
        final String where = hasFilter
                ? "WHERE ontology_id = ? AND is_deleted = 0"
                : "WHERE is_deleted = 0";
        return hasFilter
                ? jdbcTemplate.queryForList(
                        SELECT_COLUMNS +
                        "FROM ecos_ontology.ecos_ontology_data " + where + " " +
                        "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                        filterType, limit, offset)
                : jdbcTemplate.queryForList(
                        SELECT_COLUMNS +
                        "FROM ecos_ontology.ecos_ontology_data " + where + " " +
                        "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                        limit, offset);
    }

    /**
     * 按主键查询单条数据记录（is_deleted=0 过滤）。
     *
     * @return 原始行列表（命中 0 或 1 行）
     */
    public List<Map<String, Object>> findRecordById(String id) {
        return jdbcTemplate.queryForList(
                SELECT_COLUMNS +
                "FROM ecos_ontology.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                id);
    }

    /**
     * 插入数据记录（全量 UUID 主键，status 固定 'ACTIVE'，is_deleted=0）。
     */
    public int insertRecord(String id, String objectTypeId, String objectTypeName,
                            String recordKey, String payloadJson, String createdBy) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_ontology.ecos_ontology_data " +
                "(id, ontology_id, object_type, record_key, payload, status, " +
                " create_time, update_time, create_by, update_by, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now(), ?, ?, 0)",
                id, objectTypeId, objectTypeName, recordKey, payloadJson, createdBy, createdBy);
    }

    /**
     * 更新数据记录（COALESCE 风格：null 参数不覆盖已有值；objectTypeId 不可变更）。
     */
    public int updateRecord(String id, String objectTypeName, String createdBy, String payloadJson) {
        return jdbcTemplate.update(
                "UPDATE ecos_ontology.ecos_ontology_data SET " +
                "  object_type = COALESCE(?, object_type), " +
                "  create_by   = COALESCE(?, create_by), " +
                "  update_by   = COALESCE(?, update_by), " +
                "  payload     = ?, " +
                "  update_time = now() " +
                "WHERE id = ? AND is_deleted = 0",
                objectTypeName, createdBy, "system", payloadJson, id);
    }

    /**
     * 按主键计数（存在性检查，is_deleted=0 过滤）。
     */
    public Integer countById(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
    }

    /**
     * 按归属域计数（is_deleted=0 过滤）。
     */
    public Integer countByObjectType(String objectTypeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology.ecos_ontology_data " +
                "WHERE ontology_id = ? AND is_deleted = 0",
                Integer.class, objectTypeId);
    }
}
