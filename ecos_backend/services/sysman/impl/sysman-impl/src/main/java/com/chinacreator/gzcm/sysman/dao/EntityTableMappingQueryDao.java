package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 实体-表映射 DAO — 承接 EntityTableMappingQueryService 中的 JdbcTemplate 操作。
 */
@Repository
public class EntityTableMappingQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listMappings(String entityCode) {
        String sql;
        Object[] params;
        if (entityCode != null && !entityCode.isBlank()) {
            sql = "SELECT * FROM ecos_entity_table_mapping WHERE entity_code=? ORDER BY created_at DESC";
            params = new Object[]{entityCode};
        } else {
            sql = "SELECT * FROM ecos_entity_table_mapping ORDER BY created_at DESC";
            params = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, params);
    }

    public Integer countExistingMapping(String entityCode, String datasourceId, String resourceName) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_entity_table_mapping WHERE entity_code=? AND datasource_id=? AND resource_name=?",
            Integer.class, entityCode, datasourceId, resourceName);
    }

    public void insertMapping(String id, String entityCode, String entityName, String domainCode,
                              String datasourceId, String resourceName, String tableSchema,
                              String fieldMappingsJson) {
        jdbcTemplate.update(
            "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
            id, entityCode, entityName, domainCode, datasourceId, resourceName, tableSchema, fieldMappingsJson);
    }

    public Map<String, Object> getMappingById(String id) {
        return jdbcTemplate.queryForMap(
            "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    public Integer countMappingById(String id) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_entity_table_mapping WHERE id=?",
            Integer.class, id);
    }

    public void deleteMappingById(String id) {
        jdbcTemplate.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    public List<Map<String, Object>> queryAllActiveDataResources() {
        String resourcesSql = """
            SELECT r.resource_id, r.resource_name, r.resource_type, r.source_path,
                   r.description, r.field_count, r.record_count, r.status,
                   r.datasource_id, d.datasource_name
            FROM td_data_resource r
            LEFT JOIN td_datasource d ON r.datasource_id = d.datasource_id
            WHERE r.status = 'ACTIVE'
            ORDER BY d.datasource_name, r.resource_name
            """;
        return jdbcTemplate.queryForList(resourcesSql);
    }

    public List<Map<String, Object>> queryMappedResources(String entityCode) {
        String mappedSql = """
            SELECT datasource_id, resource_name
            FROM ecos_entity_table_mapping
            WHERE entity_code = ?
            """;
        return jdbcTemplate.queryForList(mappedSql, entityCode);
    }

    // ── PMO-E2 通用委托方法（供 Controller 调用）──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbcTemplate.queryForMap(sql, args);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
