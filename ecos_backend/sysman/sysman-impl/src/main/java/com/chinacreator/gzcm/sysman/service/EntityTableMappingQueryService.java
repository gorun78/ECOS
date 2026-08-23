package com.chinacreator.gzcm.sysman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实体-表映射查询服务 — 从 EntityTableMappingController 下沉的 JdbcTemplate 访问。
 * SQL 与原 Controller 保持一致。
 */
@Service
public class EntityTableMappingQueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EntityTableMappingQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 列出指定实体的所有映射，或列出全部映射 */
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
        return jdbc.queryForList(sql, params);
    }

    /** 检查映射是否已存在：返回满足条件的行数 */
    public Integer countExistingMapping(String entityCode, String datasourceId, String resourceName) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_entity_table_mapping WHERE entity_code=? AND datasource_id=? AND resource_name=?",
            Integer.class, entityCode, datasourceId, resourceName);
    }

    /** 序列化 fieldMappings 为 JSON 字符串 */
    public String serializeFieldMappings(Object fieldMappingsRaw) throws Exception {
        if (fieldMappingsRaw == null) {
            return null;
        }
        if (fieldMappingsRaw instanceof String) {
            return (String) fieldMappingsRaw;
        }
        return objectMapper.writeValueAsString(fieldMappingsRaw);
    }

    /** 插入新的实体-表映射 */
    public void insertMapping(String id, String entityCode, String entityName, String domainCode,
                              String datasourceId, String resourceName, String tableSchema,
                              String fieldMappingsJson) {
        jdbc.update(
            "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
            id, entityCode, entityName, domainCode, datasourceId, resourceName, tableSchema, fieldMappingsJson);
    }

    /** 查询刚插入的映射行 */
    public Map<String, Object> getMappingById(String id) {
        return jdbc.queryForMap(
            "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    /** 检查映射是否存在：返回行数 */
    public Integer countMappingById(String id) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_entity_table_mapping WHERE id=?",
            Integer.class, id);
    }

    /** 删除指定ID的映射 */
    public void deleteMappingById(String id) {
        jdbc.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    /** 查询所有 ACTIVE 数据资源（含数据源名称） */
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
        return jdbc.queryForList(resourcesSql);
    }

    /** 查询指定实体已映射的资源 */
    public List<Map<String, Object>> queryMappedResources(String entityCode) {
        String mappedSql = """
            SELECT datasource_id, resource_name
            FROM ecos_entity_table_mapping
            WHERE entity_code = ?
            """;
        return jdbc.queryForList(mappedSql, entityCode);
    }
}
