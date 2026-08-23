package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 本体映射持久化服务 — 封装 ecos_entity_table_mapping 表的 SQL 操作。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.ontology.controller.OntologyMappingController}
 * 调用，Controller 层不再直接持有 {@link JdbcTemplate}。</p>
 */
@Service
public class OntologyMappingService {

    private final JdbcTemplate jdbc;

    public OntologyMappingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询映射列表（可按 objectId / sourceType 过滤）。返回原始行。
     */
    public List<Map<String, Object>> listMappings(String objectId, String sourceType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_entity_table_mapping WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (objectId != null && !objectId.isBlank()) {
            sql.append(" AND entity_code=?");
            params.add(objectId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND domain_code=?");
            params.add(sourceType);
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 查询单个映射详情，不存在返回 null。
     */
    public Map<String, Object> findMappingById(String id) {
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 插入新映射记录。
     */
    public void insertMapping(String id, String objectId, String sourceName,
                              String sourceType, String sourceUri, String fieldMappingsJson) {
        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, objectId, sourceName, sourceType, "",
                sourceName, sourceUri, fieldMappingsJson);
    }

    /**
     * 更新映射记录（拼接 SQL），返回更新后的行，不存在返回 null。
     */
    public Map<String, Object> updateMapping(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    /**
     * 删除映射记录。返回被删除的行（用于日志），不存在返回 null。
     */
    public Map<String, Object> deleteMapping(String id) {
        Map<String, Object> existing;
        try {
            existing = jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        jdbc.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
        return existing;
    }
}
